package com.iot.IoT.ingestion.config;

import com.iot.IoT.ingestion.metrics.IngestionMetricsCollector;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

@Configuration
@IntegrationComponentScan
public class MqttConfig {

    public static final String INGESTION_ENQUEUED_AT_NANOS_HEADER = "ingestionEnqueuedAtNanos";
    private static final String CHANNEL_MODE_DIRECT = "direct";

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

    @Value("${ingestion.channel.mode:executor}")
    private String ingestionChannelMode;

    @Bean
    public MessageChannel mqttInputChannel(
            Executor mqttIngestionExecutor,
            IngestionMetricsCollector ingestionMetricsCollector
    ) {
        ChannelInterceptor enqueuedAtInterceptor = new ChannelInterceptor() {
            @Override
            public org.springframework.messaging.Message<?> preSend(
                    org.springframework.messaging.Message<?> message,
                    MessageChannel channel
            ) {
                if (message.getHeaders().containsKey(INGESTION_ENQUEUED_AT_NANOS_HEADER)) {
                    return message;
                }
                return MessageBuilder.fromMessage(message)
                        .setHeader(INGESTION_ENQUEUED_AT_NANOS_HEADER, System.nanoTime())
                        .build();
            }
        };

        if (CHANNEL_MODE_DIRECT.equalsIgnoreCase(ingestionChannelMode)) {
            DirectChannel channel = new DirectChannel();
            channel.addInterceptor(enqueuedAtInterceptor);
            return channel;
        }

        ExecutorChannel channel = new ExecutorChannel(mqttIngestionExecutor);
        channel.addInterceptor(enqueuedAtInterceptor);
        return channel;
    }

    @Bean
    public Executor mqttIngestionExecutor(
            IngestionMetricsCollector ingestionMetricsCollector,
            io.micrometer.core.instrument.MeterRegistry meterRegistry
    ) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("mqtt-ingest-");
        executor.setCorePoolSize(executorCorePoolSize);
        executor.setMaxPoolSize(Math.max(executorMaxPoolSize, executorCorePoolSize));
        executor.setQueueCapacity(Math.max(executorQueueCapacity, 1));
        executor.setTaskDecorator(task -> {
            long enqueuedAtNanos = System.nanoTime();
            return () -> {
                ingestionMetricsCollector.recordExecutorQueueWait(System.nanoTime() - enqueuedAtNanos);
                task.run();
            };
        });
        executor.setRejectedExecutionHandler((task, pool) -> {
            ingestionMetricsCollector.recordExecutorRejected();
            throw new RejectedExecutionException("mqtt ingestion executor saturated");
        });
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(10);
        executor.initialize();
        ingestionMetricsCollector.registerExecutorMetrics(executor, meterRegistry);
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
