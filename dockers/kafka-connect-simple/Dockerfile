# Start from the Strimzi Kafka Connect base image (match to your Strimzi & Kafka versions)
FROM quay.io/strimzi/kafka:0.46.0-kafka-4.0.0

# Make sure the directory for our connector exists
USER root
RUN mkdir -p /opt/kafka/plugins/file-connector \
  && chown -R 1001:0 /opt/kafka/plugins/file-connector

# Download the FileStream connector plugin matching your Kafka version
ARG CONNECT_VERSION=3.9.0
RUN curl -L \
     https://repo1.maven.org/maven2/org/apache/kafka/connect-file/${CONNECT_VERSION}/connect-file-${CONNECT_VERSION}.jar \
     -o /opt/kafka/plugins/file-connector/connect-file-${CONNECT_VERSION}.jar

# Switch back to the non-root user used by Strimzi images
USER 1001
