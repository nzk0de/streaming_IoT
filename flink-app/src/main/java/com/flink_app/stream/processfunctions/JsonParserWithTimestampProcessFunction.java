package com.flink_app.stream.processfunctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink_app.model.InputRecord;

import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A ProcessFunction that parses a JSON string into an InputRecord AND
 * enriches it with the record's official Flink event timestamp.
 * This is a robust alternative to using a custom DeserializationSchema.
 */

public class JsonParserWithTimestampProcessFunction extends ProcessFunction<String, InputRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(JsonParserWithTimestampProcessFunction.class);
    // ObjectMapper is thread-safe and can be reused
    private transient ObjectMapper objectMapper;

    @Override
    public void open(org.apache.flink.configuration.Configuration parameters) throws Exception {
        // Initialize the ObjectMapper here, as it's not serializable.
        objectMapper = new ObjectMapper();
    }

    @Override
    public void processElement(String jsonString, Context ctx, Collector<InputRecord> out) throws Exception {
        if (jsonString == null || jsonString.isEmpty()) {
            LOG.warn("Received null or empty JSON string.");
            return;
        }

            // Get the record's official timestamp from the context.
            // This timestamp comes from the Kafka record metadata.
            Long kafkaTimestampMs = ctx.timestamp();
            if (kafkaTimestampMs == null || kafkaTimestampMs <= 0) {
                LOG.warn("Record has an invalid timestamp: {}. Skipping. JSON: {}", kafkaTimestampMs, jsonString);
                return;
            }

            // Parse the JSON string into our POJO
            InputRecord record = objectMapper.readValue(jsonString, InputRecord.class);

            // --- THIS IS THE KEY ---
            // Overwrite the (incorrect) timestamp from the payload
            // with the (correct) Kafka timestamp from Flink's context.
            // We convert from milliseconds to microseconds to match the field's unit naming.
            // record.setHeader_timestamp_us(kafkaTimestampMs * 1000L);
            record.setKafka_timestamp(kafkaTimestampMs * 1000L); // Also set the dedicated kafka_timestamp field

            out.collect(record);

    }
}