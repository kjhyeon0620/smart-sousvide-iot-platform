package com.iot.IoT.dto;

import com.iot.IoT.entity.DeviceCommandStatus;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record DevDashboardDemoCommandRequest(
        @NotEmpty List<DeviceCommandStatus> statuses
) {
}
