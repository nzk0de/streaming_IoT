# app/manager.py
import asyncio
from collections import defaultdict

from fastapi import WebSocket


class ConnectionManager:
    """
    Manages active WebSocket connections keyed by sensor_id.
    """

    def __init__(self, buffer_size=100, buffer_interval=0.1):
        self.active_connections = defaultdict(list)
        self.lock = asyncio.Lock()
        # Message buffering for high-frequency data
        self.message_buffer = defaultdict(list)
        self.buffer_size = buffer_size
        self.buffer_interval = buffer_interval
        self.buffer_task = None
        self._loop_started = False

    @staticmethod
    def normalize_key(sensor_id: str, session_id: str) -> tuple:
        if isinstance(session_id, str) and session_id.startswith("0_"):
            session_id = session_id.split("_", 1)[1]
        return (sensor_id, session_id)

    async def connect(
        self,
        websocket: WebSocket,
        sensor_id: str,
        # self, websocket: WebSocket, sensor_id: str, sensor_session_id: str
    ):

        await websocket.accept()
        # key = self.normalize_key(sensor_id, sensor_session_id)
        key = sensor_id
        print(f"[CONNECT] Registered: {key}")

        # Ensure buffer flush loop is running
        if not self._loop_started:
            self.start_buffer_flush()

        async with self.lock:
            self.active_connections[key].append(websocket)

    async def disconnect(
        self,
        websocket: WebSocket,
        sensor_id: str,
        # self, websocket: WebSocket, sensor_id: str, sensor_session_id: str
    ):
        # key = self.normalize_key(sensor_id, sensor_session_id)
        key = sensor_id
        async with self.lock:
            conns = self.active_connections.get(key, [])
            if websocket in conns:
                conns.remove(websocket)
            if not conns:
                self.active_connections.pop(key, None)

    async def broadcast(self, sensor_id: str, message: dict):
        # async def broadcast(self, sensor_id: str, sensor_session_id: str, message: dict):
        print(f"[DEBUG] All active keys: {list(self.active_connections.keys())}")

        async with self.lock:
            # key = self.normalize_key(sensor_id, sensor_session_id)
            key = sensor_id
            if key not in self.active_connections:
                return
            conns = self.active_connections[key].copy()  # Copy to avoid lock contention
        
        # Send to all WebSockets concurrently (outside the lock)
        if conns:
            print(f"[BROADCAST] Broadcasting to {len(conns)} connections")
            tasks = []
            for ws in conns:
                task = asyncio.create_task(self._safe_send(ws, message))
                tasks.append(task)
            
            # Wait for all sends to complete
            results = await asyncio.gather(*tasks, return_exceptions=True)
            
            # Remove failed connections
            failed_connections = []
            for i, result in enumerate(results):
                if isinstance(result, Exception):
                    failed_connections.append(conns[i])
            
            if failed_connections:
                async with self.lock:
                    current_conns = self.active_connections.get(key, [])
                    for ws in failed_connections:
                        if ws in current_conns:
                            current_conns.remove(ws)
                    if not current_conns:
                        self.active_connections.pop(key, None)

    async def _safe_send(self, websocket: WebSocket, message: dict):
        """Safely send message to WebSocket with timeout"""
        try:
            # Add timeout to prevent slow clients from blocking
            await asyncio.wait_for(websocket.send_json(message), timeout=1.0)
        except asyncio.TimeoutError:
            print("WebSocket send timeout - client too slow")
            raise
        except Exception as e:
            print(f"WebSocket send error: {e}")
            raise

    def _create_batch_message(self, sensor_id: str, messages: list) -> dict:
        """Create batch message in format expected by frontend"""
        if not messages:
            return {}
            
        # Detect message type based on content to determine format
        first_message = messages[0] if messages else {}
        
        # Check if this is aggregate/summary data
        if any(key in first_message for key in ['sum_workload', 'last_workload', 'workload']):
            # For aggregate data, frontend expects individual messages, not batches
            # Return the last (most recent) aggregate message
            return messages[-1]
        else:
            # For raw sensor data, use transformed-data batch format
            return {
                "type": "transformed-data",
                "samples": messages,
                "sensor_id": sensor_id,
                "batch_size": len(messages)
            }

    def start_buffer_flush(self):
        """Start the buffer flushing task"""
        try:
            if not self._loop_started and (self.buffer_task is None or self.buffer_task.done()):
                self.buffer_task = asyncio.create_task(self._buffer_flush_loop())
                self._loop_started = True
        except RuntimeError:
            # No event loop running, will start later when needed
            pass

    async def _buffer_flush_loop(self):
        """Periodically flush buffered messages"""
        while True:
            try:
                await asyncio.sleep(self.buffer_interval)
                await self._flush_buffers()
            except asyncio.CancelledError:
                break
            except Exception as e:
                print(f"Buffer flush error: {e}")

    async def _flush_buffers(self):
        """Flush all buffered messages"""
        if not self.message_buffer:
            return
            
        # Get all buffered messages
        buffers_to_flush = {}
        async with self.lock:
            for key, messages in self.message_buffer.items():
                if messages:
                    buffers_to_flush[key] = messages.copy()
                    messages.clear()
        
        # Send buffered messages
        for key, messages in buffers_to_flush.items():
            if key in self.active_connections:
                conns = self.active_connections[key].copy()
                if conns and messages:
                    # Create batch message in format expected by frontend
                    batch_message = self._create_batch_message(key, messages[-10:])  # Send only last 10 messages
                    
                    tasks = []
                    for ws in conns:
                        task = asyncio.create_task(self._safe_send(ws, batch_message))
                        tasks.append(task)
                    
                    await asyncio.gather(*tasks, return_exceptions=True)

    async def broadcast_buffered(self, sensor_id: str, message: dict):
        """Add message to buffer instead of immediate broadcast"""
        key = sensor_id
        
        async with self.lock:
            self.message_buffer[key].append(message)
            
            # Force flush if buffer is full
            if len(self.message_buffer[key]) >= self.buffer_size:
                messages = self.message_buffer[key].copy()
                self.message_buffer[key].clear()
                
                # Send immediately if buffer is full
                if key in self.active_connections:
                    conns = self.active_connections[key].copy()
                    if conns:
                        batch_message = self._create_batch_message(key, messages)
                        
                        tasks = []
                        for ws in conns:
                            task = asyncio.create_task(self._safe_send(ws, batch_message))
                            tasks.append(task)
                        
                        await asyncio.gather(*tasks, return_exceptions=True)


# Create two separate managers
raw_manager = ConnectionManager()
aggregate_manager = ConnectionManager()
