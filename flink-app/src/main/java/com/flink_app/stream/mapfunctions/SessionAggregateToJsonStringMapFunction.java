package com.flink_app.stream.mapfunctions; // <<< PACKAGE DECLARATION

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flink_app.model.SessionAggregate;

import org.apache.flink.api.common.functions.MapFunction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SessionAggregateToJsonStringMapFunction implements MapFunction<SessionAggregate, String> {
    private static final Logger LOG = LoggerFactory.getLogger(SessionAggregateToJsonStringMapFunction.class);
    
    // ObjectMapper is thread-safe and relatively expensive to create.
    // Making it transient tells Flink not to try to serialize it.
    // It will be null when the task starts/restarts and will be initialized on first use per task instance.
    private transient ObjectMapper objectMapper;

    @Override
    public String map(SessionAggregate aggregate) throws Exception {
        if (objectMapper == null) {
            objectMapper = new ObjectMapper();
        }
        try {
            return objectMapper.writeValueAsString(aggregate);
        } catch (Exception e) {
            LOG.error("Error serializing SessionAggregate to JSON for session_id: {}. Aggregate: {}", 
                      (aggregate != null ? aggregate.getSensor_session_id() : "null"), 
                      (aggregate != null ? aggregate.toString() : "null"), // toString might be heavy
                      e);
            // Depending on your error handling strategy:

            return null; 
        }
    }
}