package com.iot.IoT.dto;

import java.util.List;

public record DevicePageResponse(
        List<DeviceResponse> items,
        long totalElements,
        int totalPages,
        int page,
        int size
) {
}
