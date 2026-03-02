package com.iot.IoT.ingestion.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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

    private final AtomicLong lastMqttReceived = new AtomicLong(0);
    private final AtomicLong lastParseSuccess = new AtomicLong(0);
    private final AtomicLong lastParseFailure = new AtomicLong(0);
    private final AtomicLong lastInfluxSuccess = new AtomicLong(0);
    private final AtomicLong lastInfluxFailure = new AtomicLong(0);
    private final AtomicLong lastInfluxBypass = new AtomicLong(0);
    private final AtomicLong lastRedisSuccess = new AtomicLong(0);
    private final AtomicLong lastRedisFailure = new AtomicLong(0);

    private final Counter mqttReceivedCounter;
    private final Counter parseSuccessCounter;
    private final Counter parseFailureCounter;
    private final Counter influxSuccessCounter;
    private final Counter influxFailureCounter;
    private final Counter influxBypassCounter;
    private final Counter redisSuccessCounter;
    private final Counter redisFailureCounter;

    public IngestionMetricsCollector(MeterRegistry meterRegistry) {
        this.mqttReceivedCounter = meterRegistry.counter("iot.ingestion.mqtt.received.total");
        this.parseSuccessCounter = meterRegistry.counter("iot.ingestion.parse.success.total");
        this.parseFailureCounter = meterRegistry.counter("iot.ingestion.parse.failure.total");
        this.influxSuccessCounter = meterRegistry.counter("iot.ingestion.influx.success.total");
        this.influxFailureCounter = meterRegistry.counter("iot.ingestion.influx.failure.total");
        this.influxBypassCounter = meterRegistry.counter("iot.ingestion.influx.bypass.total");
        this.redisSuccessCounter = meterRegistry.counter("iot.ingestion.redis.success.total");
        this.redisFailureCounter = meterRegistry.counter("iot.ingestion.redis.failure.total");
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

        long mqttReceivedDelta = mqttReceived - lastMqttReceived.getAndSet(mqttReceived);
        long parseSuccessDelta = parseSuccess - lastParseSuccess.getAndSet(parseSuccess);
        long parseFailureDelta = parseFailure - lastParseFailure.getAndSet(parseFailure);
        long influxSuccessDelta = influxSuccess - lastInfluxSuccess.getAndSet(influxSuccess);
        long influxFailureDelta = influxFailure - lastInfluxFailure.getAndSet(influxFailure);
        long influxBypassDelta = influxBypass - lastInfluxBypass.getAndSet(influxBypass);
        long redisSuccessDelta = redisSuccess - lastRedisSuccess.getAndSet(redisSuccess);
        long redisFailureDelta = redisFailure - lastRedisFailure.getAndSet(redisFailure);

        if (mqttReceivedDelta == 0
                && parseSuccessDelta == 0
                && parseFailureDelta == 0
                && influxSuccessDelta == 0
                && influxFailureDelta == 0
                && influxBypassDelta == 0
                && redisSuccessDelta == 0
                && redisFailureDelta == 0) {
            return;
        }

        log.info("[INGEST-METRICS/1s] recv={}, parseOk={}, parseFail={}, influxOk={}, influxFail={}, influxBypass={}, redisOk={}, redisFail={} | totals recv={}, parseOk={}, parseFail={}, influxOk={}, influxFail={}, influxBypass={}, redisOk={}, redisFail={}",
                mqttReceivedDelta,
                parseSuccessDelta,
                parseFailureDelta,
                influxSuccessDelta,
                influxFailureDelta,
                influxBypassDelta,
                redisSuccessDelta,
                redisFailureDelta,
                mqttReceived,
                parseSuccess,
                parseFailure,
                influxSuccess,
                influxFailure,
                influxBypass,
                redisSuccess,
                redisFailure);
    }
}
