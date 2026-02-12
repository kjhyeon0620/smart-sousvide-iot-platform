package com.iot.IoT.ingestion.adapter.redis;

import com.iot.IoT.ingestion.port.HeartbeatPort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;

@Component
public class RedisHeartbeatAdapter implements HeartbeatPort {

    private static final String KEY_PREFIX = "device:";
    private static final String KEY_SUFFIX = ":lastSeen";

    private final StringRedisTemplate redisTemplate;
    private final Duration heartbeatTtl;

    public RedisHeartbeatAdapter(
            StringRedisTemplate redisTemplate,
            @Value("${ingestion.heartbeat-ttl-seconds:120}") long heartbeatTtlSeconds
    ) {
        this.redisTemplate = redisTemplate;
        this.heartbeatTtl = Duration.ofSeconds(heartbeatTtlSeconds);
    }

    @Override
    public void updateLastSeen(String deviceId, Instant lastSeenAt) {
        String key = KEY_PREFIX + deviceId + KEY_SUFFIX;
        String value = String.valueOf(lastSeenAt.toEpochMilli());
        redisTemplate.opsForValue().set(key, value, heartbeatTtl);
    }
}
