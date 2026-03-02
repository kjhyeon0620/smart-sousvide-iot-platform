package com.iot.IoT.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateDeviceRequest(
        @NotBlank @Size(max = 64) String deviceId,
        @Size(max = 100) String name,
        Boolean enabled
) {
}
