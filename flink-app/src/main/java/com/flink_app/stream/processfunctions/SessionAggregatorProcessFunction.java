package com.flink_app.stream.processfunctions;

import com.flink_app.constants.AppConstants;
import com.flink_app.model.SensorSample;
import com.flink_app.model.SessionAggregate;
import com.flink_app.model.TransformedRecord;

import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

public class SessionAggregatorProcessFunction extends KeyedProcessFunction<String, TransformedRecord, SessionAggregate> {

    private static final long AGGREGATION_INTERVAL_MS = 2000L;

    // STATE FOR THE CURRENT 2-SECOND AGGREGATION INTERVAL
    private transient ListState<Double> currentIntervalVelocitiesState;

    // STATE FOR THE ENTIRE SESSION (SCOPED BY THE KEY: sensor_session_id)
    private transient ValueState<String> sensorIdState; // <-- NEW: To store the sensor_id
    private transient ValueState<Double> sessionMaxVelocityState;
    private transient ValueState<Double> sessionTotalDistanceState;
    private transient ValueState<Long> nextTimerTimestampState;

    public SessionAggregatorProcessFunction() {}

    @Override
    public void open(Configuration parameters) throws Exception {
        // Interval state
        ListStateDescriptor<Double> velocitiesDesc = new ListStateDescriptor<>("currentIntervalVelocities", Types.DOUBLE);
        currentIntervalVelocitiesState = getRuntimeContext().getListState(velocitiesDesc);

        // Session-wide state
        ValueStateDescriptor<String> sensorIdDesc = new ValueStateDescriptor<>("sensorId", Types.STRING);
        sensorIdState = getRuntimeContext().getState(sensorIdDesc); // <-- NEW: Initialize state

        ValueStateDescriptor<Double> maxVelocityDesc = new ValueStateDescriptor<>("sessionMaxVelocity", Types.DOUBLE);
        sessionMaxVelocityState = getRuntimeContext().getState(maxVelocityDesc);

        ValueStateDescriptor<Double> totalDistanceDesc = new ValueStateDescriptor<>("sessionTotalDistance", Types.DOUBLE);
        sessionTotalDistanceState = getRuntimeContext().getState(totalDistanceDesc);

        ValueStateDescriptor<Long> timerStateDesc = new ValueStateDescriptor<>("nextTimerTimestamp", Types.LONG);
        nextTimerTimestampState = getRuntimeContext().getState(timerStateDesc);
    }

    @Override
    public void processElement(TransformedRecord record, Context ctx, Collector<SessionAggregate> out) throws Exception {
        // --- State Initialization for the Session ---
        Double currentMaxVelocity = sessionMaxVelocityState.value();
        if (currentMaxVelocity == null) {
            currentMaxVelocity = Double.NEGATIVE_INFINITY;
        }

        Double currentTotalDistance = sessionTotalDistanceState.value();
        if (currentTotalDistance == null) {
            currentTotalDistance = 0.0;
        }

        // <-- NEW: Store sensor_id on first record for this session
        if (sensorIdState.value() == null && record.getSensor_id() != null) {
            sensorIdState.update(record.getSensor_id());
        }

        // --- Accumulate Data ---
        boolean hasNewData = false;
        if (record.getSamples() != null) {
            for (SensorSample sample : record.getSamples()) {
                Double velocity = sample.getPredicted_velocity();
                if (velocity != null) {
                    hasNewData = true;
                    currentIntervalVelocitiesState.add(velocity); // Add to interval list for per-interval logic
                    if (velocity > currentMaxVelocity) {
                        currentMaxVelocity = velocity; // Update session max
                    }
                    // Update session total distance
                    currentTotalDistance += Math.abs(velocity) * AppConstants.SAMPLING_PERIOD_S;
                }
            }
        }

        // --- Update Session State and Timers ---
        if (hasNewData) {
            sessionMaxVelocityState.update(currentMaxVelocity);
            sessionTotalDistanceState.update(currentTotalDistance);
        }
        
        // Set a timer to fire for periodic aggregation output.
        // This only runs once per key until the timer fires.
        if (nextTimerTimestampState.value() == null) {
            long nextTriggerTime = ctx.timerService().currentProcessingTime() + AGGREGATION_INTERVAL_MS;
            ctx.timerService().registerProcessingTimeTimer(nextTriggerTime);
            nextTimerTimestampState.update(nextTriggerTime);
        }
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<SessionAggregate> out) throws Exception {
        // Check if there was any new data processed in the last interval.
        if (currentIntervalVelocitiesState.get() != null && currentIntervalVelocitiesState.get().iterator().hasNext()) {
            
            // --- Retrieve all necessary info for the output record ---
            String sessionKey = ctx.getCurrentKey(); // This IS the sensor_session_id
            String sensorId = sensorIdState.value(); // Get the stored sensor_id
            Double maxVelSoFar = sessionMaxVelocityState.value();
            Double distSoFar = sessionTotalDistanceState.value();

            // --- Construct the final aggregate object ---
            SessionAggregate aggregate = new SessionAggregate(
                    sessionKey,
                    sensorId,
                    maxVelSoFar != null ? maxVelSoFar : 0.0,
                    distSoFar != null ? distSoFar : 0.0,
                    ctx.timerService().currentProcessingTime() // Use current time for the timestamp
            );

            out.collect(aggregate);
        }

        // --- Cleanup and Schedule Next Timer ---

        // Clear the state for the just-completed interval.
        // DO NOT clear session-wide state (max velocity, distance, sensorId).
        currentIntervalVelocitiesState.clear();

        // Schedule the next timer to continue emitting periodic updates for this session.
        long nextTriggerTime = ctx.timerService().currentProcessingTime() + AGGREGATION_INTERVAL_MS;
        ctx.timerService().registerProcessingTimeTimer(nextTriggerTime);
        nextTimerTimestampState.update(nextTriggerTime);
    }
}