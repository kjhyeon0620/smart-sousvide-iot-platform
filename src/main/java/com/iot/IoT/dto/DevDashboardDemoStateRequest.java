package com.iot.IoT.dto;

import com.iot.IoT.ingestion.dto.DeviceState;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

public record DevDashboardDemoStateRequest(
        Boolean online,
        Boolean enabled,
        @DecimalMin("0.0") BigDecimal temp,
        @DecimalMin("1.0") BigDecimal targetTemp,
        DeviceState state
) {
}
