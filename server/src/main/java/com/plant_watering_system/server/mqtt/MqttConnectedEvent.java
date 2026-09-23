package com.plant_watering_system.server.mqtt;

// Published on every successful MQTT connect/reconnect (listeners must not block)
public record MqttConnectedEvent() {}
