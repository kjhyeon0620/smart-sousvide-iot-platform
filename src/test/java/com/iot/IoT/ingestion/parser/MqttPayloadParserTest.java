package com.iot.IoT.ingestion.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.exception.InvalidMqttPayloadException;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MqttPayloadParserTest {

    private MqttPayloadParser parser;

    @BeforeEach
    void setUp() {
        ObjectMapper objectMapper = new ObjectMapper();
        Validator validator = Validation.buildDefaultValidatorFactory().getValidator();
        parser = new MqttPayloadParser(objectMapper, validator);
    }

    @Test
    @DisplayName("Valid JSON payload should map to DeviceStatusMessage")
    void parseDeviceStatus_success() {
        String payload = "{\"temp\":65.5,\"state\":\"ON\",\"target_temp\":70.0}";

        DeviceStatusMessage result = parser.parseDeviceStatus(payload);

        assertEquals(new BigDecimal("65.5"), result.temp());
        assertEquals(new BigDecimal("70.0"), result.targetTemp());
        assertEquals(DeviceState.ON, result.state());
    }

    @Test
    @DisplayName("Missing field should fail validation")
    void parseDeviceStatus_missingField() {
        String payload = "{\"temp\":65.5,\"state\":\"ON\"}";

        assertThrows(InvalidMqttPayloadException.class, () -> parser.parseDeviceStatus(payload));
    }

    @Test
    @DisplayName("Unknown state should fail JSON parsing")
    void parseDeviceStatus_unknownEnum() {
        String payload = "{\"temp\":65.5,\"state\":\"RUNNING\",\"target_temp\":70.0}";

        assertThrows(InvalidMqttPayloadException.class, () -> parser.parseDeviceStatus(payload));
    }
}
