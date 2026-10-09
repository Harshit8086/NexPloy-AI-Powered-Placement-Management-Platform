package com.nexploy.campuslink.ai.client;

import java.util.List;

/**
 * Boundary to the language-extraction provider (a Spring AI {@code ChatClient} adapter in
 * production; a deterministic rule-based adapter by default). It performs bounded, structured
 * extraction only — resume skills/qualifications and job-description requirements — and returns
 * schema-shaped records. It never mutates authorization, eligibility, offer state, or schedules
 * (SECURITY.md section 6); callers use its output as evidence, not as a decision.
 *
 * <p>Every extracted field carries the verbatim {@code evidence} span it was drawn from, so an
 * officer can trace a claim back to the source text rather than trusting an unexplained result.
 */
public interface AiExtractionClient {

    ResumeExtraction extractResume(String resumeText);

    JobRequirements parseJobDescription(String jobDescriptionText);

    /** Identifies the adapter and prompt version for the audit trail. */
    ExtractionMetadata metadata();

    record ResumeExtraction(
        List<ExtractedItem> skills,
        List<ExtractedItem> qualifications,
        ExtractionMetadata metadata
    ) {
    }

    record JobRequirements(
        List<ExtractedItem> requiredSkills,
        List<ExtractedItem> preferredSkills,
        List<ExtractedItem> qualifications,
        ExtractionMetadata metadata
    ) {
    }

    /**
     * One extracted value plus the verbatim source span that justifies it. {@code normalizedValue}
     * is the canonical skill/qualification name; {@code evidence} is the untrusted source excerpt.
     */
    record ExtractedItem(
        String normalizedValue,
        String evidence
    ) {
    }

    record ExtractionMetadata(
        String provider,
        String modelVersion,
        String promptVersion
    ) {
    }
}
