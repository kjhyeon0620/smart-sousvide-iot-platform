package com.iot.IoT.controller;

import com.iot.IoT.dto.DevDashboardDemoResponse;
import com.iot.IoT.service.DevDashboardDemoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Profile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DevDashboardDemoControllerTest {

    private MockMvc mockMvc;
    private DevDashboardDemoService service;

    @BeforeEach
    void setUp() {
        service = mock(DevDashboardDemoService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new DevDashboardDemoController(service))
                .setValidator(validator)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("controller should be limited to local and dev profiles")
    void profileGuard() {
        Profile profile = DevDashboardDemoController.class.getAnnotation(Profile.class);

        assertThat(profile).isNotNull();
        assertThat(profile.value()).containsExactlyInAnyOrder("local", "dev");
    }

    @Test
    @DisplayName("GET /dev/dashboard-demo should report availability")
    void availability() throws Exception {
        mockMvc.perform(get("/dev/dashboard-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.available").value(true))
                .andExpect(jsonPath("$.deviceIdPrefix").value("SV-DEMO-"));
    }

    @Test
    @DisplayName("POST /dev/dashboard-demo/scenario should create scenario")
    void createScenario() throws Exception {
        when(service.createScenario(any())).thenReturn(response(5, 180, 4));

        mockMvc.perform(post("/dev/dashboard-demo/scenario")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "count": 5,
                                  "scenario": "mixed",
                                  "baseTargetTemp": 64.5
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdDevices").value(5))
                .andExpect(jsonPath("$.temperaturePoints").value(180));
    }

    @Test
    @DisplayName("PATCH /dev/dashboard-demo/devices/{deviceId} should update demo state")
    void updateDevice() throws Exception {
        when(service.updateDevice(eq("SV-DEMO-001"), any())).thenReturn(response(1, 1, 0));

        mockMvc.perform(patch("/dev/dashboard-demo/devices/SV-DEMO-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "online": true,
                                  "enabled": true,
                                  "temp": 61.2,
                                  "targetTemp": 64.5,
                                  "state": "HEATING"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdDevices").value(1));
    }

    @Test
    @DisplayName("POST /dev/dashboard-demo/devices/{deviceId}/commands should add command samples")
    void addCommands() throws Exception {
        when(service.addCommandSamples(eq("SV-DEMO-001"), any())).thenReturn(response(1, 0, 4));

        mockMvc.perform(post("/dev/dashboard-demo/devices/SV-DEMO-001/commands")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "statuses": ["PENDING", "SENT", "FAILED", "EXPIRED"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.commands").value(4));
    }

    @Test
    @DisplayName("DELETE /dev/dashboard-demo should clear demo data")
    void clear() throws Exception {
        when(service.clear()).thenReturn(response(0, 0, 0));

        mockMvc.perform(delete("/dev/dashboard-demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.createdDevices").value(0));
    }

    private DevDashboardDemoResponse response(int devices, int telemetry, int commands) {
        return new DevDashboardDemoResponse(devices, devices, 0, 0, telemetry, commands, null, List.of());
    }
}
