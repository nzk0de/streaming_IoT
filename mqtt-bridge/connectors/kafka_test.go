// mqtt-bridge/connectors/kafka_test.go
package connectors

import (
	"context"
	"testing"
	"mqtt-bridge/config" // Import your own config package

	"github.com/stretchr/testify/assert" // For clean test assertions
	"github.com/testcontainers/testcontainers-go"
	"github.com/testcontainers/testcontainers-go/modules/kafka"
)

func TestNewKafkaProducer_Integration(t *testing.T) {
	// -------------------- ARRANGE --------------------
	// 1. Set up the test environment by starting a real Kafka broker in Docker.
	//    We use the Confluent Kafka image, which is a standard choice.
	ctx := context.Background()
	kafkaContainer, err := kafka.RunContainer(ctx, testcontainers.WithImage("confluentinc/cp-kafka:7.5.0"))
	if err != nil {nikosevo4hp6zones
		t.Fatalf("failed to start kafka container: %s", err)
	}

	// 2. Schedule the container to be terminated after the test is complete.
	defer func() {
		if err := kafkaContainer.Terminate(ctx); err != nil {
			t.Fatalf("failed to stop kafka container: %s", err)
		}
	}()

	// 3. Get the dynamic bootstrap server address from the container.
	//    The Brokers() function provides the address in the format the client needs.
	brokers, err := kafkaContainer.Brokers(ctx)
	assert.NoError(t, err)

	// 4. Create a test-specific configuration object.
	testConfig := &config.Config{
		KafkaBrokersStr: brokers[0], // Use the first (and only) broker from the list
	}

	// -------------------- ACT --------------------
	// 5. Call the function we want to test with our test configuration.
	producer, err := NewKafkaProducer(testConfig)
	assert.NoError(t, err, "NewKafkaProducer setup function should not return an error")
	defer producer.Close() // Ensure the producer is closed cleanly.

	// -------------------- ASSERT --------------------
	// 6. Assert that the producer can communicate with the broker.
	//    The confluent-kafka-go producer connects lazily. The best way to
	//    verify a live connection is to perform a lightweight metadata request.
	//    If this call succeeds, it proves the client can reach and talk to the broker.
	//    We ask for metadata about all topics (nil) with a 5-second timeout.
	_, err = producer.GetMetadata(nil, true, 5000)
	assert.NoError(t, err, "Should be able to get metadata from the Kafka broker, which confirms a successful connection")
}