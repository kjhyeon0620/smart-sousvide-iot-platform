package com.iot.IoT.ingestion.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record DeviceStatusMessage(
        @NotNull BigDecimal temp,
        @NotNull DeviceState state,
        @NotNull @JsonProperty("target_temp") BigDecimal targetTemp
) {
    @JsonCreator
    public DeviceStatusMessage(
            @JsonProperty("temp") BigDecimal temp,
            @JsonProperty("state") DeviceState state,
            @JsonProperty("target_temp") BigDecimal targetTemp
    ) {
        this.temp = temp;
        this.state = state;
        this.targetTemp = targetTemp;
    }
}
