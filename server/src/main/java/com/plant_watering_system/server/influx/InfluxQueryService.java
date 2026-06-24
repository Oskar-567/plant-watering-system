package com.plant_watering_system.server.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.QueryApi;
import com.plant_watering_system.server.dto.BatteryPoint;
import com.plant_watering_system.server.dto.MoisturePoint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class InfluxQueryService {

    private final QueryApi queryApi;
    private final String bucket;
    private final String org;

    public InfluxQueryService(
            InfluxDBClient client,
            @Value("${influx.bucket}") String bucket,
            @Value("${influx.org}") String org) {
        this.queryApi = client.getQueryApi();
        this.bucket = bucket;
        this.org = org;
    }

    public List<MoisturePoint> getMoisture(String instanceId, String range) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: -%s)
                  |> filter(fn: (r) => r._measurement == "soil_moisture")
                  |> filter(fn: (r) => r.instance_id == "%s")
                  |> filter(fn: (r) => r._field == "percent")
                """.formatted(bucket, range, instanceId);

        return queryApi.query(flux, org).stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> new MoisturePoint(
                        record.getTime(),
                        Integer.parseInt((String) record.getValueByKey("sensor_index")),
                        ((Number) record.getValue()).doubleValue()
                ))
                .toList();
    }

    public List<BatteryPoint> getBattery(String instanceId, String range) {
        String flux = """
                from(bucket: "%s")
                  |> range(start: -%s)
                  |> filter(fn: (r) => r._measurement == "battery")
                  |> filter(fn: (r) => r.instance_id == "%s")
                  |> pivot(rowKey: ["_time"], columnKey: ["_field"], valueColumn: "_value")
                """.formatted(bucket, range, instanceId);

        return queryApi.query(flux, org).stream()
                .flatMap(table -> table.getRecords().stream())
                .map(record -> new BatteryPoint(
                        record.getTime(),
                        ((Number) record.getValueByKey("soc")).doubleValue(),
                        ((Number) record.getValueByKey("voltage")).doubleValue()
                ))
                .toList();
    }
}
