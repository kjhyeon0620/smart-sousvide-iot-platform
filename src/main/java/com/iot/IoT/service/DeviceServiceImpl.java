package com.iot.IoT.service;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.DuplicateDeviceException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DeviceServiceImpl implements DeviceService {

    private static final int MIN_PAGE_SIZE = 1;
    private static final int MAX_PAGE_SIZE = 100;

    private final DeviceRepository deviceRepository;

    public DeviceServiceImpl(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
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
}
