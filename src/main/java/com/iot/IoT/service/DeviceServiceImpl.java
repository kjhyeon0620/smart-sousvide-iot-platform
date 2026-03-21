package com.iot.IoT.service;

import com.iot.IoT.control.ControlAction;
import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DeviceCommandPageResponse;
import com.iot.IoT.dto.DeviceCommandResponse;
import com.iot.IoT.dto.DeviceControlPolicyResponse;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

@Service
public class DeviceServiceImpl implements DeviceService {

    private final DeviceQueryService deviceQueryService;
    private final DeviceControlPolicyService deviceControlPolicyService;
    private final DeviceCommandService deviceCommandService;
    private final DeviceCommandReliabilityService deviceCommandReliabilityService;

    public DeviceServiceImpl(
            DeviceQueryService deviceQueryService,
            DeviceControlPolicyService deviceControlPolicyService,
            DeviceCommandService deviceCommandService,
            DeviceCommandReliabilityService deviceCommandReliabilityService
    ) {
        this.deviceQueryService = deviceQueryService;
        this.deviceControlPolicyService = deviceControlPolicyService;
        this.deviceCommandService = deviceCommandService;
        this.deviceCommandReliabilityService = deviceCommandReliabilityService;
    }

    @Override
    public DeviceResponse create(CreateDeviceRequest request) {
        return deviceQueryService.create(request);
    }

    @Override
    public DeviceResponse findById(Long id) {
        return deviceQueryService.findById(id);
    }

    @Override
    public DevicePageResponse findAll(int page, int size) {
        return deviceQueryService.findAll(page, size);
    }

    @Override
    public DeviceResponse updateEnabled(Long id, boolean enabled) {
        return deviceQueryService.updateEnabled(id, enabled);
    }

    @Override
    public DeviceStatusResponse getStatus(Long id) {
        return deviceQueryService.getStatus(id);
    }

    @Override
    public DeviceTemperatureSeriesResponse getTemperatures(Long id, Instant from, Instant to, int limit) {
        return deviceQueryService.getTemperatures(id, from, to, limit);
    }

    @Override
    public DeviceControlPolicyResponse getControlPolicy(Long id) {
        return deviceControlPolicyService.getControlPolicy(id);
    }

    @Override
    public DeviceControlPolicyResponse updateControlPolicy(Long id, BigDecimal targetTemp, BigDecimal hysteresis) {
        return deviceControlPolicyService.updateControlPolicy(id, targetTemp, hysteresis);
    }

    @Override
    public DeviceCommandResponse sendCommand(Long id, ControlAction commandType, String idempotencyKey) {
        return deviceCommandService.sendCommand(id, commandType, idempotencyKey);
    }

    @Override
    public DeviceCommandPageResponse getCommands(Long id, int limit) {
        return deviceQueryService.getCommands(id, limit);
    }

    @Override
    public DeviceCommandResponse acknowledgeCommand(Long id, Long commandId) {
        return deviceCommandService.acknowledgeCommand(id, commandId);
    }

    @Override
    public void sendAutoControlCommand(String deviceId, ControlAction commandType, Instant decidedAt) {
        deviceCommandService.sendAutoControlCommand(deviceId, commandType, decidedAt);
    }

    @Override
    public void processCommandReliability() {
        deviceCommandReliabilityService.processCommandReliability();
    }
}
