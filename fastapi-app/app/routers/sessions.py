# app/routers/sessions.py
from fastapi import APIRouter, HTTPException, status

# Assuming you import the shared db_manager instance
from app.db_manager import db_manager
from typing import List, Dict, Any  # Import List, Dict, Any

router = APIRouter(
    prefix="/sessions",  # Good practice to add prefix
    tags=["sessions"],  # Good practice to add tags
)

# Health check endpoints
@router.get("/health", tags=["health"])
async def health_check():
    """Health check endpoint for Kubernetes probes"""
    try:
        # Check database connectivity
        db_status = "connected" if db_manager.is_connected() else "disconnected"
        return {
            "status": "healthy",
            "database": db_status,
            "service": "fastapi-sessions"
        }
    except Exception as e:
        return {
            "status": "unhealthy", 
            "error": str(e),
            "service": "fastapi-sessions"
        }

@router.get("/ready", tags=["health"]) 
async def readiness_check():
    """Readiness check endpoint - ensures service is ready to handle requests"""
    try:
        # More thorough check - actually test database query
        await db_manager.list_active_sessions()
        return {
            "status": "ready",
            "database": "operational",
            "service": "fastapi-sessions"
        }
    except Exception as e:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail=f"Service not ready: {str(e)}"
        )


# Use a more specific response model if you have Pydantic models defined
@router.get("", response_model=List[Dict[str, Any]])  # Endpoint is now just /sessions
async def get_active_sessions():
    # Await the result of the async database call
    active_sessions = await db_manager.list_active_sessions()
    return active_sessions

