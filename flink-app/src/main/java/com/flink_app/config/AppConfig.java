package com.flink_app.config;

import java.io.Serializable;

import org.apache.flink.api.java.utils.ParameterTool;

public class AppConfig implements Serializable {
    public final String kafkaBootstrapServers;
    public final String kafkaInputTopic;
    public final String kafkaOutputTopic;
    public final String kafkaConsumerGroup;
    public final int flinkParallelism;
    public final long checkpointIntervalMs;
    public final String awsAccessKeyId;
    public final String awsSecretAccessKey;
    public final String awsRegion;
    public final String s3outputPath;
    public final String flinkJobName;
    public final String kafkaNewSessionTopic;
    public final Long maxOutOfOrdernessMs;
    public final String kafkaAggregationTopic;

    public AppConfig(ParameterTool params) {
        kafkaBootstrapServers = System.getenv().getOrDefault("KAFKA_BOOTSTRAP_SERVERS", "localhost:9092");

        kafkaConsumerGroup = "flink-imu-processor-group";
        maxOutOfOrdernessMs = Long.parseLong(params.get("max.out.of.orderness.ms", System.getenv().getOrDefault("MAX_OUT_OF_ORDERNESS_MS", "1000")));
        flinkParallelism = Integer.parseInt(System.getenv().getOrDefault("FLINK_PARALLELISM", "1"));
        checkpointIntervalMs = Integer.parseInt(System.getenv().getOrDefault("CHECKPOINT_INTERVAL_MS", "60000")); // 5 minutes
        flinkJobName = System.getenv().getOrDefault("FLINK_JOB_NAME", "FlinkSensorProcessingJob");

        // --- GET AWS Credentials and S3 Path DIRECTLY FROM ENVIRONMENT VARIABLES ---
        awsAccessKeyId = System.getenv("AWS_ACCESS_KEY_ID");
        awsSecretAccessKey = System.getenv("AWS_SECRET_ACCESS_KEY");
        awsRegion = System.getenv().getOrDefault("AWS_REGION", "eu-central-1");
        s3outputPath = System.getenv("S3_OUTPUT_PATH");

        // --- TOPICS that can be overridden by params OR use environment variables ---
        kafkaInputTopic = System.getenv().getOrDefault("KAFKA_INPUT_TOPIC", "imu-data-all");
        kafkaOutputTopic = System.getenv().getOrDefault("KAFKA_OUTPUT_TOPIC", "sensor-data-transformed");
        kafkaNewSessionTopic = System.getenv().getOrDefault("KAFKA_NEW_SESSION_TOPIC", "new_session_topic");
        kafkaAggregationTopic = System.getenv().getOrDefault("KAFKA_AGGREGATION_TOPIC", "sensor-data-aggregated");
    }

    public static AppConfig fromArgs(String[] args) {
        return new AppConfig(ParameterTool.fromArgs(args));
    }

    // Optional: Add getters if you prefer to access fields via methods externally
    public String getKafkaBootstrapServers() { return kafkaBootstrapServers; }
    public String getKafkaInputTopic() { return kafkaInputTopic; }
    public String getKafkaOutputTopic() { return kafkaOutputTopic; }
    public String getKafkaConsumerGroup() { return kafkaConsumerGroup; }
    public int getFlinkParallelism() { return flinkParallelism; }
    public long getCheckpointIntervalMs() { return checkpointIntervalMs; }
    public String getAwsAccessKeyId() { return awsAccessKeyId; }
    public String getAwsSecretAccessKey() { return awsSecretAccessKey; }
    public String getAwsRegion() { return awsRegion; }
    public String getS3outputPath() { return s3outputPath; }
    public String getFlinkJobName() { return flinkJobName; }
    public String getKafkaNewSessionTopic() { return kafkaNewSessionTopic; }
    public Long getMaxOutOfOrdernessMs() { return maxOutOfOrdernessMs; }
    public String getKafkaAggregationTopic() { return kafkaAggregationTopic; } // <<< NEW GETTER
}