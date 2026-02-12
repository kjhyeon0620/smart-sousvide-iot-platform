package com.iot.IoT.ingestion.service;

import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
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
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DeviceIngestionServiceImplTest {

    private TemperatureTimeSeriesPort temperatureTimeSeriesPort;
    private HeartbeatPort heartbeatPort;
    private DeviceIngestionServiceImpl service;

    @BeforeEach
    void setUp() {
        temperatureTimeSeriesPort = Mockito.mock(TemperatureTimeSeriesPort.class);
        heartbeatPort = Mockito.mock(HeartbeatPort.class);
        service = new DeviceIngestionServiceImpl(temperatureTimeSeriesPort, heartbeatPort);
    }

    @Test
    @DisplayName("Should write both Influx and Redis when ingestion succeeds")
    void ingest_success() {
        DeviceStatusMessage message = sampleMessage();

        service.ingest(message);

        verify(temperatureTimeSeriesPort, times(1)).save(eq(message), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
    }

    @Test
    @DisplayName("Should still update Redis when Influx write fails")
    void ingest_influxFails_redisStillUpdates() {
        DeviceStatusMessage message = sampleMessage();
        doThrow(new RuntimeException("influx down")).when(temperatureTimeSeriesPort).save(eq(message), any());

        service.ingest(message);

        verify(temperatureTimeSeriesPort, times(1)).save(eq(message), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
    }

    @Test
    @DisplayName("Should still attempt Influx write when Redis update fails")
    void ingest_redisFails_influxStillWrites() {
        DeviceStatusMessage message = sampleMessage();
        doThrow(new RuntimeException("redis down")).when(heartbeatPort).updateLastSeen(eq("SV-001"), any());

        service.ingest(message);

        verify(temperatureTimeSeriesPort, times(1)).save(eq(message), any());
        verify(heartbeatPort, times(1)).updateLastSeen(eq("SV-001"), any());
    }

    private DeviceStatusMessage sampleMessage() {
        return new DeviceStatusMessage(
                "SV-001",
                new BigDecimal("60.5"),
                DeviceState.HEATING,
                new BigDecimal("65.0")
        );
    }
}
