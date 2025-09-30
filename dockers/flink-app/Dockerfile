# -------- STAGE 1: Build with Maven --------
FROM maven:3.9-eclipse-temurin-17 AS builder

ARG APP_PROJECT_DIR_IN_CONTEXT=flink-app
WORKDIR /build

COPY ${APP_PROJECT_DIR_IN_CONTEXT}/pom.xml .
RUN mvn dependency:go-offline -B

COPY ${APP_PROJECT_DIR_IN_CONTEXT}/src ./src
RUN mvn clean package -DskipTests

# -------- STAGE 2: Final Flink Image --------
FROM flink:1.19.2-scala_2.12-java17

ENV FLINK_APPLICATION_JAR_NAME=flink-java-sensor-app-1.0-SNAPSHOT.jar
ENV FLINK_APPLICATION_JAR_PATH_IN_IMAGE=/opt/flink/usrlib/${FLINK_APPLICATION_JAR_NAME}
ENV MLFLOW_ARTIFACT_DIR_CONTAINER=/opt/mlflow_artifacts_cache

# Copy only the compiled JAR from builder stage
COPY --from=builder /build/target/${FLINK_APPLICATION_JAR_NAME} ${FLINK_APPLICATION_JAR_PATH_IN_IMAGE}

# Add the S3 filesystem plugin (optional)
RUN mkdir -p /opt/flink/plugins/flink-s3-fs-hadoop && \
    curl -L -o /opt/flink/plugins/flink-s3-fs-hadoop/flink-s3-fs-hadoop.jar \
    https://repo1.maven.org/maven2/org/apache/flink/flink-s3-fs-hadoop/1.19.2/flink-s3-fs-hadoop-1.19.2.jar

# MLflow artifacts dir
RUN mkdir -p ${MLFLOW_ARTIFACT_DIR_CONTAINER} && \
    chown -R flink:flink ${MLFLOW_ARTIFACT_DIR_CONTAINER} && \
    chmod -R 775 ${MLFLOW_ARTIFACT_DIR_CONTAINER}

WORKDIR /opt/flink
