// Create this in com.flink_app.stream.mapfunctions package
package com.flink_app.stream.mapfunctions;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink_app.model.NewSessionEvent;

import org.apache.flink.api.common.functions.MapFunction;

public class NewSessionEventToJsonStringMapFunction implements MapFunction<NewSessionEvent, String> {
    private static final long serialVersionUID = 1L;
    private transient ObjectMapper objectMapper;

    @Override
    public String map(NewSessionEvent event) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {

            throw e;
        }
    }
}