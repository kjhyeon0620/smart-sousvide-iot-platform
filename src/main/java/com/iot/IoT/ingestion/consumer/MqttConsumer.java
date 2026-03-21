package com.iot.IoT.ingestion.consumer;

import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.exception.InvalidMqttPayloadException;
import com.iot.IoT.ingestion.metrics.IngestionMetricsCollector;
import com.iot.IoT.ingestion.parser.MqttPayloadParser;
import com.iot.IoT.ingestion.service.DeviceIngestionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MqttConsumer {

    private static final Logger log = LoggerFactory.getLogger(MqttConsumer.class);

    private final MqttPayloadParser mqttPayloadParser;
    private final DeviceIngestionService deviceIngestionService;
    private final IngestionMetricsCollector ingestionMetricsCollector;
    private final Duration duplicateSuppressWindow;
    private final Map<String, Instant> recentPayloads = new ConcurrentHashMap<>();

    public MqttConsumer(
            MqttPayloadParser mqttPayloadParser,
            DeviceIngestionService deviceIngestionService,
            IngestionMetricsCollector ingestionMetricsCollector,
            @org.springframework.beans.factory.annotation.Value("${ingestion.duplicate-suppress-window-seconds:2}")
            long duplicateSuppressWindowSeconds
    ) {
        this.mqttPayloadParser = mqttPayloadParser;
        this.deviceIngestionService = deviceIngestionService;
        this.ingestionMetricsCollector = ingestionMetricsCollector;
        this.duplicateSuppressWindow = Duration.ofSeconds(Math.max(duplicateSuppressWindowSeconds, 1));
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        long startedAtNanos = System.nanoTime();
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String rawPayload = payloadAsString(message.getPayload());
        ingestionMetricsCollector.recordMqttReceived();
        ingestionMetricsCollector.incrementInFlight();

        try {
            DeviceStatusMessage statusMessage = mqttPayloadParser.parseDeviceStatus(rawPayload);
            if (isSuppressedDuplicate(statusMessage, rawPayload)) {
                ingestionMetricsCollector.recordDuplicateDropped();
                log.warn("[RELIABILITY] Duplicate telemetry suppressed. topic={}, deviceId={}, windowSeconds={}",
                        topic,
                        statusMessage.deviceId(),
                        duplicateSuppressWindow.toSeconds());
                return;
            }
            ingestionMetricsCollector.recordParseSuccess();
            log.info("[MQTT] Parsed device status. topic={}, deviceId={}, temp={}, targetTemp={}, state={}",
                    topic,
                    statusMessage.deviceId(),
                    statusMessage.temp(),
                    statusMessage.targetTemp(),
                    statusMessage.state());
            deviceIngestionService.ingest(statusMessage);
        } catch (InvalidMqttPayloadException ex) {
            ingestionMetricsCollector.recordParseFailure();
            ingestionMetricsCollector.recordParseDeadLetter();
            log.warn("[MQTT] Invalid payload. topic={}, payload={}, reason={}",
                    topic,
                    rawPayload,
                    ex.getMessage());
            log.warn("[RELIABILITY] Parse failure classified. topic={}, failureType={}, replayable={}",
                    topic,
                    ex.failureType(),
                    ex.failureType().replayable());
        } catch (RuntimeException ex) {
            ingestionMetricsCollector.recordProcessingFailure();
            ingestionMetricsCollector.recordStorageReplayCandidate();
            log.error("[MQTT] Processing failed. topic={}, payload={}", topic, rawPayload, ex);
            log.error("[RELIABILITY] Runtime processing failure classified. topic={}, replayable=true", topic, ex);
        } finally {
            ingestionMetricsCollector.decrementInFlight();
            ingestionMetricsCollector.recordProcessingLatency(System.nanoTime() - startedAtNanos);
        }
    }

    private boolean isSuppressedDuplicate(DeviceStatusMessage message, String rawPayload) {
        Instant now = Instant.now();
        Instant threshold = now.minus(duplicateSuppressWindow);
        recentPayloads.entrySet().removeIf(entry -> entry.getValue().isBefore(threshold));

        String dedupKey = message.deviceId() + ":" + rawPayload;
        Instant previous = recentPayloads.put(dedupKey, now);
        return previous != null && !previous.isBefore(threshold);
    }

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }
}
