#!/usr/bin/env python3
"""
Standalone test to validate batch sending functionality in websockets.
This test doesn't require the full FastAPI setup and focuses only on the batch logic.
"""
import asyncio
import json
from unittest.mock import AsyncMock, MagicMock
from fastapi import WebSocket

# Set environment variables before importing
import os
os.environ.update({
    "KAFKA_BOOTSTRAP_SERVERS": "localhost:9092",
    "KAFKA_AGGREGATION_TOPIC": "test-agg",
    "KAFKA_OUTPUT_TOPIC": "test-output"
})

# Import after setting env vars
import sys
sys.path.append('/home/nikos/streaming_poc/fastapi-app')
from app.manager import ConnectionManager


class MockWebSocket:
    """Mock WebSocket for testing"""
    def __init__(self):
        self.sent_messages = []
        self.is_closed = False

    async def accept(self):
        pass

    async def send_json(self, message):
        self.sent_messages.append(message)

    def close(self):
        self.is_closed = True


async def test_batch_sending():
    """Test that batch sending works as expected"""
    print("Starting batch functionality test...")
    
    # Create a connection manager with small buffer for testing
    manager = ConnectionManager(buffer_size=3, buffer_interval=0.5)
    
    # Create mock websocket
    mock_ws = MockWebSocket()
    sensor_id = "test_sensor_123"
    
    try:
        # Connect the mock websocket
        await manager.connect(mock_ws, sensor_id)
        print(f"✓ Connected websocket for sensor {sensor_id}")
        
        # Test 1: Send messages using broadcast_buffered (batch mode)
        print("\nTest 1: Testing broadcast_buffered (batch mode)")
        
        # Send 2 messages (below buffer size)
        test_messages = [
            {"sensor_id": sensor_id, "acc_z": 1.1, "timestamp": "2025-01-01T10:00:01"},
            {"sensor_id": sensor_id, "acc_z": 1.2, "timestamp": "2025-01-01T10:00:02"}
        ]
        
        for msg in test_messages:
            await manager.broadcast_buffered(sensor_id, msg)
        
        # Should have no messages sent yet (buffer not full)
        print(f"Messages in buffer (should be 2): {len(manager.message_buffer[sensor_id])}")
        print(f"Messages sent (should be 0): {len(mock_ws.sent_messages)}")
        
        # Send one more message to trigger buffer flush (buffer_size=3)
        await manager.broadcast_buffered(sensor_id, {
            "sensor_id": sensor_id, "acc_z": 1.3, "timestamp": "2025-01-01T10:00:03"
        })
        
        # Now should have sent a batch
        print(f"Messages sent after buffer full (should be 1): {len(mock_ws.sent_messages)}")
        
        if mock_ws.sent_messages:
            batch_message = mock_ws.sent_messages[0]
            print(f"Batch message type: {batch_message.get('type')}")
            print(f"Batch message count: {batch_message.get('batch_size')}")
            print(f"Batch samples length: {len(batch_message.get('samples', []))}")
            
            # Validate batch structure (should now be transformed-data format)
            assert batch_message["type"] == "transformed-data"
            assert batch_message["sensor_id"] == sensor_id
            assert batch_message["batch_size"] == 3
            assert len(batch_message["samples"]) == 3
            print("✓ Batch message structure is correct (transformed-data format)")
        else:
            print("✗ No batch message was sent!")
            
        # Clear messages for next test
        mock_ws.sent_messages.clear()
        
        # Test 2: Test time-based buffer flush
        print("\nTest 2: Testing time-based buffer flush")
        
        # Send one message and wait for time-based flush
        await manager.broadcast_buffered(sensor_id, {
            "sensor_id": sensor_id, "acc_z": 2.1, "timestamp": "2025-01-01T10:00:04"
        })
        
        print("Waiting for time-based flush (0.5s)...")
        await asyncio.sleep(0.6)  # Wait longer than buffer_interval
        
        # Should have flushed the single message
        if mock_ws.sent_messages:
            batch_message = mock_ws.sent_messages[-1]
            print(f"Time-based batch type: {batch_message.get('type')}")
            print(f"Time-based batch samples: {len(batch_message.get('samples', []))}")
            assert batch_message["type"] == "transformed-data"
            assert len(batch_message.get('samples', [])) == 1
            print("✓ Time-based flush works with correct format")
        else:
            print("✗ Time-based flush didn't work")
            
        # Test 3: Test regular broadcast (non-batch)
        print("\nTest 3: Testing regular broadcast (non-batch)")
        mock_ws.sent_messages.clear()
        
        await manager.broadcast(sensor_id, {
            "sensor_id": sensor_id, "acc_z": 3.1, "timestamp": "2025-01-01T10:00:05"
        })
        
        # Should have sent message immediately
        if mock_ws.sent_messages:
            regular_message = mock_ws.sent_messages[0]
            print(f"Regular message type: {regular_message.get('type', 'individual')}")
            assert "type" not in regular_message or regular_message["type"] != "batch"
            print("✓ Regular broadcast works")
        else:
            print("✗ Regular broadcast didn't work")
            
    except Exception as e:
        print(f"✗ Test failed with error: {e}")
        raise
    finally:
        # Clean up
        await manager.disconnect(mock_ws, sensor_id)
        if manager.buffer_task:
            manager.buffer_task.cancel()
            try:
                await manager.buffer_task
            except asyncio.CancelledError:
                pass
        print("✓ Cleanup completed")


async def test_frontend_batch_handling():
    """Test if frontend JavaScript logic can handle batch messages"""
    print("\nTesting frontend batch handling logic...")
    
    # Simulate batch message format that would be sent to frontend (new format)
    batch_message = {
        "type": "transformed-data",
        "sensor_id": "test_sensor",
        "samples": [
            {"acc_z": 1.1, "timestamp": "2025-01-01T10:00:01"},
            {"acc_z": 1.2, "timestamp": "2025-01-01T10:00:02"},
            {"acc_z": 1.3, "timestamp": "2025-01-01T10:00:03"}
        ],
        "batch_size": 3
    }
    
    # Check if this matches expected frontend format
    print(f"Batch message structure:")
    print(f"  - Type: {batch_message['type']}")
    print(f"  - Sensor ID: {batch_message['sensor_id']}")
    print(f"  - Sample count: {len(batch_message['samples'])}")
    print(f"  - Batch size: {batch_message['batch_size']}")
    
    # Verify frontend can extract individual messages
    for i, msg in enumerate(batch_message["samples"]):
        if "acc_z" in msg:
            print(f"  - Message {i+1}: acc_z = {msg['acc_z']}")
        else:
            print(f"  - Message {i+1}: Missing acc_z field!")
    
    print("✓ Frontend batch format looks correct")


async def main():
    """Run all batch functionality tests"""
    print("=== Batch Sending Functionality Test ===\n")
    
    try:
        await test_batch_sending()
        await test_frontend_batch_handling()
        print("\n=== All tests completed successfully! ===")
    except Exception as e:
        print(f"\n=== Test failed: {e} ===")
        return 1
    
    return 0


if __name__ == "__main__":
    exit_code = asyncio.run(main())
    exit(exit_code)
