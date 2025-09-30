package com.flink_app.onnx;

import java.io.Closeable;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;

public class OnnxSessionManager implements Closeable {

    private static final Logger LOG = LoggerFactory.getLogger(OnnxSessionManager.class);

    private final OrtEnvironment environment;
    private final OrtSession session;

    // --- START: CORRECTED NAMES BASED ON YOUR NEW EXPORT COMMAND ---
    private static final String INPUT_NAME = "sequence_features";
    private static final String H_INPUT_NAME = "h_0";
    private static final String C_INPUT_NAME = "c_0";

    // These are now separate outputs, matching the Python 'return'
    private static final String VELOCITY_OUTPUT_NAME = "velocity_pred";
    private static final String MOVEMENT_PROB_OUTPUT_NAME = "movement_prob";

    private static final String H_OUTPUT_NAME = "h_n";
    private static final String C_OUTPUT_NAME = "c_n";
    // --- END: CORRECTED NAMES ---

    private final String inputName;
    private final String velocityOutputName;
    private final String movementProbOutputName;
    private final String hInputName;
    private final String cInputName;
    private final String hOutputName;
    private final String cOutputName;
    private final boolean isStateful;

    public OnnxSessionManager(byte[] modelBytes) throws OrtException {
        this.environment = OrtEnvironment.getEnvironment();
        this.session = environment.createSession(modelBytes, new OrtSession.SessionOptions());

        LOG.info("Model loaded. Available inputs: {}", session.getInputInfo().keySet());
        LOG.info("Model loaded. Available outputs: {}", session.getOutputInfo().keySet());

        this.inputName = INPUT_NAME;
        this.velocityOutputName = VELOCITY_OUTPUT_NAME;
        this.movementProbOutputName = MOVEMENT_PROB_OUTPUT_NAME;
        this.hInputName = H_INPUT_NAME;
        this.cInputName = C_INPUT_NAME;
        this.hOutputName = H_OUTPUT_NAME;
        this.cOutputName = C_OUTPUT_NAME;

        this.isStateful = session.getInputInfo().containsKey(this.hInputName);
        
        LOG.info("Manager configured with inputs: '{}', '{}', '{}'", this.inputName, this.hInputName, this.cInputName);
        LOG.info("Manager configured with outputs: '{}', '{}', '{}', '{}'", this.velocityOutputName, this.movementProbOutputName, this.hOutputName, this.cOutputName);
    }

    public synchronized OrtSession.Result run(Map<String, OnnxTensor> inputs) throws OrtException {
        return session.run(inputs);
    }

    // --- Getter methods for names ---
    public OrtEnvironment getEnvironment() { return environment; }
    public boolean isStatefulInference() { return isStateful; }
    public String getInputName() { return inputName; }
    public String getVelocityOutputName() { return velocityOutputName; }
    public String getMovementProbOutputName() { return movementProbOutputName; }
    public String getHInputName() { return hInputName; }
    public String getCInputName() { return cInputName; }
    public String getHOutputName() { return hOutputName; }
    public String getCOutputName() { return cOutputName; }

    @Override
    public void close() { /* ... */ }
}