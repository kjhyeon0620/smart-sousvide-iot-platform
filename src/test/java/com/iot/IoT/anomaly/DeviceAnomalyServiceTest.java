package com.iot.IoT.anomaly;

import com.iot.IoT.entity.AnomalyStatus;
import com.iot.IoT.entity.Device;
import com.iot.IoT.entity.DeviceAnomaly;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.repository.DeviceAnomalyRepository;
import com.iot.IoT.repository.DeviceRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceAnomalyServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-20T00:00:00Z");
    private static final String DEVICE_ID = "SV-001";

    private DeviceAnomalyRepository anomalyRepository;
    private DeviceRepository deviceRepository;
    private SimpleMeterRegistry meterRegistry;
    private DeviceAnomalyService service;

    @BeforeEach
    void setUp() {
        anomalyRepository = mock(DeviceAnomalyRepository.class);
        deviceRepository = mock(DeviceRepository.class);
        meterRegistry = new SimpleMeterRegistry();
        service = createService(true);
        when(anomalyRepository.findFirstByDeviceIdAndTypeAndStatus(anyString(), any(), any()))
                .thenReturn(Optional.empty());
        Device device = spy(new Device());
        when(device.getId()).thenReturn(1L);
        when(deviceRepository.findByDeviceId(DEVICE_ID)).thenReturn(Optional.of(device));
    }

    private DeviceAnomalyService createService(boolean enabled) {
        // stuck after 60s; other rules effectively off for these tests
        AnomalyDetector detector = new AnomalyDetector(
                new BigDecimal("1.0"), 3600, 3600, new BigDecimal("0.0"), 60, new BigDecimal("0.3"));
        return new DeviceAnomalyService(
                detector, anomalyRepository, deviceRepository, new AnomalyMetricsRecorder(meterRegistry), enabled);
    }

    private void send(String deviceId, int seconds, String temp, DeviceState state) {
        service.evaluate(new DeviceStatusMessage(deviceId, new BigDecimal(temp), state, new BigDecimal("60.0")),
                T0.plusSeconds(seconds));
    }

    private double counter(String name, String type) {
        return meterRegistry.counter(name, "type", type).count();
    }

    private double failures() {
        return meterRegistry.counter("iot.anomaly.evaluation.failures.total").count();
    }

    @Test
    @DisplayName("Opens once, keeps it open, then resolves when the condition clears")
    void openAndResolve() {
        send(DEVICE_ID, 0, "60.0", DeviceState.HOLDING);
        send(DEVICE_ID, 60, "60.0", DeviceState.HOLDING);
        send(DEVICE_ID, 70, "60.0", DeviceState.HOLDING);

        ArgumentCaptor<DeviceAnomaly> saved = ArgumentCaptor.forClass(DeviceAnomaly.class);
        verify(anomalyRepository, times(1)).save(saved.capture());
        assertThat(saved.getValue().getType()).isEqualTo(AnomalyType.SENSOR_STUCK);
        assertThat(saved.getValue().getStatus()).isEqualTo(AnomalyStatus.OPEN);
        assertThat(saved.getValue().getDevicePk()).isEqualTo(1L);
        assertThat(saved.getValue().getDetectedAt()).isEqualTo(T0.plusSeconds(60));
        assertThat(counter("iot.anomaly.opened.total", "SENSOR_STUCK")).isEqualTo(1.0);

        DeviceAnomaly open = saved.getValue();
        when(anomalyRepository.findFirstByDeviceIdAndTypeAndStatus(DEVICE_ID, AnomalyType.SENSOR_STUCK, AnomalyStatus.OPEN))
                .thenReturn(Optional.of(open));
        send(DEVICE_ID, 80, "60.1", DeviceState.HOLDING);

        assertThat(open.getStatus()).isEqualTo(AnomalyStatus.RESOLVED);
        assertThat(open.getResolvedAt()).isEqualTo(T0.plusSeconds(80));
        assertThat(counter("iot.anomaly.resolved.total", "SENSOR_STUCK")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Unregistered device is tracked in memory without storing or repeated lookups")
    void unregisteredDevice() {
        when(deviceRepository.findByDeviceId("SV-404")).thenReturn(Optional.empty());

        send("SV-404", 0, "60.0", DeviceState.HOLDING);
        send("SV-404", 60, "60.0", DeviceState.HOLDING);
        send("SV-404", 70, "60.0", DeviceState.HOLDING);
        send("SV-404", 80, "60.0", DeviceState.HOLDING);

        verify(anomalyRepository, never()).save(any());
        verify(deviceRepository, times(1)).findByDeviceId("SV-404");
        assertThat(counter("iot.anomaly.opened.total", "SENSOR_STUCK")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Storage failure records a metric and retries on the next message")
    void storageFailureRetries() {
        when(anomalyRepository.save(any()))
                .thenThrow(new RuntimeException("mysql down"))
                .thenAnswer(invocation -> invocation.getArgument(0));

        send(DEVICE_ID, 0, "60.0", DeviceState.HOLDING);
        send(DEVICE_ID, 60, "60.0", DeviceState.HOLDING);
        assertThat(failures()).isEqualTo(1.0);
        assertThat(counter("iot.anomaly.opened.total", "SENSOR_STUCK")).isZero();

        send(DEVICE_ID, 70, "60.0", DeviceState.HOLDING);
        verify(anomalyRepository, times(2)).save(any());
        assertThat(counter("iot.anomaly.opened.total", "SENSOR_STUCK")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("OPEN rows from a previous run are resolved when the condition no longer holds")
    void preloadedOpenIsResolved() {
        DeviceAnomaly stale = new DeviceAnomaly();
        stale.setDeviceId(DEVICE_ID);
        stale.setType(AnomalyType.TARGET_DEVIATION);
        stale.setStatus(AnomalyStatus.OPEN);
        when(anomalyRepository.findByStatus(AnomalyStatus.OPEN)).thenReturn(List.of(stale));
        when(anomalyRepository.findFirstByDeviceIdAndTypeAndStatus(DEVICE_ID, AnomalyType.TARGET_DEVIATION, AnomalyStatus.OPEN))
                .thenReturn(Optional.of(stale));

        service.preloadOpenAnomalies();
        send(DEVICE_ID, 0, "60.0", DeviceState.HOLDING);

        assertThat(stale.getStatus()).isEqualTo(AnomalyStatus.RESOLVED);
        assertThat(counter("iot.anomaly.resolved.total", "TARGET_DEVIATION")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("OFF resolves open anomalies")
    void offResolves() {
        send(DEVICE_ID, 0, "60.0", DeviceState.HOLDING);
        send(DEVICE_ID, 60, "60.0", DeviceState.HOLDING);
        send(DEVICE_ID, 70, "60.0", DeviceState.OFF);

        assertThat(counter("iot.anomaly.resolved.total", "SENSOR_STUCK")).isEqualTo(1.0);
    }

    @Test
    @DisplayName("Disabled service does nothing")
    void disabled() {
        service = createService(false);

        send(DEVICE_ID, 0, "60.0", DeviceState.HOLDING);
        send(DEVICE_ID, 60, "60.0", DeviceState.HOLDING);

        verify(anomalyRepository, never()).findFirstByDeviceIdAndTypeAndStatus(any(), any(), eq(AnomalyStatus.OPEN));
        verify(anomalyRepository, never()).save(any());
    }
}
