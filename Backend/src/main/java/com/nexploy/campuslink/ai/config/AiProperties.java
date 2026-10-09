package com.nexploy.campuslink.ai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code nexploy.ai} namespace (our wiring, distinct from Spring AI's own
 * {@code spring.ai.*} provider settings).
 *
 * @param provider     which extraction/analysis adapter is active: {@code "rule-based"} (default,
 *                     fully offline) or {@code "llm"} (the Spring AI {@link
 *                     org.springframework.ai.chat.client.ChatClient} path). Chosen so the LLM is
 *                     never reached unless explicitly enabled.
 * @param systemPrompt the shared system message applied to every {@code ChatClient} call; it
 *                     instructs the model to treat input as data, not instructions.
 */
@ConfigurationProperties(prefix = "nexploy.ai")
public record AiProperties(
    String provider,
    String systemPrompt
) {

    public boolean llmEnabled() {
        return "llm".equalsIgnoreCase(provider);
    }
}
