package com.flink_app.stream.processfunctions;

import java.io.File;
import java.nio.FloatBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.flink_app.constants.AppConstants;
import com.flink_app.model.ModelParams;
import com.flink_app.model.SensorSample;
import com.flink_app.model.TransformedRecord;
import com.flink_app.onnx.OnnxSessionManager;

// ... all previous imports are needed ...
import org.apache.flink.api.common.state.ListState;
import org.apache.flink.api.common.state.ListStateDescriptor;
import org.apache.flink.api.common.state.ValueState;
import org.apache.flink.api.common.state.ValueStateDescriptor;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.common.typeutils.base.array.FloatPrimitiveArraySerializer;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.runtime.state.FunctionInitializationContext;
import org.apache.flink.runtime.state.FunctionSnapshotContext;
import org.apache.flink.streaming.api.checkpoint.CheckpointedFunction;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtSession;

public class InferenceProcessFunction
        extends KeyedProcessFunction<String, TransformedRecord, TransformedRecord>
        implements CheckpointedFunction {

    private static final Logger LOG = LoggerFactory.getLogger(InferenceProcessFunction.class);

    // --- NEW: Threshold for masking velocity ---
    private static final double MOVEMENT_PROB_THRESHOLD = 0.5;

    // --- Batching Configuration ---
    private final long batchTimeoutMs;

    // --- Shared Resources ---
    private static volatile OnnxSessionManager onnxSessionManager;
    private static volatile ModelParams modelParams;
    private static final Object lock = new Object();

    // --- Flink State ---
    private transient ListState<TransformedRecord> recordBufferState;
    private transient ValueState<float[]> hStateValueState;
    private transient ValueState<float[]> cStateValueState;

    // --- Instance-specific members ---
    private transient List<String> featureCols;
    private transient int lstmHiddenDim;
    private transient int lstmNumLayers;
    private transient boolean lstmBidirectional;
    private transient int lstmNumDirections;

    public InferenceProcessFunction( long batchTimeoutMs) {
        this.batchTimeoutMs = batchTimeoutMs;
    }

    // open() method is IDENTICAL to your previous version. No changes needed.
    @Override
    public void open(Configuration parameters) throws Exception {
        if (onnxSessionManager == null) {
            synchronized (lock) {
                if (onnxSessionManager == null) {
                    LOG.info("Initializing shared ONNX session and model parameters for the first time on this TaskManager...");
                    
                    File modelFile = getRuntimeContext().getDistributedCache().getFile(AppConstants.DIST_CACHE_ONNX_MODEL_KEY);
                    File modelParamsFile = getRuntimeContext().getDistributedCache().getFile(AppConstants.DIST_CACHE_MODEL_PARAMS_KEY);

                    byte[] modelBytes = Files.readAllBytes(modelFile.toPath());
                    modelParams = ModelParams.fromJsonFile(modelParamsFile);
                    onnxSessionManager = new OnnxSessionManager(modelBytes);
                    
                    LOG.info("Shared resources initialized successfully.");
                }
            }
        }
        this.lstmHiddenDim = modelParams.hiddenDim;
        this.lstmNumLayers = modelParams.numLayers;
        this.lstmBidirectional = modelParams.bidirectional;
        this.lstmNumDirections = this.lstmBidirectional ? 2 : 1;
        this.featureCols = modelParams.featureCols;
        LOG.info("Opening InferenceProcessFunction instance. Using shared ONNX session.");
    }


    @Override
    public void processElement(TransformedRecord value, Context ctx, Collector<TransformedRecord> out) throws Exception {
        recordBufferState.add(value);
        ctx.timerService().registerProcessingTimeTimer(ctx.timerService().currentProcessingTime() + batchTimeoutMs);
    }

    // In InferenceProcessFunction.java

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<TransformedRecord> out) throws Exception {
        Iterable<TransformedRecord> bufferedRecordsIterable = recordBufferState.get();
        if (bufferedRecordsIterable == null || !bufferedRecordsIterable.iterator().hasNext()) {
            return;
        }

        // Batch preparation and input tensor creation (no changes here)
        List<TransformedRecord> batchToProcess = new ArrayList<>();
        int totalSamplesInBatch = 0;
        for (TransformedRecord record : bufferedRecordsIterable) {
            batchToProcess.add(record);
            totalSamplesInBatch += record.getSamples().size();
        }
        recordBufferState.clear();

        List<SensorSample> allSamplesInBatch = new ArrayList<>(totalSamplesInBatch);
        for (TransformedRecord record : batchToProcess) {
            allSamplesInBatch.addAll(record.getSamples());
        }

        int numFeatures = this.featureCols.size();
        float[][][] inputArray = new float[1][totalSamplesInBatch][numFeatures];
        for (int i = 0; i < totalSamplesInBatch; i++) {
            SensorSample sample = allSamplesInBatch.get(i);
            for (int j = 0; j < numFeatures; j++) {
                inputArray[0][i][j] = sample.getFeature(this.featureCols.get(j), 0.0D).floatValue();
            }
        }
        
        OnnxTensor inputTensor = OnnxTensor.createTensor(onnxSessionManager.getEnvironment(), inputArray);
        Map<String, OnnxTensor> inputs = new HashMap<>();
        inputs.put(onnxSessionManager.getInputName(), inputTensor);

        if (onnxSessionManager.isStatefulInference()) {
            // State preparation logic is unchanged
            float[] hStateArray = hStateValueState.value();
            float[] cStateArray = cStateValueState.value();
            int stateShapeElements = lstmNumLayers * lstmNumDirections * 1 * lstmHiddenDim;
            if (hStateArray == null) hStateArray = new float[stateShapeElements];
            if (cStateArray == null) cStateArray = new float[stateShapeElements];
            long[] stateShape = { (long)lstmNumLayers * lstmNumDirections, 1L, lstmHiddenDim };
            inputs.put(onnxSessionManager.getHInputName(), OnnxTensor.createTensor(onnxSessionManager.getEnvironment(), FloatBuffer.wrap(hStateArray), stateShape));
            inputs.put(onnxSessionManager.getCInputName(), OnnxTensor.createTensor(onnxSessionManager.getEnvironment(), FloatBuffer.wrap(cStateArray), stateShape));
        }

        OrtSession.Result results = null;
        try {
            results = onnxSessionManager.run(inputs);
            
            // --- START: CORRECTED OUTPUT HANDLING FOR TWO SEPARATE TENSORS ---

            // 1. Get both primary outputs by their correct names.
            OnnxValue velocityOutputValue = results.get(onnxSessionManager.getVelocityOutputName())
                .orElseThrow(() -> new RuntimeException("Missing 'velocity_pred' output from model"));
            OnnxValue movementProbValue = results.get(onnxSessionManager.getMovementProbOutputName())
                .orElseThrow(() -> new RuntimeException("Missing 'movement_prob' output from model"));

            // 2. Cast results to 2D float arrays. Shape is [batch_size][sequence_length], so [1][totalSamplesInBatch].
            float[][] velocityOutputArray = (float[][]) velocityOutputValue.getValue();
            float[][] movementProbArray = (float[][]) movementProbValue.getValue();

            // 3. Assign predictions back to samples, applying the masking logic.
            if (velocityOutputArray.length > 0 && velocityOutputArray[0].length == totalSamplesInBatch &&
                movementProbArray.length > 0 && movementProbArray[0].length == totalSamplesInBatch) {
                
                for (int i = 0; i < totalSamplesInBatch; i++) {
                    double velocityPred = (double) velocityOutputArray[0][i];
                    double movementProb = (double) movementProbArray[0][i];

                    if (movementProb < MOVEMENT_PROB_THRESHOLD) {
                        velocityPred = 0.0;
                    }
                    
                    SensorSample currentSample = allSamplesInBatch.get(i);
                    currentSample.predicted_velocity = velocityPred;
                }
            } else {
                LOG.warn("Output array dimensions do not match the number of samples in the batch. Skipping prediction assignment.");
            }
            // --- END: CORRECTED OUTPUT HANDLING ---

            // Update state logic is unchanged
            if (onnxSessionManager.isStatefulInference()) {
                FloatBuffer hNextBuffer = ((OnnxTensor) results.get(onnxSessionManager.getHOutputName()).orElseThrow()).getFloatBuffer();
                FloatBuffer cNextBuffer = ((OnnxTensor) results.get(onnxSessionManager.getCOutputName()).orElseThrow()).getFloatBuffer();
                float[] hNextArray = new float[hNextBuffer.remaining()]; hNextBuffer.get(hNextArray); hStateValueState.update(hNextArray);
                float[] cNextArray = new float[cNextBuffer.remaining()]; cNextBuffer.get(cNextArray); cStateValueState.update(cNextArray);
            }

        } finally {
            inputTensor.close();
            inputs.values().forEach(OnnxTensor::close);
            if (results != null) results.close();
        }

        // Emit results logic is unchanged
        int sampleCursor = 0;
        for (TransformedRecord originalRecord : batchToProcess) {
            int numSamplesInRecord = originalRecord.getSamples().size();
            List<SensorSample> predictedSamples = new ArrayList<>(allSamplesInBatch.subList(sampleCursor, sampleCursor + numSamplesInRecord));
            out.collect(new TransformedRecord(
                originalRecord.getSensor_id(),
                originalRecord.getHeader_timestamp_us(),
                originalRecord.getKafka_timestamp(),
                predictedSamples,
                originalRecord.getSensor_session_id()
            ));
            sampleCursor += numSamplesInRecord;
        }
    }
    // initializeState, snapshotState, and close methods are unchanged.
    @Override
    public void initializeState(FunctionInitializationContext context) throws Exception {
        ListStateDescriptor<TransformedRecord> bufferDescriptor = new ListStateDescriptor<>(
                "recordBuffer",
                TypeInformation.of(TransformedRecord.class)
        );
        recordBufferState = context.getKeyedStateStore().getListState(bufferDescriptor);
        
        hStateValueState = context.getKeyedStateStore().getState(new ValueStateDescriptor<>("lstmHState", FloatPrimitiveArraySerializer.INSTANCE));
        cStateValueState = context.getKeyedStateStore().getState(new ValueStateDescriptor<>("lstmCState", FloatPrimitiveArraySerializer.INSTANCE));

        LOG.info("BatchingInferenceProcessFunction state initialized.");
    }

    @Override
    public void snapshotState(FunctionSnapshotContext context) {}

    @Override
    public void close() throws Exception {
        super.close();
    }
}