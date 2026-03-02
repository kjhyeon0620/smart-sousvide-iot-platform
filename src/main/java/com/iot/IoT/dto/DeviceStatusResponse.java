package com.iot.IoT.dto;

import com.iot.IoT.ingestion.dto.DeviceState;

import java.math.BigDecimal;
import java.time.Instant;

public record DeviceStatusResponse(
        Long id,
        String deviceId,
        String name,
        boolean enabled,
        Instant lastSeenAt,
        boolean online,
        BigDecimal latestTemp,
        BigDecimal latestTargetTemp,
        DeviceState latestState,
        Instant latestOccurredAt
) {
}
