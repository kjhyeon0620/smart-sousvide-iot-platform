package com.iot.IoT.service;

import com.iot.IoT.control.ControlAction;
import com.iot.IoT.dto.DeviceCommandResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.entity.DeviceCommand;
import com.iot.IoT.entity.DeviceCommandStatus;
import com.iot.IoT.mqtt.port.DeviceCommandPublisherPort;
import com.iot.IoT.repository.DeviceCommandRepository;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceCommandNotFoundException;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.metrics.DownlinkMetricsRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class DeviceCommandService {

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final DeviceCommandPublisherPort deviceCommandPublisherPort;
    private final DownlinkMetricsRecorder downlinkMetricsRecorder;
    private final Duration commandRetryInterval;
    private final Duration commandAckTimeout;
    private final Duration autoControlDedupWindow;
    private final int commandMaxRetries;

    public DeviceCommandService(
            DeviceRepository deviceRepository,
            DeviceCommandRepository deviceCommandRepository,
            DeviceCommandPublisherPort deviceCommandPublisherPort,
            DownlinkMetricsRecorder downlinkMetricsRecorder,
            @Value("${downlink.retry-interval-seconds:10}") long commandRetryIntervalSeconds,
            @Value("${downlink.ack-timeout-seconds:30}") long commandAckTimeoutSeconds,
            @Value("${control.auto-command-dedup-window-seconds:30}") long autoControlDedupWindowSeconds,
            @Value("${downlink.max-retries:3}") int commandMaxRetries
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandRepository = deviceCommandRepository;
        this.deviceCommandPublisherPort = deviceCommandPublisherPort;
        this.downlinkMetricsRecorder = downlinkMetricsRecorder;
        this.commandRetryInterval = Duration.ofSeconds(commandRetryIntervalSeconds);
        this.commandAckTimeout = Duration.ofSeconds(commandAckTimeoutSeconds);
        this.autoControlDedupWindow = Duration.ofSeconds(Math.max(autoControlDedupWindowSeconds, 1));
        this.commandMaxRetries = Math.max(commandMaxRetries, 0);
    }

    @Transactional
    public DeviceCommandResponse sendCommand(Long id, ControlAction commandType, String idempotencyKey) {
        return sendCommand(findEntity(id), commandType, idempotencyKey);
    }

    @Transactional
    public DeviceCommandResponse acknowledgeCommand(Long id, Long commandId) {
        Device device = findEntity(id);
        DeviceCommand command = deviceCommandRepository.findByIdAndDevicePk(commandId, device.getId())
                .orElseThrow(() -> new DeviceCommandNotFoundException(device.getId(), commandId));

        if (command.getStatus() != DeviceCommandStatus.ACKED) {
            command.setStatus(DeviceCommandStatus.ACKED);
            command.setAckedAt(Instant.now());
            command.setNextRetryAt(null);
            command.setErrorMessage(null);
            command = deviceCommandRepository.save(command);
            downlinkMetricsRecorder.recordAcked();
        }
        return toCommandResponse(command);
    }

    @Transactional
    public void sendAutoControlCommand(String deviceId, ControlAction commandType, Instant decidedAt) {
        if (commandType == ControlAction.HOLD) {
            return;
        }

        String normalizedDeviceId = normalize(deviceId);
        if (normalizedDeviceId == null || normalizedDeviceId.isBlank()) {
            return;
        }

        Optional<Device> device = deviceRepository.findByDeviceId(normalizedDeviceId);
        if (device.isEmpty() || !device.get().isEnabled()) {
            return;
        }

        String idempotencyKey = buildAutoControlIdempotencyKey(normalizedDeviceId, commandType, decidedAt);
        sendCommand(device.get(), commandType, idempotencyKey);
    }

    private DeviceCommandResponse sendCommand(Device device, ControlAction commandType, String idempotencyKey) {
        Optional<DeviceCommand> existing = deviceCommandRepository.findByDevicePkAndIdempotencyKey(
                device.getId(),
                idempotencyKey
        );
        if (existing.isPresent()) {
            downlinkMetricsRecorder.recordIdempotencyHit();
            return toCommandResponse(existing.get());
        }

        String topic = buildCommandTopic(device.getDeviceId());

        DeviceCommand command = new DeviceCommand();
        command.setDevicePk(device.getId());
        command.setDeviceId(device.getDeviceId());
        command.setIdempotencyKey(idempotencyKey);
        command.setCommandType(commandType);
        command.setStatus(DeviceCommandStatus.PENDING);
        command.setRetryCount(0);
        command.setMaxRetries(commandMaxRetries);
        command.setTopic(topic);
        command.setPayload("");

        DeviceCommand created = deviceCommandRepository.save(command);
        if (created.getId() == null) {
            throw new IllegalStateException("Device command id was not generated");
        }
        Instant requestedAt = created.getRequestedAt() == null ? Instant.now() : created.getRequestedAt();
        created.setRequestedAt(requestedAt);
        created.setExpireAt(requestedAt.plus(commandAckTimeout));
        String payload = buildCommandPayload(created.getId(), commandType, requestedAt);
        created.setPayload(payload);

        try {
            deviceCommandPublisherPort.publish(topic, payload);
            Instant sentAt = Instant.now();
            created.setStatus(DeviceCommandStatus.SENT);
            created.setSentAt(sentAt);
            created.setNextRetryAt(sentAt.plus(commandRetryInterval));
            created.setErrorMessage(null);
            downlinkMetricsRecorder.recordSent();
        } catch (RuntimeException ex) {
            created.setStatus(DeviceCommandStatus.FAILED);
            created.setSentAt(null);
            created.setNextRetryAt(null);
            created.setErrorMessage(ex.getMessage());
            downlinkMetricsRecorder.recordFailed();
        }

        return toCommandResponse(deviceCommandRepository.save(created));
    }

    private Device findEntity(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
    }

    private DeviceCommandResponse toCommandResponse(DeviceCommand command) {
        return new DeviceCommandResponse(
                command.getId(),
                command.getDevicePk(),
                command.getDeviceId(),
                command.getCommandType(),
                command.getStatus(),
                command.getTopic(),
                command.getPayload(),
                command.getRequestedAt(),
                command.getSentAt(),
                command.getErrorMessage()
        );
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private String buildCommandTopic(String deviceId) {
        return "devices/%s/cmd".formatted(deviceId);
    }

    private String buildCommandPayload(Long commandId, ControlAction commandType, Instant requestedAt) {
        return """
                {"commandId":%d,"commandType":"%s","requestedAt":"%s"}
                """.formatted(commandId, commandType.name(), requestedAt.toString()).trim();
    }

    private String buildAutoControlIdempotencyKey(String deviceId, ControlAction commandType, Instant decidedAt) {
        long dedupBucket = decidedAt.toEpochMilli() / autoControlDedupWindow.toMillis();
        return "auto:%s:%s:%d".formatted(deviceId, commandType.name(), dedupBucket);
    }
}
