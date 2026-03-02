package com.iot.IoT.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceCommandReliabilityScheduler {

    private final DeviceService deviceService;

    public DeviceCommandReliabilityScheduler(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @Scheduled(fixedDelayString = "${downlink.reliability.scan-interval-ms:5000}")
    public void scanAndProcess() {
        deviceService.processCommandReliability();
    }
}
