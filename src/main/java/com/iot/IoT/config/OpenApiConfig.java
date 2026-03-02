package com.iot.IoT.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI iotOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("IoT Backend API")
                        .description("Device management, downlink command, and reliability APIs")
                        .version("v1")
                        .contact(new Contact().name("IoT Backend Team"))
                        .license(new License().name("Internal Use")));
    }

    @Bean
    public GroupedOpenApi deviceApiGroup() {
        return GroupedOpenApi.builder()
                .group("device-api")
                .pathsToMatch("/devices/**")
                .build();
    }
}
