package com.iot.IoT.ingestion.port;

import com.iot.IoT.ingestion.dto.DeviceStatusMessage;

import java.time.Instant;

public interface TemperatureTimeSeriesPort {

    void save(DeviceStatusMessage message, Instant occurredAt);
}
