package com.iot.IoT.anomaly;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Per-device rule windows. Null fields mean the window is not running.
 */
public record DeviceAnomalyState(
        BigDecimal lastTargetTemp,
        boolean reachedTarget,
        Instant deviationSince,
        Instant heatingCheckpointAt,
        BigDecimal heatingCheckpointTemp,
        BigDecimal lastTemp,
        Instant lastTempChangedAt
) {
}
