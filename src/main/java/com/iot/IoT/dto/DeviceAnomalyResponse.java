package com.iot.IoT.dto;

import com.iot.IoT.anomaly.AnomalyType;
import com.iot.IoT.entity.AnomalyStatus;

import java.time.Instant;

public record DeviceAnomalyResponse(
        Long id,
        AnomalyType type,
        AnomalyStatus status,
        Instant detectedAt,
        Instant resolvedAt,
        String detail
) {
}
