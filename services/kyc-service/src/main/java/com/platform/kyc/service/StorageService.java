package com.platform.kyc.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.UUID;

/**
 * Stores KYC documents in S3 (MinIO-compatible in dev).
 * The raw S3 object key is encrypted before being persisted to the database.
 */
@Service
@Slf4j
public class StorageService {

    private final S3Client s3;
    private final S3Presigner presigner;
    private final EncryptionService encryptionService;
    private final String bucket;
    private final String provider;

    public StorageService(
            EncryptionService encryptionService,
            @Value("${app.storage.bucket}") String bucket,
            @Value("${app.storage.endpoint}") String endpoint,
            @Value("${app.storage.access-key}") String accessKey,
            @Value("${app.storage.secret-key}") String secretKey,
            @Value("${app.storage.region}") String region,
            @Value("${app.storage.provider:MOCK}") String provider) {

        this.encryptionService = encryptionService;
        this.bucket = bucket;
        this.provider = provider;

        var credentials = StaticCredentialsProvider.create(
            AwsBasicCredentials.create(accessKey, secretKey));
        var awsRegion = Region.of(region);

        this.s3 = S3Client.builder()
            .credentialsProvider(credentials)
            .endpointOverride(URI.create(endpoint))
            .region(awsRegion)
            .forcePathStyle(true) // required for MinIO
            .build();

        this.presigner = S3Presigner.builder()
            .credentialsProvider(credentials)
            .endpointOverride(URI.create(endpoint))
            .region(awsRegion)
            .build();
    }

    /**
     * Upload a document to S3 and return the AES-encrypted S3 key for DB storage.
     * The encrypted key is what goes in kyc_documents.s3_ref_encrypted — never the raw key.
     */
    public String upload(UUID applicantId, String documentType, MultipartFile file) throws IOException {
        String objectKey = "kyc/%s/%s/%s".formatted(
            applicantId, documentType.toLowerCase(),
            UUID.randomUUID() + "-" + sanitise(file.getOriginalFilename()));

        if ("MOCK".equals(provider)) {
            log.info("[MOCK] Skipping real S3 upload. Key would be: {}", objectKey);
        } else {
            s3.putObject(
                PutObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .contentType(file.getContentType())
                    .serverSideEncryption("aws:kms") // SSE-KMS in prod; MinIO ignores this header
                    .build(),
                RequestBody.fromInputStream(file.getInputStream(), file.getSize()));
            log.info("Uploaded KYC document: bucket={} key={}", bucket, objectKey);
        }

        // Encrypt the object key before returning for DB storage.
        return encryptionService.encrypt(objectKey);
    }

    /**
     * Generate a pre-signed URL for a document (ADMIN access only — caller must enforce).
     * Expiry is deliberately short to reduce exposure window.
     */
    public String generatePresignedUrl(String encryptedRef) {
        String objectKey = encryptionService.decrypt(encryptedRef);

        if ("MOCK".equals(provider)) {
            return "https://storage.mock.internal/" + objectKey;
        }

        return presigner.presignGetObject(GetObjectPresignRequest.builder()
            .getObjectRequest(GetObjectRequest.builder()
                .bucket(bucket)
                .key(objectKey)
                .build())
            .signatureDuration(Duration.ofMinutes(5))
            .build())
            .url()
            .toString();
    }

    private String sanitise(String filename) {
        if (filename == null) return "document";
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
