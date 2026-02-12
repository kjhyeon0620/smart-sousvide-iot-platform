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
        String payload = "{\"deviceId\":\"SV-001\",\"temp\":65.5,\"targetTemp\":70.0,\"state\":\"HEATING\"}";

        DeviceStatusMessage result = parser.parseDeviceStatus(payload);

        assertEquals("SV-001", result.deviceId());
        assertEquals(new BigDecimal("65.5"), result.temp());
        assertEquals(new BigDecimal("70.0"), result.targetTemp());
        assertEquals(DeviceState.HEATING, result.state());
    }

    @Test
    @DisplayName("Missing required field should fail validation")
    void parseDeviceStatus_missingField() {
        String payload = "{\"deviceId\":\"SV-001\",\"temp\":65.5,\"state\":\"HEATING\"}";

        assertThrows(InvalidMqttPayloadException.class, () -> parser.parseDeviceStatus(payload));
    }

    @Test
    @DisplayName("Unknown state should fail JSON parsing")
    void parseDeviceStatus_unknownEnum() {
        String payload = "{\"deviceId\":\"SV-001\",\"temp\":65.5,\"targetTemp\":70.0,\"state\":\"RUNNING\"}";

        assertThrows(InvalidMqttPayloadException.class, () -> parser.parseDeviceStatus(payload));
    }

    @Test
    @DisplayName("Invalid number type should fail JSON parsing")
    void parseDeviceStatus_invalidNumberType() {
        String payload = "{\"deviceId\":\"SV-001\",\"temp\":\"hot\",\"targetTemp\":70.0,\"state\":\"HEATING\"}";

        assertThrows(InvalidMqttPayloadException.class, () -> parser.parseDeviceStatus(payload));
    }

    @Test
    @DisplayName("Unknown field should fail JSON parsing")
    void parseDeviceStatus_unknownField() {
        String payload = "{\"deviceId\":\"SV-001\",\"temp\":65.5,\"targetTemp\":70.0,\"state\":\"HEATING\",\"foo\":\"bar\"}";

        assertThrows(InvalidMqttPayloadException.class, () -> parser.parseDeviceStatus(payload));
    }
}
