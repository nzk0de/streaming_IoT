package com.flink_app.model;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

public class ModelParams {
    public int hiddenDim;
    public int numLayers;
    public boolean bidirectional;
    public List<String> featureCols;

    public static ModelParams fromJsonFile(File file) throws IOException {
        ObjectMapper objectMapper = new ObjectMapper();
        Map<String, Object> map = objectMapper.readValue(file, new TypeReference<>() {});
        ModelParams params = new ModelParams();
        params.hiddenDim = ((Number) map.get("model_lstm_hidden_dim")).intValue();
        params.numLayers = ((Number) map.get("model_lstm_num_layers")).intValue();
        params.bidirectional = (Boolean) map.get("model_lstm_bidirectional");
        params.featureCols = objectMapper.convertValue(
            map.get("feature_cols"),
            new TypeReference<List<String>>() {}
        );
        return params;
    }
}