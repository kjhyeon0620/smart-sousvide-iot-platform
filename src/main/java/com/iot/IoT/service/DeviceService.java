package com.iot.IoT.service;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;

public interface DeviceService {

    DeviceResponse create(CreateDeviceRequest request);

    DeviceResponse findById(Long id);

    DevicePageResponse findAll(int page, int size);

    DeviceResponse updateEnabled(Long id, boolean enabled);
}
