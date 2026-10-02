package com.iot.IoT.controller;

import com.iot.IoT.dto.DevDashboardDemoAvailabilityResponse;
import com.iot.IoT.dto.DevDashboardDemoCommandRequest;
import com.iot.IoT.dto.DevDashboardDemoResponse;
import com.iot.IoT.dto.DevDashboardDemoScenarioRequest;
import com.iot.IoT.dto.DevDashboardDemoStateRequest;
import com.iot.IoT.service.DevDashboardDemoService;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile({"local", "dev"})
@RequestMapping("/dev/dashboard-demo")
public class DevDashboardDemoController {

    private final DevDashboardDemoService demoService;

    public DevDashboardDemoController(DevDashboardDemoService demoService) {
        this.demoService = demoService;
    }

    @GetMapping
    public DevDashboardDemoAvailabilityResponse availability() {
        return new DevDashboardDemoAvailabilityResponse(true, DevDashboardDemoService.DEMO_DEVICE_PREFIX);
    }

    @PostMapping("/scenario")
    public DevDashboardDemoResponse createScenario(@Valid @RequestBody DevDashboardDemoScenarioRequest request) {
        return demoService.createScenario(request);
    }

    @PatchMapping("/devices/{deviceId}")
    public DevDashboardDemoResponse updateDevice(
            @PathVariable String deviceId,
            @Valid @RequestBody DevDashboardDemoStateRequest request
    ) {
        return demoService.updateDevice(deviceId, request);
    }

    @PostMapping("/devices/{deviceId}/commands")
    public DevDashboardDemoResponse addCommands(
            @PathVariable String deviceId,
            @Valid @RequestBody DevDashboardDemoCommandRequest request
    ) {
        return demoService.addCommandSamples(deviceId, request);
    }

    @DeleteMapping
    public DevDashboardDemoResponse clear() {
        return demoService.clear();
    }
}
