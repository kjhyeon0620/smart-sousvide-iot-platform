package com.iot.IoT.dto;

public record DevDashboardDemoAvailabilityResponse(
        boolean available,
        String deviceIdPrefix
) {
}
