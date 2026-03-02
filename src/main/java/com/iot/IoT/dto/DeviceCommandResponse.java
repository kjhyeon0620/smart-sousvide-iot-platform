package com.iot.IoT.dto;

import com.iot.IoT.control.ControlAction;
import com.iot.IoT.entity.DeviceCommandStatus;

import java.time.Instant;

public record DeviceCommandResponse(
        Long commandId,
        Long devicePk,
        String deviceId,
        ControlAction commandType,
        DeviceCommandStatus status,
        String topic,
        String payload,
        Instant requestedAt,
        Instant sentAt,
        String errorMessage
) {
}
