package com.iot.IoT.ingestion.config;

import com.iot.IoT.ingestion.metrics.IngestionMetricsCollector;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.verify;

class MqttConfigTest {

    @Test
    @DisplayName("Saturated executor should run the task on the caller thread instead of throwing")
    void saturatedExecutorRunsOnCaller() throws InterruptedException {
        MqttConfig config = new MqttConfig();
        ReflectionTestUtils.setField(config, "executorCorePoolSize", 1);
        ReflectionTestUtils.setField(config, "executorMaxPoolSize", 1);
        ReflectionTestUtils.setField(config, "executorQueueCapacity", 1);
        IngestionMetricsCollector metrics = Mockito.mock(IngestionMetricsCollector.class);
        Executor executor = config.mqttIngestionExecutor(metrics, new SimpleMeterRegistry());

        CountDownLatch release = new CountDownLatch(1);
        Runnable blocker = () -> {
            try {
                release.await(5, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        executor.execute(blocker); // occupies the only worker
        executor.execute(blocker); // fills the queue

        AtomicReference<Thread> ranOn = new AtomicReference<>();
        assertThatCode(() -> executor.execute(() -> ranOn.set(Thread.currentThread())))
                .doesNotThrowAnyException();

        assertThat(ranOn.get()).isSameAs(Thread.currentThread());
        verify(metrics).recordExecutorRejected();
        release.countDown();
        ((ThreadPoolTaskExecutor) executor).shutdown();
    }

    @Test
    @DisplayName("Inbound client should reconnect automatically after connection loss")
    void clientFactoryEnablesAutomaticReconnect() {
        MqttConfig config = new MqttConfig();
        ReflectionTestUtils.setField(config, "brokerUrl", "tcp://localhost:1883");
        ReflectionTestUtils.setField(config, "username", "");
        ReflectionTestUtils.setField(config, "password", "");

        MqttConnectOptions options =
                ((DefaultMqttPahoClientFactory) config.mqttClientFactory()).getConnectionOptions();

        assertThat(options.isAutomaticReconnect()).isTrue();
        assertThat(options.isCleanSession()).isTrue();
    }

    @Test
    @DisplayName("Single subscriber should keep the plain topic and client id")
    void singleSubscriberKeepsPlainTopic() {
        assertThat(MqttConfig.inboundSubscriptions(1, "backend", "sousvide/+/status"))
                .containsExactly(new MqttConfig.InboundSubscription("backend", "sousvide/+/status"));
    }

    @Test
    @DisplayName("Multiple subscribers should share one subscription group with distinct client ids")
    void multipleSubscribersUseSharedSubscription() {
        assertThat(MqttConfig.inboundSubscriptions(3, "backend", "sousvide/+/status"))
                .containsExactly(
                        new MqttConfig.InboundSubscription("backend-0", "$share/ingestion/sousvide/+/status"),
                        new MqttConfig.InboundSubscription("backend-1", "$share/ingestion/sousvide/+/status"),
                        new MqttConfig.InboundSubscription("backend-2", "$share/ingestion/sousvide/+/status"));
    }

    @Test
    @DisplayName("Registrar should register one inbound adapter per subscriber")
    void registrarRegistersAdapterPerSubscriber() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.mqtt.client-id", "backend")
                .withProperty("spring.mqtt.default-topic", "sousvide/+/status")
                .withProperty("spring.mqtt.subscriber-count", "2");
        DefaultListableBeanFactory registry = new DefaultListableBeanFactory();

        MqttConfig.inboundAdapterRegistrar(environment).postProcessBeanDefinitionRegistry(registry);

        assertThat(registry.getBeanDefinitionNames()).containsExactly("inbound-backend-0", "inbound-backend-1");
    }
}
