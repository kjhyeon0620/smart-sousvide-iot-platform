package com.iot.IoT.repository;

import com.iot.IoT.entity.DeviceCommand;
import com.iot.IoT.entity.DeviceCommandStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DeviceCommandRepository extends JpaRepository<DeviceCommand, Long> {

    List<DeviceCommand> findByDevicePkOrderByRequestedAtDesc(Long devicePk, Pageable pageable);

    Optional<DeviceCommand> findByDevicePkAndIdempotencyKey(Long devicePk, String idempotencyKey);

    Optional<DeviceCommand> findByIdAndDevicePk(Long id, Long devicePk);

    List<DeviceCommand> findByStatusIn(Collection<DeviceCommandStatus> statuses);
}
