package com.flink_app.stream.mapfunctions;

// Java utility imports
import java.util.Map;

// --- Imports ---
// Jackson imports for JSON processing and type conversion
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
// Your project's model and utility classes
import com.flink_app.model.TransformedRecord;
import com.flink_app.utils.DateTimeUtil;
// Note: com.flink_app.utils.JsonUtil is not directly used here as ObjectMapper is created locally

// Flink imports
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
// Logging imports
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Flink RichMapFunction: Converts an TransformedRecord to a JSON string,
 * adding a current processing timestamp and a formatted header timestamp.
 */
public class RecordToJsonStringMapFunction extends RichMapFunction<TransformedRecord, String> {

    private static final Logger LOG = LoggerFactory.getLogger(RecordToJsonStringMapFunction.class);

    // ObjectMapper is used for JSON serialization and POJO-to-Map conversion.
    // Declared as transient because ObjectMapper itself might not be serializable,
    // and it's better to initialize it in open() for each task instance.
    private transient ObjectMapper objectMapper;

    /**
     * Initialization method for the RichMapFunction.
     * Called once per parallel instance.
     * Used here to initialize the ObjectMapper.
     *
     * @param parameters The configuration parameters for this function.
     * @throws Exception if the initialization fails.
     */
    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        // Initialize ObjectMapper. It's generally thread-safe for configuration and use.
        this.objectMapper = new ObjectMapper();
    }

    /**
     * Transforms an TransformedRecord into a JSON string.
     * Adds a 'processing_timestamp_ms' with the current formatted time.
     * Converts 'header_timestamp_us' to 'header_datetime_ms' formatted string.
     *
     * @param record The input TransformedRecord.
     * @return A JSON string representation of the transformed record, or null on error or if input is null.
     * @throws Exception if any error occurs during mapping.
     */
    @Override
    public String map(TransformedRecord record) throws Exception {
        if (record == null) {
            LOG.debug("TransformedRecord is null, returning null.");
            return null;
        }

        try {
            long processingEpochMs = System.currentTimeMillis();
            // 1. Convert TransformedRecord to a Map.
            // This allows easy addition/modification of fields before final JSON serialization.
            // Using TypeReference to guide Jackson's generic conversion.
            Map<String, Object> recordMap = objectMapper.convertValue(record, new TypeReference<Map<String, Object>>() {});
            // Note: If TransformedRecord has getters and public fields, convertValue works well.
            // For more control or if using LinkedHashMap for specific field ordering (though not strictly guaranteed in JSON):
            // Map<String, Object> recordMap = new LinkedHashMap<>();
            // recordMap.put("sensor_id", record.getSensor_id());
            // recordMap.put("header_timestamp_us", record.getHeader_timestamp_us()); // Keep original numeric timestamp
            recordMap.put("type", "transformed-data");

            // 2. Add the current processing timestamp (UTC) with millisecond accuracy
            recordMap.put("processing_timestamp_ms", DateTimeUtil.getCurrentFormattedUtcTimestampMs());

            // 3. Convert the original kafka_timestamp (microseconds) to a formatted datetime string (UTC)
            if (record.getKafka_timestamp() != null) {
                recordMap.put("kafka_timestamp", DateTimeUtil.formatMicrosecondsToUtcTimestampMs(record.getKafka_timestamp()));
            } else {
                // Handle case where original kafka_timestamp might be null
                recordMap.put("kafka_timestamp", null);
            }
            String processingTimestamp = DateTimeUtil.getCurrentFormattedUtcTimestampMs();
            recordMap.put("processing_timestamp_ms", processingTimestamp);

            // // 3. Convert header_timestamp_us to milliseconds and formatted string
            // 4. Calculate delay and format header timestamp safely.
            Long headerTimestampUs = record.getKafka_timestamp();
            if (headerTimestampUs != null && headerTimestampUs > 0) {
                // Convert header time to milliseconds
                long headerEpochMs = headerTimestampUs / 1000;
                
                // Calculate delay
                double delayMs = (processingEpochMs - headerEpochMs) / 1000.0; // Convert to milliseconds

                // Add fields to the map
                recordMap.put("kafka_timestamp", DateTimeUtil.formatMicrosecondsToUtcTimestampMs(headerTimestampUs));
                recordMap.put("delay_ms", delayMs);
            } else {
                // If there's no valid header timestamp, we can't calculate delay.
                recordMap.put("kafka_timestamp", null);
                recordMap.put("delay_ms", null);
            }
            // Long headerTimestampUs = record.getHeader_timestamp_us();
            // if (headerTimestampUs != null) {
            //     long headerTimestampMs = headerTimestampUs / 1000;
            //     recordMap.put("header_datetime_ms", DateTimeUtil.formatMicrosecondsToUtcTimestampMs(headerTimestampUs));

            //     // 4. Compute delay in milliseconds (processing time - header time)
            //     long processingEpochMs = DateTimeUtil.parseFormattedUtcTimestampToEpochMs(processingTimestamp);
            //     long delayMs = processingEpochMs - headerTimestampMs;
            //     recordMap.put("delay_ms", delayMs);
            // } else {
            //     recordMap.put("header_datetime_ms", null);
            //     recordMap.put("delay_ms", null);
            // }
            // 4. Serialize the modified map to a JSON string
            String jsonOutput = objectMapper.writeValueAsString(recordMap);
            // LOG.debug("Successfully serialized record to JSON: {}", jsonOutput); // Can be verbose
            return jsonOutput;

        } catch (Exception e) {
            // Log the error with more details, including the problematic record if possible (be careful with sensitive data).
            LOG.warn("Failed to transform TransformedRecord and serialize to JSON. Record sensor_id: {}. Error: {}",
                    (record.getSensor_id() != null ? record.getSensor_id() : "N/A"),
                    e.getMessage(), e);
            return null; // Return null to allow downstream filtering of problematic records
        }
    }
}