package com.iot.IoT.dto;

import java.util.List;

public record DeviceAnomalyPageResponse(
        Long devicePk,
        String deviceId,
        int limit,
        List<DeviceAnomalyResponse> items
) {
}
