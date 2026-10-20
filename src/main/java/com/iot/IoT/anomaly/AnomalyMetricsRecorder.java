package com.iot.IoT.anomaly;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.Map;

@Component
public class AnomalyMetricsRecorder {

    private final Map<AnomalyType, Counter> openedCounters = new EnumMap<>(AnomalyType.class);
    private final Map<AnomalyType, Counter> resolvedCounters = new EnumMap<>(AnomalyType.class);
    private final Counter evaluationFailureCounter;

    public AnomalyMetricsRecorder(MeterRegistry meterRegistry) {
        for (AnomalyType type : AnomalyType.values()) {
            openedCounters.put(type, meterRegistry.counter("iot.anomaly.opened.total", "type", type.name()));
            resolvedCounters.put(type, meterRegistry.counter("iot.anomaly.resolved.total", "type", type.name()));
        }
        this.evaluationFailureCounter = meterRegistry.counter("iot.anomaly.evaluation.failures.total");
    }

    public void recordOpened(AnomalyType type) {
        openedCounters.get(type).increment();
    }

    public void recordResolved(AnomalyType type) {
        resolvedCounters.get(type).increment();
    }

    public void recordEvaluationFailure() {
        evaluationFailureCounter.increment();
    }
}
