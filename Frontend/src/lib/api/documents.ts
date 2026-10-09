// ============================================================
// DOCUMENTS API — live only. Documents come from the campus-scoped
// GET /api/documents (or the per-student endpoint); an empty DB yields [].
// ============================================================

import { apiRequest, apiUploadForm, apiFetchBlob } from "@/lib/api/client";
import { mapDocument, type DocumentResponseDto } from "@/lib/api/mappers";
import type { DocumentItem } from "@/types";

/** GET /api/documents — documents the signed-in user may see (campus-scoped server-side). */
export async function listDocuments(): Promise<DocumentItem[]> {
  const dtos = await apiRequest<DocumentResponseDto[]>("/documents");
  return dtos.map(mapDocument);
}

/** GET /api/students/:studentId/documents */
export async function listDocumentsByStudent(studentId: string): Promise<DocumentItem[]> {
  const dtos = await apiRequest<DocumentResponseDto[]>(`/students/${studentId}/documents`);
  return dtos.map(mapDocument);
}

/**
 * Request body for POST /api/documents — mirrors the backend RegisterDocumentRequest.
 * A STUDENT caller is pinned to their own record server-side regardless of `studentId`;
 * officers/admins may register on behalf of the given student.
 */
export interface RegisterDocumentRequest {
  studentId: string;
  /** RESUME | MARKSHEET | CERTIFICATE | OFFER_LETTER | ID_PROOF | OTHER */
  type: string;
  fileName: string;
  /** application/pdf | image/png | image/jpeg (backend rejects anything else) */
  contentType: string;
  sizeBytes: number;
  storageKey: string;
}

/**
 * POST /api/documents — registers an uploaded document's metadata. The document starts
 * scan-pending and review-pending; it cannot be approved until an officer clears the scan.
 */
export async function registerDocument(request: RegisterDocumentRequest): Promise<DocumentItem> {
  return mapDocument(
    await apiRequest<DocumentResponseDto>("/documents", { method: "POST", body: request }),
  );
}

/** POST /api/documents/:id/scan — officer/admin records the malware-scan outcome. */
export async function recordDocumentScan(
  documentId: string,
  status: "CLEAN" | "INFECTED",
): Promise<DocumentItem> {
  return mapDocument(
    await apiRequest<DocumentResponseDto>(`/documents/${documentId}/scan`, {
      method: "POST",
      params: { status },
    }),
  );
}

/** POST /api/documents/:id/review — officer/admin approves or rejects a document. */
export async function reviewDocument(
  documentId: string,
  decision: "APPROVED" | "REJECTED",
): Promise<DocumentItem> {
  return mapDocument(
    await apiRequest<DocumentResponseDto>(`/documents/${documentId}/review`, {
      method: "POST",
      body: { decision },
    }),
  );
}

/**
 * POST /api/documents/:id/file — attaches (or replaces) the real file bytes for a registered
 * document. The upload resets scan/review to PENDING server-side: fresh bytes invalidate any
 * earlier scan verdict or approval.
 */
export async function attachDocumentFile(documentId: string, file: File): Promise<DocumentItem> {
  const form = new FormData();
  form.append("file", file);
  return mapDocument(
    await apiUploadForm<DocumentResponseDto>(`/documents/${documentId}/file`, form, "POST"),
  );
}

/** GET /api/documents/:id/file — the stored file as a Blob (auth-checked, 404 when no bytes). */
export async function fetchDocumentFile(documentId: string): Promise<Blob> {
  return apiFetchBlob(`/documents/${documentId}/file`);
}
