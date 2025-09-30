package com.flink_app.model; // <<< CHECK THIS LINE

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;


@JsonIgnoreProperties(ignoreUnknown = true)
public class InputRecord implements Serializable { // <<< CHECK THIS LINE
    private static final long serialVersionUID = 1L;

    public String sensor_id;
    public Long header_timestamp_us;
    public Long kafka_timestamp; 
    public List<Map<String, Object>> samples;

    public InputRecord() {}

    public InputRecord(String sensor_id, Long header_timestamp_us, List<Map<String, Object>> samples) {
        this.sensor_id = sensor_id;
        this.header_timestamp_us = header_timestamp_us;
        this.kafka_timestamp = null; // Initialize to null, will be set later
        this.samples = samples;
    }

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
    public List<Map<String, Object>> getSamples() {
        return samples;
    }

    public void setSamples(List<Map<String, Object>> samples) {
        this.samples = samples;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        InputRecord that = (InputRecord) o;
        return Objects.equals(sensor_id, that.sensor_id) &&
               Objects.equals(header_timestamp_us, that.header_timestamp_us) &&
               Objects.equals(kafka_timestamp, that.kafka_timestamp) &&
               Objects.equals(samples, that.samples);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensor_id, header_timestamp_us, samples);
    }

    @Override
    public String toString() {
        return "InputRecord{" +
               "sensor_id='" + sensor_id + '\'' +
               ", header_timestamp_us=" + header_timestamp_us +
                ", kafka_timestamp=" + kafka_timestamp +
               ", samples_count=" + (samples != null ? samples.size() : "null") +
               '}';
    }
}