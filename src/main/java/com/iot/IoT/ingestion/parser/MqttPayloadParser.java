package com.iot.IoT.ingestion.parser;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.exception.InvalidMqttPayloadException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.stream.Collectors;

@Component
public class MqttPayloadParser {

    private final ObjectMapper objectMapper;
    private final Validator validator;

    public MqttPayloadParser(ObjectMapper objectMapper, Validator validator) {
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    public DeviceStatusMessage parseDeviceStatus(String payload) {
        try {
            DeviceStatusMessage message = objectMapper.readValue(payload, DeviceStatusMessage.class);
            validate(message);
            return message;
        } catch (JsonProcessingException e) {
            throw new InvalidMqttPayloadException("MQTT payload is not valid JSON for DeviceStatusMessage", e);
        }
    }

    private void validate(DeviceStatusMessage message) {
        Set<ConstraintViolation<DeviceStatusMessage>> violations = validator.validate(message);
        if (!violations.isEmpty()) {
            String details = violations.stream()
                    .map(v -> v.getPropertyPath() + " " + v.getMessage())
                    .collect(Collectors.joining(", "));
            throw new InvalidMqttPayloadException("MQTT payload validation failed: " + details);
        }
    }
}
