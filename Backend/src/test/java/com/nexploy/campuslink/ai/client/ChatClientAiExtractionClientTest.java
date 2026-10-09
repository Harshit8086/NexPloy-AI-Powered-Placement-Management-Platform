package com.nexploy.campuslink.ai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.nexploy.campuslink.ai.client.AiExtractionClient.ExtractedItem;
import com.nexploy.campuslink.ai.client.AiExtractionClient.ResumeExtraction;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit test for the LLM adapter's mapping and metadata, with the {@link ChatClient} mocked so no
 * real model is called. The adapter reads the raw model reply via {@code .content()} and converts it
 * itself, so these tests stub the returned JSON string. They verify the structured payload is wrapped
 * with our audit metadata and — the reported production 500 — that an empty or unparseable reply
 * degrades to empty lists rather than throwing.
 */
class ChatClientAiExtractionClientTest {

    private final ChatClient chatClient = mock(ChatClient.class, RETURNS_DEEP_STUBS);
    private final ChatClientAiExtractionClient client = build();

    private ChatClientAiExtractionClient build() {
        ChatClientAiExtractionClient c = new ChatClientAiExtractionClient(chatClient);
        // @Value is not injected when constructed directly; set it for the metadata assertion.
        ReflectionTestUtils.setField(c, "modelVersion", "openai/gpt-oss-120b");
        return c;
    }

    private void stubContent(String content) {
        when(chatClient.prompt().user(anyString()).call().content()).thenReturn(content);
    }

    @Test
    void wrapsStructuredPayloadWithAuditMetadata() {
        stubContent("""
            {
              "skills": [{"normalizedValue": "Java", "evidence": "skilled in Java"}],
              "qualifications": [{"normalizedValue": "B.Tech", "evidence": "B.Tech in CSE"}]
            }
            """);

        ResumeExtraction result = client.extractResume("skilled in Java, B.Tech in CSE");

        assertThat(result.skills()).extracting(ExtractedItem::normalizedValue).containsExactly("Java");
        assertThat(result.qualifications()).extracting(ExtractedItem::normalizedValue).containsExactly("B.Tech");
        assertThat(result.metadata().provider()).isEqualTo("groq-openai");
        assertThat(result.metadata().modelVersion()).isEqualTo("openai/gpt-oss-120b");
        assertThat(result.metadata().promptVersion()).isNotBlank();
    }

    @Test
    void emptyModelResponseDegradesToEmptyLists() {
        // The exact production failure: the model returned "" and BeanOutputConverter could not parse
        // it. The adapter must treat this as "found nothing", not a 500.
        stubContent("");

        ResumeExtraction result = client.extractResume("anything");

        assertThat(result.skills()).isEmpty();
        assertThat(result.qualifications()).isEmpty();
        assertThat(result.metadata().provider()).isEqualTo("groq-openai");
    }

    @Test
    void nullModelResponseDegradesToEmptyLists() {
        stubContent(null);

        ResumeExtraction result = client.extractResume("anything");

        assertThat(result.skills()).isEmpty();
        assertThat(result.qualifications()).isEmpty();
    }

    @Test
    void unparseableModelResponseDegradesToEmptyLists() {
        stubContent("I could not find any structured data in this document.");

        ResumeExtraction result = client.extractResume("anything");

        assertThat(result.skills()).isEmpty();
        assertThat(result.qualifications()).isEmpty();
    }
}
