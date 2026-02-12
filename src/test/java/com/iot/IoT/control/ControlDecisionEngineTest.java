package com.iot.IoT.control;

import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ControlDecisionEngineTest {

    private final ControlDecisionEngine engine = new ControlDecisionEngine(new BigDecimal("0.3"));

    @Test
    @DisplayName("Should return HEAT_ON when temp is below lower bound")
    void decide_heatOn() {
        DeviceStatusMessage message = message("60.0", "65.0", DeviceState.HEATING);

        ControlAction result = engine.decide(message);

        assertEquals(ControlAction.HEAT_ON, result);
    }

    @Test
    @DisplayName("Should return HEAT_OFF when temp is above upper bound")
    void decide_heatOff() {
        DeviceStatusMessage message = message("65.5", "65.0", DeviceState.HEATING);

        ControlAction result = engine.decide(message);

        assertEquals(ControlAction.HEAT_OFF, result);
    }

    @Test
    @DisplayName("Should return HOLD when temp is within deadband")
    void decide_holdInsideDeadband() {
        DeviceStatusMessage message = message("65.1", "65.0", DeviceState.HOLDING);

        ControlAction result = engine.decide(message);

        assertEquals(ControlAction.HOLD, result);
    }

    @Test
    @DisplayName("Should return HEAT_OFF when state is OFF")
    void decide_offState() {
        DeviceStatusMessage message = message("60.0", "65.0", DeviceState.OFF);

        ControlAction result = engine.decide(message);

        assertEquals(ControlAction.HEAT_OFF, result);
    }

    @Test
    @DisplayName("Should return HOLD at deadband boundary")
    void decide_deadbandBoundary() {
        DeviceStatusMessage lowerBoundary = message("64.7", "65.0", DeviceState.HEATING);
        DeviceStatusMessage upperBoundary = message("65.3", "65.0", DeviceState.HEATING);

        assertEquals(ControlAction.HOLD, engine.decide(lowerBoundary));
        assertEquals(ControlAction.HOLD, engine.decide(upperBoundary));
    }

    private DeviceStatusMessage message(String temp, String targetTemp, DeviceState state) {
        return new DeviceStatusMessage(
                "SV-001",
                new BigDecimal(temp),
                state,
                new BigDecimal(targetTemp)
        );
    }
}
