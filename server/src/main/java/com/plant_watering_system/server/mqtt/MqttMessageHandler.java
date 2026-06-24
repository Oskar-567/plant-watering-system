package com.plant_watering_system.server.mqtt;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class MqttMessageHandler {

    private static final Logger log = LoggerFactory.getLogger(MqttMessageHandler.class);

    public void handle(String topic, String payload) {
        log.debug("MQTT message on {}: {}", topic, payload);
    }
}
