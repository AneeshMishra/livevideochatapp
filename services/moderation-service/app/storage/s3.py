"""S3 storage helper.

Wraps boto3 (sync) in a thread executor. Works with real AWS S3 or MinIO
(set S3_ENDPOINT_URL to the MinIO address).
"""
import asyncio
import logging
import uuid
from typing import Optional

import boto3
from botocore.config import Config
from botocore.exceptions import ClientError

from app.config import get_settings

logger = logging.getLogger(__name__)


class S3Storage:

    def __init__(self) -> None:
        settings = get_settings()
        kwargs: dict = {
            "region_name": settings.aws_region,
            "aws_access_key_id": settings.s3_access_key or settings.aws_access_key_id or None,
            "aws_secret_access_key": settings.s3_secret_key or settings.aws_secret_access_key or None,
            "config": Config(signature_version="s3v4"),
        }
        if settings.s3_endpoint_url:
            kwargs["endpoint_url"] = settings.s3_endpoint_url
        self._client = boto3.client("s3", **kwargs)
        self._bucket = settings.s3_bucket

    async def upload_frame(self, image_bytes: bytes, room_id: str, extension: str = "jpg") -> str:
        """Upload a video frame and return the S3 key."""
        key = f"frames/{room_id}/{uuid.uuid4()}.{extension}"
        await asyncio.get_event_loop().run_in_executor(
            None, self._put_object, key, image_bytes, "image/jpeg"
        )
        return key

    async def upload_image(self, image_bytes: bytes, context: str = "image") -> str:
        key = f"images/{context}/{uuid.uuid4()}.jpg"
        await asyncio.get_event_loop().run_in_executor(
            None, self._put_object, key, image_bytes, "image/jpeg"
        )
        return key

    def _put_object(self, key: str, body: bytes, content_type: str) -> None:
        try:
            self._client.put_object(
                Bucket=self._bucket,
                Key=key,
                Body=body,
                ContentType=content_type,
                # Objects are private by default; never make them public
            )
        except ClientError as exc:
            logger.warning("S3 upload failed (%s): %s — continuing without storage", key, exc)

    def ensure_bucket(self) -> None:
        try:
            self._client.head_bucket(Bucket=self._bucket)
        except ClientError:
            try:
                self._client.create_bucket(Bucket=self._bucket)
                logger.info("Created S3 bucket: %s", self._bucket)
            except ClientError as exc:
                logger.warning("Could not create bucket %s: %s", self._bucket, exc)
