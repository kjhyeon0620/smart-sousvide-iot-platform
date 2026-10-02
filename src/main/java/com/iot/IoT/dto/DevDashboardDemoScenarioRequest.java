package com.iot.IoT.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

public record DevDashboardDemoScenarioRequest(
        @Min(1) @Max(20) Integer count,
        String scenario,
        @DecimalMin("1.0") BigDecimal baseTargetTemp
) {
}
