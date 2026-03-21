package com.iot.IoT.ingestion.service;

import com.iot.IoT.control.ControlAction;
import com.iot.IoT.control.ControlDecisionEngine;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.metrics.IngestionMetricsCollector;
import com.iot.IoT.ingestion.port.HeartbeatPort;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesPort;
import com.iot.IoT.service.DeviceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Instant;

@Service
public class DeviceIngestionServiceImpl implements DeviceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(DeviceIngestionServiceImpl.class);
    private static final String INFLUX_WRITE_MODE_BYPASS = "bypass";

    private final TemperatureTimeSeriesPort temperatureTimeSeriesPort;
    private final HeartbeatPort heartbeatPort;
    private final ControlDecisionEngine controlDecisionEngine;
    private final IngestionMetricsCollector ingestionMetricsCollector;
    private final DeviceService deviceService;
    private final String influxWriteMode;

    public DeviceIngestionServiceImpl(
            TemperatureTimeSeriesPort temperatureTimeSeriesPort,
            HeartbeatPort heartbeatPort,
            ControlDecisionEngine controlDecisionEngine,
            IngestionMetricsCollector ingestionMetricsCollector,
            DeviceService deviceService,
            @Value("${ingestion.influx.write-mode:strict}") String influxWriteMode
    ) {
        this.temperatureTimeSeriesPort = temperatureTimeSeriesPort;
        this.heartbeatPort = heartbeatPort;
        this.controlDecisionEngine = controlDecisionEngine;
        this.ingestionMetricsCollector = ingestionMetricsCollector;
        this.deviceService = deviceService;
        this.influxWriteMode = influxWriteMode;
    }

    @Override
    public void ingest(DeviceStatusMessage message) {
        Instant now = Instant.now();
        boolean influxWritten = false;
        boolean redisUpdated = false;

        if (isInfluxWriteBypassMode()) {
            ingestionMetricsCollector.recordInfluxBypass();
        } else {
            try {
                temperatureTimeSeriesPort.save(message, now);
                influxWritten = true;
                ingestionMetricsCollector.recordInfluxSuccess();
            } catch (Exception e) {
                ingestionMetricsCollector.recordInfluxFailure();
                ingestionMetricsCollector.recordStorageReplayCandidate();
                log.error("[INGESTION] Influx write failed. deviceId={}, temp={}, targetTemp={}, state={}",
                        message.deviceId(),
                        message.temp(),
                        message.targetTemp(),
                        message.state(),
                        e);
                log.error("[RELIABILITY] Storage failure classified. store=INFLUX, deviceId={}, replayable=true",
                        message.deviceId(), e);
            }
        }

        try {
            heartbeatPort.updateLastSeen(message.deviceId(), now);
            redisUpdated = true;
            ingestionMetricsCollector.recordRedisSuccess();
        } catch (Exception e) {
            ingestionMetricsCollector.recordRedisFailure();
            ingestionMetricsCollector.recordStorageReplayCandidate();
            log.error("[INGESTION] Redis heartbeat update failed. deviceId={}", message.deviceId(), e);
            log.error("[RELIABILITY] Storage failure classified. store=REDIS, deviceId={}, replayable=true",
                    message.deviceId(), e);
        }

        if (redisUpdated) {
            ingestionMetricsCollector.recordCorePipelineSuccess();
        }
        if (redisUpdated && influxWritten) {
            ingestionMetricsCollector.recordOverallPipelineSuccess();
        }

        try {
            ControlAction action = controlDecisionEngine.decide(message);
            log.info("[CONTROL] Decision made. deviceId={}, temp={}, targetTemp={}, state={}, action={}",
                    message.deviceId(),
                    message.temp(),
                    message.targetTemp(),
                    message.state(),
                    action);
            deviceService.sendAutoControlCommand(message.deviceId(), action, now);
        } catch (RuntimeException ex) {
            ingestionMetricsCollector.recordControlReplayCandidate();
            log.error("[CONTROL] Auto control dispatch failed. deviceId={}", message.deviceId(), ex);
            log.error("[RELIABILITY] Control dispatch failure classified. deviceId={}, replayable=true",
                    message.deviceId(), ex);
        }
    }

    private boolean isInfluxWriteBypassMode() {
        return INFLUX_WRITE_MODE_BYPASS.equalsIgnoreCase(influxWriteMode);
    }
}
