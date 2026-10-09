package com.nexploy.campuslink.ai.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.nexploy.campuslink.ai.client.AiExtractionClient.ResumeExtraction;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/**
 * Live smoke test for the real LLM extraction path against Groq. It is OPT-IN: it runs only when a
 * {@code GROQ_API_KEY} environment variable is present, so normal CI (no key) skips it entirely and
 * never makes a network call. It flips {@code nexploy.ai.provider=llm} so the autowired
 * {@link AiExtractionClient} is the {@link ChatClientAiExtractionClient}, then makes one real call.
 *
 * <p>Run it from a shell that has GROQ_API_KEY set (restart your terminal after setting the user
 * variable): {@code mvn -f Backend/pom.xml test -Dtest=ChatClientAiExtractionLiveTest}.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "nexploy.ai.provider=llm")
@EnabledIfEnvironmentVariable(named = "GROQ_API_KEY", matches = ".+")
class ChatClientAiExtractionLiveTest {

    @Autowired
    private AiExtractionClient client;

    @Test
    void extractsSkillsFromARealResumeCall() {
        assertThat(client).isInstanceOf(ChatClientAiExtractionClient.class);

        ResumeExtraction result = client.extractResume(
            "Backend engineer with 3 years building REST APIs in Java and Spring Boot, "
                + "plus Python for data tooling. B.Tech in Computer Science.");

        // The model should return at least one grounded skill; each item carries an evidence span.
        assertThat(result.skills()).isNotEmpty();
        assertThat(result.skills()).allSatisfy(item -> {
            assertThat(item.normalizedValue()).isNotBlank();
            assertThat(item.evidence()).isNotBlank();
        });
        assertThat(result.metadata().provider()).isEqualTo("groq-openai");
    }
}
