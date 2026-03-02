package com.iot.IoT.controller;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.service.DeviceService;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.DuplicateDeviceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DeviceControllerTest {

    private MockMvc mockMvc;

    private DeviceService deviceService;

    @BeforeEach
    void setUp() {
        deviceService = mock(DeviceService.class);
        DeviceController controller = new DeviceController(deviceService);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setValidator(validator)
                .setControllerAdvice(new GlobalApiExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("POST /devices should return 201 with location")
    void create_success() throws Exception {
        DeviceResponse response = sampleResponse(1L, "SV-001", true);
        when(deviceService.create(any(CreateDeviceRequest.class))).thenReturn(response);

        String payload = """
                {
                  "deviceId": "SV-001",
                  "name": "bath-1"
                }
                """;

        mockMvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/devices/1"))
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.deviceId").value("SV-001"));
    }

    @Test
    @DisplayName("POST /devices should return 409 for duplicate device")
    void create_duplicate() throws Exception {
        when(deviceService.create(any(CreateDeviceRequest.class)))
                .thenThrow(new DuplicateDeviceException("SV-001"));

        String payload = """
                {
                  "deviceId": "SV-001",
                  "name": "bath-1"
                }
                """;

        mockMvc.perform(post("/devices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DEVICE_DUPLICATE"));
    }

    @Test
    @DisplayName("GET /devices/{id} should return 404 when missing")
    void getById_notFound() throws Exception {
        when(deviceService.findById(99L)).thenThrow(new DeviceNotFoundException(99L));

        mockMvc.perform(get("/devices/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("DEVICE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /devices should return paged response")
    void list_success() throws Exception {
        DevicePageResponse pageResponse = new DevicePageResponse(
                List.of(sampleResponse(1L, "SV-001", true)),
                1,
                1,
                0,
                20
        );
        when(deviceService.findAll(eq(0), eq(20))).thenReturn(pageResponse);

        mockMvc.perform(get("/devices?page=0&size=20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].deviceId").value("SV-001"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test
    @DisplayName("PATCH /devices/{id}/enabled should update enabled")
    void patchEnabled_success() throws Exception {
        when(deviceService.updateEnabled(1L, false)).thenReturn(sampleResponse(1L, "SV-001", false));

        mockMvc.perform(patch("/devices/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(false));
    }

    @Test
    @DisplayName("PATCH /devices/{id}/enabled should validate request")
    void patchEnabled_invalidRequest() throws Exception {
        mockMvc.perform(patch("/devices/1/enabled")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private DeviceResponse sampleResponse(Long id, String deviceId, boolean enabled) {
        Instant now = Instant.parse("2026-03-02T00:00:00Z");
        return new DeviceResponse(id, deviceId, "bath-1", enabled, now, now);
    }
}
