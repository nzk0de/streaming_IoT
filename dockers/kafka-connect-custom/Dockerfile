# Start from the official Confluent Kafka Connect base image
FROM confluentinc/cp-kafka-connect:7.4.0

# Install curl and unzip using microdnf
USER root
ARG MONGO_CONNECTOR_VERSION=1.11.1
ARG MONGO_UBER_JAR_URL=https://repo1.maven.org/maven2/org/mongodb/kafka/mongo-kafka-connect/${MONGO_CONNECTOR_VERSION}/mongo-kafka-connect-${MONGO_CONNECTOR_VERSION}-all.jar
ARG CONNECT_PLUGIN_DIR=/usr/share/confluent-hub-components/mongodb-kafka-connect

RUN echo "Creating plugin directory: ${CONNECT_PLUGIN_DIR}" && \
    mkdir -p ${CONNECT_PLUGIN_DIR} && \
    echo "Downloading MongoDB Connector Uber JAR..." && \
    curl --fail --show-error -o ${CONNECT_PLUGIN_DIR}/mongo-kafka-connect-uber.jar -L ${MONGO_UBER_JAR_URL} && \
    echo "MongoDB Connector Uber JAR downloaded." && \
    ls -l ${CONNECT_PLUGIN_DIR}/

# Switch back to non-root user
USER appuser
