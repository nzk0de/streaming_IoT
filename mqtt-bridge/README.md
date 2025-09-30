# Go MQTT to Kafka Bridge

This service acts as a bridge, subscribing to sensor data published on an MQTT broker and forwarding parsed data to specific Kafka topics. It is designed to replace a similar Python implementation, aiming for better performance and concurrency.

**Current Behavior:**

*   Connects to a specified MQTT broker (potentially using TLS).
*   Subscribes to a wildcard MQTT topic pattern (e.g., `stream/+`).
*   Extracts the sensor ID from the MQTT topic.
*   Parses incoming binary data payloads according to a predefined format (including header, timestamp, samples, and CRC-16/KERMIT check).
*   Applies conversion factors to raw sensor readings (accelerometer, gyroscope, magnetometer).
*   Produces the parsed data as JSON messages to Kafka.
*   **Crucially, it sends data for each sensor to a *separate* Kafka topic based on a template (e.g., `imu_data-{sensor_id}`).**
*   Uses goroutines for concurrent processing of incoming messages.

## Prerequisites

*   **Go:** The Go programming language toolchain (version 1.18 or later recommended) must be installed on the machine where you intend to manage dependencies (`go mod tidy`, `go mod vendor`).
*   **Docker & Docker Compose:** Required to build and run the service as a container using the provided `Dockerfile` and `docker-compose.yml`.
*   **MQTT Broker:** An accessible MQTT broker where sensors publish data.
*   **Kafka Cluster:** An accessible Kafka cluster to receive the processed data.

## Setup & Installation (Host Machine)

These steps are necessary *before* building the Docker image to ensure dependencies are correctly managed.

1.  **Install Go (if not already installed):**
    On Debian/Ubuntu systems:
    ```bash
    sudo apt update && sudo apt install golang-go
    ```
    Verify the installation:
    ```bash
    go version
    ```

2.  **Navigate to the Bridge Directory:**
    ```bash
    cd path/to/your/project/mqtt_bridge
    ```
    (Replace `path/to/your/project` with the actual path).

3.  **Initialize Go Modules (if `go.mod` doesn't exist):**
    ```bash
    # Replace 'mqtt_bridge' if you prefer a different module path (e.g., github.com/user/repo)
    go mod init mqtt_bridge
    ```

4.  **Tidy and Vendor Dependencies:**
    This downloads necessary libraries (MQTT client, Kafka client, CRC library) and prepares them for the Docker build.
    ```bash
    go mod tidy
    go mod vendor  # <---- ITS IMPERATIVE TO RUN THIS COMMAND LOCALLY, TO BE ABLE TO BUILD  MQTT GO CONTAINER. WE DO THIS BECAUSE WE ARE USING A PRIVATE REPOSITORY.
    ```
    This creates/updates `go.mod`, `go.sum`, and the `vendor/` directory containing dependency source code.

## Building and Running with Docker Compose

1.  **Ensure `docker-compose.yml` is configured:** Verify that the `mqtt-bridge` service definition in your main `docker-compose.yml` correctly points to the `./mqtt_bridge` context and uses the `Dockerfile` within that directory.
2.  **Navigate to the Docker Compose Directory:**
    ```bash
    cd path/to/your/project/ # The directory containing docker-compose.yml
    ```
3.  **Build and Run:**
    ```bash
    # Build the image (if needed) and start the service in detached mode
    
    docker compose down mqtt-bridge && \
    docker compose up mqtt-bridge -d --build && \
    docker logs mqtt-bridge -f

    # To view logs:
    docker compose logs -f mqtt-bridge
    ```

## Configuration

The Go application currently uses **hardcoded constants** defined directly in `main.go` for:

*   MQTT Broker Host, Port, TLS setting, Topic Pattern
*   Kafka Brokers, Kafka Topic Template
*   Processing Worker Count, etc.

**To change the configuration, you must:**

1.  Modify the `const` values in `main.go`.
2.  Re-run `docker compose up --build -d mqtt-bridge` to rebuild the image with the new configuration baked into the binary.

*(Note: A future improvement would be to modify the Go code to read these settings from environment variables passed via the `docker-compose.yml` file for greater flexibility).*

## MQTT Subscription Strategy & Scalability
This bridge leverages MQTTv5 Shared Subscriptions for scalability:
1. Shared Subscription Format: The bridge subscribes to MQTT using the format $share/<group_name>/<topic_filter> (e.g., $share/kafka-bridge-group/stream/+).
Broker Load Balancing: The MQTT broker distributes messages matching the <topic_filter> across all connected client instances that subscribed using the same <group_name>.
2. Horizontal Scaling: This allows you to run multiple instances of the mqtt-bridge container (e.g., using docker-compose scale or Kubernetes replicas). Each instance will receive only a subset of the total matching MQTT messages, effectively distributing the processing load. This overcomes the bottleneck of duplicate processing inherent in direct wildcard subscriptions when running multiple instances.
#### Requirement: Your MQTT Broker must support MQTTv5 Shared Subscriptions for this scaling strategy to work.


---