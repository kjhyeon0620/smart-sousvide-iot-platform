package com.iot.IoT.ingestion.adapter.influx;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
public class InfluxDbTemperatureTimeSeriesAdapter implements TemperatureTimeSeriesPort {

    private static final String MEASUREMENT = "device_status";

    private final InfluxDBClient influxDBClient;
    private final String bucket;
    private final String org;

    public InfluxDbTemperatureTimeSeriesAdapter(
            InfluxDBClient influxDBClient,
            @Value("${influxdb.bucket}") String bucket,
            @Value("${influxdb.org}") String org
    ) {
        this.influxDBClient = influxDBClient;
        this.bucket = bucket;
        this.org = org;
    }

    @Override
    public void save(DeviceStatusMessage message, Instant occurredAt) {
        Point point = Point.measurement(MEASUREMENT)
                .addTag("deviceId", message.deviceId())
                .addField("temp", message.temp().doubleValue())
                .addField("targetTemp", message.targetTemp().doubleValue())
                .addField("state", message.state().name())
                .time(occurredAt, WritePrecision.MS);

        WriteApiBlocking writeApi = influxDBClient.getWriteApiBlocking();
        writeApi.writePoint(bucket, org, point);
    }
}
