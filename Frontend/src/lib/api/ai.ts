// ============================================================
// AI EXTRACTION API — POST /api/ai/resume/extract-file (multipart)
// Uploads a resume file; the backend parses it to text (PDF via PDFBox
// or plain text), screens it for prompt injection, and runs bounded,
// evidence-bearing extraction. Output is read-only structured data.
// ============================================================

import { apiRequest, apiUpload } from "@/lib/api/client";

/** One extracted value plus the verbatim source span it was drawn from. */
export interface ExtractedItem {
  normalizedValue: string;
  evidence: string;
}

/** Mirrors the backend AiExtractionClient.ResumeExtraction record. */
export interface ResumeExtractionResult {
  skills: ExtractedItem[];
  qualifications: ExtractedItem[];
  metadata: {
    provider: string;
    modelVersion: string;
    promptVersion: string;
  };
}

/** Payload for Spring AI resume summary generation. */
export interface GenerateSummaryPayload {
  name?: string;
  targetRole?: string;
  skills?: string;
  experience?: Array<{ role: string; company: string; duration: string; description: string }>;
  projects?: Array<{ name: string; techStack: string; description: string }>;
  education?: Array<{ degree: string; branch: string; college: string; year: string; cgpa: string }>;
}

export interface GenerateSummaryResponse {
  summary: string;
  provider: string;
}

/**
 * Calls backend Spring AI endpoint (POST /api/ai/resume/generate-summary)
 * powered by Groq LLM to generate an ATS-friendly professional summary.
 */
export async function generateResumeSummary(data: GenerateSummaryPayload): Promise<GenerateSummaryResponse> {
  return apiRequest<GenerateSummaryResponse>("/ai/resume/generate-summary", {
    method: "POST",
    body: data,
  });
}

/**
 * Uploads a resume file and returns the backend-extracted skills/qualifications. Live only — the
 * extraction is evidence-bearing and produced server-side; there is no client-side fabrication.
 */
export async function extractResumeFile(file: File): Promise<ResumeExtractionResult> {
  const formData = new FormData();
  formData.append("file", file);
  return apiUpload<ResumeExtractionResult>("/ai/resume/extract-file", formData);
}

/** Mirrors the backend AiExtractionClient.JobRequirements record. */
export interface JobRequirementsResult {
  requiredSkills: ExtractedItem[];
  preferredSkills: ExtractedItem[];
  qualifications: ExtractedItem[];
  metadata: {
    provider: string;
    modelVersion: string;
    promptVersion: string;
  };
}

/**
 * POST /api/ai/job-description/parse — extracts required/preferred skills and qualifications from
 * raw JD text (size-capped and prompt-injection-screened server-side). Returns read-only structured
 * data; an officer reviews and applies it manually, so extraction never mutates a drive.
 */
export async function parseJobDescription(text: string): Promise<JobRequirementsResult> {
  return apiRequest<JobRequirementsResult>("/ai/job-description/parse", {
    method: "POST",
    body: { text },
  });
}

/**
 * Uploads a job description file and returns the backend-extracted required/preferred skills and
 * qualifications. Live only — the extraction is evidence-bearing and produced server-side; there is
 * no client-side fabrication.
 */
export async function parseJobDescriptionFile(file: File): Promise<JobRequirementsResult> {
  const formData = new FormData();
  formData.append("file", file);
  return apiUpload<JobRequirementsResult>("/ai/job-description/parse-file", formData);
}

