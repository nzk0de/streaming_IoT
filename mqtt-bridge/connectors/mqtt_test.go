package connectors_test

import (
	"context"
	"fmt"
	"sync"
	"testing"
	"time"

	"mqttbridge/config"
	"mqttbridge/connectors"

	mqtt "github.com/eclipse/paho.mqtt.golang"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/testcontainers/testcontainers-go"
	// We need the 'wait' package to specify a readiness strategy
	"github.com/testcontainers/testcontainers-go/wait"
)

// A simple test message handler that signals when a message is received.
type testHandler struct {
	wg      sync.WaitGroup
	message []byte
}

func (h *testHandler) handle(client mqtt.Client, msg mqtt.Message) {
	h.message = msg.Payload()
	h.wg.Done()
}

func TestNewMQTTClient_Integration(t *testing.T) {
	if testing.Short() {
		t.Skip("skipping integration test in short mode")
	}

	// ARRANGE
	ctx := context.Background()

	// --- THIS IS THE CORRECTED SECTION ---
	// We define a request to run a generic container because there is no dedicated Mosquitto module.
	req := testcontainers.ContainerRequest{
		Image:        "eclipse-mosquitto:2.0",
		ExposedPorts: []string{"1883/tcp"},
		// We must tell Testcontainers how to know the broker is ready.
		// We wait for the log message that indicates the server has started.
		WaitingFor: wait.ForLog("mosquitto version 2.0 starting"),
	}
	mosquittoContainer, err := testcontainers.GenericContainer(ctx, testcontainers.GenericContainerRequest{
		ContainerRequest: req,
		Started:          true,
	})
	// --- END OF CORRECTION ---

	require.NoError(t, err, "Failed to start Mosquitto container")
	defer func() {
		if err := mosquittoContainer.Terminate(ctx); err != nil {
			t.Logf("failed to stop mosquitto container: %s", err)
		}
	}()

	host, err := mosquittoContainer.Host(ctx)
	require.NoError(t, err)
	port, err := mosquittoContainer.MappedPort(ctx, "1883")
	require.NoError(t, err)

	testConfig := &config.Config{
		MqttBrokerHost:     host,
		MqttPort:           port.Int(),
		MqttClientIDPrefix: "test-client-",
		MqttTopicFilter:    "test/topic",
		MqttShareGroupName: "test-group",
	}

	handler := &testHandler{}
	handler.wg.Add(1) // Expect one message

	// ACT
	client, err := connectors.NewMQTTClient(testConfig, handler.handle)
	require.NoError(t, err)
	require.NotNil(t, client)
	defer client.Disconnect(250)

	// ASSERT - Wait for the OnConnect handler to fire and subscribe
	// Polling is a robust way to wait for an async connection.
	require.Eventually(t, client.IsConnected, 2*time.Second, 100*time.Millisecond, "Client should connect to the broker")

	// Now, test the full loop: publish a message and see if our handler receives it
	topicToPublish := "test/topic" // We publish to the base topic, not the shared one
	payload := "hello world"
	token := client.Publish(topicToPublish, 1, false, payload)
	token.Wait()
	require.NoError(t, token.Error())

	// Wait for the message to be received by our handler, with a timeout.
	waitCh := make(chan struct{})
	go func() {
		handler.wg.Wait()
		close(waitCh)
	}()

	select {
	case <-waitCh:
		// Success!
		assert.Equal(t, payload, string(handler.message))
	case <-time.After(5 * time.Second):
		t.Fatal("timed out waiting for message to be received")
	}
}