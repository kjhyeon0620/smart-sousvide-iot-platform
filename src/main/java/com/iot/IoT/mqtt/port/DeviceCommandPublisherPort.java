package com.iot.IoT.mqtt.port;

public interface DeviceCommandPublisherPort {

    void publish(String topic, String payload);
}
