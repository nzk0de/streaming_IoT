# FastAPI Sensor Streaming Application

Real-time sensor data streaming and processing API built with FastAPI.

## Quick Setup

### Development
```bash
# Install dependencies
pip install -e .

# Install with development dependencies
pip install -e .[dev]

# Run the application
python run.py
```

### Production
```bash
# Install only production dependencies
pip install .

# Run with uvicorn
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

## Environment Variables

Required environment variables:
- `KAFKA_BOOTSTRAP_SERVERS` - Kafka broker addresses
- `KAFKA_AGGREGATION_TOPIC` - Topic for aggregated data
- `KAFKA_OUTPUT_TOPIC` - Topic for processed data
- `MONGO_HOST` - MongoDB host (default: mongodb)
- `MONGO_PORT` - MongoDB port (default: 27017)
- `DB_NAME` - Database name (default: streaming_poc)

## Dependencies

Core dependencies are defined in `pyproject.toml`:
- **FastAPI** - Web framework
- **Motor** - Async MongoDB driver
- **aiokafka** - Async Kafka client
- **Pydantic Settings** - Configuration management
- **Uvicorn** - ASGI server
