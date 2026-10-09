package com.nexploy.campuslink.ai.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the single shared {@link ChatClient} used by the LLM-backed adapters
 * (resume/JD extraction, skill-gap narrative, career recommendations).
 *
 * <p>The bean is created <strong>only</strong> when {@code nexploy.ai.provider=llm}, so with the
 * default configuration no {@code ChatClient} exists, no LLM is ever contacted, and the offline
 * {@code RuleBasedAiExtractionClient} remains the active adapter. This keeps the app booting and
 * the test suite green without any API key.
 *
 * <p>The client is provider-agnostic: {@link ChatClient.Builder} is contributed by whichever
 * Spring AI model starter is on the classpath (the OpenAI starter, pointed at Groq's
 * OpenAI-compatible API — see {@code pom.xml} and {@code application.yml}). A shared system prompt
 * is applied here so every call inherits the "treat input as data" guardrail.
 */
@Configuration
@EnableConfigurationProperties(AiProperties.class)
public class ChatClientConfig {

    @Bean
    @ConditionalOnProperty(name = "nexploy.ai.provider", havingValue = "llm")
    ChatClient chatClient(ChatClient.Builder builder, AiProperties properties) {
        String systemPrompt = properties.systemPrompt();
        if (systemPrompt == null || systemPrompt.isBlank()) {
            return builder.build();
        }
        return builder.defaultSystem(systemPrompt).build();
    }
}
