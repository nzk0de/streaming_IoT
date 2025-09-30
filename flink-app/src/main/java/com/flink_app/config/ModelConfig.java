package com.flink_app.config;

import java.io.Serializable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ModelConfig implements Serializable {
    private static final Logger LOG = LoggerFactory.getLogger(ModelConfig.class);

    public final String s3ModelPath;
    public final String s3ScalingInfoPath;
    public final String s3ModelParamsPath;


    public ModelConfig() {
        String s3BucketPath = requireEnv("S3_BUCKET_PATH");
        if (!s3BucketPath.endsWith("/")) {
            s3BucketPath += "/";
        }

        this.s3ModelPath = s3BucketPath + requireEnv("MLFLOW_ONNX_PATH");
        this.s3ScalingInfoPath = s3BucketPath + requireEnv("MLFLOW_SCALING_INFO_PATH");
        this.s3ModelParamsPath = s3BucketPath + requireEnv("MLFLOW_PARAMS_PATH");
        logConfig();
    }

    private String requireEnv(String name) {
        String val = System.getenv(name);
        if (val == null || val.isEmpty()) {
            throw new IllegalArgumentException("Missing required env variable: " + name);
        }
        return val;
    }

    private void logConfig() {
        LOG.info("ModelConfig loaded:");
        LOG.info("  S3 Model Path: {}", s3ModelPath);
        LOG.info("  S3 Scaling Info Path: {}", s3ScalingInfoPath);
        LOG.info("  S3 Model Params Path: {}", s3ModelParamsPath);
    }

}
