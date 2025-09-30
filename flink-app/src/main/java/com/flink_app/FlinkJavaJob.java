package com.flink_app;

import java.time.Duration;
import java.time.ZoneId;

import com.flink_app.config.AppConfig;
import com.flink_app.config.ModelConfig;
import com.flink_app.constants.AppConstants;
import com.flink_app.model.FlatSensorRecord;
import com.flink_app.model.InputRecord;
import com.flink_app.model.NewSessionEvent;
import com.flink_app.model.SessionAggregate;
import com.flink_app.model.TransformedRecord;
import com.flink_app.stream.mapfunctions.NewSessionEventToJsonStringMapFunction;
import com.flink_app.stream.mapfunctions.RecordToJsonStringMapFunction;
import com.flink_app.stream.mapfunctions.SensorTransformerMapFunction;
import com.flink_app.stream.mapfunctions.SessionAggregateToJsonStringMapFunction;
import com.flink_app.stream.mapfunctions.TransformedRecordToPojoFlatMap;
import com.flink_app.stream.processfunctions.InferenceProcessFunction;
import com.flink_app.stream.processfunctions.JsonParserWithTimestampProcessFunction;
import com.flink_app.stream.processfunctions.SessionAggregatorProcessFunction;
import com.flink_app.stream.processfunctions.SessionIdAssignerProcessFunction;

import org.apache.flink.api.common.eventtime.WatermarkStrategy;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.connector.base.DeliveryGuarantee;
import org.apache.flink.connector.file.sink.FileSink;
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema;
import org.apache.flink.connector.kafka.sink.KafkaSink;
import org.apache.flink.connector.kafka.source.KafkaSource;
import org.apache.flink.connector.kafka.source.enumerator.initializer.OffsetsInitializer;
import org.apache.flink.core.fs.Path;
import org.apache.flink.formats.parquet.avro.AvroParquetWriters;
import org.apache.flink.streaming.api.CheckpointingMode;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.sink.filesystem.OutputFileConfig;
import org.apache.flink.streaming.api.functions.sink.filesystem.bucketassigners.DateTimeBucketAssigner;
import org.apache.flink.streaming.api.functions.sink.filesystem.rollingpolicies.OnCheckpointRollingPolicy;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class FlinkJavaJob {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkJavaJob.class);

    // Define an OutputTag for the side output stream for new sessions
    private static final OutputTag<NewSessionEvent> NEW_SESSION_EVENT_OUTPUT_TAG =
