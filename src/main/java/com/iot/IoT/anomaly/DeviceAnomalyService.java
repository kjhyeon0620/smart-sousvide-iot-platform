package com.iot.IoT.anomaly;

import com.iot.IoT.entity.AnomalyStatus;
import com.iot.IoT.entity.DeviceAnomaly;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.repository.DeviceAnomalyRepository;
import com.iot.IoT.repository.DeviceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps per-device rule windows in instance memory and persists only OPEN/RESOLVED transitions.
 * ponytail: windows are lost on restart and are wrong when messages of one device are split across
 * instances (shared subscription). Move state to Redis if multi-instance ingestion becomes the norm.
 */
@Service
public class DeviceAnomalyService {

    private static final Logger log = LoggerFactory.getLogger(DeviceAnomalyService.class);

    private final AnomalyDetector detector;
    private final DeviceAnomalyRepository anomalyRepository;
    private final DeviceRepository deviceRepository;
    private final AnomalyMetricsRecorder metrics;
    private final boolean enabled;

    // ponytail: no eviction, bounded by the number of devices that ever reported.
    private final Map<String, Tracker> trackers = new ConcurrentHashMap<>();
    private final Map<String, Set<AnomalyType>> preloadedOpen = new ConcurrentHashMap<>();

    public DeviceAnomalyService(
            AnomalyDetector detector,
            DeviceAnomalyRepository anomalyRepository,
            DeviceRepository deviceRepository,
            AnomalyMetricsRecorder metrics,
            @Value("${anomaly.enabled:true}") boolean enabled
    ) {
        this.detector = detector;
        this.anomalyRepository = anomalyRepository;
        this.deviceRepository = deviceRepository;
        this.metrics = metrics;
        this.enabled = enabled;
    }

    /**
     * Loads OPEN anomalies left by a previous run so they can be resolved after a restart.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void preloadOpenAnomalies() {
        if (!enabled) {
            return;
        }
        try {
            for (DeviceAnomaly anomaly : anomalyRepository.findByStatus(AnomalyStatus.OPEN)) {
                preloadedOpen.computeIfAbsent(anomaly.getDeviceId(), id -> EnumSet.noneOf(AnomalyType.class))
                        .add(anomaly.getType());
            }
        } catch (RuntimeException ex) {
            metrics.recordEvaluationFailure();
            log.warn("[ANOMALY] Failed to preload open anomalies. Stale OPEN rows stay until the next transition.", ex);
        }
    }

    public void evaluate(DeviceStatusMessage message, Instant now) {
        if (!enabled) {
            return;
        }
        // compute() serializes evaluation per device; DB writes inside it happen only on transitions.
        trackers.compute(message.deviceId(), (deviceId, tracker) -> {
            Tracker current = tracker != null ? tracker : new Tracker(null, initialOpen(deviceId));
            try {
                return apply(current, message, now);
            } catch (RuntimeException ex) {
                metrics.recordEvaluationFailure();
                log.error("[ANOMALY] Evaluation failed. deviceId={}", deviceId, ex);
                return current;
            }
        });
    }

    private Tracker apply(Tracker current, DeviceStatusMessage message, Instant now) {
        AnomalyEvaluation evaluation = detector.evaluate(current.state(), message, now);
        Set<AnomalyType> open = EnumSet.noneOf(AnomalyType.class);
        open.addAll(current.open());

        for (AnomalyType type : evaluation.active()) {
            if (!open.contains(type) && persistOpened(message, type, now)) {
                open.add(type);
                metrics.recordOpened(type);
            }
        }
        for (AnomalyType type : current.open()) {
            if (!evaluation.active().contains(type) && persistResolved(message.deviceId(), type, now)) {
                open.remove(type);
                metrics.recordResolved(type);
            }
        }

        if (evaluation.state() == null && open.isEmpty()) {
            return null;
        }
        return new Tracker(evaluation.state(), open);
    }

    private Set<AnomalyType> initialOpen(String deviceId) {
        Set<AnomalyType> preloaded = preloadedOpen.remove(deviceId);
        return preloaded != null ? preloaded : EnumSet.noneOf(AnomalyType.class);
    }

    /**
     * @return true when the anomaly is open afterwards (stored, already stored, or device not registered)
     */
    private boolean persistOpened(DeviceStatusMessage message, AnomalyType type, Instant now) {
        String deviceId = message.deviceId();
        try {
            if (anomalyRepository.findFirstByDeviceIdAndTypeAndStatus(deviceId, type, AnomalyStatus.OPEN).isPresent()) {
                return true;
            }
            var device = deviceRepository.findByDeviceId(deviceId);
            if (device.isEmpty()) {
                log.debug("[ANOMALY] Opened for unregistered device, not stored. deviceId={}, type={}", deviceId, type);
                return true;
            }
            DeviceAnomaly anomaly = new DeviceAnomaly();
            anomaly.setDevicePk(device.get().getId());
            anomaly.setDeviceId(deviceId);
            anomaly.setType(type);
            anomaly.setStatus(AnomalyStatus.OPEN);
            anomaly.setDetectedAt(now);
            anomaly.setDetail("temp=%s, targetTemp=%s, state=%s".formatted(
                    message.temp(), message.targetTemp(), message.state()));
            anomalyRepository.save(anomaly);
            log.warn("[ANOMALY] Opened. deviceId={}, type={}, temp={}, targetTemp={}, state={}",
                    deviceId, type, message.temp(), message.targetTemp(), message.state());
            return true;
        } catch (RuntimeException ex) {
            metrics.recordEvaluationFailure();
            log.error("[ANOMALY] Failed to store opened anomaly. deviceId={}, type={}", deviceId, type, ex);
            return false;
        }
    }

    private boolean persistResolved(String deviceId, AnomalyType type, Instant now) {
        try {
            anomalyRepository.findFirstByDeviceIdAndTypeAndStatus(deviceId, type, AnomalyStatus.OPEN)
                    .ifPresent(anomaly -> {
                        anomaly.setStatus(AnomalyStatus.RESOLVED);
                        anomaly.setResolvedAt(now);
                        anomalyRepository.save(anomaly);
                    });
            log.info("[ANOMALY] Resolved. deviceId={}, type={}", deviceId, type);
            return true;
        } catch (RuntimeException ex) {
            metrics.recordEvaluationFailure();
            log.error("[ANOMALY] Failed to store resolved anomaly. deviceId={}, type={}", deviceId, type, ex);
            return false;
        }
    }

    private record Tracker(DeviceAnomalyState state, Set<AnomalyType> open) {
    }
}
