// In a new file: com/flink_app/model/FlatSensorRecord.java
package com.flink_app.model;

// A simple POJO for writing to Parquet.
// Must be public, have a no-arg constructor, and public fields or getters/setters.
public class FlatSensorRecord {

    // Public fields are easiest for Flink's reflection-based serializer
    public String sensor_id;
    public Double cur_timestamp;
    public Double kafka_timestamp; // Corrected timestamp in seconds, based on the header timestamp and sample index
    public Double acc_x;
    public Double acc_y;
    public Double acc_z;
    public Double gyro_x;
    public Double gyro_y;
    public Double gyro_z;
    public Double mag_x;
    public Double mag_y;
    public Double mag_z;
    public Double accl_mag;
    public Double gyro_mag;
    public Double predicted_velocity;

    // A public no-argument constructor is REQUIRED by Flink.
    public FlatSensorRecord() {}
}