package com.flink_app.computations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.flink_app.constants.AppConstants;
import com.flink_app.model.SensorSample;

public class FlattenAndMagnitudeTransformer {

    private final List<String> keysToFlatten;

    public FlattenAndMagnitudeTransformer() {
        this.keysToFlatten = AppConstants.JSON_NESTED_KEYS_TO_FLATTEN;
    }

    // Constructor to allow custom keys if needed
    public FlattenAndMagnitudeTransformer(List<String> customKeysToFlatten) {
        this.keysToFlatten = (customKeysToFlatten != null && !customKeysToFlatten.isEmpty())
                ? customKeysToFlatten
                : AppConstants.JSON_NESTED_KEYS_TO_FLATTEN;
    }

    public List<SensorSample> transform(List<Map<String, Object>> rawSamples) {
        if (rawSamples == null) {
            return new ArrayList<>();
        }
        List<SensorSample> transformedSamples = new ArrayList<>(rawSamples.size());

        for (Map<String, Object> rawSampleMap : rawSamples) {
            if (rawSampleMap == null) continue;

            SensorSample processedSample = new SensorSample(); // Create new structured POJO

            for (Map.Entry<String, Object> entry : rawSampleMap.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                if (keysToFlatten.contains(key) && value instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> nestedMap = (Map<String, Object>) value;
                    for (Map.Entry<String, Object> nestedEntry : nestedMap.entrySet()) {
                        String flatKey = key + "_" + nestedEntry.getKey();
                        Object nestedValue = nestedEntry.getValue();
                        if (nestedValue instanceof Number) {
                            // --- START: MODIFICATION FOR GYRO VALUES ---
                            double numericValue = ((Number) nestedValue).doubleValue();

                            // If the key is for a gyro value, convert it from degrees to radians.
                            // This is the Java equivalent of Python's np.deg2rad().
                            if ("gyro".equals(key)) { // More specific check: only convert gyro values
                                numericValue = Math.toRadians(numericValue);
                            }

                            processedSample.putFeature(flatKey, numericValue);
                            // --- END: MODIFICATION FOR GYRO VALUES ---
                        }
                    }
                } else if (value instanceof Number) {
                    processedSample.putFeature(key, ((Number) value).doubleValue());
                }
            }

            // Calculate magnitudes
            // The getFeature calls will now retrieve the converted radian values for the gyroscope.
            double accX = processedSample.getFeature("acc_x", AppConstants.DEFAULT_NUMERIC_VALUE);
            double accY = processedSample.getFeature("acc_y", AppConstants.DEFAULT_NUMERIC_VALUE);
            double accZ = processedSample.getFeature("acc_z", AppConstants.DEFAULT_NUMERIC_VALUE);
            processedSample.accl_mag = Math.sqrt(accX * accX + accY * accY + accZ * accZ);

            double gyroX = processedSample.getFeature("gyro_x", AppConstants.DEFAULT_NUMERIC_VALUE);
            double gyroY = processedSample.getFeature("gyro_y", AppConstants.DEFAULT_NUMERIC_VALUE);
            double gyroZ = processedSample.getFeature("gyro_z", AppConstants.DEFAULT_NUMERIC_VALUE);
            processedSample.gyro_mag = Math.sqrt(gyroX * gyroX + gyroY * gyroY + gyroZ * gyroZ);

            transformedSamples.add(processedSample);
        }
        return transformedSamples;
    }
}