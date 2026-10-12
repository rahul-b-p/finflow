package com.finflow.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI finflowOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("FinFlow Modular Monolith API")
                        .version("1.0.0")
                        .description("FinFlow Enterprise Financial Workflow Engine - Module 1: Application Foundation")
                        .contact(new Contact()
                                .name("FinFlow Platform Architecture Team")
                                .email("architecture@finflow.internal"))
                        .license(new License()
                                .name("Proprietary")
                                .url("https://finflow.internal/license")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Local Development Environment"),
                        new Server().url("https://staging.finflow.internal").description("Staging Environment")
                ));
    }
}