new OutputTag<NewSessionEvent>("new-session-event", TypeInformation.of(NewSessionEvent.class));


    public static void main(String[] args) throws Exception {
        // 1. Load Configuration
        final AppConfig appConfig = AppConfig.fromArgs(args);
        final ModelConfig modelConfig = new ModelConfig();
        // 2. Set up the Flink execution environment
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(appConfig.flinkParallelism);

        // Checkpointing
        if (appConfig.checkpointIntervalMs > 0) {
            env.enableCheckpointing(appConfig.checkpointIntervalMs);
            env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);
            env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);
            env.getCheckpointConfig().setCheckpointTimeout(60000);
            env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
        }
        Configuration flinkConfig = new Configuration();
        flinkConfig.setString("s3.access-key", appConfig.awsAccessKeyId);
        flinkConfig.setString("s3.secret-key", appConfig.awsSecretAccessKey);
        flinkConfig.setString("s3.endpoint", "s3.amazonaws.com"); // Adjust if using other S3-compatible storage
        flinkConfig.setString("s3.path.style.access", "true"); // Often needed for MinIO/non-AWS S3
        env.configure(flinkConfig, null);


        env.registerCachedFile(modelConfig.s3ModelPath, AppConstants.DIST_CACHE_ONNX_MODEL_KEY, true);
        env.registerCachedFile(modelConfig.s3ScalingInfoPath, AppConstants.DIST_CACHE_SCALING_INFO_KEY, true);
        env.registerCachedFile(modelConfig.s3ModelParamsPath, AppConstants.DIST_CACHE_MODEL_PARAMS_KEY, true);
        LOG.info("Registering S3 scaling info {} with distributed cache key '{}'", modelConfig.s3ScalingInfoPath, AppConstants.DIST_CACHE_SCALING_INFO_KEY);
        LOG.info("Registering S3 model {} with distributed cache key '{}'", modelConfig.s3ModelPath, AppConstants.DIST_CACHE_ONNX_MODEL_KEY);
        LOG.info("Registering S3 model params {} with distributed cache key '{}'", modelConfig.s3ModelParamsPath, AppConstants.DIST_CACHE_MODEL_PARAMS_KEY);

        LOG.info("Initializing Flink Java job with parallelism: {}", appConfig.flinkParallelism);

        // 3. Define Kafka Source
        KafkaSource<String> kafkaSource = KafkaSource.<String>builder()
                .setBootstrapServers(appConfig.kafkaBootstrapServers)
                .setTopics(appConfig.kafkaInputTopic)
                .setGroupId(appConfig.kafkaConsumerGroup)
                .setStartingOffsets(OffsetsInitializer.earliest())
                .setValueOnlyDeserializer(new SimpleStringSchema())
                .setProperty("partition.discovery.interval.ms", "60000")
                .build();

        // 4. Create data stream from Kafka source
        DataStream<String> kafkaStream = env.fromSource(
                kafkaSource,
                WatermarkStrategy.noWatermarks(), 
                "KafkaSource_" + appConfig.kafkaInputTopic
        );

        DataStream<InputRecord> parsedStream = kafkaStream
                .process(new JsonParserWithTimestampProcessFunction()) // <<< USE THE PROCESS FUNCTION HERE
                .name("JsonParseAndEnrich").uid("json-parse-enrich-uid");
                // We don't need the .filter(record -> record != null) here because the
                // ProcessFunction already handles nulls and bad parses internally.

        // 5.1. ASSIGN TIMESTAMPS AND WATERMARKS
        // This part of your code is now correct because parsedStream contains
        // InputRecord objects that have the correct timestamp populated by the ProcessFunction.
        long maxOutOfOrdernessMs = appConfig.maxOutOfOrdernessMs;
        LOG.info("Assigning watermarks with max out-of-orderness: {} ms", maxOutOfOrdernessMs);

        WatermarkStrategy<InputRecord> watermarkStrategy = WatermarkStrategy
                .<InputRecord>forBoundedOutOfOrderness(Duration.ofMillis(maxOutOfOrdernessMs))
                .withTimestampAssigner((event, previousElementTimestamp) -> {
                    // Extract the timestamp that the ProcessFunction just inserted.
                    // Use the kafka_timestamp (in milliseconds) as Flink expects milliseconds.
                    return event.getKafka_timestamp()/1000;
                })
                .withIdleness(Duration.ofSeconds(60)); // Good practice for production

        DataStream<InputRecord> timedParsedStream = parsedStream
                .assignTimestampsAndWatermarks(watermarkStrategy)
                .name("AssignTimestampsAndWatermarks").uid("assign-ts-wm-uid");

        
        
        DataStream<TransformedRecord> sensorTransformedStream = timedParsedStream
                .map(new SensorTransformerMapFunction())
                .name("SensorTransform").uid("sensor-transform-uid")
                .filter(record -> record != null && record.getSensor_id() != null); // Ensure sensor_id is not null for keying

        // DataStream<TransformedRecord> featureEngineeredStream = sensorTransformedStream
        //     .keyBy(TransformedRecord::getSensor_id)
        //     .countWindow(4, 1)
        //     .process(new RollingMessageWindowFunction())
        //     .name("FeatureEngineeringWindow");

        // --- Assign Session ID and get side output for new sessions ---
        // Ensure that records have sensor_id before this step
        SingleOutputStreamOperator<TransformedRecord> streamWithSessionId = sensorTransformedStream
                .keyBy(TransformedRecord::getSensor_id) // Key by sensor_id for session state
                .process(new SessionIdAssignerProcessFunction(NEW_SESSION_EVENT_OUTPUT_TAG))
                .name("SessionIdAssigner").uid("session-id-assigner-uid");

        // Get the side output stream for new session events
        DataStream<NewSessionEvent> newSessionEventsStream = streamWithSessionId.getSideOutput(NEW_SESSION_EVENT_OUTPUT_TAG);
        // // --- End of Session ID assignment ---

        long BATCH_TIMEOUT_MS = 1000;
        DataStream<TransformedRecord> inferredStream = streamWithSessionId // Use streamWithSessionId now
                .keyBy((KeySelector<TransformedRecord, String>) TransformedRecord::getSensor_id)
                .process(new InferenceProcessFunction(BATCH_TIMEOUT_MS))
                .name("Inference").uid("inference-uid")
                .filter(record -> record != null && record.getSamples() != null && !record.getSamples().isEmpty());
        DataStream<SessionAggregate> aggregatedStream = inferredStream
                .keyBy((KeySelector<TransformedRecord, String>) TransformedRecord::getSensor_session_id)
                .process(new SessionAggregatorProcessFunction()) 
                .name("SessionAggregator").uid("session-aggregator-uid");
                
        // 6. Serialize TransformedRecord (now with session_id) objects back to JSON strings for main output
        DataStream<String> outputJsonStream = inferredStream
                .map(new RecordToJsonStringMapFunction()) // This MapFunction needs to handle the new session_id field
                .name("RecordToJsonSerializer").uid("record-to-json-uid");
                

        KafkaSink<String> kafkaSink = KafkaSink.<String>builder()
                        .setBootstrapServers(appConfig.kafkaBootstrapServers)
                        .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                                .setTopic(appConfig.kafkaOutputTopic) // Main output topic
                                .setValueSerializationSchema(new SimpleStringSchema())
                                .build())
                        .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                        .setProperty("linger.ms", "100")
                        .setProperty("batch.size", Integer.toString(16384 * 4))
                        .build();
        outputJsonStream.sinkTo(kafkaSink)
                .name("KafkaSink_" + appConfig.kafkaOutputTopic)
                .uid("kafka-sink-main-uid");

        // Handle S3 Sink
        // EnvironmentSettings tableApiSettings = EnvironmentSettings.newInstance().inStreamingMode().build();
        // StreamTableEnvironment tableEnv = StreamTableEnvironment.create(env, tableApiSettings);
        // if (appConfig.s3outputPath != null ) {
        //     S3SinkHandler s3SinkHandler = new S3SinkHandler(tableEnv, appConfig);
        //     s3SinkHandler.sinkToS3Flattened(inferredStream); // Sink the TransformedRecord stream
        //     LOG.info("S3 Sink Handler configured for inferredStream.");
        // } else {
        //     LOG.warn("S3 output path not fully set. Skipping S3 sink.");
        // }
        if (appConfig.s3outputPath != null && !appConfig.s3outputPath.isEmpty()) {
        // 1. Flatten your stream directly into your clean POJO class.
                DataStream<FlatSensorRecord> pojoStream = inferredStream
                .flatMap(new TransformedRecordToPojoFlatMap()) // This is now clean and error-free
                .returns(FlatSensorRecord.class)
                .name("MapToPojo"); 

        // 2. Build the Sink using the reflection-based writer.
        String s3OutputPath = "s3a://" + appConfig.s3outputPath;
        LOG.info("Configuring StreamingFileSink for POJOs to path: {}", s3OutputPath);

        final FileSink<FlatSensorRecord> s3Sink = FileSink
                .forBulkFormat(
                new Path(s3OutputPath),
                // THIS IS THE KEY: A writer factory that infers the schema from the POJO class.
                AvroParquetWriters.forReflectRecord(FlatSensorRecord.class)
                )
                .withBucketAssigner(new DateTimeBucketAssigner<>("yyyy-MM-dd", ZoneId.of("UTC")))
                // Use the only rolling policy available in your Flink version
                .withRollingPolicy(OnCheckpointRollingPolicy.build())
                .withOutputFileConfig(
                OutputFileConfig.builder()
                        .withPartPrefix("data")
                        .withPartSuffix(".parquet")
                        .build()
                )
                .build();

        // 3. Add the sink to the clean POJO stream.
        pojoStream.sinkTo(s3Sink).name("S3PojoParquetSink");
        
        LOG.info("S3 StreamingFileSink configured successfully using POJO reflection.");
        }


        if (appConfig.getKafkaAggregationTopic() != null && !appConfig.getKafkaAggregationTopic().isEmpty()) {
            DataStream<String> aggregationJsonStream = aggregatedStream
                    .map(new SessionAggregateToJsonStringMapFunction())
                    .name("SessionAggregateToJson").uid("session-aggregate-to-json-uid");

            KafkaSink<String> aggregationKafkaSink = KafkaSink.<String>builder()
                    .setBootstrapServers(appConfig.getKafkaBootstrapServers()) // Use getter
                    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                            .setTopic(appConfig.getKafkaAggregationTopic()) // Use getter
                            .setValueSerializationSchema(new SimpleStringSchema())
                            .build())
                    .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE)
                    .build();

            aggregationJsonStream.sinkTo(aggregationKafkaSink)
                    .name("KafkaSink_Aggregations_" + appConfig.getKafkaAggregationTopic()) // Use getter
                    .uid("kafka-sink-aggregations-uid");
            LOG.info("Configured Kafka sink for session aggregations to topic: {}", appConfig.getKafkaAggregationTopic()); // Use getter
        } else {
            LOG.warn("Kafka topic for session aggregations (AGGREGATION_OUTPUT_TOPIC) is not configured. Skipping sink for aggregations.");
        }
