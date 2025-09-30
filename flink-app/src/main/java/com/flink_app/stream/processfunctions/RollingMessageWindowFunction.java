// package com.flink_app.stream.processfunctions;

// import java.util.ArrayList;
// import java.util.List;

// import com.flink_app.computations.RollingFeatureTransformer;
// import com.flink_app.constants.AppConstants;
// import com.flink_app.model.SensorSample;
// import com.flink_app.model.TransformedRecord;

// import org.apache.flink.configuration.Configuration;
// import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
// import org.apache.flink.streaming.api.windowing.windows.GlobalWindow;
// import org.apache.flink.util.Collector;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// public class RollingMessageWindowFunction
//         extends ProcessWindowFunction<TransformedRecord, TransformedRecord, String, GlobalWindow>
//         {

//     private static final Logger LOG = LoggerFactory.getLogger(RollingMessageWindowFunction.class);

//     private transient RollingFeatureTransformer featureExtractor;

//     @Override
//     public void open(Configuration parameters) throws Exception {
//         this.featureExtractor = new RollingFeatureTransformer(
//                 AppConstants.FEATURE_ROLLING_WINDOW_SIZE,
//                 AppConstants.SAMPLE_LEN
//         );
//         LOG.info("RollingMessageWindowFunction initialized.");
//     }

//     @Override
//     public void process(String key,
//                         Context context,
//                         Iterable<TransformedRecord> records,
//                         Collector<TransformedRecord> out) throws Exception {

//         List<SensorSample> allSamples = new ArrayList<>();
//         TransformedRecord lastMessage = null;

//         for (TransformedRecord record : records) {
//             if (record != null && record.getSamples() != null) {
//                 allSamples.addAll(record.getSamples());
//                 lastMessage = record; // Assume iteration order: last one is newest
//             }
//         }

//         if (lastMessage == null || allSamples.isEmpty()) {
//             LOG.warn("No valid records found for sensor_id {}", key);
//             return;
//         }

//         List<SensorSample> featuredSamples = featureExtractor.transform(allSamples);

//         out.collect(new TransformedRecord(
//                 lastMessage.getSensor_id(),
//                 lastMessage.getHeader_timestamp_us(),
//                 featuredSamples,
//                 lastMessage.getSensor_session_id()
//         ));

//         LOG.debug("Processed 3-message window for sensor {}, output size: {}", key, featuredSamples.size());
//     }

//     @Override
//     public void close() throws Exception {
//         LOG.info("RollingMessageWindowFunction closed.");
//     }
// }
