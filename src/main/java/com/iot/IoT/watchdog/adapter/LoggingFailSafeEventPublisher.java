package com.iot.IoT.watchdog.adapter;

import com.iot.IoT.watchdog.event.DeviceOfflineEvent;
import com.iot.IoT.watchdog.port.FailSafeEventPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class LoggingFailSafeEventPublisher implements FailSafeEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(LoggingFailSafeEventPublisher.class);

    @Override
    public void publishDeviceOffline(DeviceOfflineEvent event) {
        log.warn("[WATCHDOG] DEVICE_OFFLINE emitted. deviceId={}, lastSeenAt={}, detectedAt={}, reason={}",
                event.deviceId(),
                event.lastSeenAt(),
                event.detectedAt(),
                event.reason());
    }
}
