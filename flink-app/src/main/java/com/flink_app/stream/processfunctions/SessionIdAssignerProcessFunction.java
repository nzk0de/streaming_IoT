package com.flink_app.stream.processfunctions;

import com.flink_app.model.NewSessionEvent;
import com.flink_app.model.TransformedRecord;

import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;

public class SessionIdAssignerProcessFunction extends KeyedProcessFunction<String, TransformedRecord, TransformedRecord> {

    private static final long SESSION_TIMEOUT_US = 10 * 60 * 1000 * 1000L; // 10 minutes in microseconds

    // State to store the last seen kafka_timestamp for the current key (sensor_id)
    private transient ValueState<Long> lastTimestampState;
    // State to store the current session ID part for the current key
    private transient ValueState<Long> currentSessionIdPartState;

    // OutputTag for side outputting new session events
    private final OutputTag<NewSessionEvent> newSessionOutputTag;

    public SessionIdAssignerProcessFunction(OutputTag<NewSessionEvent> newSessionOutputTag) {
        this.newSessionOutputTag = newSessionOutputTag;
    }

    @Override
    public void open(Configuration parameters) throws Exception {
        ValueStateDescriptor<Long> lastTimestampDescriptor =
                new ValueStateDescriptor<>("lastKafkaTimestamp", Types.LONG); // Renamed for clarity
        lastTimestampState = getRuntimeContext().getState(lastTimestampDescriptor);

        ValueStateDescriptor<Long> currentSessionIdDescriptor =
                new ValueStateDescriptor<>("currentSessionIdPart", Types.LONG);
        currentSessionIdPartState = getRuntimeContext().getState(currentSessionIdDescriptor);
    }

    @Override
    public void processElement(TransformedRecord record, Context ctx, Collector<TransformedRecord> out) throws Exception {
        Long lastTs = lastTimestampState.value();
        Long currentSidPart = currentSessionIdPartState.value();
        Long newSessionIdPart;

        // MODIFIED: Check kafka_timestamp instead of header_timestamp_us
        if (record.getKafka_timestamp() == null) {
            // This should ideally not happen if the upstream parser function always sets it.
            // Forwarding without a session_id or filtering are options. We'll forward with an empty one.
             record.setSensor_session_id("");
             out.collect(record);
            return;
        }

        boolean newSession = false;
        // MODIFIED: Use kafka_timestamp and SESSION_TIMEOUT_MS for session gap detection
        if (lastTs == null || (record.getKafka_timestamp() - lastTs > SESSION_TIMEOUT_US)) {
            // This is a new session
            newSession = true;
            
            
            // MODIFIED: Generate the new session ID part using the kafka_timestamp
            // Using kafka_timestamp of the first record of the session makes it deterministic.
            newSessionIdPart = record.getKafka_timestamp();
            
            currentSessionIdPartState.update(newSessionIdPart);
        } else {
            // Existing session
            newSessionIdPart = currentSidPart;
        }
        
        // The logic to construct the final ID remains the same
        String sensorSessionId = record.getSensor_id() + "_" + newSessionIdPart;
        record.setSensor_session_id(sensorSessionId);

        // If it's a new session, emit an event to the side output
        if (newSession) {
            // MODIFIED: Use kafka_timestamp for the session's start time
            NewSessionEvent event = new NewSessionEvent(
                sensorSessionId,
                record.getSensor_id(),
                newSessionIdPart, // This is the kafka_timestamp of the first record
                record.getKafka_timestamp() // Redundant, but explicit: this is the start_timestamp of the session
            );
            ctx.output(newSessionOutputTag, event);
        }

        // MODIFIED: Update the last seen timestamp with the current record's kafka_timestamp
        lastTimestampState.update(record.getKafka_timestamp());

        // Forward the record with the assigned session_id
        out.collect(record);
    }
}