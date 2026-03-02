package com.iot.IoT.mqtt.adapter;

import com.iot.IoT.mqtt.port.DeviceCommandPublisherPort;
import com.iot.IoT.service.exception.DeviceCommandPublishException;
import org.eclipse.paho.client.mqttv3.IMqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Component
public class MqttDeviceCommandPublisherAdapter implements DeviceCommandPublisherPort {

    private static final int QOS = 1;
    private static final long TIMEOUT_MS = 5_000L;

    private final MqttPahoClientFactory mqttClientFactory;
    private final String brokerUrl;
    private final String publisherClientId;
    private IMqttAsyncClient client;

    public MqttDeviceCommandPublisherAdapter(
            MqttPahoClientFactory mqttClientFactory,
            @Value("${spring.mqtt.broker-url}") String brokerUrl,
            @Value("${spring.mqtt.client-id}") String clientId
    ) {
        this.mqttClientFactory = mqttClientFactory;
        this.brokerUrl = brokerUrl;
        this.publisherClientId = clientId + "-downlink-publisher";
    }

    @Override
    public synchronized void publish(String topic, String payload) {
        try {
            IMqttAsyncClient mqttClient = getConnectedClient();
            MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
            message.setQos(QOS);
            mqttClient.publish(topic, message).waitForCompletion(TIMEOUT_MS);
        } catch (MqttException ex) {
            throw new DeviceCommandPublishException("MQTT publish failed. topic=" + topic, ex);
        }
    }

    private IMqttAsyncClient getConnectedClient() throws MqttException {
        if (client == null) {
            client = mqttClientFactory.getAsyncClientInstance(brokerUrl, publisherClientId);
        }
        if (!client.isConnected()) {
            client.connect().waitForCompletion(TIMEOUT_MS);
        }
        return client;
    }
}
