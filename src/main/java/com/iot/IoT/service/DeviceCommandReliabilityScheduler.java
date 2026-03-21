package com.iot.IoT.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DeviceCommandReliabilityScheduler {

    private final DeviceCommandReliabilityService deviceCommandReliabilityService;

    public DeviceCommandReliabilityScheduler(DeviceCommandReliabilityService deviceCommandReliabilityService) {
        this.deviceCommandReliabilityService = deviceCommandReliabilityService;
    }

    @Scheduled(fixedDelayString = "${downlink.reliability.scan-interval-ms:5000}")
    public void scanAndProcess() {
        deviceCommandReliabilityService.processCommandReliability();
    }
}
