package com.iot.IoT.loadtest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt5.Mqtt5AsyncClient;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

public class MqttLoadSimulatorHive {

    public static void main(String[] args) throws Exception {
        SimulatorConfig config = SimulatorConfig.fromArgs(args);
        URI broker = URI.create(config.brokerUrl().replace("tcp://", "mqtt://"));

        System.out.println("[SIM-HIVE] starting with config=" + config);

        DevicePayloadFactory payloadFactory = new DevicePayloadFactory(new ObjectMapper());
        Mqtt5AsyncClient[] clients = new Mqtt5AsyncClient[config.connections()];
        double[] currentTemps = new double[config.connections()];

        LongAdder connected = new LongAdder();
        LongAdder connectFailed = new LongAdder();

        ExecutorService connectPool = Executors.newFixedThreadPool(config.connectParallelism());
        Future<?>[] connectFutures = new Future[config.connections()];

        for (int i = 0; i < config.connections(); i++) {
            int index = i;
            connectFutures[i] = connectPool.submit(() -> {
                int globalIndex = config.startIndex() + index;
                String clientId = config.clientPrefix() + "-" + globalIndex;
                currentTemps[index] = config.baseTemp() + (globalIndex % 10) * 0.05;

                try {
                    Mqtt5AsyncClient client = MqttClient.builder()
                            .useMqttVersion5()
                            .serverHost(broker.getHost())
                            .serverPort(broker.getPort() == -1 ? 1883 : broker.getPort())
                            .identifier(clientId)
                            .buildAsync();

                    client.connectWith()
                            .cleanStart(true)
                            .send()
                            .toCompletableFuture()
                            .get(15, TimeUnit.SECONDS);
                    clients[index] = client;

                    connected.increment();
                    long now = connected.sum();
                    if (now % 200 == 0 || now == config.connections()) {
                        System.out.printf("[SIM-HIVE] connected %d/%d clients%n", now, config.connections());
                    }
                } catch (Exception e) {
                    connectFailed.increment();
                }
            });
        }

        for (Future<?> future : connectFutures) {
            future.get();
        }
        connectPool.shutdown();

        if (connectFailed.sum() > 0) {
            throw new IllegalStateException("Failed to connect clients: " + connectFailed.sum());
        }

        LongAdder published = new LongAdder();
        LongAdder failed = new LongAdder();
        MqttQos qos = toQos(config.qos());

        ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
        Instant startedAt = Instant.now();

        Runnable publishTask = () -> {
            for (int i = 0; i < clients.length; i++) {
                Mqtt5AsyncClient client = clients[i];
                if (client == null) {
                    failed.increment();
                    continue;
                }

                String deviceId = "SV-" + String.format("%05d", config.startIndex() + i);
                String topic = String.format(config.topicTemplate(), deviceId);
                currentTemps[i] = payloadFactory.nextTemp(currentTemps[i], config.targetTemp());
                String payload = payloadFactory.create(deviceId, currentTemps[i], config.targetTemp());
                byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);

                for (int j = 0; j < config.messagesPerSecond(); j++) {
                    try {
                        client.publishWith()
                                .topic(topic)
                                .qos(qos)
                                .payload(bytes)
                                .send()
                                .whenComplete((ack, ex) -> {
                                    if (ex != null) {
                                        failed.increment();
                                    }
                                });
                        published.increment();
                    } catch (Exception e) {
                        failed.increment();
                    }
                }
            }
        };

        scheduler.scheduleAtFixedRate(publishTask, 0, 1, TimeUnit.SECONDS);
        Thread.sleep(config.durationSeconds() * 1000L);
        scheduler.shutdownNow();

        for (Mqtt5AsyncClient client : clients) {
            if (client == null) {
                continue;
            }
            try {
                client.disconnectWith().send().toCompletableFuture().get(2, TimeUnit.SECONDS);
            } catch (Exception ignored) {
                // best-effort shutdown
            }
        }

        Duration duration = Duration.between(startedAt, Instant.now());
        double wallSeconds = Math.max(1.0, duration.toMillis() / 1000.0);
        double activeSeconds = Math.max(1.0, config.durationSeconds());
        long sent = published.sum();
        long error = failed.sum();
        double tpsActive = sent / activeSeconds;
        double tpsWall = sent / wallSeconds;

        System.out.println("[SIM-HIVE] finished");
        System.out.println("[SIM-HIVE] durationSec=" + activeSeconds);
        System.out.println("[SIM-HIVE] wallDurationSec=" + String.format("%.3f", wallSeconds));
        System.out.println("[SIM-HIVE] published=" + sent);
        System.out.println("[SIM-HIVE] failed=" + error);
        System.out.println("[SIM-HIVE] throughput(msg/sec)=" + String.format("%.2f", tpsActive));
        System.out.println("[SIM-HIVE] throughputWall(msg/sec)=" + String.format("%.2f", tpsWall));
    }

    private static MqttQos toQos(int qos) {
        return switch (qos) {
            case 0 -> MqttQos.AT_MOST_ONCE;
            case 2 -> MqttQos.EXACTLY_ONCE;
            default -> MqttQos.AT_LEAST_ONCE;
        };
    }
}
