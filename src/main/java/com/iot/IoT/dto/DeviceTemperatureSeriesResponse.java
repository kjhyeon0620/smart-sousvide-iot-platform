package com.iot.IoT.dto;

import java.time.Instant;
import java.util.List;

public record DeviceTemperatureSeriesResponse(
        Long devicePk,
        String deviceId,
        Instant from,
        Instant to,
        int limit,
        List<DeviceTemperaturePointResponse> items
) {
}
