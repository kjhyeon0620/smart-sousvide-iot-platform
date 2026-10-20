package com.iot.IoT.anomaly;

import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AnomalyDetectorTest {

    private static final Instant T0 = Instant.parse("2026-10-20T00:00:00Z");

    private AnomalyDetector detector;
    private DeviceAnomalyState state;

    @BeforeEach
    void setUp() {
        // deviation 1.0C for 180s, stall 600s with min rise 0.0C, stuck 600s, deadband 0.3C
        detector = new AnomalyDetector(
                new BigDecimal("1.0"), 180,
                600, new BigDecimal("0.0"),
                600,
                new BigDecimal("0.3")
        );
        state = null;
    }

    private Set<AnomalyType> feed(int secondsFromStart, String temp, String target, DeviceState deviceState) {
        DeviceStatusMessage message = new DeviceStatusMessage(
                "SV-001", new BigDecimal(temp), deviceState, new BigDecimal(target));
        AnomalyEvaluation evaluation = detector.evaluate(state, message, T0.plusSeconds(secondsFromStart));
        state = evaluation.state();
        return evaluation.active();
    }

    @Test
    @DisplayName("Steady holding near target raises nothing")
    void steadyHolding() {
        for (int s = 0; s <= 1200; s += 10) {
            String temp = (s / 10) % 2 == 0 ? "60.0" : "60.1";
            assertThat(feed(s, temp, "60.0", DeviceState.HOLDING)).isEmpty();
        }
    }

    @Test
    @DisplayName("Deviation opens only after the duration and closes when back in range")
    void targetDeviation() {
        feed(0, "60.0", "60.0", DeviceState.HOLDING);           // reached target
        assertThat(feed(10, "58.5", "60.0", DeviceState.HOLDING)).isEmpty();
        assertThat(feed(189, "58.4", "60.0", DeviceState.HOLDING)).isEmpty();
        assertThat(feed(190, "58.3", "60.0", DeviceState.HOLDING)).containsExactly(AnomalyType.TARGET_DEVIATION);
        assertThat(feed(200, "59.5", "60.0", DeviceState.HOLDING)).isEmpty();
    }

    @Test
    @DisplayName("Deviation at exactly the threshold does not count")
    void targetDeviationBoundary() {
        feed(0, "60.0", "60.0", DeviceState.HOLDING);
        feed(10, "59.0", "60.0", DeviceState.HOLDING);
        assertThat(feed(400, "59.0", "60.0", DeviceState.HOLDING)).doesNotContain(AnomalyType.TARGET_DEVIATION);
    }

    @Test
    @DisplayName("Preheat far below target is not a deviation")
    void preheatIsNotDeviation() {
        for (int s = 0; s <= 1800; s += 60) {
            String temp = String.valueOf(20 + s / 60);           // rises 1C per minute
            assertThat(feed(s, temp, "60.0", DeviceState.HEATING)).doesNotContain(AnomalyType.TARGET_DEVIATION);
        }
    }

    @Test
    @DisplayName("Target change resets the reached flag so the new preheat is not a deviation")
    void targetChangeResetsReached() {
        feed(0, "60.0", "60.0", DeviceState.HOLDING);
        feed(10, "60.0", "65.0", DeviceState.HEATING);
        assertThat(feed(400, "61.0", "65.0", DeviceState.HEATING)).doesNotContain(AnomalyType.TARGET_DEVIATION);
    }

    @Test
    @DisplayName("Heating without temperature rise for the duration is a stall")
    void heatingStall() {
        feed(0, "50.0", "60.0", DeviceState.HEATING);
        assertThat(feed(300, "50.0", "60.0", DeviceState.HEATING)).isEmpty();
        assertThat(feed(600, "49.9", "60.0", DeviceState.HEATING)).containsExactly(AnomalyType.HEATING_STALL);
        assertThat(feed(660, "49.9", "60.0", DeviceState.HEATING)).containsExactly(AnomalyType.HEATING_STALL);
        assertThat(feed(700, "50.5", "60.0", DeviceState.HEATING)).isEmpty();
    }

    @Test
    @DisplayName("Healthy heating slides the checkpoint so a later stall is still caught")
    void heatingStallAfterHealthyHeating() {
        feed(0, "30.0", "60.0", DeviceState.HEATING);
        assertThat(feed(600, "40.0", "60.0", DeviceState.HEATING)).isEmpty();   // checkpoint moves to 40.0
        assertThat(feed(1200, "39.8", "60.0", DeviceState.HEATING)).containsExactly(AnomalyType.HEATING_STALL);
    }

    @Test
    @DisplayName("Leaving HEATING clears the stall")
    void stallClearsWhenNotHeating() {
        feed(0, "50.0", "60.0", DeviceState.HEATING);
        feed(600, "50.0", "60.0", DeviceState.HEATING);
        assertThat(feed(610, "50.1", "60.0", DeviceState.HOLDING)).doesNotContain(AnomalyType.HEATING_STALL);
    }

    @Test
    @DisplayName("Unchanged readings for the duration mean a stuck sensor")
    void sensorStuck() {
        feed(0, "60.0", "60.0", DeviceState.HOLDING);
        assertThat(feed(599, "60.0", "60.0", DeviceState.HOLDING)).isEmpty();
        assertThat(feed(600, "60.0", "60.0", DeviceState.HOLDING)).containsExactly(AnomalyType.SENSOR_STUCK);
        assertThat(feed(610, "60.1", "60.0", DeviceState.HOLDING)).isEmpty();
    }

    @Test
    @DisplayName("Numerically equal readings with different scale count as unchanged")
    void sensorStuckIgnoresScale() {
        feed(0, "60.0", "60.0", DeviceState.HOLDING);
        assertThat(feed(600, "60.00", "60.0", DeviceState.HOLDING)).containsExactly(AnomalyType.SENSOR_STUCK);
    }

    @Test
    @DisplayName("OFF resets every window and reports nothing")
    void offResets() {
        feed(0, "60.0", "60.0", DeviceState.HOLDING);
        assertThat(feed(600, "60.0", "60.0", DeviceState.HOLDING)).containsExactly(AnomalyType.SENSOR_STUCK);

        AnomalyEvaluation off = detector.evaluate(state,
                new DeviceStatusMessage("SV-001", new BigDecimal("60.0"), DeviceState.OFF, new BigDecimal("60.0")),
                T0.plusSeconds(610));
        assertThat(off.state()).isNull();
        assertThat(off.active()).isEmpty();

        state = null;
        assertThat(feed(620, "60.0", "60.0", DeviceState.HOLDING)).isEmpty();   // stuck window restarts
    }
}
