// go-mqtt-bridge/connectors/kafka.go
package connectors

import (
	"fmt"
	"log"
	"os"
	"mqttbridge/config"
	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
)

// NewKafkaProducer creates a new Kafka producer based on the provided configuration.
func NewKafkaProducer(cfg *config.Config) (*kafka.Producer, error) {
	hostname, _ := os.Hostname()
	producerClientID := fmt.Sprintf("go-mqtt-bridge-producer-%s", hostname)

	kafkaConfig := &kafka.ConfigMap{
		"bootstrap.servers": cfg.KafkaBrokersStr,
		"client.id":         producerClientID,
		"acks":              "all",
		"linger.ms":         10,
	}

	p, err := kafka.NewProducer(kafkaConfig)
	if err != nil {
		return nil, fmt.Errorf("failed to create Kafka producer: %w", err)
	}

	// Start a goroutine to handle delivery reports and errors
	go func() {
		for e := range p.Events() {
			switch ev := e.(type) {
			case *kafka.Message:
				if ev.TopicPartition.Error != nil {
					log.Printf("ERROR: Kafka delivery failed: %v", ev.TopicPartition.Error)
				}
			case kafka.Error:
				log.Printf("ERROR: Kafka producer error: %v", ev)
				if ev.IsFatal() {
					log.Fatal("FATAL Kafka producer error, exiting.")
				}
			}
		}
	}()

	return p, nil
}