package com.iot.IoT.service;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DeviceCommandPageResponse;
import com.iot.IoT.dto.DeviceCommandResponse;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperaturePointResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.entity.DeviceCommand;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesQueryPort;
import com.iot.IoT.repository.DeviceCommandRepository;
import com.iot.IoT.repository.DeviceRepository;
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

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Service
public class DeviceQueryService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_TEMP_LIMIT = 1;
    private static final int MAX_TEMP_LIMIT = 500;
    private static final int DEFAULT_TEMP_LIMIT = 200;
    private static final int DEFAULT_COMMAND_LIMIT = 20;
    private static final int MIN_COMMAND_LIMIT = 1;
    private static final int MAX_COMMAND_LIMIT = 100;
    private static final Duration DEFAULT_RANGE = Duration.ofHours(1);

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final WatchdogStatePort watchdogStatePort;
    private final TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort;
    private final Duration heartbeatTtl;

    public DeviceQueryService(
            DeviceRepository deviceRepository,
            DeviceCommandRepository deviceCommandRepository,
            WatchdogStatePort watchdogStatePort,
            TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort,
            @Value("${ingestion.heartbeat-ttl-seconds:120}") long heartbeatTtlSeconds
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandRepository = deviceCommandRepository;
        this.watchdogStatePort = watchdogStatePort;
        this.temperatureTimeSeriesQueryPort = temperatureTimeSeriesQueryPort;
        this.heartbeatTtl = Duration.ofSeconds(heartbeatTtlSeconds);
    }

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

    @Transactional(readOnly = true)
    public DeviceResponse findById(Long id) {
        return toResponse(findEntity(id));
    }

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

    @Transactional
    public DeviceResponse updateEnabled(Long id, boolean enabled) {
        Device device = findEntity(id);
        device.setEnabled(enabled);
        return toResponse(deviceRepository.save(device));
    }

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

    private Range resolveRange(Instant from, Instant to) {
        Instant resolvedTo = to == null ? Instant.now() : to;
        Instant resolvedFrom = from == null ? resolvedTo.minus(DEFAULT_RANGE) : from;
        if (resolvedFrom.isAfter(resolvedTo)) {
            throw new InvalidDeviceQueryException("from must be before or equal to to");
        }
        return new Range(resolvedFrom, resolvedTo);
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

    private record Range(Instant from, Instant to) {
    }
}
