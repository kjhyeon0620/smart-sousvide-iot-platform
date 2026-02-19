package com.iot.IoT.ingestion.service;

import com.iot.IoT.control.ControlDecisionEngine;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.metrics.IngestionMetricsCollector;
import com.iot.IoT.ingestion.port.HeartbeatPort;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeviceIngestionServiceImplTest {

    private static final String INFLUX_MODE_STRICT = "strict";
    private static final String INFLUX_MODE_BYPASS = "bypass";

    private TemperatureTimeSeriesPort temperatureTimeSeriesPort;
    private HeartbeatPort heartbeatPort;
    private ControlDecisionEngine controlDecisionEngine;
    private IngestionMetricsCollector ingestionMetricsCollector;
    private DeviceIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        temperatureTimeSeriesPort = Mockito.mock(TemperatureTimeSeriesPort.class);
        heartbeatPort = Mockito.mock(HeartbeatPort.class);
        controlDecisionEngine = Mockito.mock(ControlDecisionEngine.class);
        ingestionMetricsCollector = Mockito.mock(IngestionMetricsCollector.class);
        service = createService(INFLUX_MODE_STRICT);
    }

    @Test
    @DisplayName("Should write both Influx and Redis when ingestion succeeds")
    void ingest_success() {
        DeviceStatusMessage message = sampleMessage();

        service.ingest(message);

        verify(temperatureTimeSeriesPort, times(1)).save(eq(message), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
        verify(ingestionMetricsCollector, times(1)).recordInfluxSuccess();
        verify(ingestionMetricsCollector, times(0)).recordInfluxFailure();
        verify(ingestionMetricsCollector, times(1)).recordRedisSuccess();
        verify(ingestionMetricsCollector, times(0)).recordRedisFailure();
        verify(controlDecisionEngine, times(1)).decide(eq(message));
    }

    @Test
    @DisplayName("Should still update Redis when Influx write fails")
    void ingest_influxFails_redisStillUpdates() {
        DeviceStatusMessage message = sampleMessage();
        doThrow(new RuntimeException("influx down")).when(temperatureTimeSeriesPort).save(eq(message), any());

        service.ingest(message);

        verify(temperatureTimeSeriesPort, times(1)).save(eq(message), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
        verify(ingestionMetricsCollector, times(0)).recordInfluxSuccess();
        verify(ingestionMetricsCollector, times(1)).recordInfluxFailure();
        verify(ingestionMetricsCollector, times(1)).recordRedisSuccess();
        verify(ingestionMetricsCollector, times(0)).recordRedisFailure();
        verify(controlDecisionEngine, times(1)).decide(eq(message));
    }

    @Test
    @DisplayName("Should still attempt Influx write when Redis update fails")
    void ingest_redisFails_influxStillWrites() {
        DeviceStatusMessage message = sampleMessage();
        doThrow(new RuntimeException("redis down")).when(heartbeatPort).updateLastSeen(eq("SV-001"), any());

        service.ingest(message);

        verify(temperatureTimeSeriesPort, times(1)).save(eq(message), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
        verify(ingestionMetricsCollector, times(1)).recordInfluxSuccess();
        verify(ingestionMetricsCollector, times(0)).recordInfluxFailure();
        verify(ingestionMetricsCollector, times(0)).recordRedisSuccess();
        verify(ingestionMetricsCollector, times(1)).recordRedisFailure();
        verify(controlDecisionEngine, times(1)).decide(eq(message));
    }

    @Test
    @DisplayName("Should bypass Influx write but still update Redis and control")
    void ingest_bypassMode_skipsInfluxButRunsRedisAndControl() {
        service = createService(INFLUX_MODE_BYPASS);
        DeviceStatusMessage message = sampleMessage();

        service.ingest(message);

        verify(temperatureTimeSeriesPort, never()).save(any(), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
        verify(ingestionMetricsCollector, times(1)).recordInfluxBypass();
        verify(ingestionMetricsCollector, never()).recordInfluxSuccess();
        verify(ingestionMetricsCollector, never()).recordInfluxFailure();
        verify(ingestionMetricsCollector, times(1)).recordRedisSuccess();
        verify(ingestionMetricsCollector, never()).recordRedisFailure();
        verify(controlDecisionEngine, times(1)).decide(eq(message));
    }

    @Test
    @DisplayName("Should treat bypass mode case-insensitively and still run control when Redis fails")
    void ingest_bypassModeCaseInsensitive_redisFails_controlStillRuns() {
        service = createService("ByPaSs");
        DeviceStatusMessage message = sampleMessage();
        doThrow(new RuntimeException("redis down")).when(heartbeatPort).updateLastSeen(eq("SV-001"), any());

        service.ingest(message);

        verify(temperatureTimeSeriesPort, never()).save(any(), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
        verify(ingestionMetricsCollector, times(1)).recordInfluxBypass();
        verify(ingestionMetricsCollector, never()).recordInfluxSuccess();
        verify(ingestionMetricsCollector, never()).recordInfluxFailure();
        verify(ingestionMetricsCollector, never()).recordRedisSuccess();
        verify(ingestionMetricsCollector, times(1)).recordRedisFailure();
        verify(controlDecisionEngine, times(1)).decide(eq(message));
    }

    private DeviceStatusMessage sampleMessage() {
        return new DeviceStatusMessage(
                "SV-001",
                new BigDecimal("60.5"),
                DeviceState.HEATING,
                new BigDecimal("65.0")
        );
    }

    private DeviceIngestionServiceImpl createService(String influxWriteMode) {
        return new DeviceIngestionServiceImpl(
                temperatureTimeSeriesPort,
                heartbeatPort,
                controlDecisionEngine,
                ingestionMetricsCollector,
                influxWriteMode
        );
    }
}
