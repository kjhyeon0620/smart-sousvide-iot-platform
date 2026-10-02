package com.iot.IoT.service;

import com.iot.IoT.control.ControlAction;
import com.iot.IoT.dto.DevDashboardDemoCommandRequest;
import com.iot.IoT.dto.DevDashboardDemoDeviceResponse;
import com.iot.IoT.dto.DevDashboardDemoResponse;
import com.iot.IoT.dto.DevDashboardDemoScenarioRequest;
import com.iot.IoT.dto.DevDashboardDemoStateRequest;
import com.iot.IoT.entity.Device;
import com.iot.IoT.entity.DeviceCommand;
import com.iot.IoT.entity.DeviceCommandStatus;
import com.iot.IoT.ingestion.dto.DeviceState;
import com.iot.IoT.ingestion.dto.DeviceStatusMessage;
import com.iot.IoT.ingestion.port.HeartbeatPort;
import com.iot.IoT.ingestion.port.TemperatureTimeSeriesPort;
import com.iot.IoT.repository.DeviceCommandRepository;
import com.iot.IoT.repository.DeviceRepository;
import com.iot.IoT.service.exception.DeviceNotFoundException;
import com.iot.IoT.service.exception.InvalidDeviceQueryException;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@Profile({"local", "dev"})
public class DevDashboardDemoService {

    public static final String DEMO_DEVICE_PREFIX = "SV-DEMO-";
    private static final String INFLUX_CLEAR_WARNING = "InfluxDB demo telemetry is not deleted by clear; new samples are written with fresh timestamps.";
    private static final int DEFAULT_COUNT = 5;
    private static final BigDecimal DEFAULT_TARGET_TEMP = BigDecimal.valueOf(64.5);
    private static final BigDecimal DEFAULT_HYSTERESIS = BigDecimal.valueOf(0.3);
    private static final int TELEMETRY_POINTS_PER_DEVICE = 36;
    private static final String TRACKED_DEVICES_KEY = "devices:active";

    private final DeviceRepository deviceRepository;
    private final DeviceCommandRepository deviceCommandRepository;
    private final HeartbeatPort heartbeatPort;
    private final TemperatureTimeSeriesPort temperatureTimeSeriesPort;
    private final StringRedisTemplate redisTemplate;

    public DevDashboardDemoService(
            DeviceRepository deviceRepository,
            DeviceCommandRepository deviceCommandRepository,
            HeartbeatPort heartbeatPort,
            TemperatureTimeSeriesPort temperatureTimeSeriesPort,
            StringRedisTemplate redisTemplate
    ) {
        this.deviceRepository = deviceRepository;
        this.deviceCommandRepository = deviceCommandRepository;
        this.heartbeatPort = heartbeatPort;
        this.temperatureTimeSeriesPort = temperatureTimeSeriesPort;
        this.redisTemplate = redisTemplate;
    }

    @Transactional
    public DevDashboardDemoResponse createScenario(DevDashboardDemoScenarioRequest request) {
        clearMySql();
        clearRedis();

        int count = request.count() == null ? DEFAULT_COUNT : request.count();
        BigDecimal baseTargetTemp = scale(request.baseTargetTemp() == null ? DEFAULT_TARGET_TEMP : request.baseTargetTemp());
        DemoScenario scenario = DemoScenario.from(request.scenario());
        Instant now = Instant.now();

        List<DevDashboardDemoDeviceResponse> devices = new ArrayList<>();
        int temperaturePoints = 0;
        int commands = 0;

        for (int index = 1; index <= count; index++) {
            DemoState state = scenarioState(scenario, index, baseTargetTemp);
            Device device = saveDevice(index, state);
            if (state.online()) {
                heartbeatPort.updateLastSeen(device.getDeviceId(), now);
            }
            temperaturePoints += writeTelemetrySamples(device.getDeviceId(), state, now);
            if (scenario == DemoScenario.COMMAND_FAILURE || (scenario == DemoScenario.MIXED && index == count)) {
                commands += createCommandSamples(device, List.of(
                        DeviceCommandStatus.PENDING,
                        DeviceCommandStatus.SENT,
                        DeviceCommandStatus.FAILED,
                        DeviceCommandStatus.EXPIRED
                ));
            }
            devices.add(toDeviceResponse(device, state));
        }

        return response(devices, temperaturePoints, commands, INFLUX_CLEAR_WARNING);
    }

