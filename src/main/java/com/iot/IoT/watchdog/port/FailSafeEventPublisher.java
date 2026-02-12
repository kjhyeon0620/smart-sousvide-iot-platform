package com.iot.IoT.watchdog.port;

import com.iot.IoT.watchdog.event.DeviceOfflineEvent;

public interface FailSafeEventPublisher {

    void publishDeviceOffline(DeviceOfflineEvent event);
}
