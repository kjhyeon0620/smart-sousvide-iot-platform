package com.iot.IoT.controller;

import com.iot.IoT.dto.CreateDeviceRequest;
import com.iot.IoT.dto.DevicePageResponse;
import com.iot.IoT.dto.DeviceResponse;
import com.iot.IoT.dto.DeviceStatusResponse;
import com.iot.IoT.dto.DeviceTemperaturePointResponse;
import com.iot.IoT.dto.DeviceTemperatureSeriesResponse;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.service.DeviceService;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.DuplicateDeviceException;
import com.iot.IoT.service.exception.InvalidDeviceQueryException;
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
import static org.mockito.ArgumentMatchers.isNull;
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

    @Test
    @DisplayName("GET /devices/{id}/status should return composed status")
    void getStatus_success() throws Exception {
        DeviceStatusResponse response = new DeviceStatusResponse(
                1L,
                "SV-001",
                "bath-1",
                true,
                Instant.parse("2026-03-02T00:00:00Z"),
                true,
                java.math.BigDecimal.valueOf(60.1),
                java.math.BigDecimal.valueOf(65.0),
                DeviceState.HEATING,
                Instant.parse("2026-03-02T00:00:00Z")
        );
        when(deviceService.getStatus(1L)).thenReturn(response);

        mockMvc.perform(get("/devices/1/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.deviceId").value("SV-001"))
                .andExpect(jsonPath("$.online").value(true))
                .andExpect(jsonPath("$.latestState").value("HEATING"));
    }

    @Test
    @DisplayName("GET /devices/{id}/temps should return series")
    void getTemps_success() throws Exception {
        DeviceTemperatureSeriesResponse response = new DeviceTemperatureSeriesResponse(
                1L,
                "SV-001",
                Instant.parse("2026-03-02T00:00:00Z"),
                Instant.parse("2026-03-02T00:10:00Z"),
                100,
                List.of(
                        new DeviceTemperaturePointResponse(
                                Instant.parse("2026-03-02T00:00:30Z"),
                                java.math.BigDecimal.valueOf(60.2),
                                java.math.BigDecimal.valueOf(65.0),
                                DeviceState.HEATING
                        )
                )
        );
        when(deviceService.getTemperatures(eq(1L), any(Instant.class), any(Instant.class), eq(100)))
                .thenReturn(response);

        mockMvc.perform(get("/devices/1/temps")
                        .param("from", "2026-03-02T00:00:00Z")
                        .param("to", "2026-03-02T00:10:00Z")
                        .param("limit", "100"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].temp").value(60.2))
                .andExpect(jsonPath("$.items[0].state").value("HEATING"));
    }

    @Test
    @DisplayName("GET /devices/{id}/temps should return 400 for invalid query")
    void getTemps_invalidRequest() throws Exception {
        when(deviceService.getTemperatures(eq(1L), isNull(), isNull(), eq(0)))
                .thenThrow(new InvalidDeviceQueryException("from must be before or equal to to"));

        mockMvc.perform(get("/devices/1/temps"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
    }

    private DeviceResponse sampleResponse(Long id, String deviceId, boolean enabled) {
        Instant now = Instant.parse("2026-03-02T00:00:00Z");
        return new DeviceResponse(id, deviceId, "bath-1", enabled, now, now);
    }
}