    @Transactional
    public DevDashboardDemoResponse updateDevice(String deviceId, DevDashboardDemoStateRequest request) {
        assertDemoDeviceId(deviceId);
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("deviceId=" + deviceId));

        boolean enabled = request.enabled() == null ? device.isEnabled() : request.enabled();
        device.setEnabled(enabled);
        if (request.targetTemp() != null) {
            device.setControlTargetTemp(scale(request.targetTemp()));
        }
        device = deviceRepository.save(device);

        Instant now = Instant.now();
        boolean online = request.online() == null || request.online();
        if (request.online() != null) {
            if (request.online()) {
                heartbeatPort.updateLastSeen(deviceId, now);
            } else {
                clearRedisDevice(deviceId);
            }
        }

        int temperaturePoints = 0;
        DemoState demoState = new DemoState(
                online,
                enabled,
                scale(request.temp() == null ? defaultTemp(device) : request.temp()),
                scale(request.targetTemp() == null ? defaultTargetTemp(device) : request.targetTemp()),
                request.state() == null ? DeviceState.HEATING : request.state()
        );
        if (request.temp() != null || request.targetTemp() != null || request.state() != null) {
            temperatureTimeSeriesPort.save(new DeviceStatusMessage(deviceId, demoState.temp(), demoState.state(), demoState.targetTemp()), now);
            temperaturePoints = 1;
        }

