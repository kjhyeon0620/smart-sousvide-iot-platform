package com.iot.IoT.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DevicePayloadFactoryTest {

    private final DevicePayloadFactory payloadFactory = new DevicePayloadFactory(new ObjectMapper());

    @Test
    void create_shouldFollowPayloadContract() throws Exception {
        String json = payloadFactory.create("SV-001", 60.5, 65.0);

        Map<?, ?> map = new ObjectMapper().readValue(json, Map.class);

        assertEquals("SV-001", map.get("deviceId"));
        assertEquals(60.5, map.get("temp"));
        assertEquals(65.0, map.get("targetTemp"));
        assertEquals("HEATING", map.get("state"));
    }

    @Test
    void create_shouldBeHoldingWithinDeadband() throws Exception {
        String json = payloadFactory.create("SV-001", 65.1, 65.0);

        Map<?, ?> map = new ObjectMapper().readValue(json, Map.class);

        assertEquals("HOLDING", map.get("state"));
    }
}
