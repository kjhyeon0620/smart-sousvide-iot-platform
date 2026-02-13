package com.iot.IoT.watchdog.port;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

public interface WatchdogStatePort {

    Set<String> findTrackedDeviceIds();

    boolean isHeartbeatAlive(String deviceId);

    Optional<Instant> findLastSeen(String deviceId);

    boolean markOfflineNotifiedIfAbsent(String deviceId, Instant detectedAt);
}
