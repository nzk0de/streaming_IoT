package com.flink_app.model; // <<< MUST BE THE FIRST NON-COMMENT LINE

import java.io.Serializable;
import java.util.List;
import java.util.Objects; // Used for equals/hashCode

/**
 * Represents a record after initial sensor transformations (flattening, magnitudes)
 * and potentially after feature engineering (rolling features).
 * The 'samples' list now contains SensorSample objects (Map<String, Double>).
 * Includes a sensor_session_id.
 */
public class TransformedRecord implements Serializable { // <<< CLASS NAME MUST MATCH FILE NAME
    private static final long serialVersionUID = 1L;

    public String sensor_id;
    public Long header_timestamp_us;
    public Long kafka_timestamp; // This will be set later in the map function
    public List<SensorSample> samples; // List of flattened and feature-enriched samples
    public String sensor_session_id;
    // Default constructor for Flink POJO
    public TransformedRecord() {}

    public TransformedRecord(String sensor_id, Long header_timestamp_us, Long kafka_timestamp, List<SensorSample> samples, String sensor_session_id) {
        this.sensor_id = sensor_id;
        this.header_timestamp_us = header_timestamp_us;
        this.samples = samples;
        this.sensor_session_id = sensor_session_id;
        this.kafka_timestamp = kafka_timestamp;
    }

    // Getters and Setters
    public String getSensor_id() {
        return sensor_id;
    }

    public void setSensor_id(String sensor_id) {
        this.sensor_id = sensor_id;
    }

    public Long getHeader_timestamp_us() {
        return header_timestamp_us;
    }

    public void setHeader_timestamp_us(Long header_timestamp_us) {
        this.header_timestamp_us = header_timestamp_us;
    }

    public Long getKafka_timestamp() {
        return kafka_timestamp;
    }
    public void setKafka_timestamp(Long kafka_timestamp) {
        this.kafka_timestamp = kafka_timestamp;
    }

    public List<SensorSample> getSamples() {
        return samples;
    }

    public void setSamples(List<SensorSample> samples) {
        this.samples = samples;
    }

    public String getSensor_session_id() { // <<<< NEW GETTER
        return sensor_session_id;
    }

    public void setSensor_session_id(String sensor_session_id) { // <<<< NEW SETTER
        this.sensor_session_id = sensor_session_id;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TransformedRecord that = (TransformedRecord) o;
        return Objects.equals(sensor_id, that.sensor_id) &&
               Objects.equals(header_timestamp_us, that.header_timestamp_us) &&
               Objects.equals(kafka_timestamp, that.kafka_timestamp) &&
               Objects.equals(samples, that.samples) &&
               Objects.equals(sensor_session_id, that.sensor_session_id); // <<<< MODIFIED EQUALS
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensor_id, header_timestamp_us, samples, sensor_session_id); // <<<< MODIFIED HASHCODE
    }

    @Override
    public String toString() {
        return "TransformedRecord{" +
               "sensor_id='" + sensor_id + '\'' +
               ", sensor_session_id='" + sensor_session_id + '\'' + // <<<< MODIFIED TOSTRING
               ", header_timestamp_us=" + header_timestamp_us +
                ", kafka_timestamp=" + kafka_timestamp +
               ", samples_count=" + (samples != null ? samples.size() : "null") +
               '}';
    }
}