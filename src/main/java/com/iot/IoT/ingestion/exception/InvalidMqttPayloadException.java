package com.iot.IoT.ingestion.exception;

public class InvalidMqttPayloadException extends RuntimeException {

    private final FailureType failureType;

    public InvalidMqttPayloadException(String message, FailureType failureType) {
        super(message);
        this.failureType = failureType;
    }

    public InvalidMqttPayloadException(String message, Throwable cause, FailureType failureType) {
        super(message, cause);
        this.failureType = failureType;
    }

    public FailureType failureType() {
        return failureType;
    }

    public enum FailureType {
        INVALID_JSON(false),
        VALIDATION_FAILED(false),
        DUPLICATE_SUPPRESSED(false);

        private final boolean replayable;

        FailureType(boolean replayable) {
            this.replayable = replayable;
        }

        public boolean replayable() {
            return replayable;
        }
    }
}
