package com.iot.IoT.watchdog.adapter;

import com.iot.IoT.watchdog.port.WatchdogStatePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Collections;
import java.util.Optional;
import java.util.Set;

@Component
public class RedisWatchdogStateAdapter implements WatchdogStatePort {

    private static final String HEARTBEAT_KEY_PREFIX = "device:";
    private static final String HEARTBEAT_KEY_SUFFIX = ":lastSeen";
    private static final String TRACKED_DEVICES_KEY = "devices:active";
    private static final String OFFLINE_NOTIFIED_KEY_PREFIX = "watchdog:";
    private static final String OFFLINE_NOTIFIED_KEY_SUFFIX = ":offline-notified";

    private final StringRedisTemplate redisTemplate;
    private final Duration notifyCooldown;

    public RedisWatchdogStateAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${watchdog.offline-notify-cooldown-seconds:60}") long cooldownSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.notifyCooldown = Duration.ofSeconds(cooldownSeconds);
    }

    @Override
    public Set<String> findTrackedDeviceIds() {
        Set<String> members = redisTemplate.opsForSet().members(TRACKED_DEVICES_KEY);
        return members == null ? Collections.emptySet() : members;
    }

    @Override
    public boolean isHeartbeatAlive(String deviceId) {
        Boolean exists = redisTemplate.hasKey(heartbeatKey(deviceId));
        return Boolean.TRUE.equals(exists);
    }

    @Override
    public Optional<Instant> findLastSeen(String deviceId) {
        String value = redisTemplate.opsForValue().get(heartbeatKey(deviceId));
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Instant.ofEpochMilli(Long.parseLong(value)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public boolean markOfflineNotifiedIfAbsent(String deviceId, Instant detectedAt) {
        Boolean created = redisTemplate.opsForValue().setIfAbsent(
                offlineNotifiedKey(deviceId),
                String.valueOf(detectedAt.toEpochMilli()),
                notifyCooldown
        );
        return Boolean.TRUE.equals(created);
    }

    private String heartbeatKey(String deviceId) {
        return HEARTBEAT_KEY_PREFIX + deviceId + HEARTBEAT_KEY_SUFFIX;
    }

    private String offlineNotifiedKey(String deviceId) {
        return OFFLINE_NOTIFIED_KEY_PREFIX + deviceId + OFFLINE_NOTIFIED_KEY_SUFFIX;
    }
}
