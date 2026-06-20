from functools import lru_cache
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    port: int = 8090
    log_level: str = "INFO"

    # PostgreSQL (asyncpg driver)
    db_url: str = "postgresql+asyncpg://postgres:postgres@localhost:5441/moderation_db"

    # Kafka
    kafka_bootstrap_servers: str = "localhost:9092"
    kafka_streaming_topic: str = "streaming.events"
    kafka_moderation_topic: str = "moderation.events"
    kafka_group_id: str = "moderation-service"

    # JWT — base64-encoded HMAC-SHA256 secret shared across all services
    jwt_secret: str

    # AI moderation provider: MOCK | REKOGNITION | HIVE_AI
    moderation_provider: str = "MOCK"

    # AWS credentials (required for REKOGNITION provider)
    aws_region: str = "us-east-1"
    aws_access_key_id: str = ""
    aws_secret_access_key: str = ""

    # S3 storage for captured frames
    s3_bucket: str = "platform-moderation"
    s3_endpoint_url: str = ""          # leave empty for real AWS; set to MinIO URL for dev
    s3_access_key: str = "minioadmin"
    s3_secret_key: str = "minioadmin123"

    # Confidence thresholds
    ai_unsafe_threshold: float = 0.70   # above this → queue for human review
    ai_csam_threshold: float = 0.50     # above this → CSAM escalation path

    class Config:
        env_file = ".env"
        env_file_encoding = "utf-8"


@lru_cache
def get_settings() -> Settings:
    return Settings()
