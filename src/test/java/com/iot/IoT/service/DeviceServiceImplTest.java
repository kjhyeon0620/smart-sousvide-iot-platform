package com.iot.IoT.service;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.DuplicateDeviceException;
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
    private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() {
        deviceRepository = Mockito.mock(DeviceRepository.class);
        deviceService = new DeviceServiceImpl(deviceRepository);
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
