package com.flink_app.constants;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class AppConstants {
    // --- Time & Sampling ---
    public static final double SAMPLING_RATE_HZ = 200.0;
    public static final double SAMPLING_PERIOD_S = 1.0 / SAMPLING_RATE_HZ;
    public static final double MICROSECONDS_PER_SECOND = 1_000_000.0;
    public static final double MILLISECONDS_PER_SECOND = 1_000.0;

    // --- Feature Engineering ---
    public static final List<String> FEATURE_COLS = Collections.unmodifiableList(Arrays.asList(
        "accl_mag",
        // "accl_sum_of_changes",
        "gyro_mag"
        // "gyro_mag_energy"
    ));
    public static final String DIST_CACHE_ONNX_MODEL_KEY = "onnxModelFile";
    public static final String DIST_CACHE_SCALING_INFO_KEY = "scalingInfoFile";
    public static final String DIST_CACHE_MODEL_PARAMS_KEY = "modelParamsFile"; // New key for model params
    // Length of the sample window after feature extraction (tail)
    public static final int SAMPLE_LEN = 64;

    // Rolling window size for feature calculation (e.g., sum of changes, energy)
    // This was ROLLING_WINDOW_SIZE in Python's RollingFeatureTransformer
    public static final int FEATURE_ROLLING_WINDOW_SIZE = 132;
    public static final int NUM_PREV_MSGS_TO_BUFFER_FOR_FEATURES = (int) Math.ceil((double) FEATURE_ROLLING_WINDOW_SIZE / SAMPLE_LEN);
    
    public static final double DEFAULT_NUMERIC_VALUE = 0.0; // If not already present

    // Rolling window size specifically for FlattenAndMagnitudeTransformer calculations
    // (accl_sum_of_changes, gyro_mag_energy within that transformer)
    public static final int FLATTEN_TRANSFORMER_ROLLING_WINDOW_SIZE = 64;

    // --- Inference ---
    // Number of batches (output from FeatureProcessFunction) to collect before running inference
    public static final int NUM_BATCH_SAMPLES_FOR_INFERENCE = 3;

    // Names of nested JSON objects to flatten in SensorTransformer
    public static final List<String> JSON_NESTED_KEYS_TO_FLATTEN = Collections.unmodifiableList(Arrays.asList(
            "acc", "gyro", "mag"
    ));


    // Epsilon for standard deviation to avoid division by zero
    public static final double STD_DEV_EPSILON = 1e-6;


    public static final double HISTOGRAM_MAX_VELOCITY = 13.0; // m/s
    public static final int HISTOGRAM_NUM_BINS = 100; // e.g., for 0.5 m/s bin width
    public static final double HISTOGRAM_BIN_WIDTH = HISTOGRAM_MAX_VELOCITY / HISTOGRAM_NUM_BINS;
}