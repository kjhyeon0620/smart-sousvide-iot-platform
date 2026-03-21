package com.iot.IoT.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

@Component
@ConditionalOnProperty(prefix = "ingestion.metrics", name = "enabled", havingValue = "true", matchIfMissing = true)
public class IngestionMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(IngestionMetricsCollector.class);

    private final LongAdder mqttReceivedTotal = new LongAdder();
    private final LongAdder parseSuccessTotal = new LongAdder();
    private final LongAdder parseFailureTotal = new LongAdder();
    private final LongAdder influxSuccessTotal = new LongAdder();
    private final LongAdder influxFailureTotal = new LongAdder();
    private final LongAdder influxBypassTotal = new LongAdder();
    private final LongAdder redisSuccessTotal = new LongAdder();
    private final LongAdder redisFailureTotal = new LongAdder();
    private final LongAdder processingFailureTotal = new LongAdder();
    private final LongAdder overallPipelineSuccessTotal = new LongAdder();
    private final LongAdder corePipelineSuccessTotal = new LongAdder();
    private final LongAdder duplicateDroppedTotal = new LongAdder();
    private final LongAdder parseDeadLetterTotal = new LongAdder();
    private final LongAdder storageReplayCandidateTotal = new LongAdder();
    private final LongAdder controlReplayCandidateTotal = new LongAdder();
    private final AtomicInteger inFlight = new AtomicInteger(0);

    private final AtomicLong lastMqttReceived = new AtomicLong(0);
    private final AtomicLong lastParseSuccess = new AtomicLong(0);
    private final AtomicLong lastParseFailure = new AtomicLong(0);
    private final AtomicLong lastInfluxSuccess = new AtomicLong(0);
    private final AtomicLong lastInfluxFailure = new AtomicLong(0);
    private final AtomicLong lastInfluxBypass = new AtomicLong(0);
    private final AtomicLong lastRedisSuccess = new AtomicLong(0);
    private final AtomicLong lastRedisFailure = new AtomicLong(0);
    private final AtomicLong lastProcessingFailure = new AtomicLong(0);
    private final AtomicLong lastOverallPipelineSuccess = new AtomicLong(0);
    private final AtomicLong lastCorePipelineSuccess = new AtomicLong(0);
    private final AtomicLong lastDuplicateDropped = new AtomicLong(0);
    private final AtomicLong lastParseDeadLetter = new AtomicLong(0);
    private final AtomicLong lastStorageReplayCandidate = new AtomicLong(0);
    private final AtomicLong lastControlReplayCandidate = new AtomicLong(0);

    private final Counter mqttReceivedCounter;
    private final Counter parseSuccessCounter;
    private final Counter parseFailureCounter;
    private final Counter influxSuccessCounter;
    private final Counter influxFailureCounter;
    private final Counter influxBypassCounter;
    private final Counter redisSuccessCounter;
    private final Counter redisFailureCounter;
    private final Counter processingFailureCounter;
    private final Counter overallPipelineSuccessCounter;
    private final Counter corePipelineSuccessCounter;
    private final Counter duplicateDroppedCounter;
    private final Counter parseDeadLetterCounter;
    private final Counter storageReplayCandidateCounter;
    private final Counter controlReplayCandidateCounter;
    private final Timer processingLatencyTimer;

    public IngestionMetricsCollector(MeterRegistry meterRegistry) {
        this.mqttReceivedCounter = meterRegistry.counter("iot.ingestion.mqtt.received.total");
        this.parseSuccessCounter = meterRegistry.counter("iot.ingestion.parse.success.total");
        this.parseFailureCounter = meterRegistry.counter("iot.ingestion.parse.failure.total");
        this.influxSuccessCounter = meterRegistry.counter("iot.ingestion.influx.success.total");
        this.influxFailureCounter = meterRegistry.counter("iot.ingestion.influx.failure.total");
        this.influxBypassCounter = meterRegistry.counter("iot.ingestion.influx.bypass.total");
        this.redisSuccessCounter = meterRegistry.counter("iot.ingestion.redis.success.total");
        this.redisFailureCounter = meterRegistry.counter("iot.ingestion.redis.failure.total");
        this.processingFailureCounter = meterRegistry.counter("iot.ingestion.processing.failure.total");
        this.overallPipelineSuccessCounter = meterRegistry.counter("iot.ingestion.pipeline.overall.success.total");
        this.corePipelineSuccessCounter = meterRegistry.counter("iot.ingestion.pipeline.core.success.total");
        this.duplicateDroppedCounter = meterRegistry.counter("iot.ingestion.duplicate.dropped.total");
        this.parseDeadLetterCounter = meterRegistry.counter("iot.ingestion.parse.dead_letter.total");
        this.storageReplayCandidateCounter = meterRegistry.counter("iot.ingestion.storage.replay_candidate.total");
        this.controlReplayCandidateCounter = meterRegistry.counter("iot.ingestion.control.replay_candidate.total");
        this.processingLatencyTimer = meterRegistry.timer("iot.ingestion.processing.latency");
        Gauge.builder("iot.ingestion.inflight", inFlight, AtomicInteger::get)
                .description("Current number of in-flight ingestion tasks")
                .register(meterRegistry);
    }

    public void recordMqttReceived() {
        mqttReceivedTotal.increment();
        mqttReceivedCounter.increment();
    }

    public void recordParseSuccess() {
        parseSuccessTotal.increment();
        parseSuccessCounter.increment();
    }

    public void recordParseFailure() {
        parseFailureTotal.increment();
        parseFailureCounter.increment();
    }

    public void recordInfluxSuccess() {
        influxSuccessTotal.increment();
        influxSuccessCounter.increment();
    }

    public void recordInfluxFailure() {
        influxFailureTotal.increment();
        influxFailureCounter.increment();
    }

    public void recordInfluxBypass() {
        influxBypassTotal.increment();
        influxBypassCounter.increment();
    }

    public void recordRedisSuccess() {
        redisSuccessTotal.increment();
        redisSuccessCounter.increment();
    }

    public void recordRedisFailure() {
        redisFailureTotal.increment();
        redisFailureCounter.increment();
    }

    public void recordProcessingFailure() {
        processingFailureTotal.increment();
        processingFailureCounter.increment();
    }

    public void recordOverallPipelineSuccess() {
        overallPipelineSuccessTotal.increment();
        overallPipelineSuccessCounter.increment();
    }

    public void recordCorePipelineSuccess() {
        corePipelineSuccessTotal.increment();
        corePipelineSuccessCounter.increment();
    }

    public void recordDuplicateDropped() {
        duplicateDroppedTotal.increment();
        duplicateDroppedCounter.increment();
    }

    public void recordParseDeadLetter() {
        parseDeadLetterTotal.increment();
        parseDeadLetterCounter.increment();
    }

    public void recordStorageReplayCandidate() {
        storageReplayCandidateTotal.increment();
        storageReplayCandidateCounter.increment();
    }

    public void recordControlReplayCandidate() {
        controlReplayCandidateTotal.increment();
        controlReplayCandidateCounter.increment();
    }

    public void incrementInFlight() {
        inFlight.incrementAndGet();
    }

    public void decrementInFlight() {
        inFlight.updateAndGet(current -> Math.max(current - 1, 0));
    }

    public void recordProcessingLatency(long nanos) {
        if (nanos > 0) {
            processingLatencyTimer.record(Duration.ofNanos(nanos));
        }
    }

    @Scheduled(fixedDelayString = "${ingestion.metrics-log-interval-ms:1000}")
    public void logSnapshot() {
        long mqttReceived = mqttReceivedTotal.sum();
        long parseSuccess = parseSuccessTotal.sum();
        long parseFailure = parseFailureTotal.sum();
        long influxSuccess = influxSuccessTotal.sum();
        long influxFailure = influxFailureTotal.sum();
        long influxBypass = influxBypassTotal.sum();
        long redisSuccess = redisSuccessTotal.sum();
        long redisFailure = redisFailureTotal.sum();
        long processingFailure = processingFailureTotal.sum();
        long overallPipelineSuccess = overallPipelineSuccessTotal.sum();
        long corePipelineSuccess = corePipelineSuccessTotal.sum();
        long duplicateDropped = duplicateDroppedTotal.sum();
        long parseDeadLetter = parseDeadLetterTotal.sum();
        long storageReplayCandidate = storageReplayCandidateTotal.sum();
        long controlReplayCandidate = controlReplayCandidateTotal.sum();

        long mqttReceivedDelta = mqttReceived - lastMqttReceived.getAndSet(mqttReceived);
        long parseSuccessDelta = parseSuccess - lastParseSuccess.getAndSet(parseSuccess);
        long parseFailureDelta = parseFailure - lastParseFailure.getAndSet(parseFailure);
        long influxSuccessDelta = influxSuccess - lastInfluxSuccess.getAndSet(influxSuccess);
        long influxFailureDelta = influxFailure - lastInfluxFailure.getAndSet(influxFailure);
        long influxBypassDelta = influxBypass - lastInfluxBypass.getAndSet(influxBypass);
        long redisSuccessDelta = redisSuccess - lastRedisSuccess.getAndSet(redisSuccess);
        long redisFailureDelta = redisFailure - lastRedisFailure.getAndSet(redisFailure);
        long processingFailureDelta = processingFailure - lastProcessingFailure.getAndSet(processingFailure);
        long overallPipelineSuccessDelta =
                overallPipelineSuccess - lastOverallPipelineSuccess.getAndSet(overallPipelineSuccess);
        long corePipelineSuccessDelta =
                corePipelineSuccess - lastCorePipelineSuccess.getAndSet(corePipelineSuccess);
        long duplicateDroppedDelta = duplicateDropped - lastDuplicateDropped.getAndSet(duplicateDropped);
        long parseDeadLetterDelta = parseDeadLetter - lastParseDeadLetter.getAndSet(parseDeadLetter);
        long storageReplayCandidateDelta =
                storageReplayCandidate - lastStorageReplayCandidate.getAndSet(storageReplayCandidate);
        long controlReplayCandidateDelta =
                controlReplayCandidate - lastControlReplayCandidate.getAndSet(controlReplayCandidate);

        if (mqttReceivedDelta == 0
                && parseSuccessDelta == 0
                && parseFailureDelta == 0
                && influxSuccessDelta == 0
                && influxFailureDelta == 0
                && influxBypassDelta == 0
                && redisSuccessDelta == 0
                && redisFailureDelta == 0
                && processingFailureDelta == 0
                && overallPipelineSuccessDelta == 0
                && corePipelineSuccessDelta == 0
                && duplicateDroppedDelta == 0
                && parseDeadLetterDelta == 0
                && storageReplayCandidateDelta == 0
                && controlReplayCandidateDelta == 0) {
            return;
        }

        log.info("[INGEST-METRICS/1s] recv={}, parseOk={}, parseFail={}, influxOk={}, influxFail={}, influxBypass={}, redisOk={}, redisFail={}, procFail={}, overallOk={}, coreOk={}, dupDrop={}, parseDlq={}, storageReplay={}, controlReplay={}, inFlight={} | totals recv={}, parseOk={}, parseFail={}, influxOk={}, influxFail={}, influxBypass={}, redisOk={}, redisFail={}, procFail={}, overallOk={}, coreOk={}, dupDrop={}, parseDlq={}, storageReplay={}, controlReplay={}",
                mqttReceivedDelta,
                parseSuccessDelta,
                parseFailureDelta,
                influxSuccessDelta,
                influxFailureDelta,
                influxBypassDelta,
                redisSuccessDelta,
                redisFailureDelta,
                processingFailureDelta,
                overallPipelineSuccessDelta,
                corePipelineSuccessDelta,
                duplicateDroppedDelta,
                parseDeadLetterDelta,
                storageReplayCandidateDelta,
                controlReplayCandidateDelta,
                inFlight.get(),
                mqttReceived,
                parseSuccess,
                parseFailure,
                influxSuccess,
                influxFailure,
                influxBypass,
                redisSuccess,
                redisFailure,
                processingFailure,
                overallPipelineSuccess,
                corePipelineSuccess,
                duplicateDropped,
                parseDeadLetter,
                storageReplayCandidate,
                controlReplayCandidate);
    }
}
