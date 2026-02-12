package com.iot.IoT.control;

import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class ControlDecisionEngine {

    private final BigDecimal deadband;

    public ControlDecisionEngine(@Value("${control.deadband:0.3}") BigDecimal deadband) {
        this.deadband = deadband;
    }

    public ControlAction decide(DeviceStatusMessage message) {
        if (message.state() == DeviceState.OFF) {
            return ControlAction.HEAT_OFF;
        }

        BigDecimal lowerBound = message.targetTemp().subtract(deadband);
        BigDecimal upperBound = message.targetTemp().add(deadband);

        if (message.temp().compareTo(lowerBound) < 0) {
            return ControlAction.HEAT_ON;
        }
        if (message.temp().compareTo(upperBound) > 0) {
            return ControlAction.HEAT_OFF;
        }
        return ControlAction.HOLD;
    }
}
