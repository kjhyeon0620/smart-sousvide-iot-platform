package com.iot.IoT.entity;

import com.iot.IoT.control.ControlAction;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "device_commands")
public class DeviceCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "device_pk", nullable = false)
    private Long devicePk;

    @Column(name = "device_id", nullable = false, length = 64)
    private String deviceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "command_type", nullable = false, length = 32)
    private ControlAction commandType;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private DeviceCommandStatus status;

    @Column(name = "topic", nullable = false, length = 128)
    private String topic;

    @Column(name = "payload", nullable = false, length = 1000)
    private String payload;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private Instant requestedAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    public Long getId() {
        return id;
    }

    public Long getDevicePk() {
        return devicePk;
    }

    public void setDevicePk(Long devicePk) {
        this.devicePk = devicePk;
    }

    public String getDeviceId() {
        return deviceId;
    }

    public void setDeviceId(String deviceId) {
        this.deviceId = deviceId;
    }

    public ControlAction getCommandType() {
        return commandType;
    }

    public void setCommandType(ControlAction commandType) {
        this.commandType = commandType;
    }

    public DeviceCommandStatus getStatus() {
        return status;
    }

    public void setStatus(DeviceCommandStatus status) {
        this.status = status;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public Instant getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(Instant requestedAt) {
        this.requestedAt = requestedAt;
    }

    public Instant getSentAt() {
        return sentAt;
    }

    public void setSentAt(Instant sentAt) {
        this.sentAt = sentAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    @PrePersist
    void onCreate() {
        if (this.requestedAt == null) {
            this.requestedAt = Instant.now();
        }
    }
}
