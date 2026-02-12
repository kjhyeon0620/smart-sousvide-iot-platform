package com.iot.IoT.ingestion.port;

import java.time.Instant;

public interface HeartbeatPort {

    void updateLastSeen(String deviceId, Instant lastSeenAt);
}
