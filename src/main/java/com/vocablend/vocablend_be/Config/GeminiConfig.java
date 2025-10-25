package com.vocablend.vocablend_be.Config;

import com.google.genai.Client;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GeminiConfig {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    @Bean
    public Client geminiClient() {
        return new Client.Builder()
                .apiKey(geminiApiKey)
                .build();
    }
}
