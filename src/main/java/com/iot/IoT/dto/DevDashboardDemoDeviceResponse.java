package com.iot.IoT.dto;

import com.iot.IoT.ingestion.dto.DeviceState;

import java.math.BigDecimal;

public record DevDashboardDemoDeviceResponse(
        Long id,
        String deviceId,
        String name,
        boolean enabled,
        boolean online,
        BigDecimal temp,
        BigDecimal targetTemp,
        DeviceState state
) {
}
