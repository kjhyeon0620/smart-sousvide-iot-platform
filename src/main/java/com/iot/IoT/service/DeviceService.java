package com.iot.IoT.service;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DeviceControlPolicyResponse;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;

import java.math.BigDecimal;
import java.time.Instant;

public interface DeviceService {

    DeviceResponse create(CreateDeviceRequest request);

    DeviceResponse findById(Long id);

    DevicePageResponse findAll(int page, int size);

    DeviceResponse updateEnabled(Long id, boolean enabled);

    DeviceStatusResponse getStatus(Long id);

    DeviceTemperatureSeriesResponse getTemperatures(Long id, Instant from, Instant to, int limit);

    DeviceControlPolicyResponse getControlPolicy(Long id);

    DeviceControlPolicyResponse updateControlPolicy(Long id, BigDecimal targetTemp, BigDecimal hysteresis);
}
