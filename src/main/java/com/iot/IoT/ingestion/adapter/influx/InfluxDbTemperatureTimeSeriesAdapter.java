package com.iot.IoT.ingestion.adapter.influx;

import com.influxdb.client.QueryApi;
import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.WriteApiBlocking;
import com.influxdb.client.domain.WritePrecision;
import com.influxdb.query.FluxRecord;
import com.influxdb.query.FluxTable;
import com.influxdb.client.write.Point;
import com.iot.IoT.dto.DeviceTemperaturePointResponse;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesQueryPort;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class InfluxDbTemperatureTimeSeriesAdapter implements TemperatureTimeSeriesPort, TemperatureTimeSeriesQueryPort {

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

    @Override
    public Optional<DeviceTemperaturePointResponse> findLatest(String deviceId) {
        List<DeviceTemperaturePointResponse> points = findRange(deviceId, Instant.EPOCH, Instant.now(), 1);
        if (points.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(points.get(0));
    }

    @Override
    public List<DeviceTemperaturePointResponse> findRange(String deviceId, Instant from, Instant to, int limit) {
        String escapedBucket = quoteFluxString(bucket);
        String escapedDeviceId = quoteFluxString(deviceId);

        String flux = """
                from(bucket: "%s")
                  |> range(start: %s, stop: %s)
                  |> filter(fn: (r) => r._measurement == "device_status")
                  |> filter(fn: (r) => r.deviceId == "%s")
                  |> filter(fn: (r) => r._field == "temp" or r._field == "targetTemp" or r._field == "state")
                  |> pivot(rowKey:["_time"], columnKey: ["_field"], valueColumn: "_value")
                  |> keep(columns: ["_time", "temp", "targetTemp", "state"])
                  |> sort(columns: ["_time"], desc: true)
                  |> limit(n: %d)
                """.formatted(
                escapedBucket,
                from.toString(),
                to.toString(),
                escapedDeviceId,
                limit
        );

        QueryApi queryApi = influxDBClient.getQueryApi();
        List<FluxTable> tables = queryApi.query(flux, org);
        List<FluxRecord> records = new ArrayList<>();
        for (FluxTable table : tables) {
            records.addAll(table.getRecords());
        }

        return records.stream()
                .map(this::toPoint)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .sorted(Comparator.comparing(DeviceTemperaturePointResponse::occurredAt).reversed())
                .collect(Collectors.toList());
    }

    private Optional<DeviceTemperaturePointResponse> toPoint(FluxRecord record) {
        Instant occurredAt = record.getTime();
        if (occurredAt == null) {
            return Optional.empty();
        }

        BigDecimal temp = toBigDecimal(record.getValueByKey("temp"));
        BigDecimal targetTemp = toBigDecimal(record.getValueByKey("targetTemp"));
        DeviceState state = toEnum(record.getValueByKey("state"), DeviceState::valueOf);

        if (temp == null || targetTemp == null || state == null) {
            return Optional.empty();
        }

        return Optional.of(new DeviceTemperaturePointResponse(occurredAt, temp, targetTemp, state));
    }

    private static BigDecimal toBigDecimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static <T> T toEnum(Object value, Function<String, T> mapper) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.apply(String.valueOf(value));
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private static String quoteFluxString(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
