package com.iot.IoT.dto;

import java.time.Instant;

public record DeviceResponse(
        Long id,
        String deviceId,
        String name,
        boolean enabled,
        Instant createdAt,
        Instant updatedAt
) {
}
