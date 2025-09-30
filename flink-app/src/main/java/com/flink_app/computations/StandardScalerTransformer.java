package com.flink_app.computations;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.flink_app.model.SensorSample; // Uses new SensorSample

public class StandardScalerTransformer {

    private final Map<String, Double> means;
    private final Map<String, Double> stds;
    private final List<String> featureCols; // Columns to scale

    public StandardScalerTransformer(Map<String, Double> means, Map<String, Double> stds, List<String> featureCols) {
        this.means = means;
        this.stds = stds; // Assuming stds already handled the max(std, epsilon) logic during loading
        this.featureCols = featureCols;
    }

    /**
     * Applies standard scaling to the specified feature columns in a list of SensorSamples.
     * Modifies the SensorSample objects in place.
     *
     * @param samplesList List of SensorSample objects to scale.
     * @return The same list with samples scaled (modified in place).
     */
    public List<SensorSample> transform(List<SensorSample> samplesList) {
        if (samplesList == null) {
            return new ArrayList<>();
        }
        for (SensorSample sample : samplesList) {
            if (sample == null) continue;
            for (String col : featureCols) {
                Double currentValue = sample.getFeature(col, 0.0); // Get current value, default to 0.0 if missing
                double mean = means.getOrDefault(col, 0.0);
                double std = stds.getOrDefault(col, 1.0); // Default std to 1 to avoid division by zero if missing, though already handled

                double scaledValue = (currentValue - mean) / std; // std already has epsilon applied
                sample.putFeature(col, scaledValue); // Update the sample
            }
        }
        return samplesList; // Return the modified list
    }
    
    /**
     * Applies standard scaling and returns a new list of new SensorSample objects.
     * Does not modify the original samples.
     *
     * @param samplesList List of SensorSample objects to scale.
     * @return A new list with new, scaled SensorSample objects.
     */
    public List<SensorSample> transformAndCopy(List<SensorSample> samplesList) {
        if (samplesList == null) {
            return new ArrayList<>();
        }
        List<SensorSample> scaledSamples = new ArrayList<>(samplesList.size());
        for (SensorSample originalSample : samplesList) {
            if (originalSample == null) {
                scaledSamples.add(null); // Or skip
                continue;
            }
            SensorSample scaledSample = originalSample.copy(); // Work on a copy
            for (String col : featureCols) {
                Double currentValue = scaledSample.getFeature(col, 0.0);
                double mean = means.getOrDefault(col, 0.0);
                double std = stds.getOrDefault(col, 1.0);
                double scaledValue = (currentValue - mean) / std;
                scaledSample.putFeature(col, scaledValue);
            }
            scaledSamples.add(scaledSample);
        }
        return scaledSamples;
    }
}