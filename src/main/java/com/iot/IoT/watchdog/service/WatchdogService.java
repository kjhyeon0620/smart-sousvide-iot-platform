package com.iot.IoT.watchdog.service;

import com.iot.IoT.watchdog.event.DeviceOfflineEvent;
import com.iot.IoT.watchdog.port.FailSafeEventPublisher;
import com.iot.IoT.watchdog.port.WatchdogStatePort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Set;

@Service
public class WatchdogService {

    private static final String OFFLINE_REASON = "heartbeat_expired";

    private final WatchdogStatePort watchdogStatePort;
    private final FailSafeEventPublisher failSafeEventPublisher;

    public WatchdogService(
            WatchdogStatePort watchdogStatePort,
            FailSafeEventPublisher failSafeEventPublisher
    ) {
        this.watchdogStatePort = watchdogStatePort;
        this.failSafeEventPublisher = failSafeEventPublisher;
    }

    public void scanAndPublishOfflineEvents() {
        Instant detectedAt = Instant.now();
        Set<String> trackedDevices = watchdogStatePort.findTrackedDeviceIds();

        for (String deviceId : trackedDevices) {
            if (watchdogStatePort.isHeartbeatAlive(deviceId)) {
                continue;
            }

            if (!watchdogStatePort.markOfflineNotifiedIfAbsent(deviceId, detectedAt)) {
                continue;
            }

            DeviceOfflineEvent event = new DeviceOfflineEvent(
                    deviceId,
                    watchdogStatePort.findLastSeen(deviceId).orElse(null),
                    detectedAt,
                    OFFLINE_REASON
            );
            failSafeEventPublisher.publishDeviceOffline(event);
        }
    }
}
