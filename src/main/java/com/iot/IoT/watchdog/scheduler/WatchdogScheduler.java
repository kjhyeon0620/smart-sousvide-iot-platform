package com.iot.IoT.watchdog.scheduler;

import com.iot.IoT.watchdog.service.WatchdogService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class WatchdogScheduler {

    private final WatchdogService watchdogService;

    public WatchdogScheduler(WatchdogService watchdogService) {
        this.watchdogService = watchdogService;
    }

    @Scheduled(fixedDelayString = "${watchdog.scan-interval-ms:5000}")
    public void scan() {
        watchdogService.scanAndPublishOfflineEvents();
    }
}
