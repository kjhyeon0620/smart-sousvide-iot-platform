package com.iot.IoT.ingestion.port;

import com.iot.IoT.dto.DeviceTemperaturePointResponse;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface TemperatureTimeSeriesQueryPort {

    Optional<DeviceTemperaturePointResponse> findLatest(String deviceId);

    List<DeviceTemperaturePointResponse> findRange(String deviceId, Instant from, Instant to, int limit);
}
