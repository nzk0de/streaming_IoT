package com.flink_app.stream.mapfunctions;

import java.util.List;

import com.flink_app.model.FlatSensorRecord;
import com.flink_app.model.SensorSample;
import com.flink_app.model.TransformedRecord;

import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.util.Collector;

/**
 * Flattens a TransformedRecord, which contains a batch of samples, into individual
 * FlatSensorRecord objects. It critically corrects the timestamp for each sample
 * based on the known sampling frequency and the batch's header timestamp (from the watermark).
 */
public class TransformedRecordToPojoFlatMap implements FlatMapFunction<TransformedRecord, FlatSensorRecord> {

    // Define the sampling frequency (200 samples per second) as a constant.
    // Using double prevents integer division issues.
    private static final double SAMPLING_FREQUENCY = 200.0;

    // Calculate the time difference between each sample in seconds.
    private static final double TIME_DELTA_SECONDS = 1.0 / SAMPLING_FREQUENCY;

    @Override
    public void flatMap(TransformedRecord record, Collector<FlatSensorRecord> out) throws Exception {
        if (record == null || record.getSamples() == null || record.getSamples().isEmpty() || record.getHeader_timestamp_us() == null) {
            return;
        }

        List<SensorSample> samples = record.getSamples();
        
        // Use the header_timestamp_us as the base. This is the timestamp assigned to the
        // watermark for the entire batch. It is in microseconds, so we convert it to seconds.
        double baseTimestampInSeconds = record.getHeader_timestamp_us() / 1_000_000.0;
        double baseKafkaTimestampInSeconds = record.getKafka_timestamp() / 1_000_000.0; // Convert to seconds

        // Use an indexed loop to calculate the time offset for each sample.
        for (int i = 0; i < samples.size(); i++) {
            SensorSample sample = samples.get(i);
            if (sample == null) continue; // Safety check

            // Calculate the corrected, interpolated timestamp for this specific sample.
            double timestampOffset = i * TIME_DELTA_SECONDS;
            double correctedTimestamp = baseTimestampInSeconds + timestampOffset;
            double correctedKafkaTimestamp = baseKafkaTimestampInSeconds + timestampOffset; // Convert to seconds
            // Create the new record for the flattened output.
            FlatSensorRecord flatRecord = new FlatSensorRecord();
            flatRecord.sensor_id = record.getSensor_id();

            // --- THIS IS THE KEY CHANGE ---
            // 1. Assign the newly calculated, correct timestamp to the 'timestamp' field.
            flatRecord.kafka_timestamp = correctedKafkaTimestamp;
            // 2. Assign the original, uncorrected timestamp from the sample to 'cur_timestamp'.
            flatRecord.cur_timestamp = correctedTimestamp;
            // -----------------------------

            // Assign the rest of the fields as before.
            flatRecord.acc_x = sample.getAcc_x();
            flatRecord.acc_y = sample.getAcc_y();
            flatRecord.acc_z = sample.getAcc_z();
            flatRecord.gyro_x = sample.getGyro_x();
            flatRecord.gyro_y = sample.getGyro_y();
            flatRecord.gyro_z = sample.getGyro_z();
            flatRecord.mag_x = sample.getMag_x();
            flatRecord.mag_y = sample.getMag_y();
            flatRecord.mag_z = sample.getMag_z();
            flatRecord.accl_mag = sample.getAccl_mag();
            flatRecord.gyro_mag = sample.getGyro_mag();
            flatRecord.predicted_velocity = sample.getPredicted_velocity();
            
            out.collect(flatRecord);
        }
    }
}