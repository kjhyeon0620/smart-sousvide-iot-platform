package com.iot.IoT.ingestion.exception;

public class InvalidMqttPayloadException extends RuntimeException {

    public InvalidMqttPayloadException(String message) {
        super(message);
    }

    public InvalidMqttPayloadException(String message, Throwable cause) {
        super(message, cause);
    }
}
