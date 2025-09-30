package com.flink_app.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink_app.model.InputRecord;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JsonUtil {

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false); // Be lenient with extra fields
    private static final Logger LOG = LoggerFactory.getLogger(JsonUtil.class);


    public static InputRecord parseJsonToInputRecord(String jsonString) {
        if (jsonString == null || jsonString.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(jsonString, InputRecord.class);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to parse JSON string to InputRecord: {}. Error: {}", jsonString, e.getMessage());
            // Consider how to handle parsing errors. Returning null will allow filtering.
            // For more robust error handling, you could throw a custom exception or use a side output.
            return null;
        }
    }

    public static String serializeInputRecordToJson(InputRecord record) {
        if (record == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(record);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize InputRecord to JSON: {}. Error: {}", record, e.getMessage());
            return null; // Or throw an exception
        }
    }

    // Generic serializer if needed later for other record types
    public static <T> String serializeToJson(T object) {
        if (object == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            LOG.warn("Failed to serialize object of type {} to JSON: {}. Error: {}",
                    object.getClass().getSimpleName(), object, e.getMessage());
            return null;
        }
    }
}