package com.iot.IoT.service;

import com.iot.IoT.dto.DeviceControlPolicyResponse;
import com.iot.IoT.entity.Device;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class DeviceControlPolicyService {

    private final DeviceRepository deviceRepository;

    public DeviceControlPolicyService(DeviceRepository deviceRepository) {
        this.deviceRepository = deviceRepository;
    }

    @Transactional(readOnly = true)
    public DeviceControlPolicyResponse getControlPolicy(Long id) {
        return toControlPolicyResponse(findEntity(id));
    }

    @Transactional
    public DeviceControlPolicyResponse updateControlPolicy(Long id, BigDecimal targetTemp, BigDecimal hysteresis) {
        Device device = findEntity(id);
        device.setControlTargetTemp(targetTemp);
        device.setControlHysteresis(hysteresis);
        return toControlPolicyResponse(deviceRepository.save(device));
    }

    private Device findEntity(Long id) {
        return deviceRepository.findById(id)
                .orElseThrow(() -> new DeviceNotFoundException(id));
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
}
