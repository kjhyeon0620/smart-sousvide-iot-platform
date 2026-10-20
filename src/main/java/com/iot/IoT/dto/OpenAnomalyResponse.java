package com.iot.IoT.dto;

import com.iot.IoT.anomaly.AnomalyType;

import java.time.Instant;

public record OpenAnomalyResponse(
        AnomalyType type,
        Instant detectedAt
) {
}
