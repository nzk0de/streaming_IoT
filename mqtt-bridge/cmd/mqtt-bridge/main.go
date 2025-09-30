// go-mqtt-bridge/main.go
package main

import (
	"bytes"
	"context"
	"encoding/json"
	"log"
	"os"
	"os/signal"
	"regexp"
	"strings"
	"sync"
	"syscall"
	"time"
	
	// Import your new local packages
	"mqttbridge/config"
	"mqttbridge/connectors"
	"mqttbridge/parser"

	"github.com/confluentinc/confluent-kafka-go/v2/kafka"
	mqtt "github.com/eclipse/paho.mqtt.golang"
)

// --- Global State (kept to a minimum) ---
var (
	sensorBuffers = make(map[string]*bytes.Buffer)
	bufferMutex   = &sync.Mutex{}
	topicRegex    *regexp.Regexp
)

func main() {
	log.SetFlags(log.LstdFlags | log.Lmicroseconds | log.Lshortfile)
	log.Println("INFO: Starting MQTT to Kafka Bridge...")

	// 1. Load Configuration
	cfg, err := config.New()
	if err != nil {
		log.Fatalf("Configuration error: %v", err)
	}

	// 2. Compile Topic Regex from Config
	topicRegex, err = regexp.Compile(strings.ReplaceAll(cfg.MqttTopicFilter, "+", "([^/]+)"))
	if err != nil {
		log.Fatalf("Invalid MQTT topic pattern for regex: %v", err)
	}

	// 3. Initialize Kafka and MQTT Clients
	messageQueue := make(chan mqtt.Message, 10000)

	kafkaProducer, err := connectors.NewKafkaProducer(cfg)
	if err != nil {
		log.Fatalf("Could not start Kafka producer: %v", err)
	}
	defer func() {
		log.Println("INFO: Flushing and closing Kafka producer...")
		kafkaProducer.Flush(15000)
		kafkaProducer.Close()
	}()

	messagePubHandler := func(client mqtt.Client, msg mqtt.Message) {
		select {
		case messageQueue <- msg:
		default:
			log.Printf("WARNING: Processing queue full. Discarding message from topic %s", msg.Topic())
		}
	}

	mqttClient, err := connectors.NewMQTTClient(cfg, messagePubHandler)
	if err != nil {
		// Log non-fatal error as the client will try to reconnect
		log.Printf("WARNING: Initial MQTT connection failed: %v", err)
	}
	defer func() {
		if mqttClient.IsConnected() {
			log.Println("INFO: Disconnecting MQTT client...")
			mqttClient.Disconnect(250)
		}
	}()

	// 4. Set up Graceful Shutdown
	ctx, cancel := context.WithCancel(context.Background())
	sigs := make(chan os.Signal, 1)
	signal.Notify(sigs, syscall.SIGINT, syscall.SIGTERM)

	// 5. Start Worker Goroutines
	var wg sync.WaitGroup
	log.Printf("INFO: Starting %d message processing workers...", cfg.ProcessingWorkers)
	for i := 0; i < cfg.ProcessingWorkers; i++ {
		wg.Add(1)
		go messageProcessor(ctx, &wg, messageQueue, kafkaProducer, cfg)
	}

	log.Println("INFO: MQTT Bridge running. Press Ctrl+C to exit.")

	// 6. Wait for Shutdown Signal
	<-sigs
	log.Println("INFO: Shutdown signal received.")
	cancel()

	log.Println("INFO: Waiting for workers to complete...")
	wg.Wait()
	log.Println("INFO: All workers finished. Shutdown complete.")
}

// messageProcessor is the worker that pulls from the queue.
func messageProcessor(ctx context.Context, wg *sync.WaitGroup, mq <-chan mqtt.Message, p *kafka.Producer, cfg *config.Config) {
	defer wg.Done()
	for {
		select {
		case <-ctx.Done():
			return
		case msg := <-mq:
			processAndParse(p, msg, cfg)
		}
	}
}

// processAndParse contains the core logic for a single message.
func processAndParse(producer *kafka.Producer, msg mqtt.Message, cfg *config.Config) {
	match := topicRegex.FindStringSubmatch(msg.Topic())
	if len(match) < 2 {
		log.Printf("ERROR: Could not extract sensor ID from topic: %s", msg.Topic())
		return
	}
	sensorID := match[1]

	// Buffer management logic...
	bufferMutex.Lock()
	buffer, ok := sensorBuffers[sensorID]
	if !ok {
		buffer = bytes.NewBuffer(make([]byte, 0, 1024))
		sensorBuffers[sensorID] = buffer
	}
	if buffer.Len()+len(msg.Payload()) > cfg.MaxBufferSize {
		log.Printf("ERROR: [%s] Buffer overflow!", sensorID)
		buffer.Reset()
		bufferMutex.Unlock()
		return
	}
	buffer.Write(msg.Payload())
	currentData := make([]byte, buffer.Len())
	copy(currentData, buffer.Bytes())
	bufferMutex.Unlock()

	// Call the outsourced parsing logic
	parsedPackets, consumedBytes := parser.ParseFromBuffer(currentData, sensorID)

	if consumedBytes > 0 {
		bufferMutex.Lock()
		if currentBuffer, exists := sensorBuffers[sensorID]; exists {
			currentBuffer.Next(consumedBytes)
		}
		bufferMutex.Unlock()
	}

	// Produce to Kafka
	for _, packet := range parsedPackets {
		jsonData, err := json.Marshal(packet)
		if err != nil {
			log.Printf("ERROR: [%s] Failed to marshal JSON: %v", sensorID, err)
			continue
		}

		kafkaMsg := &kafka.Message{
			TopicPartition: kafka.TopicPartition{Topic: &cfg.KafkaIngestTopic, Partition: kafka.PartitionAny},
			Key:            []byte(sensorID),
			Value:          jsonData,
			Timestamp:      time.Now(),
		}
		
		err = producer.Produce(kafkaMsg, nil)
		if err != nil {
			log.Printf("ERROR: [%s] Failed to produce message to Kafka: %v", sensorID, err)
		}
	}
} 