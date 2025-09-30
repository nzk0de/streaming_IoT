package com.flink_app.stream.mapfunctions;

import java.util.ArrayList;
import java.util.List;

import com.flink_app.computations.FlattenAndMagnitudeTransformer;
import com.flink_app.constants.AppConstants;
import com.flink_app.model.InputRecord;
import com.flink_app.model.SensorSample;
import com.flink_app.model.TransformedRecord;

import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.configuration.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class SensorTransformerMapFunction extends RichMapFunction<InputRecord, TransformedRecord> {

    private static final Logger LOG = LoggerFactory.getLogger(SensorTransformerMapFunction.class);
    private transient FlattenAndMagnitudeTransformer transformer;

    @Override
    public void open(Configuration parameters) throws Exception {
        super.open(parameters);
        this.transformer = new FlattenAndMagnitudeTransformer();
    }

    @Override
    public TransformedRecord map(InputRecord inputRecord) throws Exception {
        if (inputRecord == null || inputRecord.getSamples() == null) {
            LOG.debug("InputRecord or its samples are null, returning empty transformed record.");
            return new TransformedRecord(
                inputRecord != null ? inputRecord.getSensor_id() : "unknown_sensor",
                inputRecord != null ? inputRecord.getHeader_timestamp_us() : 0L,
                inputRecord != null ? inputRecord.getKafka_timestamp() : 0L,
                new ArrayList<>(),
                null);
        }

        List<SensorSample> processedSamples = transformer.transform(inputRecord.getSamples());

        if (inputRecord.getHeader_timestamp_us() != null) {
            double timestampInSeconds = (double) inputRecord.getHeader_timestamp_us() / AppConstants.MICROSECONDS_PER_SECOND;
            for (SensorSample sample : processedSamples) {
                if (sample != null) {
                    sample.timestamp = timestampInSeconds; // <<< CORRECTED: Set field directly
                }
            }
        } else {
            for (SensorSample sample : processedSamples) {
                 if (sample != null) {
                    sample.timestamp = 0.0; // Or null, as appropriate for your logic
                 }
            }
        }

        return new TransformedRecord(
                inputRecord.getSensor_id(),
                inputRecord.getHeader_timestamp_us(),
                inputRecord.getKafka_timestamp(), // Use the kafka_timestamp directly
                processedSamples,
                null 
        );
    }
}