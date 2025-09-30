package com.flink_app.stream.serialization;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import com.flink_app.model.KafkaMessage;

import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.connector.kafka.source.reader.deserializer.KafkaRecordDeserializationSchema;
import org.apache.flink.util.Collector;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A "bulletproof" Kafka Deserialization Schema with extensive logging to debug silent failures.
 * It handles nulls and exceptions gracefully.
 */
public class KafkaMessageDeserializationSchema implements KafkaRecordDeserializationSchema<KafkaMessage> {

    private static final Logger LOG = LoggerFactory.getLogger(KafkaMessageDeserializationSchema.class);

    @Override
    public void deserialize(ConsumerRecord<byte[], byte[]> record, Collector<KafkaMessage> out) throws IOException {
        try {
            if (record == null) {
                LOG.warn("Received null ConsumerRecord from Kafka.");
                return;
            }

            // Check for a valid timestamp. Kafka uses -1 for an invalid/missing timestamp.
            long kafkaTimestamp = record.timestamp();
            if (kafkaTimestamp <= 0) {
                LOG.warn("Received record with invalid timestamp: {}. Discarding record from partition {} at offset {}.",
                         kafkaTimestamp, record.partition(), record.offset());
                return;
            }

            // Check for a valid message value (the JSON payload)
            if (record.value() == null || record.value().length == 0) {
                LOG.warn("Received record with null or empty value. Discarding record from partition {} at offset {}.",
                         record.partition(), record.offset());
                return;
            }
            
            String jsonValue = new String(record.value(), StandardCharsets.UTF_8);
            
            // If we've made it this far, everything seems valid. Create and collect the object.
            KafkaMessage message = new KafkaMessage(jsonValue, kafkaTimestamp);
            
            LOG.info("Successfully created KafkaMessage object: {}", message.toString());
            
            out.collect(message);

        } catch (Exception e) {
            // This is the most important log. If any unexpected error happens, it will be caught here.
            String keyString = record.key() != null ? new String(record.key()) : "null";
            LOG.error("CRITICAL: Deserialization failed! Dropping record. Key: '{}', Partition: {}, Offset: {}. Error: {}",
                      keyString, record.partition(), record.offset(), e.getMessage(), e);
        }
    }

    @Override
    public TypeInformation<KafkaMessage> getProducedType() {
        return TypeInformation.of(KafkaMessage.class);
    }
}