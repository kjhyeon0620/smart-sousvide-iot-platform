package com.iot.IoT.service.exception;

public class DuplicateDeviceException extends RuntimeException {

    public DuplicateDeviceException(String deviceId) {
        super("Device already exists. deviceId=" + deviceId);
    }
}
