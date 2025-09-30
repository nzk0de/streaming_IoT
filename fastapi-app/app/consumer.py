import json
import logging
import threading
import time
from queue import Queue, Empty

from kafka import KafkaConsumer, TopicPartition
from kafka.errors import NoBrokersAvailable

from app.config import settings
from app.manager import aggregate_manager, raw_manager
import asyncio

# Configure standard Python logging
logging.basicConfig(
    level=logging.INFO, format="%(asctime)s - %(name)s - %(levelname)s - %(message)s"
)
logger = logging.getLogger(__name__)

# A simple flag to signal shutdown to our background threads
shutdown_event = threading.Event()


# --- THE NEW CONSUMER WORKER ---
# This class will run in its own dedicated thread.
import asyncio
import json
import logging
import threading
import time


# Assume shutdown_event is defined globally as before
shutdown_event = threading.Event()

logger = logging.getLogger(__name__)


class KafkaConsumerWorker(threading.Thread):
    """
    A dedicated thread for consuming messages from a Kafka topic.
    It manages its own Kafka connection and runs its own asyncio event loop
    to efficiently call asynchronous functions.
    """

    def __init__(self, topic, group_id_suffix, broadcast_manager):
        super().__init__()
        self.topic = topic
        self.group_id = f"{settings.KAFKA_FASTAPI_CONSUMER_GROUP}-{group_id_suffix}"
        self.broadcast_manager = broadcast_manager
        self.consumer = None
        self.loop = None  # The event loop will be created when the thread starts
        self.name = f"KafkaConsumerWorker-{group_id_suffix}"
        self.daemon = True  # Allows main program to exit even if threads are running

    def connect(self):
        """Attempts to connect to Kafka, retrying on failure."""
        while not shutdown_event.is_set():
            try:
                logger.info(
                    f"[{self.name}] Attempting to connect to Kafka at {settings.KAFKA_BOOTSTRAP_SERVERS}..."
                )
                self.consumer = KafkaConsumer(
                    self.topic,
                    bootstrap_servers=settings.KAFKA_BOOTSTRAP_SERVERS,
                    group_id=self.group_id,
                    auto_offset_reset="latest",
                    session_timeout_ms=45000,
                    heartbeat_interval_ms=5000,
                    request_timeout_ms=60000,
                    enable_auto_commit=True,
                )
                logger.info(f"[{self.name}] Successfully connected to Kafka.")
                return True
            except NoBrokersAvailable:
                logger.error(
                    f"[{self.name}] Could not find any brokers at {settings.KAFKA_BOOTSTRAP_SERVERS}. Retrying in 10s..."
                )
                # Use the shutdown event to avoid waiting unnecessarily if a shutdown is requested
                shutdown_event.wait(10)
        return False

    def run(self):
        """The main loop for the consumer thread."""
        # --- KEY IMPROVEMENT 1: Create a single event loop for this thread ---
        self.loop = asyncio.new_event_loop()
        asyncio.set_event_loop(self.loop)

        try:
            if not self.connect():
                logger.error(
                    f"[{self.name}] Could not connect to Kafka, shutting down thread."
                )
                return

            logger.info(f"[{self.name}] Started consuming from topic '{self.topic}'.")

            while not shutdown_event.is_set():
                # Poll for messages with a 1-second timeout. This allows the loop
                # to regularly check the shutdown_event.
                messages = self.consumer.poll(timeout_ms=1000, max_records=50)

                if not messages:
                    continue

                for topic_partition, records in messages.items():
                    for msg in records:
                        try:
                            data = json.loads(msg.value.decode("utf-8"))
                            sensor_id = data.get("sensor_id")
                            if not sensor_id:
                                logger.warning(
                                    f"[{self.name}] Missing sensor_id in message: {data}"
                                )
                                continue

                            # --- KEY IMPROVEMENT 2: Use the long-lived event loop ---
                            # This is far more efficient than calling asyncio.run() repeatedly.
                            # Use broadcast_buffered for better performance with high-frequency data
                            self.loop.run_until_complete(
                                self.broadcast_manager.broadcast_buffered(sensor_id, data)
                            )

                        except Exception as e:
                            logger.error(
                                f"[{self.name}] Error processing message from partition {msg.partition}, offset {msg.offset}: {e}",
                                exc_info=True,
                            )

        except Exception as e:
            logger.error(
                f"[{self.name}] An unexpected error occurred in the main run loop: {e}",
                exc_info=True,
            )

        finally:
            logger.info(f"[{self.name}] Closing Kafka consumer.")
            if self.consumer:
                self.consumer.close()

            # --- KEY IMPROVEMENT 3: Clean up the event loop ---
            if self.loop:
                self.loop.close()
                logger.info(f"[{self.name}] Event loop closed.")

            logger.info(f"[{self.name}] Thread finished.")


# --- List to hold our worker threads ---
consumer_threads = []


# --- FastAPI Lifespan Functions ---
def run_consumer_pipeline():
    """Creates and starts the Kafka consumer threads."""
    logger.info("[Main] Starting Kafka consumer threads...")

    # Clear any old threads if this function were ever called more than once
    consumer_threads.clear()
    shutdown_event.clear()

    # Create and start a worker for the transformed data topic
    transformed_worker = KafkaConsumerWorker(
        topic=settings.KAFKA_OUTPUT_TOPIC,
        group_id_suffix="transformed-sync",
        broadcast_manager=raw_manager,
    )
    consumer_threads.append(transformed_worker)

    # Create and start a worker for the aggregated data topic
    aggregated_worker = KafkaConsumerWorker(
        topic=settings.KAFKA_AGGREGATION_TOPIC,
        group_id_suffix="aggregated-sync",
        broadcast_manager=aggregate_manager,
    )
    consumer_threads.append(aggregated_worker)

    # Start all the threads
    for thread in consumer_threads:
        thread.start()

    logger.info(f"[Main] {len(consumer_threads)} consumer threads started.")


def stop_consumer_pipeline():
    """Signals all consumer threads to shut down gracefully."""
    logger.info("[Main] Initiating consumer pipeline shutdown...")

    # Signal the event that all loops are checking
    shutdown_event.set()

    # Wait for all threads to finish
    for thread in consumer_threads:
        # The join() call will block until the thread's run() method completes.
        # The timeout prevents it from hanging forever if a thread is stuck.
        thread.join(timeout=10)
        if thread.is_alive():
            logger.warning(
                f"[Main] Thread '{thread.name}' did not shut down within the timeout."
            )

    logger.info("[Main] Consumer pipeline shutdown complete.")
