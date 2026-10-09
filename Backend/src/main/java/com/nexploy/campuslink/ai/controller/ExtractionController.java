package com.nexploy.campuslink.ai.controller;

import com.nexploy.campuslink.ai.client.AiExtractionClient.JobRequirements;
import com.nexploy.campuslink.ai.client.AiExtractionClient.ResumeExtraction;
import com.nexploy.campuslink.ai.dto.ExtractionRequestBody;
import com.nexploy.campuslink.ai.service.ExtractionService;
import com.nexploy.campuslink.ai.service.ResumeTextExtractor;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Bounded AI extraction endpoints. Output is structured, evidence-bearing, and read-only — it
 * never mutates domain state, so an officer reviews and applies it manually.
 */
@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class ExtractionController {

    private final ExtractionService extractionService;
    private final ResumeTextExtractor resumeTextExtractor;
    private final com.nexploy.campuslink.ai.service.ResumeSummaryService resumeSummaryService;

    @PostMapping("/resume/generate-summary")
    public com.nexploy.campuslink.ai.dto.ResumeSummaryResponse generateSummary(
        @RequestBody com.nexploy.campuslink.ai.dto.ResumeSummaryRequest request
    ) {
        return resumeSummaryService.generateSummary(request);
    }

    @PostMapping("/resume/extract")
    public ResumeExtraction extractResume(@Valid @RequestBody ExtractionRequestBody body) {
        return extractionService.extractResume(body.text());
    }

    /**
     * Extracts skills/qualifications from an uploaded resume file (PDF or text). The file is parsed
     * to text server-side, then screened for prompt injection and extracted like the text endpoint.
     */
    @PostMapping(value = "/resume/extract-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResumeExtraction extractResumeFile(@RequestParam("file") MultipartFile file) {
        return extractionService.extractResume(resumeTextExtractor.extract(file));
    }

    @PostMapping("/job-description/parse")
    public JobRequirements parseJobDescription(@Valid @RequestBody ExtractionRequestBody body) {
        return extractionService.parseJobDescription(body.text());
    }

    /**
     * Extracts required/preferred skills and qualifications from an uploaded job description file
     * (PDF or text). The file is parsed to text server-side, then screened for prompt injection
     * and extracted like the text endpoint.
     */
    @PostMapping(value = "/job-description/parse-file", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public JobRequirements parseJobDescriptionFile(@RequestParam("file") MultipartFile file) {
        return extractionService.parseJobDescription(resumeTextExtractor.extract(file));
    }
}
