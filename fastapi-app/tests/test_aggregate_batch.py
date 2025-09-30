#!/usr/bin/env python3
"""
Test aggregate data batching to ensure it handles batch_summary format correctly.
"""
import asyncio
import os
import sys

# Set environment variables
os.environ.update({
    "KAFKA_BOOTSTRAP_SERVERS": "localhost:9092",
    "KAFKA_AGGREGATION_TOPIC": "test-agg",
    "KAFKA_OUTPUT_TOPIC": "test-output"
})

sys.path.append('/home/nikos/streaming_poc/fastapi-app')
from app.manager import ConnectionManager


class MockWebSocket:
    def __init__(self):
        self.sent_messages = []
        self.is_closed = False

    async def accept(self):
        pass

    async def send_json(self, message):
        self.sent_messages.append(message)

    def close(self):
        self.is_closed = True


async def test_aggregate_batching():
    """Test that aggregate data is handled correctly"""
    print("Testing aggregate data batching...")
    
    manager = ConnectionManager(buffer_size=2, buffer_interval=0.3)
    mock_ws = MockWebSocket()
    sensor_id = "agg_sensor_123"
    
    try:
        await manager.connect(mock_ws, sensor_id)
        
        # Send aggregate/summary data
        agg_data = [
            {"sensor_id": sensor_id, "sum_workload": 15.5, "last_workload": 5.2, "timestamp": "2025-01-01T10:00:01"},
            {"sensor_id": sensor_id, "sum_workload": 18.7, "last_workload": 3.2, "timestamp": "2025-01-01T10:00:02"}
        ]
        
        for data in agg_data:
            await manager.broadcast_buffered(sensor_id, data)
        
        # Should send immediately (buffer full)
        if mock_ws.sent_messages:
            sent_message = mock_ws.sent_messages[0]
            print(f"Aggregate message keys: {list(sent_message.keys())}")
            
            # For aggregate data, should send the last message directly (not as a batch)
            assert "sum_workload" in sent_message
            assert "last_workload" in sent_message
            assert sent_message["sum_workload"] == 18.7  # Should be the last message
            print("✓ Aggregate data handled correctly (latest message sent)")
        else:
            print("✗ No aggregate message was sent!")
            
    finally:
        await manager.disconnect(mock_ws, sensor_id)
        if manager.buffer_task:
            manager.buffer_task.cancel()
            try:
                await manager.buffer_task
            except asyncio.CancelledError:
                pass


async def main():
    print("=== Aggregate Data Batching Test ===\n")
    
    try:
        await test_aggregate_batching()
        print("\n=== Aggregate test completed successfully! ===")
    except Exception as e:
        print(f"\n=== Aggregate test failed: {e} ===")
        return 1
    
    return 0


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    exit(exit_code)
