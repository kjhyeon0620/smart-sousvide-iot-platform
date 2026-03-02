package com.iot.IoT.repository;

import com.iot.IoT.entity.DeviceCommand;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {

    List<DeviceCommand> findByDevicePkOrderByRequestedAtDesc(Long devicePk, Pageable pageable);
}
