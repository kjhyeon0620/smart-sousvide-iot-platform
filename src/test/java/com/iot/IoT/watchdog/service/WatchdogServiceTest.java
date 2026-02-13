package com.iot.IoT.watchdog.service;

import com.iot.IoT.watchdog.event.DeviceOfflineEvent;
import com.iot.IoT.watchdog.port.FailSafeEventPublisher;
import com.iot.IoT.watchdog.port.WatchdogStatePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WatchdogServiceTest {

    private WatchdogStatePort watchdogStatePort;
    private FailSafeEventPublisher failSafeEventPublisher;
    private WatchdogService watchdogService;

    @BeforeEach
    void setUp() {
        watchdogStatePort = Mockito.mock(WatchdogStatePort.class);
        failSafeEventPublisher = Mockito.mock(FailSafeEventPublisher.class);
        watchdogService = new WatchdogService(watchdogStatePort, failSafeEventPublisher);
    }

    @Test
    @DisplayName("Should not publish event when heartbeat is alive")
    void scanAndPublishOfflineEvents_alive() {
        when(watchdogStatePort.findTrackedDeviceIds()).thenReturn(Set.of("SV-001"));
        when(watchdogStatePort.isHeartbeatAlive("SV-001")).thenReturn(true);

        watchdogService.scanAndPublishOfflineEvents();

        verify(failSafeEventPublisher, never()).publishDeviceOffline(any());
    }

    @Test
    @DisplayName("Should publish offline event once when heartbeat expired")
    void scanAndPublishOfflineEvents_offline() {
        Instant lastSeen = Instant.now().minusSeconds(180);
        when(watchdogStatePort.findTrackedDeviceIds()).thenReturn(Set.of("SV-001"));
        when(watchdogStatePort.isHeartbeatAlive("SV-001")).thenReturn(false);
        when(watchdogStatePort.markOfflineNotifiedIfAbsent(eq("SV-001"), any())).thenReturn(true);
        when(watchdogStatePort.findLastSeen("SV-001")).thenReturn(Optional.of(lastSeen));

        watchdogService.scanAndPublishOfflineEvents();

        ArgumentCaptor<DeviceOfflineEvent> captor = ArgumentCaptor.forClass(DeviceOfflineEvent.class);
        verify(failSafeEventPublisher, times(1)).publishDeviceOffline(captor.capture());

        DeviceOfflineEvent event = captor.getValue();
        assertEquals("SV-001", event.deviceId());
        assertEquals(lastSeen, event.lastSeenAt());
        assertNotNull(event.detectedAt());
        assertEquals("heartbeat_expired", event.reason());
    }

    @Test
    @DisplayName("Should suppress duplicate notification within cooldown")
    void scanAndPublishOfflineEvents_duplicateSuppressed() {
        when(watchdogStatePort.findTrackedDeviceIds()).thenReturn(Set.of("SV-001"));
        when(watchdogStatePort.isHeartbeatAlive("SV-001")).thenReturn(false);
        when(watchdogStatePort.markOfflineNotifiedIfAbsent(eq("SV-001"), any())).thenReturn(false);

        watchdogService.scanAndPublishOfflineEvents();

        verify(failSafeEventPublisher, never()).publishDeviceOffline(any());
    }

    @Test
    @DisplayName("Should publish again after cooldown expires")
    void scanAndPublishOfflineEvents_publishAfterCooldown() {
        when(watchdogStatePort.findTrackedDeviceIds()).thenReturn(Set.of("SV-001"));
        when(watchdogStatePort.isHeartbeatAlive("SV-001")).thenReturn(false);
        when(watchdogStatePort.findLastSeen("SV-001")).thenReturn(Optional.empty());
        when(watchdogStatePort.markOfflineNotifiedIfAbsent(eq("SV-001"), any()))
                .thenReturn(false)
                .thenReturn(true);

        watchdogService.scanAndPublishOfflineEvents();
        watchdogService.scanAndPublishOfflineEvents();

        verify(failSafeEventPublisher, times(1)).publishDeviceOffline(any());
    }
}
