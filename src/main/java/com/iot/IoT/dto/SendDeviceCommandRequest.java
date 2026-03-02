package com.iot.IoT.dto;

import com.iot.IoT.control.ControlAction;
import jakarta.validation.constraints.NotNull;

public record SendDeviceCommandRequest(
        @NotNull ControlAction commandType
) {
}
