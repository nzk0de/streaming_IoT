#!/bin/bash

# --- Debugging: Print shell info ---
echo "--- Shell Information ---"
ps -p $$
echo "SHELL ENV VAR: $SHELL"
echo "BASH_VERSION: $BASH_VERSION"
echo "-------------------------"

# --- Configuration ---
# These will pick up environment variables set by Docker Compose
# and provide defaults if they are not set or empty.
KAFKA_BOOTSTRAP_SERVERS="${KAFKA_BOOTSTRAP_SERVERS:-kafka:9092}"
NUMBER_OF_PARTITIONS="${NUMBER_OF_PARTITIONS:-1}"
KAFKA_DEFAULT_REPLICATION_FACTOR="${KAFKA_DEFAULT_REPLICATION_FACTOR:-3}" # Default to 3 if not set
MAX_WAIT_SECONDS=120
WAIT_INTERVAL=5

echo "--- Initial Script Variables ---"
echo "KAFKA_BOOTSTRAP_SERVERS: $KAFKA_BOOTSTRAP_SERVERS"
echo "NUMBER_OF_PARTITIONS:      $NUMBER_OF_PARTITIONS" # This should show the value from env or '1'
echo "KAFKA_DEFAULT_REPLICATION_FACTOR:        $KAFKA_DEFAULT_REPLICATION_FACTOR"
echo "--------------------------------"

# --- Topic configuration ---
declare -A TOPICS_TO_CREATE
# Explicitly use the script variables that have processed the environment variables
TOPICS_TO_CREATE["imu-data-all"]="${NUMBER_OF_PARTITIONS}"
TOPICS_TO_CREATE["sensor-data-transformed"]="1"
TOPICS_TO_CREATE["new-session-topic"]="${NUMBER_OF_PARTITIONS}"
TOPICS_TO_CREATE["sensor-data-aggregated"]="1"
declare -A TOPIC_RETENTION_CONFIGS
TOPIC_RETENTION_CONFIGS["imu-data-all"]="--config retention.ms=604800000 --config retention.bytes=1073741824"
TOPIC_RETENTION_CONFIGS["sensor-data-transformed"]="--config retention.ms=604800000 --config retention.bytes=1073741824"
TOPIC_RETENTION_CONFIGS["new-session-topic"]="--config retention.ms=604800000 --config retention.bytes=1073741824"
TOPIC_RETENTION_CONFIGS["ssensor-data-aggregated"]="--config retention.ms=604800000 --config retention.bytes=1073741824"

echo "--- Kafka Topic Creation Script ---"
echo "Using Bootstrap Servers: $KAFKA_BOOTSTRAP_SERVERS"
echo "Default Partitions Value used for topics: $NUMBER_OF_PARTITIONS" # For echo, not for the array
echo "-----------------------------------"

# --- Wait for Kafka Broker ---
echo "Waiting for Kafka broker at $KAFKA_BOOTSTRAP_SERVERS..."
SECONDS=0
while true; do
  # Use the script variable for bootstrap servers
  kafka-topics --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" --list &>/dev/null
  if [ $? -eq 0 ]; then
    echo "Kafka broker is ready!"
    break
  fi

  if [ $SECONDS -ge $MAX_WAIT_SECONDS ]; then
    echo "ERROR: Timeout waiting for Kafka broker after $MAX_WAIT_SECONDS seconds."
    exit 1
  fi

  echo "Broker not ready yet, waiting ${WAIT_INTERVAL}s... ($SECONDS/$MAX_WAIT_SECONDS)"
  sleep $WAIT_INTERVAL
done

# --- Create Topics ---
echo "Creating topics..."
all_successful=true
for topic in "${!TOPICS_TO_CREATE[@]}"; do
  # These will now correctly get the numeric value stored in the array
  partitions_for_topic="${TOPICS_TO_CREATE[$topic]}"
  replicas_for_topic="${KAFKA_DEFAULT_REPLICATION_FACTOR}" # Use the script variable for replicas
  retention_config="${TOPIC_RETENTION_CONFIGS[$topic]}"

  echo -n "Creating topic '$topic' (Partitions: $partitions_for_topic, Replicas: $replicas_for_topic)... "

  # Use script variables for bootstrap servers, partitions, and replicas
  kafka-topics --bootstrap-server "$KAFKA_BOOTSTRAP_SERVERS" \
               --create \
               --topic "$topic" \
               --partitions "$partitions_for_topic" \
               --replication-factor "$replicas_for_topic" \
               --if-not-exists \
               $retention_config # Make sure this variable doesn't contain problematic characters if empty

  if [ $? -eq 0 ]; then
    echo "SUCCESS (or already exists)."
  else
    echo "FAILED."
    all_successful=false
  fi
done

# --- Final Status ---
echo "-----------------------------------"
if $all_successful; then
  echo "Topic creation process completed successfully (or topics already existed)."
  exit 0
else
  echo "ERROR: One or more topics failed to be created."
  exit 1
fi