package com.flink_app.model;

import java.io.Serializable;

/**
 * A simple POJO to hold the raw JSON string from Kafka
 * and the message's metadata timestamp.
 */
public class KafkaMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    public String jsonValue;
    public long kafkaTimestamp; // This is in milliseconds

    public KafkaMessage() {}

    public KafkaMessage(String jsonValue, long kafkaTimestamp) {
        this.jsonValue = jsonValue;
        this.kafkaTimestamp = kafkaTimestamp;
    }

    public String getJsonValue() {
        return jsonValue;
    }

    public void setJsonValue(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public long getKafkaTimestamp() {
        return kafkaTimestamp;
    }

    public void setKafkaTimestamp(long kafkaTimestamp) {
        this.kafkaTimestamp = kafkaTimestamp;
    }
}