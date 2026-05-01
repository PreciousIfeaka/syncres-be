package com.precious.syncres.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Value("${gemini.model:gemini-1.5-pro}")
    private String model;

    @Bean
    public WebClient geminiWebClient(WebClient.Builder builder) {
        return builder
                .baseUrl("https://generativelanguage.googleapis.com/v1beta/models/" + model)
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();
    }
}
