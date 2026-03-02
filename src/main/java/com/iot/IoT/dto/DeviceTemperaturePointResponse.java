package com.iot.IoT.dto;

import com.iot.IoT.ingestion.dto.DeviceState;

import java.math.BigDecimal;
import java.time.Instant;

public record DeviceTemperaturePointResponse(
        Instant occurredAt,
        BigDecimal temp,
        BigDecimal targetTemp,
        DeviceState state
) {
}
