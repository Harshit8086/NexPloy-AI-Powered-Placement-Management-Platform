package com.nexploy.campuslink.ai.service;

import com.nexploy.campuslink.ai.client.AiExtractionClient;
import com.nexploy.campuslink.ai.client.AiExtractionClient.JobRequirements;
import com.nexploy.campuslink.ai.client.AiExtractionClient.ResumeExtraction;
import com.nexploy.campuslink.auth.entity.UserAccount;
import com.nexploy.campuslink.auth.service.CampusAccessService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Orchestrates bounded language extraction. It requires an authenticated user, screens the
 * untrusted text for prompt injection, then delegates to the extraction boundary. The result is
 * read-only structured evidence: it never mutates authorization, eligibility, offer state, or
 * schedules (SECURITY.md section 6, ADR-005). Officers use it to speed up data entry and review,
 * with the source evidence attached so every extracted item is traceable.
 */
@Service
@RequiredArgsConstructor
public class ExtractionService {

    private final PromptInjectionGuard promptInjectionGuard;
    private final AiExtractionClient aiExtractionClient;
    private final CampusAccessService campusAccessService;

    public ResumeExtraction extractResume(String resumeText) {
        // Authentication is required even though extraction touches no campus-scoped record;
        // currentUser() throws if the request is anonymous, so this endpoint is never public.
        requireAuthenticated();
        promptInjectionGuard.verify(resumeText);
        return aiExtractionClient.extractResume(resumeText);
    }

    public JobRequirements parseJobDescription(String jobDescriptionText) {
        requireAuthenticated();
        promptInjectionGuard.verify(jobDescriptionText);
        return aiExtractionClient.parseJobDescription(jobDescriptionText);
    }

    private void requireAuthenticated() {
        UserAccount user = campusAccessService.currentUser();
        if (user == null) {
            throw new org.springframework.security.access.AccessDeniedException("Authentication required");
        }
    }
}
