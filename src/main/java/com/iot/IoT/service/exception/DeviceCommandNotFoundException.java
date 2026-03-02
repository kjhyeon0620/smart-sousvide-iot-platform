package com.iot.IoT.service.exception;

public class DeviceCommandNotFoundException extends RuntimeException {

    public DeviceCommandNotFoundException(Long devicePk, Long commandId) {
        super("Device command not found. devicePk=" + devicePk + ", commandId=" + commandId);
    }
}
