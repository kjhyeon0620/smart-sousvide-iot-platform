package com.iot.IoT.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DeviceStatusMessage(
        @NotBlank String deviceId,
        @NotNull BigDecimal temp,
        @NotNull DeviceState state,
        @NotNull BigDecimal targetTemp
) {
    @JsonCreator
    public DeviceStatusMessage(
            @JsonProperty("deviceId") String deviceId,
            @JsonProperty("temp") BigDecimal temp,
            @JsonProperty("state") DeviceState state,
            @JsonProperty("targetTemp") BigDecimal targetTemp
    ) {
        this.deviceId = deviceId;
        this.temp = temp;
        this.state = state;
        this.targetTemp = targetTemp;
    }
}
