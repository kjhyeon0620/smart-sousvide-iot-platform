package com.iot.IoT.service.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class DownlinkMetricsRecorder {

    private final Counter sentCounter;
    private final Counter failedCounter;
    private final Counter ackedCounter;
    private final Counter expiredCounter;
    private final Counter retriedCounter;
    private final Counter idempotencyHitCounter;

    public DownlinkMetricsRecorder(MeterRegistry meterRegistry) {
        this.sentCounter = meterRegistry.counter("iot.downlink.command.sent.total");
        this.failedCounter = meterRegistry.counter("iot.downlink.command.failed.total");
        this.ackedCounter = meterRegistry.counter("iot.downlink.command.acked.total");
        this.expiredCounter = meterRegistry.counter("iot.downlink.command.expired.total");
        this.retriedCounter = meterRegistry.counter("iot.downlink.command.retried.total");
        this.idempotencyHitCounter = meterRegistry.counter("iot.downlink.command.idempotency.hit.total");
    }

    public void recordSent() {
        sentCounter.increment();
    }

    public void recordFailed() {
        failedCounter.increment();
    }

    public void recordAcked() {
        ackedCounter.increment();
    }

    public void recordExpired() {
        expiredCounter.increment();
    }

    public void recordRetried() {
        retriedCounter.increment();
    }

    public void recordIdempotencyHit() {
        idempotencyHitCounter.increment();
    }
}
