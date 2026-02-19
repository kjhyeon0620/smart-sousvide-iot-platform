package com.iot.IoT.ingestion.service;

import com.iot.IoT.control.ControlAction;
import com.iot.IoT.control.ControlDecisionEngine;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.metrics.IngestionMetricsCollector;
import com.iot.IoT.ingestion.port.HeartbeatPort;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeviceIngestionServiceImpl implements DeviceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceIngestionServiceImpl.class);

    private final TemperatureTimeSeriesPort temperatureTimeSeriesPort;
    private final HeartbeatPort heartbeatPort;
    private final ControlDecisionEngine controlDecisionEngine;
    private final IngestionMetricsCollector ingestionMetricsCollector;

    public DeviceIngestionServiceImpl(
            TemperatureTimeSeriesPort temperatureTimeSeriesPort,
            HeartbeatPort heartbeatPort,
            ControlDecisionEngine controlDecisionEngine,
            IngestionMetricsCollector ingestionMetricsCollector
    ) {
        this.temperatureTimeSeriesPort = temperatureTimeSeriesPort;
        this.heartbeatPort = heartbeatPort;
        this.controlDecisionEngine = controlDecisionEngine;
        this.ingestionMetricsCollector = ingestionMetricsCollector;
    }

    @Override
    public void ingest(DeviceStatusMessage message) {
        Instant now = Instant.now();

        try {
            temperatureTimeSeriesPort.save(message, now);
            ingestionMetricsCollector.recordInfluxSuccess();
        } catch (Exception e) {
            ingestionMetricsCollector.recordInfluxFailure();
            log.error("[INGESTION] Influx write failed. deviceId={}, temp={}, targetTemp={}, state={}",
                    message.deviceId(),
                    message.temp(),
                    message.targetTemp(),
                    message.state(),
                    e);
        }

        try {
            heartbeatPort.updateLastSeen(message.deviceId(), now);
            ingestionMetricsCollector.recordRedisSuccess();
        } catch (Exception e) {
            ingestionMetricsCollector.recordRedisFailure();
            log.error("[INGESTION] Redis heartbeat update failed. deviceId={}", message.deviceId(), e);
        }

        ControlAction action = controlDecisionEngine.decide(message);
        log.info("[CONTROL] Decision made. deviceId={}, temp={}, targetTemp={}, state={}, action={}",
                message.deviceId(),
                message.temp(),
                message.targetTemp(),
                message.state(),
                action);
    }
}
