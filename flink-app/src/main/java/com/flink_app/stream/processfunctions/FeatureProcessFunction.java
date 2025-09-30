// package com.flink_app.stream.processfunctions;

// import java.util.ArrayList;
// import java.util.LinkedList; // For managing state buffer efficiently
// import java.util.List;

// import com.flink_app.computations.RollingFeatureTransformer;
// import com.flink_app.constants.AppConstants; // For ROLLING_WINDOW_SIZE, SAMPLE_LEN, NUM_PREV_MSGS_TO_BUFFER_FOR_FEATURES
// import com.flink_app.model.SensorSample;
// import com.flink_app.model.TransformedRecord;

// import org.apache.flink.api.common.state.ListState;
// import org.apache.flink.api.common.state.ListStateDescriptor;
// import org.apache.flink.api.common.typeinfo.TypeHint;
// import org.apache.flink.api.common.typeinfo.TypeInformation;
// import org.apache.flink.configuration.Configuration; // For open method
// import org.apache.flink.runtime.state.FunctionInitializationContext;
// import org.apache.flink.runtime.state.FunctionSnapshotContext;
// import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
// import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
// import org.apache.flink.util.Collector;

// public class FeatureProcessFunction
//         extends KeyedProcessFunction<String, TransformedRecord, TransformedRecord>
//         implements CheckpointedFunction { // Implement CheckpointedFunction for ListState

//     private transient ListState<List<SensorSample>> bufferedSamplesState;
//     private transient RollingFeatureTransformer featureExtractor;

//     // Corresponds to self.WIN in Python, determines how many "messages" (List<SensorSample>) to keep in state
//     private final int maxBufferedMessages;


//     public FeatureProcessFunction() {
//         // Use constants for configuration
//         this.maxBufferedMessages = AppConstants.NUM_PREV_MSGS_TO_BUFFER_FOR_FEATURES;
//     }

//     @Override
//     public void open(Configuration parameters) throws Exception {
//         super.open(parameters);
//         // Initialize non-stateful resources here
//         this.featureExtractor = new RollingFeatureTransformer(
//                 AppConstants.FEATURE_ROLLING_WINDOW_SIZE,
//                 AppConstants.SAMPLE_LEN
//         );
//     }

//     @Override
//     public void processElement(TransformedRecord Pvalue, // Renamed to Pvalue to avoid conflict
//                                Context ctx,
//                                Collector<TransformedRecord> out) throws Exception {

//         List<List<SensorSample>> previousMessageSamples = new LinkedList<>(); // Use LinkedList for efficient add/removeFirst
//         if (bufferedSamplesState.get() != null) {
//             for (List<SensorSample> msgSamples : bufferedSamplesState.get()) {
//                 previousMessageSamples.add(msgSamples);
//             }
//         }

//         // Combine previous samples with current message's samples
//         List<SensorSample> allSamplesForWindow = new ArrayList<>();
//         for (List<SensorSample> msgList : previousMessageSamples) {
//             allSamplesForWindow.addAll(msgList);
//         }
//         if (Pvalue.getSamples() != null) {
//             allSamplesForWindow.addAll(Pvalue.getSamples());
//         }

//         // Apply feature extraction
//         // The RollingFeatureTransformer expects a flat list of all samples needed for its window calculations,
//         // and it will handle the rolling window logic internally and tailing.
//         List<SensorSample> featuredSamplesOutput = featureExtractor.transform(allSamplesForWindow);

//         // Update state: add current message's samples to the buffer
//         if (Pvalue.getSamples() != null && !Pvalue.getSamples().isEmpty()) {
//             previousMessageSamples.add(new ArrayList<>(Pvalue.getSamples())); // Add a copy
//         }

//         // Maintain the size of the buffer (number of messages)
//         while (previousMessageSamples.size() > maxBufferedMessages) {
//             previousMessageSamples.remove(0); // Remove the oldest message
//         }
//         bufferedSamplesState.update(previousMessageSamples);

//         // Yield the new TransformedRecord with featured samples
//         // The 'featuredSamplesOutput' from RollingFeatureTransformer is already tailed to SAMPLE_LEN
//         if (!featuredSamplesOutput.isEmpty()) {
//             out.collect(new TransformedRecord(
//                     Pvalue.getSensor_id(),
//                     Pvalue.getHeader_timestamp_us(),
//                     featuredSamplesOutput,
//                     Pvalue.getSensor_session_id()
//             ));
//         }
//     }

//     // --- CheckpointedFunction methods ---
//     @Override
//     public void snapshotState(FunctionSnapshotContext context) throws Exception {
//         // Nothing to do here for ListState if updates are done directly via .update() or .add()
//         // Flink handles the snapshotting of managed state.
//     }

//     @Override
//     public void initializeState(FunctionInitializationContext context) throws Exception {
//         ListStateDescriptor<List<SensorSample>> descriptor =
//                 new ListStateDescriptor<>(
//                         "bufferedPastSamples", // state name
//                         TypeInformation.of(new TypeHint<List<SensorSample>>() {}) // type information
//                 );
//         bufferedSamplesState = context.getKeyedStateStore().getListState(descriptor);
//         // If using operator state instead of keyed state, use:
//         // bufferedSamplesState = context.getOperatorStateStore().getListState(descriptor);

//         // Initialize non-stateful resources in open() instead if they don't depend on restored state
//         // this.featureExtractor = new RollingFeatureTransformer(
//         //         AppConstants.FEATURE_ROLLING_WINDOW_SIZE,
//         //         AppConstants.SAMPLE_LEN
//         // );
//     }
// }