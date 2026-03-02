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
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesQueryPort;
import com.iot.IoT.mqtt.port.DeviceCommandPublisherPort;
import com.iot.IoT.repository.DeviceCommandRepository;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceCommandNotFoundException;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.DuplicateDeviceException;
import com.iot.IoT.service.exception.InvalidDeviceQueryException;
import com.iot.IoT.watchdog.port.WatchdogStatePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceServiceImpl implements DeviceService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_TEMP_LIMIT = 1;
    private static final int MAX_TEMP_LIMIT = 500;
    private static final int DEFAULT_TEMP_LIMIT = 200;
    private static final int DEFAULT_COMMAND_LIMIT = 20;
    private static final int MIN_COMMAND_LIMIT = 1;
    private static final int MAX_COMMAND_LIMIT = 100;
    private static final Duration DEFAULT_RANGE = Duration.ofHours(1);
    private static final EnumSet<DeviceCommandStatus> RELIABILITY_TARGET_STATUSES =
            EnumSet.of(DeviceCommandStatus.SENT, DeviceCommandStatus.PENDING);

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final WatchdogStatePort watchdogStatePort;
    private final TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort;
    private final DeviceCommandPublisherPort deviceCommandPublisherPort;
    private final Duration heartbeatTtl;
    private final Duration commandRetryInterval;
    private final Duration commandAckTimeout;
    private final int commandMaxRetries;

    public DeviceServiceImpl(
            DeviceRepository deviceRepository,
            DeviceCommandRepository deviceCommandRepository,
            WatchdogStatePort watchdogStatePort,
            TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort,
            DeviceCommandPublisherPort deviceCommandPublisherPort,
            @Value("${ingestion.heartbeat-ttl-seconds:120}") long heartbeatTtlSeconds,
            @Value("${downlink.retry-interval-seconds:10}") long commandRetryIntervalSeconds,
            @Value("${downlink.ack-timeout-seconds:30}") long commandAckTimeoutSeconds,
            @Value("${downlink.max-retries:3}") int commandMaxRetries
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandRepository = deviceCommandRepository;
        this.watchdogStatePort = watchdogStatePort;
        this.temperatureTimeSeriesQueryPort = temperatureTimeSeriesQueryPort;
        this.deviceCommandPublisherPort = deviceCommandPublisherPort;
        this.heartbeatTtl = Duration.ofSeconds(heartbeatTtlSeconds);
        this.commandRetryInterval = Duration.ofSeconds(commandRetryIntervalSeconds);
        this.commandAckTimeout = Duration.ofSeconds(commandAckTimeoutSeconds);
        this.commandMaxRetries = Math.max(commandMaxRetries, 0);
    }

    @Override
    @Transactional
    public DeviceResponse create(CreateDeviceRequest request) {
        String normalizedDeviceId = normalize(request.deviceId());
        if (deviceRepository.existsByDeviceId(normalizedDeviceId)) {
            throw new DuplicateDeviceException(normalizedDeviceId);
        }

        Device device = new Device();
        device.setDeviceId(normalizedDeviceId);
        device.setName(normalizeNullable(request.name()));
        device.setEnabled(request.enabled() == null || request.enabled());

        Device saved = deviceRepository.save(device);
        return toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceResponse findById(Long id) {
        Device device = findEntity(id);
        return toResponse(device);
    }

    @Override
    @Transactional(readOnly = true)
    public DevicePageResponse findAll(int page, int size) {
        int normalizedPage = Math.max(page, 0);
        int normalizedSize = clamp(size, MIN_PAGE_SIZE, MAX_PAGE_SIZE);

        Page<Device> result = deviceRepository.findAll(
                PageRequest.of(normalizedPage, normalizedSize, Sort.by(Sort.Direction.ASC, "id"))
        );

        return new DevicePageResponse(
                result.getContent().stream().map(this::toResponse).toList(),
                result.getTotalElements(),
                result.getTotalPages(),
                normalizedPage,
                normalizedSize
        );
    }

    @Override
    @Transactional
    public DeviceResponse updateEnabled(Long id, boolean enabled) {
        Device device = findEntity(id);
        device.setEnabled(enabled);
        Device updated = deviceRepository.save(device);
        return toResponse(updated);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceStatusResponse getStatus(Long id) {
        Device device = findEntity(id);
        Optional<Instant> lastSeen = watchdogStatePort.findLastSeen(device.getDeviceId());
        Optional<DeviceTemperaturePointResponse> latest = temperatureTimeSeriesQueryPort.findLatest(device.getDeviceId());

        return new DeviceStatusResponse(
                device.getId(),
                device.getDeviceId(),
                device.getName(),
                device.isEnabled(),
                lastSeen.orElse(null),
                isOnline(lastSeen),
                latest.map(DeviceTemperaturePointResponse::temp).orElse(null),
                latest.map(DeviceTemperaturePointResponse::targetTemp).orElse(null),
                latest.map(DeviceTemperaturePointResponse::state).orElse(null),
                latest.map(DeviceTemperaturePointResponse::occurredAt).orElse(null)
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceTemperatureSeriesResponse getTemperatures(Long id, Instant from, Instant to, int limit) {
        Device device = findEntity(id);
        Range range = resolveRange(from, to);
        int normalizedLimit = normalizeTempLimit(limit);
        List<DeviceTemperaturePointResponse> items = temperatureTimeSeriesQueryPort.findRange(
                device.getDeviceId(),
                range.from(),
                range.to(),
                normalizedLimit
        );

        return new DeviceTemperatureSeriesResponse(
                device.getId(),
                device.getDeviceId(),
                range.from(),
                range.to(),
                normalizedLimit,
                items
        );
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceControlPolicyResponse getControlPolicy(Long id) {
        Device device = findEntity(id);
        return toControlPolicyResponse(device);
    }

    @Override
    @Transactional
    public DeviceControlPolicyResponse updateControlPolicy(Long id, BigDecimal targetTemp, BigDecimal hysteresis) {
        Device device = findEntity(id);
        device.setControlTargetTemp(targetTemp);
        device.setControlHysteresis(hysteresis);
        Device updated = deviceRepository.save(device);
        return toControlPolicyResponse(updated);
    }

    @Override
    @Transactional
    public DeviceCommandResponse sendCommand(Long id, ControlAction commandType, String idempotencyKey) {
        Device device = findEntity(id);
        Optional<DeviceCommand> existing = deviceCommandRepository.findByDevicePkAndIdempotencyKey(
                device.getId(),
                idempotencyKey
        );
        if (existing.isPresent()) {
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
        } catch (RuntimeException ex) {
            created.setStatus(DeviceCommandStatus.FAILED);
            created.setSentAt(null);
            created.setNextRetryAt(null);
            created.setErrorMessage(ex.getMessage());
        }

        DeviceCommand saved = deviceCommandRepository.save(created);
        return toCommandResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public DeviceCommandPageResponse getCommands(Long id, int limit) {
        Device device = findEntity(id);
        int normalizedLimit = normalizeCommandLimit(limit);
        List<DeviceCommandResponse> items = deviceCommandRepository.findByDevicePkOrderByRequestedAtDesc(
                        device.getId(),
                        PageRequest.of(0, normalizedLimit)
                ).stream()
                .map(this::toCommandResponse)
                .toList();
        return new DeviceCommandPageResponse(device.getId(), device.getDeviceId(), normalizedLimit, items);
    }

    @Override
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
        }
        return toCommandResponse(command);
    }

    @Override
    @Transactional
    public void processCommandReliability() {
        Instant now = Instant.now();
        List<DeviceCommand> targets = deviceCommandRepository.findByStatusIn(RELIABILITY_TARGET_STATUSES);
        for (DeviceCommand command : targets) {
            if (command.getStatus() == DeviceCommandStatus.ACKED
                    || command.getStatus() == DeviceCommandStatus.EXPIRED
                    || command.getStatus() == DeviceCommandStatus.FAILED) {
                continue;
            }
            if (command.getExpireAt() != null && now.isAfter(command.getExpireAt())) {
                command.setStatus(DeviceCommandStatus.EXPIRED);
                command.setNextRetryAt(null);
                command.setErrorMessage("ack timeout expired");
                deviceCommandRepository.save(command);
                continue;
            }
            if (command.getStatus() == DeviceCommandStatus.PENDING) {
                retryPublish(command, now);
                continue;
            }
            if (command.getStatus() == DeviceCommandStatus.SENT
                    && command.getNextRetryAt() != null
                    && !now.isBefore(command.getNextRetryAt())
                    && command.getRetryCount() < command.getMaxRetries()) {
                retryPublish(command, now);
            }
        }
    }

    private Device findEntity(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
    }

    private DeviceResponse toResponse(Device device) {
        return new DeviceResponse(
                device.getId(),
                device.getDeviceId(),
                device.getName(),
                device.isEnabled(),
                device.getCreatedAt(),
                device.getUpdatedAt()
        );
    }

    private DeviceControlPolicyResponse toControlPolicyResponse(Device device) {
        return new DeviceControlPolicyResponse(
                device.getId(),
                device.getDeviceId(),
                device.getControlTargetTemp(),
                device.getControlHysteresis(),
                device.getUpdatedAt()
        );
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

    private void retryPublish(DeviceCommand command, Instant now) {
        try {
            deviceCommandPublisherPort.publish(command.getTopic(), command.getPayload());
            command.setStatus(DeviceCommandStatus.SENT);
            command.setSentAt(now);
            command.setRetryCount(command.getRetryCount() + 1);
            command.setNextRetryAt(now.plus(commandRetryInterval));
            command.setErrorMessage(null);
        } catch (RuntimeException ex) {
            int nextRetry = command.getRetryCount() + 1;
            command.setRetryCount(nextRetry);
            if (nextRetry >= command.getMaxRetries()) {
                command.setStatus(DeviceCommandStatus.FAILED);
                command.setNextRetryAt(null);
            } else {
                command.setStatus(DeviceCommandStatus.SENT);
                command.setNextRetryAt(now.plus(commandRetryInterval));
            }
            command.setErrorMessage(ex.getMessage());
        }
        deviceCommandRepository.save(command);
    }

    private static String normalize(String value) {
        return value == null ? null : value.trim();
    }

    private static String normalizeNullable(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isOnline(Optional<Instant> lastSeen) {
        if (lastSeen.isEmpty()) {
            return false;
        }
        Instant onlineThreshold = Instant.now().minus(heartbeatTtl);
        return !lastSeen.get().isBefore(onlineThreshold);
    }

    private int normalizeTempLimit(int limit) {
        if (limit == 0) {
            return DEFAULT_TEMP_LIMIT;
        }
        if (limit < MIN_TEMP_LIMIT || limit > MAX_TEMP_LIMIT) {
            throw new InvalidDeviceQueryException(
                    "limit must be between %d and %d".formatted(MIN_TEMP_LIMIT, MAX_TEMP_LIMIT)
            );
        }
        return limit;
    }

    private int normalizeCommandLimit(int limit) {
        if (limit == 0) {
            return DEFAULT_COMMAND_LIMIT;
        }
        if (limit < MIN_COMMAND_LIMIT || limit > MAX_COMMAND_LIMIT) {
            throw new InvalidDeviceQueryException(
                    "limit must be between %d and %d".formatted(MIN_COMMAND_LIMIT, MAX_COMMAND_LIMIT)
            );
        }
        return limit;
    }

    private String buildCommandTopic(String deviceId) {
        return "devices/%s/cmd".formatted(deviceId);
    }

    private String buildCommandPayload(Long commandId, ControlAction commandType, Instant requestedAt) {
        return """
                {"commandId":%d,"commandType":"%s","requestedAt":"%s"}
                """.formatted(commandId, commandType.name(), requestedAt.toString()).trim();
    }

    private Range resolveRange(Instant from, Instant to) {
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_RANGE) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidDeviceQueryException("from must be before or equal to to");
        }
        return new Range(resolvedFrom, resolvedTo);
    }

    private record Range(Instant from, Instant to) {
    }
}