        return response(List.of(toDeviceResponse(device, demoState)), temperaturePoints, 0, null);
    }

    @Transactional
    public DevDashboardDemoResponse addCommandSamples(String deviceId, DevDashboardDemoCommandRequest request) {
        assertDemoDeviceId(deviceId);
        Device device = deviceRepository.findByDeviceId(deviceId)
                .orElseThrow(() -> new DeviceNotFoundException("deviceId=" + deviceId));
        int commands = createCommandSamples(device, request.statuses());
        DemoState state = new DemoState(true, device.isEnabled(), defaultTemp(device), defaultTargetTemp(device), DeviceState.HOLDING);
        return response(List.of(toDeviceResponse(device, state)), 0, commands, null);
    }

    @Transactional
    public DevDashboardDemoResponse clear() {
        clearMySql();
        clearRedis();
        return new DevDashboardDemoResponse(0, 0, 0, 0, 0, 0, INFLUX_CLEAR_WARNING, List.of());
    }

    private Device saveDevice(int index, DemoState state) {
        String deviceId = "%s%03d".formatted(DEMO_DEVICE_PREFIX, index);
        Device device = deviceRepository.findByDeviceId(deviceId).orElseGet(Device::new);
        device.setDeviceId(deviceId);
        device.setName("Demo bath %02d".formatted(index));
        device.setEnabled(state.enabled());
        device.setControlTargetTemp(state.targetTemp());
        device.setControlHysteresis(DEFAULT_HYSTERESIS);
        return deviceRepository.save(device);
    }

    private int writeTelemetrySamples(String deviceId, DemoState state, Instant now) {
        BigDecimal startTemp = state.state() == DeviceState.HEATING
                ? state.temp().subtract(BigDecimal.valueOf(4.2))
                : state.temp().subtract(BigDecimal.valueOf(0.4));
        for (int offset = TELEMETRY_POINTS_PER_DEVICE - 1; offset >= 0; offset--) {
            BigDecimal temp = interpolate(startTemp, state.temp(), TELEMETRY_POINTS_PER_DEVICE - 1 - offset, TELEMETRY_POINTS_PER_DEVICE - 1);
            temperatureTimeSeriesPort.save(new DeviceStatusMessage(deviceId, temp, state.state(), state.targetTemp()), now.minusSeconds(offset * 60L));
        }
        return TELEMETRY_POINTS_PER_DEVICE;
    }

    private int createCommandSamples(Device device, List<DeviceCommandStatus> statuses) {
        int created = 0;
        Instant now = Instant.now();
        for (DeviceCommandStatus status : statuses) {
            DeviceCommand command = new DeviceCommand();
            command.setDevicePk(device.getId());
            command.setDeviceId(device.getDeviceId());
            command.setCommandType(commandTypeFor(status));
            command.setStatus(status);
            command.setTopic("devices/%s/cmd".formatted(device.getDeviceId()));
            command.setRequestedAt(now.minusSeconds(60L * (created + 1)));
            command.setIdempotencyKey("demo:%s:%s:%d".formatted(device.getDeviceId(), status.name().toLowerCase(Locale.ROOT), now.toEpochMilli()));
            command.setPayload("{\"demo\":true,\"status\":\"%s\"}".formatted(status.name()));
            command.setRetryCount(status == DeviceCommandStatus.FAILED ? 3 : 0);
            command.setMaxRetries(3);
            command.setExpireAt(command.getRequestedAt().plusSeconds(30));
            if (status == DeviceCommandStatus.SENT || status == DeviceCommandStatus.EXPIRED || status == DeviceCommandStatus.FAILED) {
                command.setSentAt(command.getRequestedAt().plusSeconds(1));
            }
            if (status == DeviceCommandStatus.SENT || status == DeviceCommandStatus.PENDING) {
                command.setNextRetryAt(now.plusSeconds(30));
            }
            if (status == DeviceCommandStatus.FAILED) {
                command.setErrorMessage("Demo publish failure");
            } else if (status == DeviceCommandStatus.EXPIRED) {
                command.setErrorMessage("Demo ACK timeout");
            }
            deviceCommandRepository.save(command);
            created++;
        }
        return created;
    }

    private void clearMySql() {
        deviceCommandRepository.deleteByDeviceIdStartingWith(DEMO_DEVICE_PREFIX);
        deviceRepository.deleteByDeviceIdStartingWith(DEMO_DEVICE_PREFIX);
    }

    private void clearRedis() {
        Set<String> active = redisTemplate.opsForSet().members(TRACKED_DEVICES_KEY);
        if (active != null) {
            for (String deviceId : active) {
                if (isDemoDeviceId(deviceId)) {
                    redisTemplate.opsForSet().remove(TRACKED_DEVICES_KEY, deviceId);
                    clearRedisDevice(deviceId);
                }
            }
        }
        deleteRedisKeys("device:%s*:lastSeen".formatted(DEMO_DEVICE_PREFIX));
        deleteRedisKeys("watchdog:%s*:offline-notified".formatted(DEMO_DEVICE_PREFIX));
    }

    private void clearRedisDevice(String deviceId) {
        redisTemplate.delete("device:%s:lastSeen".formatted(deviceId));
        redisTemplate.delete("watchdog:%s:offline-notified".formatted(deviceId));
    }

    private void deleteRedisKeys(String pattern) {
        Set<String> keys = redisTemplate.keys(pattern);
        if (keys != null && !keys.isEmpty()) {
            redisTemplate.delete(keys);
        }
    }

    private DevDashboardDemoResponse response(
            List<DevDashboardDemoDeviceResponse> devices,
            int temperaturePoints,
            int commands,
            String warning
    ) {
        int onlineDevices = (int) devices.stream().filter(DevDashboardDemoDeviceResponse::online).count();
        int disabledDevices = (int) devices.stream().filter(device -> !device.enabled()).count();
        return new DevDashboardDemoResponse(
                devices.size(),
                onlineDevices,
                devices.size() - onlineDevices,
                disabledDevices,
                temperaturePoints,
                commands,
                warning,
                devices
        );
    }

    private DevDashboardDemoDeviceResponse toDeviceResponse(Device device, DemoState state) {
        return new DevDashboardDemoDeviceResponse(
                device.getId(),
                device.getDeviceId(),
                device.getName(),
                device.isEnabled(),
                state.online(),
                state.temp(),
                state.targetTemp(),
                state.state()
        );
    }

    private DemoState scenarioState(DemoScenario scenario, int index, BigDecimal targetTemp) {
        return switch (scenario) {
            case HEATING -> new DemoState(true, true, targetTemp.subtract(BigDecimal.valueOf(4.0 + index * 0.2)), targetTemp, DeviceState.HEATING);
            case HOLDING -> new DemoState(true, true, targetTemp.subtract(BigDecimal.valueOf(index % 2 == 0 ? 0.1 : -0.1)), targetTemp, DeviceState.HOLDING);
            case OFFLINE -> new DemoState(false, true, targetTemp.subtract(BigDecimal.valueOf(1.2)), targetTemp, DeviceState.OFF);
            case COMMAND_FAILURE -> new DemoState(true, true, targetTemp.subtract(BigDecimal.valueOf(0.6)), targetTemp, DeviceState.HOLDING);
            case MIXED -> mixedState(index, targetTemp);
        };
    }

    private DemoState mixedState(int index, BigDecimal targetTemp) {
        return switch ((index - 1) % 5) {
            case 0 -> new DemoState(true, true, targetTemp.subtract(BigDecimal.valueOf(3.2)), targetTemp, DeviceState.HEATING);
            case 1 -> new DemoState(true, true, targetTemp.subtract(BigDecimal.valueOf(0.1)), targetTemp, DeviceState.HOLDING);
            case 2 -> new DemoState(false, true, targetTemp.subtract(BigDecimal.valueOf(1.0)), targetTemp, DeviceState.OFF);
            case 3 -> new DemoState(true, false, targetTemp.subtract(BigDecimal.valueOf(2.0)), targetTemp, DeviceState.OFF);
            default -> new DemoState(true, true, targetTemp.subtract(BigDecimal.valueOf(5.0)), targetTemp, DeviceState.HEATING);
        };
    }

    private BigDecimal defaultTargetTemp(Device device) {
        return scale(device.getControlTargetTemp() == null ? DEFAULT_TARGET_TEMP : device.getControlTargetTemp());
    }

    private BigDecimal defaultTemp(Device device) {
        return defaultTargetTemp(device).subtract(BigDecimal.valueOf(1.0));
    }

    private ControlAction commandTypeFor(DeviceCommandStatus status) {
        return status == DeviceCommandStatus.PENDING ? ControlAction.HEAT_ON
                : status == DeviceCommandStatus.SENT ? ControlAction.HOLD
                : ControlAction.HEAT_OFF;
    }

    private void assertDemoDeviceId(String deviceId) {
        if (!isDemoDeviceId(deviceId)) {
            throw new InvalidDeviceQueryException("demo deviceId must start with " + DEMO_DEVICE_PREFIX);
        }
    }

    private boolean isDemoDeviceId(String deviceId) {
        return deviceId != null && deviceId.startsWith(DEMO_DEVICE_PREFIX);
    }

    private BigDecimal interpolate(BigDecimal from, BigDecimal to, int index, int maxIndex) {
        if (maxIndex <= 0) {
            return scale(to);
        }
        BigDecimal ratio = BigDecimal.valueOf(index).divide(BigDecimal.valueOf(maxIndex), 4, RoundingMode.HALF_UP);
        return scale(from.add(to.subtract(from).multiply(ratio)));
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private enum DemoScenario {
        MIXED,
        HEATING,
        HOLDING,
        OFFLINE,
        COMMAND_FAILURE;

        static DemoScenario from(String value) {
            if (value == null || value.isBlank()) {
                return MIXED;
            }
            String normalized = value.trim().replace('-', '_').toUpperCase(Locale.ROOT);
            try {
                return DemoScenario.valueOf(normalized);
            } catch (IllegalArgumentException ex) {
                throw new InvalidDeviceQueryException("unsupported demo scenario: " + value);
            }
        }
    }

    private record DemoState(
            boolean online,
            boolean enabled,
            BigDecimal temp,
            BigDecimal targetTemp,
            DeviceState state
    ) {
    }
}
