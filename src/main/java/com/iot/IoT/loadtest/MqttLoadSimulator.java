package com.iot.IoT.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttAsyncClient;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class MqttLoadSimulator {

    public static void main(String[] args) throws Exception {
        SimulatorConfig config = SimulatorConfig.fromArgs(args);

        System.out.println("[SIM] starting with config=" + config);

        DevicePayloadFactory payloadFactory = new DevicePayloadFactory(new ObjectMapper());
        MqttAsyncClient[] clients = new MqttAsyncClient[config.connections()];
        double[] currentTemps = new double[config.connections()];
        LongAdder connected = new LongAdder();
        LongAdder connectFailed = new LongAdder();
        Queue<Throwable> connectErrors = new ConcurrentLinkedQueue<>();

        ExecutorService connectPool = Executors.newFixedThreadPool(config.connectParallelism());
        Future<?>[] connectFutures = new Future[config.connections()];

        for (int i = 0; i < config.connections(); i++) {
            int index = i;
            connectFutures[i] = connectPool.submit(() -> {
                int globalIndex = config.startIndex() + index;
                String clientId = config.clientPrefix() + "-" + globalIndex;
                currentTemps[index] = config.baseTemp() + (globalIndex % 10) * 0.05;

                try {
                    MqttAsyncClient client = new MqttAsyncClient(config.brokerUrl(), clientId, new MemoryPersistence());
                    client.setCallback(new NoopCallback());

                    MqttConnectOptions options = new MqttConnectOptions();
                    options.setAutomaticReconnect(true);
                    options.setCleanSession(true);
                    client.connect(options).waitForCompletion();

                    clients[index] = client;
                    connected.increment();
                    long now = connected.sum();
                    if (now % 200 == 0 || now == config.connections()) {
                        System.out.printf("[SIM] connected %d/%d clients%n", now, config.connections());
                    }
                } catch (Exception e) {
                    connectFailed.increment();
                    connectErrors.add(e);
                }
            });
        }

        for (Future<?> future : connectFutures) {
            future.get();
        }
        connectPool.shutdown();

        if (connectFailed.sum() > 0) {
            throw new IllegalStateException("Failed to connect clients: " + connectFailed.sum(), connectErrors.peek());
        }

        LongAdder published = new LongAdder();
        LongAdder failed = new LongAdder();

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Instant startedAt = Instant.now();

        Runnable publishTask = () -> {
            for (int i = 0; i < clients.length; i++) {
                MqttAsyncClient client = clients[i];
                if (client == null || !client.isConnected()) {
                    failed.increment();
                    continue;
                }
                String deviceId = "SV-" + String.format("%05d", config.startIndex() + i);
                String topic = String.format(config.topicTemplate(), deviceId);

                currentTemps[i] = payloadFactory.nextTemp(currentTemps[i], config.targetTemp());
                String payload = payloadFactory.create(deviceId, currentTemps[i], config.targetTemp());

                for (int j = 0; j < config.messagesPerSecond(); j++) {
                    try {
                        MqttMessage message = new MqttMessage(payload.getBytes(StandardCharsets.UTF_8));
                        message.setQos(config.qos());
                        client.publish(topic, message);
                        published.increment();
                    } catch (MqttException e) {
                        failed.increment();
                    }
                }
            }
        };

        scheduler.scheduleAtFixedRate(publishTask, 0, 1, TimeUnit.SECONDS);
        Thread.sleep(config.durationSeconds() * 1000L);
        scheduler.shutdownNow();

        for (MqttAsyncClient client : clients) {
            if (client == null) {
                continue;
            }
            try {
                if (client.isConnected()) {
                    client.disconnect().waitForCompletion();
                }
                client.close();
            } catch (Exception ignored) {
                // best-effort shutdown
            }
        }

        Duration duration = Duration.between(startedAt, Instant.now());
        double seconds = Math.max(1.0, duration.toMillis() / 1000.0);
        long sent = published.sum();
        long error = failed.sum();
        double tps = sent / seconds;

        System.out.println("[SIM] finished");
        System.out.println("[SIM] durationSec=" + seconds);
        System.out.println("[SIM] published=" + sent);
        System.out.println("[SIM] failed=" + error);
        System.out.println("[SIM] throughput(msg/sec)=" + String.format("%.2f", tps));
    }

    private static class NoopCallback implements MqttCallback {

        @Override
        public void connectionLost(Throwable cause) {
            // ignore in simulator
        }

        @Override
        public void messageArrived(String topic, MqttMessage message) {
            // publish-only simulator
        }

        @Override
        public void deliveryComplete(IMqttDeliveryToken token) {
            // no-op
        }
    }
}
