// Create this in com.flink_app.model package
package com.flink_app.model;

import java.io.Serializable;
import java.util.Objects;

public class NewSessionEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    public String sensor_session_id; // Composite key for MongoDB: sensor_id + "_" + session_id_part
    public String sensor_id; // The sensor_id from TransformedRecord
    public Long session_id_part; // The generated part of the session_id (e.g., timestamp or UUID)
    public Long session_start_timestamp_us; // Timestamp of the first record in this session

    public NewSessionEvent() {}

    public NewSessionEvent(String sensor_session_id, String sensor_id, Long session_id_part, Long session_start_timestamp_us) {
        this.sensor_session_id = sensor_session_id;
        this.sensor_id = sensor_id;
        this.session_id_part = session_id_part;
        this.session_start_timestamp_us = session_start_timestamp_us;
    }

    // Standard Getters, Setters, equals, hashCode, toString
    public String getSensor_session_id() { return sensor_session_id; }
    public void setSensor_session_id(String sensor_session_id) { this.sensor_session_id = sensor_session_id; }
    public String getsensor_id() { return sensor_id; }
    public void setsensor_id(String sensor_id) { this.sensor_id = sensor_id; }
    public Long getSession_id_part() { return session_id_part; }
    public void setSession_id_part(Long session_id_part) { this.session_id_part = session_id_part; }
    public Long getSession_start_timestamp_us() { return session_start_timestamp_us; }
    public void setSession_start_timestamp_us(Long session_start_timestamp_us) { this.session_start_timestamp_us = session_start_timestamp_us; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NewSessionEvent that = (NewSessionEvent) o;
        return Objects.equals(sensor_session_id, that.sensor_session_id) &&
               Objects.equals(sensor_id, that.sensor_id) &&
               Objects.equals(session_id_part, that.session_id_part) &&
               Objects.equals(session_start_timestamp_us, that.session_start_timestamp_us);
    }

    @Override
    public int hashCode() {
        return Objects.hash(sensor_session_id, sensor_id, session_id_part, session_start_timestamp_us);
    }

    @Override
    public String toString() {
        return "NewSessionEvent{" +
                "sensor_session_id='" + sensor_session_id + '\'' +
                ", sensor_id='" + sensor_id + '\'' +
                ", session_id_part='" + session_id_part + '\'' +
                ", session_start_timestamp_us=" + session_start_timestamp_us +
                '}';
    }
}