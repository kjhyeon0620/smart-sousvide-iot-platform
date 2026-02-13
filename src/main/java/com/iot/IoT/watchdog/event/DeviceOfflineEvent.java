package com.iot.IoT.watchdog.event;

import java.time.Instant;

public record DeviceOfflineEvent(
        String deviceId,
        Instant lastSeenAt,
        Instant detectedAt,
        String reason
) {
}
