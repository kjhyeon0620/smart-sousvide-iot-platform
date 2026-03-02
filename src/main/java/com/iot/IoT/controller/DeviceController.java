package com.iot.IoT.controller;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DeviceControlPolicyResponse;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;
import com.iot.IoT.dto.UpdateDeviceControlPolicyRequest;
import com.iot.IoT.dto.UpdateDeviceEnabledRequest;
import com.iot.IoT.service.DeviceService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;

@RestController
@RequestMapping("/devices")
@Validated
public class DeviceController {

    private final DeviceService deviceService;

    public DeviceController(DeviceService deviceService) {
        this.deviceService = deviceService;
    }

    @PostMapping
    public ResponseEntity<DeviceResponse> create(@Valid @RequestBody CreateDeviceRequest request) {
        DeviceResponse response = deviceService.create(request);
        return ResponseEntity.created(URI.create("/devices/" + response.id())).body(response);
    }

    @GetMapping("/{id}")
    public DeviceResponse findById(@PathVariable Long id) {
        return deviceService.findById(id);
    }

    @GetMapping
    public DevicePageResponse findAll(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size
    ) {
        return deviceService.findAll(page, size);
    }

    @PatchMapping("/{id}/enabled")
    public DeviceResponse updateEnabled(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDeviceEnabledRequest request
    ) {
        return deviceService.updateEnabled(id, request.enabled());
    }

    @GetMapping("/{id}/status")
    public DeviceStatusResponse getStatus(@PathVariable Long id) {
        return deviceService.getStatus(id);
    }

    @GetMapping("/{id}/temps")
    public DeviceTemperatureSeriesResponse getTemperatures(
            @PathVariable Long id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int limit
    ) {
        return deviceService.getTemperatures(id, from, to, limit);
    }

    @GetMapping("/{id}/control-policy")
    public DeviceControlPolicyResponse getControlPolicy(@PathVariable Long id) {
        return deviceService.getControlPolicy(id);
    }

    @PatchMapping("/{id}/control-policy")
    public DeviceControlPolicyResponse updateControlPolicy(
            @PathVariable Long id,
            @Valid @RequestBody UpdateDeviceControlPolicyRequest request
    ) {
        return deviceService.updateControlPolicy(id, request.targetTemp(), request.hysteresis());
    }
}
