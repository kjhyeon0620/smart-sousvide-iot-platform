package com.iot.IoT.service;

import com.iot.IoT.dto.DevDashboardDemoCommandRequest;
import com.iot.IoT.dto.DevDashboardDemoScenarioRequest;
import com.iot.IoT.dto.DevDashboardDemoStateRequest;
import com.iot.IoT.entity.Device;
import com.iot.IoT.entity.DeviceCommandStatus;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.port.HeartbeatPort;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesPort;
import com.iot.IoT.repository.DeviceCommandRepository;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.InvalidDeviceQueryException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DevDashboardDemoServiceTest {

    private DeviceRepository deviceRepository;
    private DeviceCommandRepository deviceCommandRepository;
    private HeartbeatPort heartbeatPort;
    private TemperatureTimeSeriesPort temperatureTimeSeriesPort;
    private StringRedisTemplate redisTemplate;
    private SetOperations<String, String> setOperations;
    private DevDashboardDemoService service;

    @BeforeEach
    void setUp() {
        deviceRepository = mock(DeviceRepository.class);
        deviceCommandRepository = mock(DeviceCommandRepository.class);
        heartbeatPort = mock(HeartbeatPort.class);
        temperatureTimeSeriesPort = mock(TemperatureTimeSeriesPort.class);
        redisTemplate = mock(StringRedisTemplate.class);
        setOperations = mock(SetOperations.class);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("devices:active")).thenReturn(Set.of());
        when(deviceRepository.save(any(Device.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service = new DevDashboardDemoService(
                deviceRepository,
                deviceCommandRepository,
                heartbeatPort,
                temperatureTimeSeriesPort,
                redisTemplate
        );
    }

    @Test
    @DisplayName("createScenario should create prefixed devices with telemetry samples")
    void createScenario() {
        when(deviceRepository.findByDeviceId("SV-DEMO-001")).thenReturn(Optional.empty());
        when(deviceRepository.findByDeviceId("SV-DEMO-002")).thenReturn(Optional.empty());

        var response = service.createScenario(new DevDashboardDemoScenarioRequest(2, "heating", BigDecimal.valueOf(64.5)));

        assertThat(response.createdDevices()).isEqualTo(2);
        assertThat(response.onlineDevices()).isEqualTo(2);
        assertThat(response.temperaturePoints()).isEqualTo(72);
        assertThat(response.devices()).extracting("deviceId").containsExactly("SV-DEMO-001", "SV-DEMO-002");
        verify(deviceRepository).deleteByDeviceIdStartingWith("SV-DEMO-");
        verify(deviceCommandRepository).deleteByDeviceIdStartingWith("SV-DEMO-");
        verify(heartbeatPort, times(2)).updateLastSeen(any(), any());
        verify(temperatureTimeSeriesPort, times(72)).save(any(), any());
    }

    @Test
    @DisplayName("updateDevice should reject non-demo device IDs")
    void updateDevice_rejectsNonDemo() {
        var request = new DevDashboardDemoStateRequest(true, true, BigDecimal.valueOf(61.2), BigDecimal.valueOf(64.5), DeviceState.HEATING);

        assertThatThrownBy(() -> service.updateDevice("SV-001", request))
                .isInstanceOf(InvalidDeviceQueryException.class)
                .hasMessageContaining("SV-DEMO-");

        verify(deviceRepository, never()).findByDeviceId("SV-001");
    }

    @Test
    @DisplayName("addCommandSamples should reject non-demo device IDs")
    void addCommandSamples_rejectsNonDemo() {
        var request = new DevDashboardDemoCommandRequest(
                java.util.List.of(DeviceCommandStatus.SENT, DeviceCommandStatus.EXPIRED)
        );

        assertThatThrownBy(() -> service.addCommandSamples("SV-001", request))
                .isInstanceOf(InvalidDeviceQueryException.class)
                .hasMessageContaining("SV-DEMO-");

        verify(deviceRepository, never()).findByDeviceId("SV-001");
    }

    @Test
    @DisplayName("clear should delete only demo-prefixed MySQL and Redis data")
    void clear() {
        when(setOperations.members("devices:active")).thenReturn(Set.of("SV-DEMO-001", "SV-001"));
        when(redisTemplate.keys("device:SV-DEMO-*:lastSeen")).thenReturn(Set.of("device:SV-DEMO-002:lastSeen"));
        when(redisTemplate.keys("watchdog:SV-DEMO-*:offline-notified")).thenReturn(Set.of("watchdog:SV-DEMO-002:offline-notified"));

        service.clear();

        verify(deviceCommandRepository).deleteByDeviceIdStartingWith("SV-DEMO-");
        verify(deviceRepository).deleteByDeviceIdStartingWith("SV-DEMO-");
        verify(setOperations).remove("devices:active", "SV-DEMO-001");
        verify(setOperations, never()).remove("devices:active", "SV-001");
        verify(redisTemplate).delete("device:SV-DEMO-001:lastSeen");
        verify(redisTemplate).delete("watchdog:SV-DEMO-001:offline-notified");
        verify(redisTemplate).delete(eq(Set.of("device:SV-DEMO-002:lastSeen")));
        verify(redisTemplate).delete(eq(Set.of("watchdog:SV-DEMO-002:offline-notified")));
    }
}
