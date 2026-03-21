package com.iot.IoT.service;

import com.iot.IoT.entity.DeviceCommand;
import com.iot.IoT.entity.DeviceCommandStatus;
import com.iot.IoT.mqtt.port.DeviceCommandPublisherPort;
import com.iot.IoT.repository.DeviceCommandRepository;
import com.iot.IoT.service.metrics.DownlinkMetricsRecorder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

@Service
public class DeviceCommandReliabilityService {

    private static final EnumSet<DeviceCommandStatus> RELIABILITY_TARGET_STATUSES =
            EnumSet.of(DeviceCommandStatus.SENT, DeviceCommandStatus.PENDING);

    private final DeviceCommandRepository deviceCommandRepository;
    private final DeviceCommandPublisherPort deviceCommandPublisherPort;
    private final DownlinkMetricsRecorder downlinkMetricsRecorder;
    private final Duration commandRetryInterval;

    public DeviceCommandReliabilityService(
            DeviceCommandRepository deviceCommandRepository,
            DeviceCommandPublisherPort deviceCommandPublisherPort,
            DownlinkMetricsRecorder downlinkMetricsRecorder,
            @Value("${downlink.retry-interval-seconds:10}") long commandRetryIntervalSeconds
    ) {
        this.deviceCommandRepository = deviceCommandRepository;
        this.deviceCommandPublisherPort = deviceCommandPublisherPort;
        this.downlinkMetricsRecorder = downlinkMetricsRecorder;
        this.commandRetryInterval = Duration.ofSeconds(commandRetryIntervalSeconds);
    }

    @Transactional
    public void processCommandReliability() {
        Instant now = Instant.now();
        List<DeviceCommand> targets = deviceCommandRepository.findByStatusIn(RELIABILITY_TARGET_STATUSES);
        for (DeviceCommand command : targets) {
            if (command.getStatus() == DeviceCommandStatus.ACKED
                    || command.getStatus() == DeviceCommandStatus.EXPIRED
                    || command.getStatus() == DeviceCommandStatus.FAILED) {
                continue;
            }
            if (command.getExpireAt() != null && now.isAfter(command.getExpireAt())) {
                command.setStatus(DeviceCommandStatus.EXPIRED);
                command.setNextRetryAt(null);
                command.setErrorMessage("ack timeout expired");
                deviceCommandRepository.save(command);
                downlinkMetricsRecorder.recordExpired();
                continue;
            }
            if (command.getStatus() == DeviceCommandStatus.PENDING) {
                retryPublish(command, now);
                continue;
            }
            if (command.getStatus() == DeviceCommandStatus.SENT
                    && command.getNextRetryAt() != null
                    && !now.isBefore(command.getNextRetryAt())
                    && command.getRetryCount() < command.getMaxRetries()) {
                retryPublish(command, now);
            }
        }
    }

    private void retryPublish(DeviceCommand command, Instant now) {
        try {
            deviceCommandPublisherPort.publish(command.getTopic(), command.getPayload());
            command.setStatus(DeviceCommandStatus.SENT);
            command.setSentAt(now);
            command.setRetryCount(command.getRetryCount() + 1);
            command.setNextRetryAt(now.plus(commandRetryInterval));
            command.setErrorMessage(null);
            downlinkMetricsRecorder.recordRetried();
            downlinkMetricsRecorder.recordSent();
        } catch (RuntimeException ex) {
            int nextRetry = command.getRetryCount() + 1;
            command.setRetryCount(nextRetry);
            if (nextRetry >= command.getMaxRetries()) {
                command.setStatus(DeviceCommandStatus.FAILED);
                command.setNextRetryAt(null);
                downlinkMetricsRecorder.recordFailed();
            } else {
                command.setStatus(DeviceCommandStatus.SENT);
                command.setNextRetryAt(now.plus(commandRetryInterval));
                downlinkMetricsRecorder.recordRetried();
            }
            command.setErrorMessage(ex.getMessage());
        }
        deviceCommandRepository.save(command);
    }
}
