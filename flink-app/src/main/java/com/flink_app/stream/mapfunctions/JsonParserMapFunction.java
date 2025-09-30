package com.flink_app.stream.mapfunctions;

import com.flink_app.model.InputRecord;
import com.flink_app.utils.JsonUtil;

import org.apache.flink.api.common.functions.MapFunction;

public class JsonParserMapFunction implements MapFunction<String, InputRecord> {


    @Override
    public InputRecord map(String jsonString) throws Exception {
        // The actual parsing logic is in JsonUtil
        InputRecord record = JsonUtil.parseJsonToInputRecord(jsonString);
        // LOG.debug("Parsed JSON to InputRecord: {} -> {}", jsonString, record); // Can be very verbose
        return record; // JsonUtil returns null on error, which can be filtered out later
    }
}