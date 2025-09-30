from pydantic import Field, MongoDsn
from typing import Optional
from pydantic_settings import BaseSettings


class AppSettings(BaseSettings):
    """
    Defines and validates application settings using Pydantic.
    It automatically reads from environment variables.
    """

    # --- MongoDB Configuration ---
    MONGO_HOST: str = "mongodb"
    MONGO_PORT: int = 27017
    DB_NAME: str = Field(
        "streaming_poc",
        description="Name of the MongoDB database to connect to.",
    )
    # --- Kafka Configuration ---
    KAFKA_BOOTSTRAP_SERVERS: str = Field(
        ..., description="Comma-separated list of Kafka brokers."
    )
    KAFKA_FASTAPI_CONSUMER_GROUP: str = "fastapi-consumer-group"
    # --- Topics ---
    KAFKA_AGGREGATION_TOPIC: str = Field(
        ..., description="Topic for aggregated data from Flink."
    )
    KAFKA_OUTPUT_TOPIC: str = Field(..., description="Topic for transformed data.")

    # --- Dynamically Constructed Properties (The "magic" happens here) ---
    @property
    def MONGO_URI(self) -> MongoDsn:
        uri = f"mongodb://{self.MONGO_HOST}:{self.MONGO_PORT}"
        return MongoDsn(uri)

    class Config:
        # This tells Pydantic to be case-insensitive when reading env vars.
        # So, MONGO_HOST in the model will match MONGO_HOST in the environment.
        case_sensitive = False
        # Optional: You can also specify a .env file for local development
        # env_file = ".env"
        # env_file_encoding = "utf-8"


# Create a single, reusable instance of the settings.
# Pydantic will automatically read from the environment and validate on instantiation.
# If a required variable (one without a default value) is missing,
# Pydantic will raise a very clear ValidationError.
settings = AppSettings()

# You can still print for debugging during startup if you wish.
print("INFO: MongoDB URI:", settings.MONGO_URI)
print("INFO: Kafka Bootstrap Servers:", settings.KAFKA_BOOTSTRAP_SERVERS)
