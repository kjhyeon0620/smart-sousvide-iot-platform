package com.iot.IoT.dto;

import java.util.List;

public record DevDashboardDemoResponse(
        int createdDevices,
        int onlineDevices,
        int offlineDevices,
        int disabledDevices,
        int temperaturePoints,
        int commands,
        String warning,
        List<DevDashboardDemoDeviceResponse> devices
) {
}
