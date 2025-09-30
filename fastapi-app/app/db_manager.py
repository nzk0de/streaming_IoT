# app/db_manager.py
import logging
from typing import List, Dict, Any

# Replace pymongo with motor
from motor.motor_asyncio import (
    AsyncIOMotorClient,
    AsyncIOMotorDatabase,
    AsyncIOMotorCollection,
)

# Import PyMongo errors for specific exception handling during connection
from pymongo.errors import ConnectionFailure, ServerSelectionTimeoutError

from app.config import settings

logger = logging.getLogger(__name__)


class DBManager:
    """
    Minimal DB Manager focused on listing active athlete sessions.
    """

    client: AsyncIOMotorClient = None
    db: AsyncIOMotorDatabase = None
    athlete_sessions: AsyncIOMotorCollection = None

    async def connect_to_database(self):
        """Initializes the database connection and the athlete_sessions collection."""
        if self.client:
            logger.warning("Database connection already established.")
            return  # Avoid reconnecting if already connected

        logger.info(f"Attempting to connect to MongoDB at {settings.MONGO_URI}...")
        try:
            self.client = AsyncIOMotorClient(
                str(settings.MONGO_URI),
                serverSelectionTimeoutMS=5000,  # Timeout after 5 seconds
                waitQueueTimeoutMS=100,
            )
            # Optional: Force connection check on startup
            await self.client.admin.command("ismaster")

            self.db = self.client[settings.DB_NAME]
            self.athlete_sessions = self.db["athlete_sessions"]
            logger.info(
                f"Successfully connected to MongoDB database: {settings.DB_NAME} and accessed collection 'athlete_sessions'"
            )

        except (ConnectionFailure, ServerSelectionTimeoutError) as e:
            logger.error(f"MongoDB connection failed: {e}")
            self.client = None  # Ensure client is None if connection failed
            self.db = None
            self.athlete_sessions = None
            raise  # Re-raise the exception to be caught by the lifespan handler in main.py
        except Exception as e:
            logger.error(f"An unexpected error occurred during DB connection: {e}")
            self.client = None
            self.db = None
            self.athlete_sessions = None
            raise

    def is_connected(self) -> bool:
        """Check if database connection is active."""
        return self.client is not None and self.db is not None

    async def close_database_connection(self):
        """Closes the database connection."""
        if self.client:
            logger.info("Closing MongoDB connection...")
            self.client.close()
            self.client = None  # Set client to None after closing
            self.db = None
            self.athlete_sessions = None
            logger.info("MongoDB connection closed.")
        else:
            logger.info("No active MongoDB connection to close.")

    async def list_active_sessions(self) -> List[Dict[str, Any]]:
        """
        Asynchronously list all active athlete sessions.
        Active sessions are those where 'ended_at' is null.
        """

        logger.debug("Querying for active sessions (ended_at: null)...")
        try:
            # Query for documents where 'ended_at' field is explicitly null
            sessions_cursor = self.athlete_sessions.find({})
            # Fetch all results into a list
            # length=None retrieves all matching documents
            results = await sessions_cursor.to_list(length=None)
            logger.info(f"Found {len(results)} active session(s).")

            # No _id conversion needed as per your previous message about custom _ids
            # The raw documents (dictionaries) are returned
            return results

        except Exception as e:
            logger.exception(f"Error occurred while listing active sessions: {e}")
            return []  # Return empty list on error or re-raise


# Create a shared instance of the manager
db_manager = DBManager()
