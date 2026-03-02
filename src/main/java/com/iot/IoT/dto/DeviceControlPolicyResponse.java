package com.iot.IoT.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DeviceControlPolicyResponse(
        Long devicePk,
        String deviceId,
        BigDecimal targetTemp,
        BigDecimal hysteresis,
        Instant updatedAt
) {
}
