// ============================================================
// STUDENT CERTIFICATES — the signed-in student's showcase certificates
// (full CRUD + document upload/view) under /api/students/me/certificates.
// Metadata is JSON; create/update are multipart (form fields + a `file` part);
// the uploaded document is streamed back from the /file endpoint.
// ============================================================

import { apiRequest, apiUploadForm, apiFetchBlob } from "@/lib/api/client";

/** Certificate classification — mirrors the backend CertificateType enum names. */
export type CertificateType =
  | "COURSE_COMPLETION"
  | "INTERNSHIP"
  | "WORKSHOP"
  | "HACKATHON"
  | "COMPETITION"
  | "ACHIEVEMENT"
  | "WEBINAR"
  | "ACADEMIC"
  | "PROFESSIONAL_CERTIFICATION"
  | "OTHER";

/** Options for the type dropdown, in display order. */
export const CERTIFICATE_TYPES: { value: CertificateType; label: string }[] = [
  { value: "COURSE_COMPLETION", label: "Course Completion" },
  { value: "INTERNSHIP", label: "Internship" },
  { value: "WORKSHOP", label: "Workshop" },
  { value: "HACKATHON", label: "Hackathon" },
  { value: "COMPETITION", label: "Competition" },
  { value: "ACHIEVEMENT", label: "Achievement" },
  { value: "WEBINAR", label: "Webinar" },
  { value: "ACADEMIC", label: "Academic" },
  { value: "PROFESSIONAL_CERTIFICATION", label: "Professional Certification" },
  { value: "OTHER", label: "Other" },
];

/** Human label for a certificate type value (falls back to the raw value). */
export function certificateTypeLabel(type: string | null | undefined): string {
  return CERTIFICATE_TYPES.find((t) => t.value === type)?.label ?? (type ?? "Other");
}

/** A student certificate as returned by the API (metadata only — never the file bytes). */
export interface StudentCertificate {
  id: string;
  title: string;
  type: CertificateType;
  issuingOrganization: string;
  description: string | null;
  credentialId: string | null;
  verificationUrl: string | null;
  issuerName: string | null;
  organizationWebsite: string | null;
  fileName: string | null;
  contentType: string | null;
  fileSizeBytes: number | null;
  createdAt: string;
  updatedAt: string;
}

/** Whether a certificate has an uploaded document attached (drives the "View" affordance). */
export function certificateHasFile(cert: StudentCertificate): boolean {
  return Boolean(cert.fileName);
}

/** The editable metadata fields (required: title/type/issuingOrganization). */
export interface CertificateInput {
  title: string;
  type: CertificateType;
  issuingOrganization: string;
  description?: string;
  credentialId?: string;
  verificationUrl?: string;
  issuerName?: string;
  organizationWebsite?: string;
}

function toFormData(input: CertificateInput, file?: File | null): FormData {
  const form = new FormData();
  form.append("title", input.title);
  form.append("type", input.type);
  form.append("issuingOrganization", input.issuingOrganization);
  form.append("description", input.description ?? "");
  form.append("credentialId", input.credentialId ?? "");
  form.append("verificationUrl", input.verificationUrl ?? "");
  form.append("issuerName", input.issuerName ?? "");
  form.append("organizationWebsite", input.organizationWebsite ?? "");
  if (file) form.append("file", file);
  return form;
}

/** GET /api/students/me/certificates */
export async function listCertificates(): Promise<StudentCertificate[]> {
  return apiRequest<StudentCertificate[]>("/students/me/certificates");
}

/** POST /api/students/me/certificates — a document file is required. */
export async function createCertificate(
  input: CertificateInput,
  file: File,
): Promise<StudentCertificate> {
  return apiUploadForm<StudentCertificate>(
    "/students/me/certificates",
    toFormData(input, file),
    "POST",
  );
}

/** PUT /api/students/me/certificates/:id — updates metadata only (JSON); the stored file is untouched. */
export async function updateCertificateMetadata(
  id: string,
  input: CertificateInput,
): Promise<StudentCertificate> {
  return apiRequest<StudentCertificate>(`/students/me/certificates/${id}`, {
    method: "PUT",
    body: input,
  });
}

/** POST /api/students/me/certificates/:id/file — replaces the uploaded document. */
export async function replaceCertificateFile(id: string, file: File): Promise<StudentCertificate> {
  const form = new FormData();
  form.append("file", file);
  return apiUploadForm<StudentCertificate>(`/students/me/certificates/${id}/file`, form, "POST");
}

/** DELETE /api/students/me/certificates/:id */
export async function deleteCertificate(id: string): Promise<void> {
  await apiRequest<void>(`/students/me/certificates/${id}`, { method: "DELETE" });
}

/** GET /api/students/me/certificates/:id/file — the uploaded document as a Blob (auth-checked). */
export async function fetchCertificateFile(id: string): Promise<Blob> {
  return apiFetchBlob(`/students/me/certificates/${id}/file`);
}
