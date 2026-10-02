package com.iot.IoT.service.exception;

public class DeviceNotFoundException extends RuntimeException {

    public DeviceNotFoundException(Long id) {
        super("Device not found. id=" + id);
    }

    public DeviceNotFoundException(String message) {
        super("Device not found. " + message);
    }
}
