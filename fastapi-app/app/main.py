# app/main.py
import asyncio
import logging
from contextlib import asynccontextmanager

from app.consumer import run_consumer_pipeline, stop_consumer_pipeline

# Import the SHARED instance from db_manager
from app.db_manager import db_manager  # <--- IMPORT THE INSTANCE
from app.routers import sessions, websockets
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pymongo.errors import ConnectionFailure  # Import for error handling

logger = logging.getLogger(__name__)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("Application Lifespan: Startup")
    # --- Start consumer, passing the *same* connected instance ---
    logger.info("Starting consumer pipeline...")
    run_consumer_pipeline()
    # Pass the imported db_manager instance here
    # --- Connect to Database ---
    try:
        await db_manager.connect_to_database()  # <--- ADD THIS LINE
        logger.info("Database connection successful.")
    except ConnectionFailure as e:
        logger.error(f"Failed to connect to MongoDB: {e}")

    except Exception as e:  # Catch other potential errors during connection
        logger.error(f"An unexpected error occurred during DB connection: {e}")
        # raise RuntimeError("Unexpected error during DB connection") from e
    # Optional: Add error handling for startup_task if needed

    yield  # Application runs here

    logger.info("Application Lifespan: Shutdown")
    # --- Stop consumer first (optional, but good practice) ---
    stop_consumer_pipeline()
    logger.info("Application Lifespan: Shutdown complete")


def create_app() -> FastAPI:
    # Create FastAPI app with the lifespan handler
    app = FastAPI(lifespan=lifespan, title="Sensor Data Processor")

    # Add CORS middleware - environment-aware configuration
    import os
    environment = os.getenv("ENVIRONMENT", "production")
    
    if environment == "development":
        # Development: Allow localhost and common dev ports
        allowed_origins = [
            "http://localhost:3000",
            "http://localhost:8501", 
        ]
    else:
        # Production: Only allow specific domains
        allowed_origins = [
            "https://api.streaming-poc.local",
            "https://admin.streaming-poc.local",
            os.getenv("FRONTEND_URL", "https://your-frontend-domain.com")
        ]
    
    app.add_middleware(
        CORSMiddleware,
        allow_origins=allowed_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["Content-Type", "Authorization", "X-Requested-With"],
    )

    # Include routers
    app.include_router(sessions.router)
    app.include_router(websockets.router)
    
    @app.get("/")
    async def root():
        """Root endpoint"""
        return {
            "message": "Real-time IoT Data Streaming Platform",
            "status": "running",
            "docs": "/docs",
            "health_check": "/sessions/health",
            "readiness_check": "/sessions/ready"
        }

    return app


# Instantiate the app
app = create_app()
