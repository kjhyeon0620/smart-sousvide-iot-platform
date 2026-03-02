package com.iot.IoT.service;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperaturePointResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesQueryPort;
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
public class DeviceServiceImpl implements DeviceService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MIN_TEMP_LIMIT = 1;
    private static final int MAX_TEMP_LIMIT = 500;
    private static final int DEFAULT_TEMP_LIMIT = 200;
    private static final Duration DEFAULT_RANGE = Duration.ofHours(1);

    private final DeviceRepository deviceRepository;
    private final WatchdogStatePort watchdogStatePort;
    private final TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort;
    private final Duration heartbeatTtl;

    public DeviceServiceImpl(
            DeviceRepository deviceRepository,
            WatchdogStatePort watchdogStatePort,
            TemperatureTimeSeriesQueryPort temperatureTimeSeriesQueryPort,
            @Value("${ingestion.heartbeat-ttl-seconds:120}") long heartbeatTtlSeconds
    ) {
        this.deviceRepository = deviceRepository;
        this.watchdogStatePort = watchdogStatePort;
        this.temperatureTimeSeriesQueryPort = temperatureTimeSeriesQueryPort;
        this.heartbeatTtl = Duration.ofSeconds(heartbeatTtlSeconds);
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
