package com.iot.IoT.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record UpdateDeviceControlPolicyRequest(
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 4, fraction = 2)
        BigDecimal targetTemp,
        @NotNull
        @DecimalMin(value = "0.0", inclusive = false)
        @Digits(integer = 2, fraction = 2)
        BigDecimal hysteresis
) {
}
