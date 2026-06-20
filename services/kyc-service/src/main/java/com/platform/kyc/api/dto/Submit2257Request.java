package com.platform.kyc.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record Submit2257Request(

        @NotBlank @Size(max = 200)
        String legalName,

        /** ISO 8601 date: YYYY-MM-DD */
        @NotBlank @Pattern(regexp = "\\d{4}-\\d{2}-\\d{2}")
        String dateOfBirth,

        String address,

        /** PASSPORT | DRIVERS_LICENSE | NATIONAL_ID */
        @NotBlank @Size(max = 50)
        String documentTypeCode,

        @NotBlank @Size(max = 100)
        String documentNumber,

        /** ISO 3166-1 alpha-3 */
        @NotBlank @Size(min = 3, max = 3)
        String issuingCountry
) {}
