package com.iot.IoT.repository;

import com.iot.IoT.anomaly.AnomalyType;
import com.iot.IoT.entity.AnomalyStatus;
import com.iot.IoT.entity.DeviceAnomaly;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DeviceAnomalyRepository extends JpaRepository<DeviceAnomaly, Long> {

    List<DeviceAnomaly> findByStatus(AnomalyStatus status);

    Optional<DeviceAnomaly> findFirstByDeviceIdAndTypeAndStatus(String deviceId, AnomalyType type, AnomalyStatus status);

    List<DeviceAnomaly> findByDevicePkOrderByDetectedAtDesc(Long devicePk, Pageable pageable);

    List<DeviceAnomaly> findByDevicePkAndStatusOrderByDetectedAtDesc(Long devicePk, AnomalyStatus status, Pageable pageable);

    List<DeviceAnomaly> findByDevicePkAndStatusOrderByDetectedAtAsc(Long devicePk, AnomalyStatus status);
}
