package com.flink_app.model;

import java.io.Serializable;
import java.util.Objects;

/**
 * Represents the final aggregated metrics for a specific sensor session.
 */
public class SessionAggregate implements Serializable {
    private static final long serialVersionUID = 1L;

    public String sensor_session_id;
    public String sensor_id; // The physical sensor this session belongs to
    public Double session_max_velocity;
    public Double session_total_distance;
    public Long aggregation_timestamp_ms;

    // Default constructor for Flink
    public SessionAggregate() {}

    public SessionAggregate(String sensor_session_id, String sensor_id, Double session_max_velocity, Double session_total_distance, Long aggregation_timestamp_ms) {
        this.sensor_session_id = sensor_session_id;
        this.sensor_id = sensor_id;
        this.session_max_velocity = session_max_velocity;
        this.session_total_distance = session_total_distance;
        this.aggregation_timestamp_ms = aggregation_timestamp_ms;
    }


    public String getSensor_id() { return sensor_id; }
    public void setSensor_id(String sensor_id) { this.sensor_id = sensor_id; }
    public String getSensor_session_id() { return sensor_session_id; }
    public void setSensor_session_id(String sensor_session_id) { this.sensor_session_id = sensor_session_id; }


    public Double getMax_velocity_so_far() { return session_max_velocity; }
    public void setMax_velocity_so_far(Double max_velocity_so_far) { this.session_max_velocity = max_velocity_so_far; }

    public Double getDistance_so_far() { return session_total_distance; }
    public void setDistance_so_far(Double distance_so_far) { this.session_total_distance = distance_so_far; }

    public Long getAggregation_timestamp_ms() { return aggregation_timestamp_ms; }
    public void setAggregation_timestamp_ms(Long aggregation_timestamp_ms) { this.aggregation_timestamp_ms = aggregation_timestamp_ms; }

    @Override
    public String toString() {
        return "SessionAggregate{" +
                "sensor_session_id='" + sensor_session_id + '\'' +
                ", sensor_id='" + sensor_id + '\'' +
                ", session_max_velocity=" + session_max_velocity +
                ", session_total_distance=" + session_total_distance +
                ", aggregation_timestamp_ms=" + aggregation_timestamp_ms +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SessionAggregate that = (SessionAggregate) o;
        return Objects.equals(sensor_session_id, that.sensor_session_id) && Objects.equals(sensor_id, that.sensor_id) && Objects.equals(session_max_velocity, that.session_max_velocity) && Objects.equals(session_total_distance, that.session_total_distance) && Objects.equals(aggregation_timestamp_ms, that.aggregation_timestamp_ms);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensor_session_id, sensor_id, session_max_velocity, session_total_distance, aggregation_timestamp_ms);
    }


}