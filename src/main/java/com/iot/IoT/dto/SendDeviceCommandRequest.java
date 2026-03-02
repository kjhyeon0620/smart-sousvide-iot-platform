package com.iot.IoT.dto;

import com.iot.IoT.control.ControlAction;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SendDeviceCommandRequest(
        @NotNull ControlAction commandType,
        @NotBlank @Size(max = 100) String idempotencyKey
) {
}
