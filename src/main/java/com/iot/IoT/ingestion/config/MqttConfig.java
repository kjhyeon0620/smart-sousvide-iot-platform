package com.iot.IoT.ingestion.config;

import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@IntegrationComponentScan
public class MqttConfig {

    @Value("${spring.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${spring.mqtt.client-id}")
    private String clientId;

    @Value("${spring.mqtt.username:}")
    private String username;

    @Value("${spring.mqtt.password:}")
    private String password;

    @Value("${spring.mqtt.default-topic}")
    private String defaultTopic;

    @Value("${ingestion.executor.core-pool-size:4}")
    private int executorCorePoolSize;

    @Value("${ingestion.executor.max-pool-size:16}")
    private int executorMaxPoolSize;

    @Value("${ingestion.executor.queue-capacity:5000}")
    private int executorQueueCapacity;

    @Bean
    public MessageChannel mqttInputChannel() {
        return new ExecutorChannel(mqttIngestionExecutor());
    }

    @Bean
    public Executor mqttIngestionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mqtt-ingest-");
        executor.setCorePoolSize(executorCorePoolSize);
        executor.setMaxPoolSize(Math.max(executorMaxPoolSize, executorCorePoolSize));
        executor.setQueueCapacity(Math.max(executorQueueCapacity, 1));
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        return executor;
    }

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        MqttConnectOptions options = new MqttConnectOptions();
        options.setServerURIs(new String[]{brokerUrl});
        options.setCleanSession(true);

        if (!username.isBlank()) {
            options.setUserName(username);
        }
        if (!password.isBlank()) {
            options.setPassword(password.toCharArray());
        }

        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        factory.setConnectionOptions(options);
        return factory;
    }

    @Bean
    public MessageProducer inbound(MqttPahoClientFactory mqttClientFactory) {
        MqttPahoMessageDrivenChannelAdapter adapter =
                new MqttPahoMessageDrivenChannelAdapter(clientId, mqttClientFactory, defaultTopic);
        adapter.setCompletionTimeout(5_000);
        adapter.setQos(1);
        adapter.setOutputChannelName("mqttInputChannel");
        return adapter;
    }
}