//         // --- Sink for New Session Events to new-session-topic ---
        // 8. Sink the processed data (with session_id) to the main Kafka topic


        if (appConfig.kafkaNewSessionTopic != null && !appConfig.kafkaNewSessionTopic.isEmpty()) {
            DataStream<String> newSessionJsonStream = newSessionEventsStream
                    .map(new NewSessionEventToJsonStringMapFunction())
                    .name("NewSessionEventToJson").uid("new-session-event-to-json-uid");

            KafkaSink<String> newSessionKafkaSink = KafkaSink.<String>builder()
                    .setBootstrapServers(appConfig.kafkaBootstrapServers)
                    .setRecordSerializer(KafkaRecordSerializationSchema.builder()
                            .setTopic(appConfig.kafkaNewSessionTopic) // Specific topic for new sessions
                            .setValueSerializationSchema(new SimpleStringSchema())
                            .build())
                    .setDeliveryGuarantee(DeliveryGuarantee.AT_LEAST_ONCE) // Or EXACTLY_ONCE
                    .build();

            newSessionJsonStream.sinkTo(newSessionKafkaSink)
                    .name("KafkaSink_NewSessions_" + appConfig.kafkaNewSessionTopic)
                    .uid("kafka-sink-new-sessions-uid");
            LOG.info("Configured Kafka sink for new session events to topic: {}", appConfig.kafkaNewSessionTopic);
        } else {
            LOG.warn("Kafka topic for new sessions (KAFKA_NEW_SESSION_TOPIC) is not configured. Skipping sink for new session events.");
        }
        // --- End of New Session Sink ---

        // 9. Execute the Flink job
        LOG.info("Starting Flink job execution...");
        env.execute("Flink Sensor Processing Job with Sessions");
    }
}
