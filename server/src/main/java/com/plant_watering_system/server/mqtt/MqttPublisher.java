package com.plant_watering_system.server.mqtt;

import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttPublisher {

    private static final Logger log = LoggerFactory.getLogger(MqttPublisher.class);

    private final MqttClientManager clientManager;

    public MqttPublisher(MqttClientManager clientManager) {
        this.clientManager = clientManager;
    }

    public void publish(String topic, String payload) {
        try {
            MqttMessage message = new MqttMessage(payload.getBytes());
            message.setQos(1);
            clientManager.getClient().publish(topic, message);
        } catch (MqttException e) {
            log.error("Failed to publish to {}: {}", topic, e.getMessage());
        }
    }
}
