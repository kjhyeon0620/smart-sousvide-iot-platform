package com.iot.IoT.ingestion.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class InfluxDbConfig {

    @Bean(destroyMethod = "close")
    public InfluxDBClient influxDBClient(
            @Value("${influxdb.url}") String url,
            @Value("${influxdb.token}") String token
    ) {
        return InfluxDBClientFactory.create(url, token.toCharArray());
    }
}
