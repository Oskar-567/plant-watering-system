package com.plant_watering_system.server.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class InfluxWriteService {

    private final WriteApiBlocking writeApi;
    private final String bucket;
    private final String org;

    public InfluxWriteService(
            InfluxDBClient client,
            @Value("${influx.bucket}") String bucket,
            @Value("${influx.org}") String org) {
        this.writeApi = client.getWriteApiBlocking();
        this.bucket = bucket;
        this.org = org;
    }

    public void writeMoisture(String instanceId, int sensorIndex, double percent) {
        Point point = Point.measurement("soil_moisture")
                .addTag("instance_id", instanceId)
                .addTag("sensor_index", String.valueOf(sensorIndex))
                .addField("percent", percent)
                .time(Instant.now(), WritePrecision.MS);
        writeApi.writePoint(bucket, org, point);
    }

    public void writeFlow(String instanceId, double liters) {
        Point point = Point.measurement("flow")
                .addTag("instance_id", instanceId)
                .addField("liters", liters)
                .time(Instant.now(), WritePrecision.MS);
        writeApi.writePoint(bucket, org, point);
    }

    public void writeBattery(String instanceId, double soc, double voltage) {
        Point point = Point.measurement("battery")
                .addTag("instance_id", instanceId)
                .addField("soc", soc)
                .addField("voltage", voltage)
                .time(Instant.now(), WritePrecision.MS);
        writeApi.writePoint(bucket, org, point);
    }
}
