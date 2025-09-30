package com.flink_app.model;

import java.io.Serializable;
import java.util.Objects;

public class SensorSample implements Serializable {
    private static final long serialVersionUID = 2L; // New version

    // Raw or flattened sensor data
    public Double acc_x;
    public Double acc_y;
    public Double acc_z;
    public Double gyro_x;
    public Double gyro_y;
    public Double gyro_z;
    public Double mag_x;
    public Double mag_y;
    public Double mag_z;

    // Calculated magnitudes
    public Double accl_mag;
    public Double gyro_mag;

    // Timestamp
    public Double timestamp; // in seconds
    public Double kafka_timestamp; // in seconds, original timestamp from the sample
    // Rolling features
    // public Double accl_sum_of_changes;
    // public Double gyro_mag_energy;

    // Predicted value
    public Double predicted_velocity;

    // Default constructor for Flink/Kryo/Jackson
    public SensorSample() {}


    public Double getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Double timestamp) {
        this.timestamp = timestamp;
    }
    // --- Getters and Setters ---

    public Double getAcc_x() { return acc_x; }
    public void setAcc_x(Double acc_x) { this.acc_x = acc_x; }

    public Double getAcc_y() { return acc_y; }
    public void setAcc_y(Double acc_y) { this.acc_y = acc_y; }

    public Double getAcc_z() { return acc_z; }
    public void setAcc_z(Double acc_z) { this.acc_z = acc_z; }

    public Double getGyro_x() { return gyro_x; }
    public void setGyro_x(Double gyro_x) { this.gyro_x = gyro_x; }

    public Double getGyro_y() { return gyro_y; }
    public void setGyro_y(Double gyro_y) { this.gyro_y = gyro_y; }

    public Double getGyro_z() { return gyro_z; }
    public void setGyro_z(Double gyro_z) { this.gyro_z = gyro_z; }

    public Double getMag_x() { return mag_x; }
    public void setMag_x(Double mag_x) { this.mag_x = mag_x; }

    public Double getMag_y() { return mag_y; }
    public void setMag_y(Double mag_y) { this.mag_y = mag_y; }

    public Double getMag_z() { return mag_z; }
    public void setMag_z(Double mag_z) { this.mag_z = mag_z; }

    public Double getAccl_mag() { return accl_mag; }
    public void setAccl_mag(Double accl_mag) { this.accl_mag = accl_mag; }

    public Double getGyro_mag() { return gyro_mag; }
    public void setGyro_mag(Double gyro_mag) { this.gyro_mag = gyro_mag; }

    public Double getKafka_timestamp() {
        return kafka_timestamp;
    }

    public void setKafka_timestamp(Double kafka_timestamp) {
        this.kafka_timestamp = kafka_timestamp;
    }
    // public Double getAccl_sum_of_changes() { return accl_sum_of_changes; }
    // public void setAccl_sum_of_changes(Double accl_sum_of_changes) { this.accl_sum_of_changes = accl_sum_of_changes; }

    // public Double getGyro_mag_energy() { return gyro_mag_energy; }
    // public void setGyro_mag_energy(Double gyro_mag_energy) { this.gyro_mag_energy = gyro_mag_energy; }

    public Double getPredicted_velocity() { return predicted_velocity; }
    public void setPredicted_velocity(Double predicted_velocity) { this.predicted_velocity = predicted_velocity; }

    // Method to get a feature by name, checking dedicated fields first
    public Double getFeature(String featureName, Double defaultValue) {
        switch (featureName) {
            case "acc_x": return acc_x != null ? acc_x : defaultValue;
            case "acc_y": return acc_y != null ? acc_y : defaultValue;
            case "acc_z": return acc_z != null ? acc_z : defaultValue;
            case "gyro_x": return gyro_x != null ? gyro_x : defaultValue;
            case "gyro_y": return gyro_y != null ? gyro_y : defaultValue;
            case "gyro_z": return gyro_z != null ? gyro_z : defaultValue;
            case "mag_x": return mag_x != null ? mag_x : defaultValue;
            case "mag_y": return mag_y != null ? mag_y : defaultValue;
            case "mag_z": return mag_z != null ? mag_z : defaultValue;
            case "accl_mag": return accl_mag != null ? accl_mag : defaultValue;
            case "gyro_mag": return gyro_mag != null ? gyro_mag : defaultValue;
            case "timestamp": return timestamp != null ? timestamp : defaultValue;
            case "kafka_timestamp": return kafka_timestamp != null ? kafka_timestamp : defaultValue;
            // case "accl_sum_of_changes": return accl_sum_of_changes != null ? accl_sum_of_changes : defaultValue;
            // case "gyro_mag_energy": return gyro_mag_energy != null ? gyro_mag_energy : defaultValue;
            case "predicted_velocity": return predicted_velocity != null ? predicted_velocity : defaultValue;
            default:
                return defaultValue;
        }
    }

    // Method to set a feature by name, trying dedicated fields first
    public void putFeature(String featureName, Double value) {
        switch (featureName) {
            case "acc_x": this.acc_x = value; break;
            case "acc_y": this.acc_y = value; break;
            case "acc_z": this.acc_z = value; break;
            case "gyro_x": this.gyro_x = value; break;
            case "gyro_y": this.gyro_y = value; break;
            case "gyro_z": this.gyro_z = value; break;
            case "mag_x": this.mag_x = value; break;
            case "mag_y": this.mag_y = value; break;
            case "mag_z": this.mag_z = value; break;
            case "accl_mag": this.accl_mag = value; break;
            case "gyro_mag": this.gyro_mag = value; break;
            case "timestamp": this.timestamp = value; break;
            case "kafka_timestamp": this.kafka_timestamp = value; break;
            // case "accl_sum_of_changes": this.accl_sum_of_changes = value; break;
            // case "gyro_mag_energy": this.gyro_mag_energy = value; break;
            case "predicted_velocity": this.predicted_velocity = value; break;
            default:
                break;
        }
    }

    public SensorSample copy() {
        SensorSample newSample = new SensorSample();
        newSample.acc_x = this.acc_x;
        newSample.acc_y = this.acc_y;
        newSample.acc_z = this.acc_z;
        newSample.gyro_x = this.gyro_x;
        newSample.gyro_y = this.gyro_y;
        newSample.gyro_z = this.gyro_z;
        newSample.mag_x = this.mag_x;
        newSample.mag_y = this.mag_y;
        newSample.mag_z = this.mag_z;
        newSample.accl_mag = this.accl_mag;
        newSample.gyro_mag = this.gyro_mag;
        newSample.timestamp = this.timestamp;
        newSample.kafka_timestamp = this.kafka_timestamp; // Copy kafka_timestamp
        // newSample.accl_sum_of_changes = this.accl_sum_of_changes;
        // newSample.gyro_mag_energy = this.gyro_mag_energy;
        newSample.predicted_velocity = this.predicted_velocity;
        return newSample;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SensorSample that = (SensorSample) o;
        return Objects.equals(acc_x, that.acc_x) &&
               Objects.equals(acc_y, that.acc_y) &&
               Objects.equals(acc_z, that.acc_z) &&
               Objects.equals(gyro_x, that.gyro_x) &&
               Objects.equals(gyro_y, that.gyro_y) &&
               Objects.equals(gyro_z, that.gyro_z) &&
               Objects.equals(mag_x, that.mag_x) &&
               Objects.equals(mag_y, that.mag_y) &&
               Objects.equals(mag_z, that.mag_z) &&
               Objects.equals(accl_mag, that.accl_mag) &&
               Objects.equals(gyro_mag, that.gyro_mag) &&
               Objects.equals(timestamp, that.timestamp) &&
               Objects.equals(kafka_timestamp, that.kafka_timestamp) && // Compare kafka_timestamp
            //    Objects.equals(accl_sum_of_changes, that.accl_sum_of_changes) &&
            //    Objects.equals(gyro_mag_energy, that.gyro_mag_energy) &&
               Objects.equals(predicted_velocity, that.predicted_velocity);
    }

    @Override
    public int hashCode() {
        return Objects.hash(acc_x, acc_y, acc_z, gyro_x, gyro_y, gyro_z, mag_x, mag_y, mag_z,
                            accl_mag, gyro_mag, timestamp, kafka_timestamp, 
                             predicted_velocity);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("SensorSample{");
        if (timestamp!=null) sb.append("timestamp=").append(timestamp);
        if (kafka_timestamp!=null) sb.append(", cur_timestamp=").append(kafka_timestamp);
        if (acc_x!=null) sb.append(", acc_x=").append(acc_x);
        if (acc_y!=null) sb.append(", acc_y=").append(acc_y);
        if (acc_z!=null) sb.append(", acc_z=").append(acc_z);
        if (gyro_x!=null) sb.append(", gyro_x=").append(gyro_x);
        if (gyro_y!=null) sb.append(", gyro_y=").append(gyro_y);
        if (gyro_z!=null) sb.append(", gyro_z=").append(gyro_z);
        if (mag_x!=null) sb.append(", mag_x=").append(mag_x);
        if (mag_y!=null) sb.append(", mag_y=").append(mag_y);
        if (mag_z!=null) sb.append(", mag_z=").append(mag_z);
        if (accl_mag!=null) sb.append(", accl_mag=").append(accl_mag);
        if (gyro_mag!=null) sb.append(", gyro_mag=").append(gyro_mag);
        // if (accl_sum_of_changes!=null) sb.append(", accl_sum_of_changes=").append(accl_sum_of_changes);
        // if (gyro_mag_energy!=null) sb.append(", gyro_mag_energy=").append(gyro_mag_energy);
        if (predicted_velocity!=null) sb.append(", predicted_velocity=").append(predicted_velocity);
        sb.append('}');
        return sb.toString();
    }
}