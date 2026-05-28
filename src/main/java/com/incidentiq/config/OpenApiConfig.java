package com.incidentiq.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger UI configuration.
 * Provides metadata displayed on the Swagger documentation page.
 */
@Configuration
public class OpenApiConfig {

        @Bean

        public OpenAPI incidentIqOpenApi() {
                return new OpenAPI()
                                .info(new Info()
                                                .title("IncidentIQ API")
                                                .description("Enterprise-level Incident Management System — "
                                                                + "Track, manage, assign, and resolve incidents across your organization.")
                                                .version("1.0.0")
                                                .contact(new Contact()
                                                                .name("IncidentIQ Team")
                                                                .email("support@incidentiq.com"))
                                                .license(new License()
                                                                .name("MIT License")
                                                                .url("https://opensource.org/licenses/MIT")))
                                .addSecurityItem(new io.swagger.v3.oas.models.security.SecurityRequirement()
                                                .addList("bearerAuth"))
                                .components(new io.swagger.v3.oas.models.Components()
                                                .addSecuritySchemes("bearerAuth",
                                                                new io.swagger.v3.oas.models.security.SecurityScheme()
                                                                                .name("bearerAuth")
                                                                                .type(io.swagger.v3.oas.models.security.SecurityScheme.Type.HTTP)
                                                                                .scheme("bearer")
                                                                                .bearerFormat("JWT")))
                                .addServersItem(new io.swagger.v3.oas.models.servers.Server()
                                                .url("/api/incidents")
                                                .description("API Gateway"));
        }
}
