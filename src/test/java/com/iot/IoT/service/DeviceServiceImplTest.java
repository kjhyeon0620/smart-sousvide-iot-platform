package com.iot.IoT.service;

import com.iot.IoT.control.ControlAction;
import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DeviceCommandPageResponse;
import com.iot.IoT.dto.DeviceCommandResponse;
import com.iot.IoT.dto.DeviceControlPolicyResponse;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperaturePointResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.entity.DeviceCommand;
import com.iot.IoT.entity.DeviceCommandStatus;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesQueryPort;
import com.iot.IoT.mqtt.port.DeviceCommandPublisherPort;
import com.iot.IoT.repository.DeviceCommandRepository;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceCommandNotFoundException;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.DuplicateDeviceException;
import com.iot.IoT.service.exception.InvalidDeviceQueryException;
import com.iot.IoT.service.metrics.DownlinkMetricsRecorder;
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
    private DeviceCommandRepository deviceCommandRepository;
    private WatchdogStatePort watchdogStatePort;
    private TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort;
    private DeviceCommandPublisherPort deviceCommandPublisherPort;
    private DownlinkMetricsRecorder downlinkMetricsRecorder;
    private DeviceServiceImpl deviceService;

    @BeforeEach
    void setUp() {
        deviceRepository = Mockito.mock(DeviceRepository.class);
        deviceCommandRepository = Mockito.mock(DeviceCommandRepository.class);
        watchdogStatePort = Mockito.mock(WatchdogStatePort.class);
        temperatureTimeSeriesQueryPort = Mockito.mock(TemperatureTimeSeriesQueryPort.class);
        deviceCommandPublisherPort = Mockito.mock(DeviceCommandPublisherPort.class);
        downlinkMetricsRecorder = Mockito.mock(DownlinkMetricsRecorder.class);
        deviceService = new DeviceServiceImpl(
                deviceRepository,
                deviceCommandRepository,
                watchdogStatePort,
                temperatureTimeSeriesQueryPort,
                deviceCommandPublisherPort,
                downlinkMetricsRecorder,
                120,
                10,
                30,
                30,
                3
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

    @Test
    @DisplayName("Should publish command and store SENT history")
    void sendCommand_success() {
        Device device = sampleDevice(1L, "SV-001", true);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCommandRepository.save(any(DeviceCommand.class)))
                .thenAnswer(invocation -> {
                    DeviceCommand command = invocation.getArgument(0);
                    if (command.getId() == null) {
                        setId(command, 10L);
                    }
                    if (command.getRequestedAt() == null) {
                        command.setRequestedAt(Instant.parse("2026-03-02T00:00:00Z"));
                    }
                    return command;
                });

        when(deviceCommandRepository.findByDevicePkAndIdempotencyKey(1L, "idem-1")).thenReturn(Optional.empty());

        DeviceCommandResponse response = deviceService.sendCommand(1L, ControlAction.HEAT_ON, "idem-1");

        assertEquals(10L, response.commandId());
        assertEquals(DeviceCommandStatus.SENT, response.status());
        verify(deviceCommandPublisherPort, times(1))
                .publish(eq("devices/SV-001/cmd"), any(String.class));
    }

    @Test
    @DisplayName("Should store FAILED history when publish fails")
    void sendCommand_publishFail() {
        Device device = sampleDevice(1L, "SV-001", true);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCommandRepository.save(any(DeviceCommand.class)))
                .thenAnswer(invocation -> {
                    DeviceCommand command = invocation.getArgument(0);
                    if (command.getId() == null) {
                        setId(command, 11L);
                    }
                    if (command.getRequestedAt() == null) {
                        command.setRequestedAt(Instant.parse("2026-03-02T00:00:00Z"));
                    }
                    return command;
                });
        Mockito.doThrow(new RuntimeException("publish failed"))
                .when(deviceCommandPublisherPort)
                .publish(eq("devices/SV-001/cmd"), any(String.class));

        when(deviceCommandRepository.findByDevicePkAndIdempotencyKey(1L, "idem-2")).thenReturn(Optional.empty());

        DeviceCommandResponse response = deviceService.sendCommand(1L, ControlAction.HEAT_OFF, "idem-2");

        assertEquals(11L, response.commandId());
        assertEquals(DeviceCommandStatus.FAILED, response.status());
        assertEquals("publish failed", response.errorMessage());
    }

    @Test
    @DisplayName("Should return command history with limit")
    void getCommands_success() {
        Device device = sampleDevice(1L, "SV-001", true);
        DeviceCommand history = new DeviceCommand();
        history.setDevicePk(1L);
        history.setDeviceId("SV-001");
        history.setCommandType(ControlAction.HOLD);
        history.setStatus(DeviceCommandStatus.SENT);
        history.setTopic("devices/SV-001/cmd");
        history.setPayload("{\"commandId\":99}");
        history.setRequestedAt(Instant.parse("2026-03-02T00:00:00Z"));
        history.setSentAt(Instant.parse("2026-03-02T00:00:01Z"));
        setId(history, 99L);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCommandRepository.findByDevicePkOrderByRequestedAtDesc(eq(1L), any(PageRequest.class)))
                .thenReturn(List.of(history));

        DeviceCommandPageResponse response = deviceService.getCommands(1L, 20);

        assertEquals("SV-001", response.deviceId());
        assertEquals(1, response.items().size());
        assertEquals(ControlAction.HOLD, response.items().get(0).commandType());
    }

    @Test
    @DisplayName("Should return existing command when idempotency key already exists")
    void sendCommand_idempotencyHit() {
        Device device = sampleDevice(1L, "SV-001", true);
        DeviceCommand existing = new DeviceCommand();
        existing.setDevicePk(1L);
        existing.setDeviceId("SV-001");
        existing.setCommandType(ControlAction.HEAT_ON);
        existing.setStatus(DeviceCommandStatus.SENT);
        existing.setTopic("devices/SV-001/cmd");
        existing.setPayload("{\"commandId\":77}");
        existing.setIdempotencyKey("idem-hit");
        existing.setRequestedAt(Instant.parse("2026-03-02T00:00:00Z"));
        setId(existing, 77L);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCommandRepository.findByDevicePkAndIdempotencyKey(1L, "idem-hit"))
                .thenReturn(Optional.of(existing));

        DeviceCommandResponse response = deviceService.sendCommand(1L, ControlAction.HEAT_ON, "idem-hit");

        assertEquals(77L, response.commandId());
        verify(deviceCommandPublisherPort, never()).publish(any(String.class), any(String.class));
    }

    @Test
    @DisplayName("Should acknowledge command")
    void acknowledgeCommand_success() {
        Device device = sampleDevice(1L, "SV-001", true);
        DeviceCommand command = new DeviceCommand();
        command.setDevicePk(1L);
        command.setDeviceId("SV-001");
        command.setCommandType(ControlAction.HEAT_ON);
        command.setStatus(DeviceCommandStatus.SENT);
        command.setTopic("devices/SV-001/cmd");
        command.setPayload("{\"commandId\":12}");
        command.setIdempotencyKey("ack-1");
        command.setRequestedAt(Instant.parse("2026-03-02T00:00:00Z"));
        setId(command, 12L);

        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCommandRepository.findByIdAndDevicePk(12L, 1L)).thenReturn(Optional.of(command));
        when(deviceCommandRepository.save(any(DeviceCommand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        DeviceCommandResponse response = deviceService.acknowledgeCommand(1L, 12L);

        assertEquals(DeviceCommandStatus.ACKED, response.status());
    }

    @Test
    @DisplayName("Should throw when acknowledging missing command")
    void acknowledgeCommand_notFound() {
        Device device = sampleDevice(1L, "SV-001", true);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCommandRepository.findByIdAndDevicePk(12L, 1L)).thenReturn(Optional.empty());

        assertThrows(DeviceCommandNotFoundException.class, () -> deviceService.acknowledgeCommand(1L, 12L));
    }

    @Test
    @DisplayName("Should publish auto control command for enabled device")
    void sendAutoControlCommand_enabledDevice() {
        Device device = sampleDevice(1L, "SV-001", true);
        when(deviceRepository.findByDeviceId("SV-001")).thenReturn(Optional.of(device));
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));
        when(deviceCommandRepository.findByDevicePkAndIdempotencyKey(eq(1L), any())).thenReturn(Optional.empty());
        when(deviceCommandRepository.save(any(DeviceCommand.class)))
                .thenAnswer(invocation -> {
                    DeviceCommand command = invocation.getArgument(0);
                    if (command.getId() == null) {
                        setId(command, 21L);
                    }
                    if (command.getRequestedAt() == null) {
                        command.setRequestedAt(Instant.parse("2026-03-02T00:00:00Z"));
                    }
                    return command;
                });

        deviceService.sendAutoControlCommand(
                "SV-001",
                ControlAction.HEAT_ON,
                Instant.parse("2026-03-02T00:00:05Z")
        );

        verify(deviceCommandPublisherPort, times(1)).publish(eq("devices/SV-001/cmd"), any(String.class));
    }

    @Test
    @DisplayName("Should skip auto control command for disabled device")
    void sendAutoControlCommand_disabledDevice() {
        Device device = sampleDevice(1L, "SV-001", false);
        when(deviceRepository.findByDeviceId("SV-001")).thenReturn(Optional.of(device));

        deviceService.sendAutoControlCommand(
                "SV-001",
                ControlAction.HEAT_ON,
                Instant.parse("2026-03-02T00:00:05Z")
        );

        verify(deviceCommandPublisherPort, never()).publish(any(String.class), any(String.class));
        verify(deviceCommandRepository, never()).save(any(DeviceCommand.class));
    }

    @Test
    @DisplayName("Should skip auto control hold action")
    void sendAutoControlCommand_holdAction() {
        deviceService.sendAutoControlCommand(
                "SV-001",
                ControlAction.HOLD,
                Instant.parse("2026-03-02T00:00:05Z")
        );

        verify(deviceRepository, never()).findByDeviceId(any());
        verify(deviceCommandPublisherPort, never()).publish(any(String.class), any(String.class));
    }

    @Test
    @DisplayName("Should expire command when ack timeout is reached")
    void processCommandReliability_expire() {
        DeviceCommand command = new DeviceCommand();
        command.setStatus(DeviceCommandStatus.SENT);
        command.setTopic("devices/SV-001/cmd");
        command.setPayload("{\"commandId\":1}");
        command.setRetryCount(0);
        command.setMaxRetries(3);
        command.setExpireAt(Instant.now().minusSeconds(1));
        command.setRequestedAt(Instant.now().minusSeconds(10));
        command.setIdempotencyKey("exp-1");

        when(deviceCommandRepository.findByStatusIn(any())).thenReturn(List.of(command));
        when(deviceCommandRepository.save(any(DeviceCommand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deviceService.processCommandReliability();

        assertEquals(DeviceCommandStatus.EXPIRED, command.getStatus());
    }

    @Test
    @DisplayName("Should retry publish when nextRetryAt is due")
    void processCommandReliability_retry() {
        DeviceCommand command = new DeviceCommand();
        command.setStatus(DeviceCommandStatus.SENT);
        command.setTopic("devices/SV-001/cmd");
        command.setPayload("{\"commandId\":2}");
        command.setRetryCount(0);
        command.setMaxRetries(3);
        command.setRequestedAt(Instant.now().minusSeconds(10));
        command.setExpireAt(Instant.now().plusSeconds(20));
        command.setNextRetryAt(Instant.now().minusSeconds(1));
        command.setIdempotencyKey("retry-1");

        when(deviceCommandRepository.findByStatusIn(any())).thenReturn(List.of(command));
        when(deviceCommandRepository.save(any(DeviceCommand.class))).thenAnswer(invocation -> invocation.getArgument(0));

        deviceService.processCommandReliability();

        verify(deviceCommandPublisherPort, times(1)).publish(eq("devices/SV-001/cmd"), eq("{\"commandId\":2}"));
        assertEquals(1, command.getRetryCount());
        assertEquals(DeviceCommandStatus.SENT, command.getStatus());
    }

    @Test
    @DisplayName("Should throw invalid query when command limit is out of range")
    void getCommands_invalidLimit() {
        Device device = sampleDevice(1L, "SV-001", true);
        when(deviceRepository.findById(1L)).thenReturn(Optional.of(device));

        assertThrows(InvalidDeviceQueryException.class,
                () -> deviceService.getCommands(1L, 999));
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

    private void setId(DeviceCommand command, Long id) {
        try {
            var idField = DeviceCommand.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(command, id);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
