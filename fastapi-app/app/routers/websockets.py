import asyncio
from app.manager import aggregate_manager, raw_manager, ConnectionManager
from fastapi import APIRouter, WebSocket, WebSocketDisconnect

router = APIRouter()

KEEP_ALIVE_TIMEOUT_SECONDS = 60

async def handle_keep_alive_websocket(websocket: WebSocket, sensor_id: str, manager: ConnectionManager):
    """
    A robust handler that expects periodic keep-alive messages from the client.
    """
    await manager.connect(websocket, sensor_id)
    await websocket.send_json({
        "message": f"WebSocket connection established for {manager.name} data. Please send a keep-alive message every <{KEEP_ALIVE_TIMEOUT_SECONDS} seconds."
    })
    try:
        while True:
            # wait_for adds a timeout to the receive operation.
            message = await asyncio.wait_for(
                websocket.receive_text(), 
                timeout=KEEP_ALIVE_TIMEOUT_SECONDS
            )
            # You can optionally check the message content, e.g., if message == "ping":
            print(f"Received keep-alive for sensor '{sensor_id}': {message}")

    except asyncio.TimeoutError:
        print(f"Keep-alive timeout for sensor '{sensor_id}'. Connection is stale. Closing.")
    except WebSocketDisconnect:
        print(f"Client for sensor '{sensor_id}' disconnected cleanly.")
    finally:
        await manager.disconnect(websocket, sensor_id)


@router.websocket("/ws/raw/{sensor_id}")
async def raw_websocket_endpoint(websocket: WebSocket, sensor_id: str):
    await handle_keep_alive_websocket(websocket, sensor_id, raw_manager)


@router.websocket("/ws/aggregate/{sensor_id}")
async def aggregate_websocket_endpoint(websocket: WebSocket, sensor_id: str):
    await handle_keep_alive_websocket(websocket, sensor_id, aggregate_manager)