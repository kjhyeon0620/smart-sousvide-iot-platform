package com.iot.IoT.service;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DeviceControlPolicyResponse;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperaturePointResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesQueryPort;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.DuplicateDeviceException;
import com.iot.IoT.service.exception.InvalidDeviceQueryException;
import com.iot.IoT.watchdog.port.WatchdogStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DeviceServiceImplTest {

    private DeviceRepository deviceRepository;
    private WatchdogStatePort watchdogStatePort;
    private TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort;
    private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() {
        deviceRepository = Mockito.mock(DeviceRepository.class);
        watchdogStatePort = Mockito.mock(WatchdogStatePort.class);
        temperatureTimeSeriesQueryPort = Mockito.mock(TemperatureTimeSeriesQueryPort.class);
        deviceService = new DeviceServiceImpl(
                deviceRepository,
                watchdogStatePort,
                temperatureTimeSeriesQueryPort,
                120
        );
    }

    @Test
    @DisplayName("Should create device with enabled=true by default")
    void create_defaultEnabled() {
        CreateDeviceRequest request = new CreateDeviceRequest(" SV-001 ", " test device ", null);
        when(deviceRepository.existsByDeviceId("SV-001")).thenReturn(false);
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> {
            Device device = invocation.getArgument(0);
            setIdAndTimestamps(device, 1L);
            return device;
        });

        DeviceResponse response = deviceService.create(request);

        assertEquals(1L, response.id());
        assertEquals("SV-001", response.deviceId());
        assertEquals("test device", response.name());
        assertEquals(true, response.enabled());
        assertNotNull(response.createdAt());
        assertNotNull(response.updatedAt());

        ArgumentCaptor<Device> captor = ArgumentCaptor.forClass(Device.class);
        verify(deviceRepository, times(1)).save(captor.capture());
        Device saved = captor.getValue();
        assertEquals("SV-001", saved.getDeviceId());
        assertEquals("test device", saved.getName());
        assertEquals(true, saved.isEnabled());
    }

    @Test
    @DisplayName("Should throw duplicate exception when deviceId already exists")
    void create_duplicate() {
        when(deviceRepository.existsByDeviceId("SV-001")).thenReturn(true);

        assertThrows(DuplicateDeviceException.class,
                () -> deviceService.create(new CreateDeviceRequest("SV-001", null, true)));

        verify(deviceRepository, never()).save(any(Device.class));
    }

    @Test
    @DisplayName("Should throw not found when device does not exist")
    void findById_notFound() {
        when(deviceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(DeviceNotFoundException.class, () -> deviceService.findById(99L));
    }

    @Test
    @DisplayName("Should update enabled flag")
    void updateEnabled_success() {
        Device device = sampleDevice(10L, "SV-010", true);
        when(deviceRepository.findById(10L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceResponse response = deviceService.updateEnabled(10L, false);

        assertEquals(false, response.enabled());
        verify(deviceRepository, times(1)).save(eq(device));
    }

    @Test
    @DisplayName("Should return paged response")
    void findAll_success() {
        Device d1 = sampleDevice(1L, "SV-001", true);
        Device d2 = sampleDevice(2L, "SV-002", false);
        when(deviceRepository.findAll(any(PageRequest.class)))
                .thenReturn(new PageImpl<>(List.of(d1, d2), PageRequest.of(0, 2), 2));

        DevicePageResponse response = deviceService.findAll(0, 2);

        assertEquals(2, response.items().size());
        assertEquals(2, response.totalElements());
        assertEquals(1, response.totalPages());
        assertEquals(0, response.page());
        assertEquals(2, response.size());
    }

    @Test
    @DisplayName("Should return composed status with online true and latest telemetry")
    void getStatus_success() {
        Device device = sampleDevice(1L, "SV-001", true);
        Instant now = Instant.now();
        DeviceTemperaturePointResponse latest = new DeviceTemperaturePointResponse(
                now.minusSeconds(2),
                java.math.BigDecimal.valueOf(60.1),
                java.math.BigDecimal.valueOf(65.0),
                DeviceState.HEATING
        );
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(watchdogStatePort.findLastSeen("SV-001")).thenReturn(Optional.of(now.minusSeconds(10)));
        when(temperatureTimeSeriesQueryPort.findLatest("SV-001")).thenReturn(Optional.of(latest));

        DeviceStatusResponse response = deviceService.getStatus(1L);

        assertEquals("SV-001", response.deviceId());
        assertEquals(true, response.online());
        assertEquals(DeviceState.HEATING, response.latestState());
    }

    @Test
    @DisplayName("Should return temperature series")
    void getTemperatures_success() {
        Device device = sampleDevice(1L, "SV-001", true);
        Instant from = Instant.parse("2026-03-01T00:00:00Z");
        Instant to = Instant.parse("2026-03-01T00:10:00Z");
        List<DeviceTemperaturePointResponse> points = List.of(
                new DeviceTemperaturePointResponse(
                        Instant.parse("2026-03-01T00:01:00Z"),
                        java.math.BigDecimal.valueOf(60.0),
                        java.math.BigDecimal.valueOf(65.0),
                        DeviceState.HEATING
                )
        );
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(temperatureTimeSeriesQueryPort.findRange("SV-001", from, to, 50)).thenReturn(points);

        DeviceTemperatureSeriesResponse response = deviceService.getTemperatures(1L, from, to, 50);

        assertEquals("SV-001", response.deviceId());
        assertEquals(1, response.items().size());
        assertEquals(50, response.limit());
    }

    @Test
    @DisplayName("Should throw invalid query when from is after to")
    void getTemperatures_invalidRange() {
        Device device = sampleDevice(1L, "SV-001", true);
        Instant from = Instant.parse("2026-03-01T00:10:00Z");
        Instant to = Instant.parse("2026-03-01T00:00:00Z");
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        assertThrows(InvalidDeviceQueryException.class,
                () -> deviceService.getTemperatures(1L, from, to, 10));
    }

    @Test
    @DisplayName("Should throw invalid query when limit is out of range")
    void getTemperatures_invalidLimit() {
        Device device = sampleDevice(1L, "SV-001", true);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        assertThrows(InvalidDeviceQueryException.class,
                () -> deviceService.getTemperatures(1L, Instant.now().minusSeconds(60), Instant.now(), 9999));
    }

    @Test
    @DisplayName("Should return control policy")
    void getControlPolicy_success() {
        Device device = sampleDevice(1L, "SV-001", true);
        device.setControlTargetTemp(java.math.BigDecimal.valueOf(65.0));
        device.setControlHysteresis(java.math.BigDecimal.valueOf(0.3));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        DeviceControlPolicyResponse response = deviceService.getControlPolicy(1L);

        assertEquals("SV-001", response.deviceId());
        assertEquals(java.math.BigDecimal.valueOf(65.0), response.targetTemp());
        assertEquals(java.math.BigDecimal.valueOf(0.3), response.hysteresis());
    }

    @Test
    @DisplayName("Should update control policy")
    void updateControlPolicy_success() {
        Device device = sampleDevice(1L, "SV-001", true);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceControlPolicyResponse response = deviceService.updateControlPolicy(
                1L,
                java.math.BigDecimal.valueOf(64.5),
                java.math.BigDecimal.valueOf(0.5)
        );

        assertEquals(java.math.BigDecimal.valueOf(64.5), response.targetTemp());
        assertEquals(java.math.BigDecimal.valueOf(0.5), response.hysteresis());
        verify(deviceRepository, times(1)).save(eq(device));
    }

    private Device sampleDevice(Long id, String deviceId, boolean enabled) {
        Device device = new Device();
        device.setDeviceId(deviceId);
        device.setEnabled(enabled);
        device.setName("name-" + id);
        setIdAndTimestamps(device, id);
        return device;
    }

    private void setIdAndTimestamps(Device device, Long id) {
        try {
            var idField = Device.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(device, id);

            Instant now = Instant.now();
            var createdAtField = Device.class.getDeclaredField("createdAt");
            createdAtField.setAccessible(true);
            createdAtField.set(device, now);

            var updatedAtField = Device.class.getDeclaredField("updatedAt");
            updatedAtField.setAccessible(true);
            updatedAtField.set(device, now);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
