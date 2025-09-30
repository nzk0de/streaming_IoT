// go-mqtt-bridge/connectors/mqtt.go
package connectors

import (
	"crypto/tls"
	"fmt"
	"log"
	"os"
	"time"
	"mqttbridge/config"
	mqtt "github.com/eclipse/paho.mqtt.golang"
)

// NewMQTTClient creates and connects an MQTT client based on the provided configuration.
func NewMQTTClient(cfg *config.Config, handler mqtt.MessageHandler) (mqtt.Client, error) {
	hostname, _ := os.Hostname()
	clientID := fmt.Sprintf("%s%s-%d", cfg.MqttClientIDPrefix, hostname, time.Now().UnixNano())
	brokerURI := fmt.Sprintf("tcp://%s:%d", cfg.MqttBrokerHost, cfg.MqttPort)

	opts := mqtt.NewClientOptions()
	opts.AddBroker(brokerURI)
	opts.SetClientID(clientID)
	opts.SetAutoReconnect(true)
	opts.SetMaxReconnectInterval(10 * time.Second)
	opts.SetConnectionLostHandler(func(client mqtt.Client, err error) {
		log.Printf("WARNING: Disconnected from MQTT broker: %v. Reconnecting...", err)
	})
	opts.SetOnConnectHandler(func(client mqtt.Client) {
		log.Printf("INFO: Connected to MQTT broker %s:%d.", cfg.MqttBrokerHost, cfg.MqttPort)
		subscribe(client, cfg, handler)
	})

	if cfg.MqttUseTLS {
		opts.SetTLSConfig(&tls.Config{InsecureSkipVerify: true})
	}

	client := mqtt.NewClient(opts)
	token := client.Connect()
	if token.WaitTimeout(10*time.Second) && token.Error() != nil {
		return nil, fmt.Errorf("failed to connect to MQTT broker: %w", token.Error())
	}
	
	if !client.IsConnected() {
		log.Println("WARNING: Initial MQTT connection attempt timed out, but auto-reconnect is enabled.")
	}

	return client, nil
}

func subscribe(client mqtt.Client, cfg *config.Config, handler mqtt.MessageHandler) {
	sharedTopic := fmt.Sprintf("$share/%s/%s", cfg.MqttShareGroupName, cfg.MqttTopicFilter)
	log.Printf("INFO: Subscribing to shared topic: %s", sharedTopic)

	token := client.Subscribe(sharedTopic, 1, handler)
	go func() {
		if token.WaitTimeout(5*time.Second) && token.Error() != nil {
			log.Printf("ERROR: Failed to subscribe to '%s': %v", sharedTopic, token.Error())
		} else if token.Error() == nil {
			log.Printf("INFO: Successfully subscribed to shared topic: %s", sharedTopic)
		}
	}()
}