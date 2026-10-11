package com.iot.IoT.ingestion.config;

import com.iot.IoT.ingestion.metrics.IngestionMetricsCollector;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.integration.annotation.IntegrationComponentScan;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.channel.ExecutorChannel;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

@Configuration
@IntegrationComponentScan
public class MqttConfig {

    public static final String INGESTION_ENQUEUED_AT_NANOS_HEADER = "ingestionEnqueuedAtNanos";
    private static final String CHANNEL_MODE_DIRECT = "direct";
    private static final String SHARED_SUBSCRIPTION_GROUP = "ingestion";

    @Value("${spring.mqtt.broker-url}")
    private String brokerUrl;

    @Value("${spring.mqtt.username:}")
    private String username;

    @Value("${spring.mqtt.password:}")
    private String password;

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
        // Saturation must not propagate to the Paho callback: an exception there drops the subscription.
        // Running on the caller (MQTT receive thread) slows intake instead, so excess load backs up in the broker.
        executor.setRejectedExecutionHandler((task, pool) -> {
            ingestionMetricsCollector.recordExecutorRejected();
            if (!pool.isShutdown()) {
                task.run();
            }
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
        // Spring Integration 7 adapter has no recovery loop; it re-subscribes in connectComplete() after Paho reconnects.
        options.setAutomaticReconnect(true);

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
    public static BeanDefinitionRegistryPostProcessor inboundAdapterRegistrar(Environment environment) {
        return registry -> {
            int subscriberCount = environment.getProperty("spring.mqtt.subscriber-count", Integer.class, 1);
            String clientId = environment.getRequiredProperty("spring.mqtt.client-id");
            String topic = environment.getRequiredProperty("spring.mqtt.default-topic");
            for (InboundSubscription subscription : inboundSubscriptions(subscriberCount, clientId, topic)) {
                registry.registerBeanDefinition("inbound-" + subscription.clientId(),
                        BeanDefinitionBuilder.genericBeanDefinition(MqttPahoMessageDrivenChannelAdapter.class)
                                .addConstructorArgValue(subscription.clientId())
                                .addConstructorArgReference("mqttClientFactory")
                                .addConstructorArgValue(new String[]{subscription.topic()})
                                .addPropertyValue("completionTimeout", 5_000)
                                .addPropertyValue("qos", 1)
                                .addPropertyValue("outputChannelName", "mqttInputChannel")
                                .getBeanDefinition());
            }
        };
    }

    /**
     * One subscriber keeps the plain topic and client id. More than one splits the load
     * across clients with a broker shared subscription, so per-device ordering is not guaranteed.
     */
    static List<InboundSubscription> inboundSubscriptions(int subscriberCount, String clientId, String topic) {
        if (subscriberCount <= 1) {
            return List.of(new InboundSubscription(clientId, topic));
        }
        String sharedTopic = "$share/" + SHARED_SUBSCRIPTION_GROUP + "/" + topic;
        return IntStream.range(0, subscriberCount)
                .mapToObj(i -> new InboundSubscription(clientId + "-" + i, sharedTopic))
                .toList();
    }

    record InboundSubscription(String clientId, String topic) {
    }
}
