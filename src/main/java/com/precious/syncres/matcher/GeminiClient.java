package com.precious.syncres.matcher;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class GeminiClient {

    private final WebClient webClient;
    private final String apiKey;
    private final int maxOutputTokens;
    private final double temperature;
    private final ObjectMapper objectMapper;

    public GeminiClient(WebClient geminiWebClient,
                        @Value("${gemini.api-key}") String apiKey,
                        @Value("${gemini.max-output-tokens:8192}") int maxOutputTokens,
                        @Value("${gemini.temperature:0.2}") double temperature,
                        ObjectMapper objectMapper) {
        this.webClient = geminiWebClient;
        this.apiKey = apiKey;
        this.maxOutputTokens = maxOutputTokens;
        this.temperature = temperature;
        this.objectMapper = objectMapper;
    }

    public String generate(String systemInstruction, String userMessage) {
        Map<String, Object> requestBody = Map.of(
                "system_instruction", Map.of("parts", Map.of("text", systemInstruction)),
                "contents", List.of(Map.of(
                        "role", "user",
                        "parts", List.of(Map.of("text", userMessage))
                )),
                "generationConfig", Map.of(
                        "maxOutputTokens", maxOutputTokens,
                        "temperature", temperature,
                        "responseMimeType", "application/json"
                )
        );

        try {
            String response = webClient.post()
                    .uri(uriBuilder -> uriBuilder
                            .path(":generateContent")
                            .queryParam("key", apiKey)
                            .build())
                    .bodyValue(requestBody)
                    .retrieve()
                    .bodyToMono(String.class)
                    .block();

            JsonNode root = objectMapper.readTree(response);
            String text = root.path("candidates").get(0)
                    .path("content").path("parts").get(0)
                    .path("text").asText();

            return defensiveParse(text);
        } catch (Exception e) {
            log.error("Gemini API call failed", e);
            throw new RuntimeException("Gemini API call failed: " + e.getMessage());
        }
    }

    private String defensiveParse(String text) {
        if (text == null) return null;

        return text.replaceAll("(?s)^```json\\s*", "")
                   .replaceAll("(?s)\\s*```$", "")
                   .trim();
    }
}
