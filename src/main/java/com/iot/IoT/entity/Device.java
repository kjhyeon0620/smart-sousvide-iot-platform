package com.iot.IoT.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(
        name = "devices",
        uniqueConstraints = @UniqueConstraint(name = "uk_devices_device_id", columnNames = "device_id")
)
public class Device {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_id", nullable = false, updatable = false, length = 64)
    private String deviceId;

    @Column(name = "name", length = 100)
    private String name;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "control_target_temp", precision = 6, scale = 2)
    private BigDecimal controlTargetTemp;

    @Column(name = "control_hysteresis", precision = 4, scale = 2)
    private BigDecimal controlHysteresis;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public Long getId() {
        return id;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public BigDecimal getControlTargetTemp() {
        return controlTargetTemp;
    }

    public void setControlTargetTemp(BigDecimal controlTargetTemp) {
        this.controlTargetTemp = controlTargetTemp;
    }

    public BigDecimal getControlHysteresis() {
        return controlHysteresis;
    }

    public void setControlHysteresis(BigDecimal controlHysteresis) {
        this.controlHysteresis = controlHysteresis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
