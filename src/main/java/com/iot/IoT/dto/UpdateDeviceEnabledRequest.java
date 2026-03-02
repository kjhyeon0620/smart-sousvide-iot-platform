package com.iot.IoT.dto;

import jakarta.validation.constraints.NotNull;

public record UpdateDeviceEnabledRequest(
        @NotNull Boolean enabled
) {
}
