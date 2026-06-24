package com.plant_watering_system.server.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxConfig {

    @Bean
    public InfluxDBClient influxDBClient(
            @Value("${influx.url}") String url,
            @Value("${influx.token}") String token,
            @Value("${influx.org}") String org,
            @Value("${influx.bucket}") String bucket) {
        return InfluxDBClientFactory.create(url, token.toCharArray(), org, bucket);
    }
}
