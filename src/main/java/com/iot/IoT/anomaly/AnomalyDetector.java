package com.iot.IoT.anomaly;

import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;

/**
 * Pure rule evaluation. Time comes from the caller (server receive time), never from message counts.
 */
@Component
public class AnomalyDetector {

    private final BigDecimal deviationThreshold;
    private final Duration deviationDuration;
    private final Duration stallDuration;
    private final BigDecimal stallMinRise;
    private final Duration stuckDuration;
    private final BigDecimal deadband;

    public AnomalyDetector(
            @Value("${anomaly.target-deviation.threshold-celsius:1.0}") BigDecimal deviationThreshold,
            @Value("${anomaly.target-deviation.duration-seconds:180}") long deviationDurationSeconds,
            @Value("${anomaly.heating-stall.duration-seconds:600}") long stallDurationSeconds,
            @Value("${anomaly.heating-stall.min-rise-celsius:0.0}") BigDecimal stallMinRise,
            @Value("${anomaly.sensor-stuck.duration-seconds:600}") long stuckDurationSeconds,
            @Value("${control.deadband:0.3}") BigDecimal deadband
    ) {
        this.deviationThreshold = deviationThreshold;
        this.deviationDuration = Duration.ofSeconds(deviationDurationSeconds);
        this.stallDuration = Duration.ofSeconds(stallDurationSeconds);
        this.stallMinRise = stallMinRise;
        this.stuckDuration = Duration.ofSeconds(stuckDurationSeconds);
        this.deadband = deadband;
    }

    public AnomalyEvaluation evaluate(DeviceAnomalyState previous, DeviceStatusMessage message, Instant now) {
        if (message.state() == DeviceState.OFF) {
            return new AnomalyEvaluation(null, Set.of());
        }

        BigDecimal temp = message.temp();
        BigDecimal target = message.targetTemp();
        BigDecimal gap = temp.subtract(target).abs();
        Set<AnomalyType> active = EnumSet.noneOf(AnomalyType.class);

        // TARGET_DEVIATION: only after the device has reached the current target (preheat is not a deviation)
        boolean sameTarget = previous != null && previous.lastTargetTemp().compareTo(target) == 0;
        boolean reached = (sameTarget && previous.reachedTarget()) || gap.compareTo(deadband) <= 0;
        Instant deviationSince = null;
        if (reached && gap.compareTo(deviationThreshold) > 0) {
            deviationSince = sameTarget && previous.deviationSince() != null ? previous.deviationSince() : now;
            if (elapsed(deviationSince, now, deviationDuration)) {
                active.add(AnomalyType.TARGET_DEVIATION);
            }
        }

        // HEATING_STALL: compare against a checkpoint that slides forward every stallDuration while heating is healthy.
        // ponytail: checkpoint instead of a rolling window, so detection can take up to 2x stallDuration.
        Instant checkpointAt = null;
        BigDecimal checkpointTemp = null;
        if (message.state() == DeviceState.HEATING) {
            boolean wasHeating = previous != null && previous.heatingCheckpointAt() != null;
            checkpointAt = wasHeating ? previous.heatingCheckpointAt() : now;
            checkpointTemp = wasHeating ? previous.heatingCheckpointTemp() : temp;
            if (elapsed(checkpointAt, now, stallDuration)) {
                if (temp.subtract(checkpointTemp).compareTo(stallMinRise) <= 0) {
                    active.add(AnomalyType.HEATING_STALL);
                } else {
                    checkpointAt = now;
                    checkpointTemp = temp;
                }
            }
        }

        // SENSOR_STUCK: reading has not changed for stuckDuration
        boolean unchanged = previous != null && previous.lastTemp().compareTo(temp) == 0;
        Instant changedAt = unchanged ? previous.lastTempChangedAt() : now;
        if (elapsed(changedAt, now, stuckDuration)) {
            active.add(AnomalyType.SENSOR_STUCK);
        }

        DeviceAnomalyState next = new DeviceAnomalyState(
                target, reached, deviationSince, checkpointAt, checkpointTemp, temp, changedAt);
        return new AnomalyEvaluation(next, active);
    }

    private static boolean elapsed(Instant since, Instant now, Duration duration) {
        return !Duration.between(since, now).minus(duration).isNegative();
    }
}
