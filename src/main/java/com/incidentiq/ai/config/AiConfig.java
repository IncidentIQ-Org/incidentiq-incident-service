package com.incidentiq.ai.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

/**
 * Wiring for the AI layer.
 *
 * NOTE: the application already has a {@code @LoadBalanced} RestTemplate used for Eureka
 * service-to-service calls. That one resolves hostnames via the discovery server and would
 * break when calling an external host like openrouter.ai. We therefore expose a SEPARATE,
 * plain RestTemplate ("aiRestTemplate") with explicit connect/read timeouts dedicated to
 * outbound LLM calls.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class AiConfig {

    @Bean(name = "aiRestTemplate")
    public RestTemplate aiRestTemplate(AiProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(props.getConnectTimeoutMs());
        factory.setReadTimeout(props.getReadTimeoutMs());
        return new RestTemplate(factory);
    }
}
