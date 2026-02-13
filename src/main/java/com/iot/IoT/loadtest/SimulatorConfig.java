package com.iot.IoT.loadtest;

import java.util.HashMap;
import java.util.Map;

public record SimulatorConfig(
        String brokerUrl,
        String topicTemplate,
        int connections,
        int messagesPerSecond,
        int durationSeconds,
        int qos,
        String clientPrefix,
        double baseTemp,
        double targetTemp
) {

    public static SimulatorConfig fromArgs(String[] args) {
        Map<String, String> values = parseArgs(args);

        return new SimulatorConfig(
                values.getOrDefault("broker-url", "tcp://localhost:1883"),
                values.getOrDefault("topic-template", "sousvide/%s/status"),
                Integer.parseInt(values.getOrDefault("connections", "100")),
                Integer.parseInt(values.getOrDefault("messages-per-second", "1")),
                Integer.parseInt(values.getOrDefault("duration-seconds", "60")),
                Integer.parseInt(values.getOrDefault("qos", "1")),
                values.getOrDefault("client-prefix", "sim"),
                Double.parseDouble(values.getOrDefault("base-temp", "60.0")),
                Double.parseDouble(values.getOrDefault("target-temp", "65.0"))
        );
    }

    private static Map<String, String> parseArgs(String[] args) {
        Map<String, String> map = new HashMap<>();
        for (String arg : args) {
            if (!arg.startsWith("--")) {
                continue;
            }
            int idx = arg.indexOf('=');
            if (idx < 0) {
                continue;
            }
            map.put(arg.substring(2, idx), arg.substring(idx + 1));
        }
        return map;
    }
}
