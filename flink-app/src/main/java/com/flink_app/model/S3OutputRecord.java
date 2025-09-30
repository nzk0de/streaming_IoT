// src/main/java/com/flink_app/model/S3OutputRecord.java
package com.flink_app.model;

import java.io.Serializable;
import java.util.Objects;
public class S3OutputRecord implements Serializable {
    private static final long serialVersionUID = 1L;

    public String sensor_id;
    // public Double header_timestamp_us; // If you wanted this from TransformedRecord
    public SensorSample sample_data; // Embeds the SensorSample

    // --- NEW EXTRA COLUMN ---
    public String processing_info; // Example of an extra column

    // Default constructor
    public S3OutputRecord() {}

    public S3OutputRecord(String sensor_id, SensorSample sample_data, String processing_info) {
        this.sensor_id = sensor_id;
        this.sample_data = sample_data; // Or sample_data.copy() if you want to be defensive
        this.processing_info = processing_info;
    }

    // Getters and Setters (important for Table API if fields are not public,
    // or if you want to strictly follow JavaBean conventions)
    public String getSensor_id() { return sensor_id; }
    public void setSensor_id(String sensor_id) { this.sensor_id = sensor_id; }

    public SensorSample getSample_data() { return sample_data; }
    public void setSample_data(SensorSample sample_data) { this.sample_data = sample_data; }

    public String getProcessing_info() { return processing_info; }
    public void setProcessing_info(String processing_info) { this.processing_info = processing_info; }


    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        S3OutputRecord that = (S3OutputRecord) o;
        return Objects.equals(sensor_id, that.sensor_id) &&
               Objects.equals(sample_data, that.sample_data) &&
               Objects.equals(processing_info, that.processing_info);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensor_id, sample_data, processing_info);
    }

    @Override
    public String toString() {
        return "S3OutputRecord{" +
                "sensor_id='" + sensor_id + '\'' +
                ", sample_data=" + (sample_data != null ? sample_data.toString() : "null") +
                ", processing_info='" + processing_info + '\'' +
                '}';
    }
}