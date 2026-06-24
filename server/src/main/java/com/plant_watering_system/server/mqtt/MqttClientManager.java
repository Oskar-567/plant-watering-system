package com.plant_watering_system.server.mqtt;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.eclipse.paho.client.mqttv3.*;
import org.springframework.context.annotation.Lazy;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "mqtt.enabled", havingValue = "true")
public class MqttClientManager implements MqttCallbackExtended {

    private static final Logger log = LoggerFactory.getLogger(MqttClientManager.class);

    private final MqttClient client;
    private final MqttMessageHandler messageHandler;
    private final String username;
    private final String password;

    public MqttClientManager(
            @Value("${mqtt.broker}") String broker,
            @Value("${mqtt.port:1883}") int port,
            @Value("${mqtt.client-id}") String clientId,
            @Value("${mqtt.username:}") String username,
            @Value("${mqtt.password:}") String password,
            @Lazy MqttMessageHandler messageHandler) {
        try {
            this.client = new MqttClient("tcp://" + broker + ":" + port, clientId, new MemoryPersistence());
        } catch (MqttException e) {
            throw new RuntimeException("Failed to create MQTT client", e);
        }
        this.messageHandler = messageHandler;
        this.username = username;
        this.password = password;
    }

    @PostConstruct
    public void connect() throws MqttException {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setAutomaticReconnect(true);
        options.setCleanSession(true);
        if (!username.isBlank()) {
            options.setUserName(username);
            options.setPassword(password.toCharArray());
        }
        client.setCallback(this);
        client.connect(options);
    }

    @Override
    public void connectComplete(boolean reconnect, String serverURI) {
        try {
            client.subscribe("+/sensors/#", 1);
            client.subscribe("+/status", 1);
            log.info("MQTT {} {}", reconnect ? "reconnected to" : "connected to", serverURI);
        } catch (MqttException e) {
            log.error("Failed to subscribe after connect", e);
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage message) {
        messageHandler.handle(topic, new String(message.getPayload()));
    }

    @Override
    public void connectionLost(Throwable cause) {
        log.warn("MQTT connection lost: {}", cause.getMessage());
    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken token) {}

    @PreDestroy
    public void disconnect() {
        try {
            if (client.isConnected()) client.disconnect();
            client.close();
        } catch (MqttException e) {
            log.warn("Error during MQTT disconnect", e);
        }
    }

    public MqttClient getClient() {
        return client;
    }
}
