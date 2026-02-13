package com.iot.IoT.loadtest;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class DevicePayloadFactory {

    private static final double DEADBAND = 0.3;

    private final ObjectMapper objectMapper;

    public DevicePayloadFactory(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String create(String deviceId, double temp, double targetTemp) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("deviceId", deviceId);
        payload.put("temp", round1(temp));
        payload.put("targetTemp", round1(targetTemp));
        payload.put("state", resolveState(temp, targetTemp));

        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to create payload", e);
        }
    }

    public double nextTemp(double currentTemp, double targetTemp) {
        double direction = currentTemp < targetTemp ? 0.15 : -0.12;
        double noise = ThreadLocalRandom.current().nextDouble(-0.08, 0.08);
        return currentTemp + direction + noise;
    }

    private String resolveState(double temp, double targetTemp) {
        if (temp < targetTemp - DEADBAND) {
            return "HEATING";
        }
        if (temp > targetTemp + DEADBAND) {
            return "OFF";
        }
        return "HOLDING";
    }

    private static double round1(double value) {
        return Math.round(value * 10.0) / 10.0;
    }
}
