package com.iot.IoT.repository;

import com.iot.IoT.entity.Device;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DeviceRepository extends JpaRepository<Device, Long> {

    boolean existsByDeviceId(String deviceId);
}
