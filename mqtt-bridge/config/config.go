// go-mqtt-bridge/config/config.go
package config

import (
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"
)

// Config holds all configuration for the application.
type Config struct {
	// MQTT Settings
	MqttBrokerHost     string
	MqttPort           int
	MqttUseTLS         bool
	MqttShareGroupName string
	MqttTopicFilter    string
	MqttClientIDPrefix string

	// Kafka Settings
	KafkaBrokersStr  string
	KafkaIngestTopic string

	// Application Settings
	MaxBufferSize     int
	ProcessingWorkers int
	NumPartitions     int
}

// New creates a configuration object from environment variables.
func New() (*Config, error) {
	cfg := &Config{
		MqttBrokerHost:     getEnv("MQTT_BROKER_HOST", ""),
		MqttPort:           getEnvAsInt("MQTT_BROKER_PORT", 1883),
		MqttUseTLS:         getEnvAsBool("MQTT_USE_TLS", false),
		MqttShareGroupName: getEnv("MQTT_SHARE_GROUP_NAME", "go-bridge-group"),
		MqttTopicFilter:    getEnv("MQTT_TOPIC_FILTER", ""),
		MqttClientIDPrefix: getEnv("MQTT_CLIENT_ID_PREFIX", "go-mqtt-bridge-"),

		KafkaBrokersStr:  getEnv("KAFKA_BOOTSTRAP_SERVERS", ""),
		KafkaIngestTopic: getEnv("KAFKA_INPUT_TOPIC", "imu-data-all"),

		MaxBufferSize:     1024 * 1024,
		ProcessingWorkers: 200,
		NumPartitions:     20,
	}

	// Validate required fields
	if cfg.MqttBrokerHost == "" {
		return nil, fmt.Errorf("MQTT_BROKER_HOST must be set")
	}
	if cfg.MqttTopicFilter == "" {
		return nil, fmt.Errorf("MQTT_TOPIC_FILTER must be set")
	}
	if cfg.KafkaBrokersStr == "" {
		return nil, fmt.Errorf("KAFKA_BOOTSTRAP_SERVERS must be set")
	}

	return cfg, nil
}

// --- Internal Helper Functions ---

func getEnv(key, fallback string) string {
	if value, ok := os.LookupEnv(key); ok {
		return value
	}
	log.Printf("Warning: Environment variable %s not set, using fallback: %s", key, fallback)
	return fallback
}

func getEnvAsInt(key string, fallback int) int {
	strValue := getEnv(key, strconv.Itoa(fallback))
	if value, err := strconv.Atoi(strValue); err == nil {
		return value
	}
	log.Printf("Warning: Could not parse env var %s as int, using fallback: %d", key, fallback)
	return fallback
}

func getEnvAsBool(key string, fallback bool) bool {
	strValue := strings.ToLower(getEnv(key, strconv.FormatBool(fallback)))
	if value, err := strconv.ParseBool(strValue); err == nil {
		return value
	}
	log.Printf("Warning: Could not parse env var %s as bool, using fallback: %t", key, fallback)
	return fallback
}