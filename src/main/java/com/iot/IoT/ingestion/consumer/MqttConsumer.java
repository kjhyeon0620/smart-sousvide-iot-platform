package com.iot.IoT.ingestion.consumer;

import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.exception.InvalidMqttPayloadException;
import com.iot.IoT.ingestion.parser.MqttPayloadParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.mqtt.support.MqttHeaders;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MqttConsumer {

    private static final Logger log = LoggerFactory.getLogger(MqttConsumer.class);

    private final MqttPayloadParser mqttPayloadParser;

    public MqttConsumer(MqttPayloadParser mqttPayloadParser) {
        this.mqttPayloadParser = mqttPayloadParser;
    }

    @ServiceActivator(inputChannel = "mqttInputChannel")
    public void handle(Message<?> message) {
        String topic = (String) message.getHeaders().get(MqttHeaders.RECEIVED_TOPIC);
        String rawPayload = payloadAsString(message.getPayload());

        try {
            DeviceStatusMessage statusMessage = mqttPayloadParser.parseDeviceStatus(rawPayload);
            log.info("[MQTT] Parsed device status. topic={}, deviceId={}, temp={}, targetTemp={}, state={}",
                    topic,
                    statusMessage.deviceId(),
                    statusMessage.temp(),
                    statusMessage.targetTemp(),
                    statusMessage.state());
        } catch (InvalidMqttPayloadException ex) {
            log.warn("[MQTT] Invalid payload. topic={}, payload={}, reason={}",
                    topic,
                    rawPayload,
                    ex.getMessage());
        }
    }

    private String payloadAsString(Object payload) {
        if (payload instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return String.valueOf(payload);
    }
}
