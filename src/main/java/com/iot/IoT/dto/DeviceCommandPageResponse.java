package com.iot.IoT.dto;

import java.util.List;

public record DeviceCommandPageResponse(
        Long devicePk,
        String deviceId,
        int limit,
        List<DeviceCommandResponse> items
) {
}
