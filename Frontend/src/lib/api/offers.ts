// ============================================================
// OFFERS API — live only. Every offer comes from the campus-scoped
// GET /api/offers (or the per-student endpoint); an empty DB yields [].
// ============================================================

import { apiRequest } from "@/lib/api/client";
import { mapOffer, type OfferResponseDto } from "@/lib/api/mappers";
import type { Offer } from "@/types";

/** GET /api/offers — offers the signed-in user may see (campus-scoped server-side). */
export async function listOffers(campusId?: string): Promise<Offer[]> {
  const dtos = await apiRequest<OfferResponseDto[]>("/offers", {
    params: campusId ? { campusId } : undefined,
  });
  return dtos.map(mapOffer);
}

/** GET /api/students/:studentId/offers */
export async function listOffersByStudent(studentId: string): Promise<Offer[]> {
  const dtos = await apiRequest<OfferResponseDto[]>(`/students/${studentId}/offers`);
  return dtos.map(mapOffer);
}

/**
 * Offers extended from a given drive. The backend has no by-drive endpoint, but GET /api/offers
 * is campus-scoped for a recruiter (their own campus), so we fetch that and filter by driveId —
 * never fabricated.
 */
export async function listOffersByDrive(driveId: string): Promise<Offer[]> {
  const dtos = await apiRequest<OfferResponseDto[]>("/offers");
  return dtos.map(mapOffer).filter((o) => o.driveId === driveId);
}

/** PPO offers — GET /api/offers filtered to offer types carrying a PPO. */
export async function listPpoOffers(): Promise<Offer[]> {
  const dtos = await apiRequest<OfferResponseDto[]>("/offers");
  return dtos.map(mapOffer).filter((o) => o.offerType.includes("PPO"));
}

/**
 * Request body for POST /api/offers — mirrors the backend CreateOfferRequest (camelCase).
 * FK ids are plain strings validated server-side; packageLpa is numeric.
 */
export interface CreateOfferRequest {
  studentId: string;
  driveId: string;
  recruiterId: string;
  role: string;
  packageLpa: number;
  companyName?: string | null;
  offerType?: string | null;
  location?: string | null;
  joiningDate?: string | null;
  acceptanceDeadline?: string | null;
  fromInternship?: string | null;
}

/** POST /api/offers — officer/recruiter extends a new offer. Returns the created offer. */
export async function extendOffer(request: CreateOfferRequest): Promise<Offer> {
  return mapOffer(await apiRequest<OfferResponseDto>("/offers", { method: "POST", body: request }));
}

/** POST /api/offers/:id/accept — the signed-in student accepts their offer. */
export async function acceptOffer(offerId: string): Promise<Offer> {
  return mapOffer(await apiRequest<OfferResponseDto>(`/offers/${offerId}/accept`, { method: "POST" }));
}

/** POST /api/offers/:id/decline — the signed-in student declines their offer. */
export async function declineOffer(offerId: string): Promise<Offer> {
  return mapOffer(
    await apiRequest<OfferResponseDto>(`/offers/${offerId}/decline`, { method: "POST" }),
  );
}

/** POST /api/offers/:id/revoke — officer/recruiter revokes an extended offer (soft — status REVOKED). */
export async function revokeOffer(offerId: string): Promise<Offer> {
  return mapOffer(await apiRequest<OfferResponseDto>(`/offers/${offerId}/revoke`, { method: "POST" }));
}

/**
 * POST /api/offers/:id/verify-documents — officer/admin verifies the student's documents
 * (all must be APPROVED in the Documents module) and advances the funnel to DOCS.
 */
export async function verifyOfferDocuments(offerId: string): Promise<Offer> {
  return mapOffer(
    await apiRequest<OfferResponseDto>(`/offers/${offerId}/verify-documents`, { method: "POST" }),
  );
}

/** POST /api/offers/:id/join — officer/admin confirms the student joined (funnel → JOINED). */
export async function markOfferJoined(offerId: string): Promise<Offer> {
  return mapOffer(await apiRequest<OfferResponseDto>(`/offers/${offerId}/join`, { method: "POST" }));
}

/** POST /api/offers/:id/nudge — recruiter/officer sends an automated reminder prompt to the candidate. */
export async function nudgeOffer(offerId: string): Promise<Offer> {
  return mapOffer(await apiRequest<OfferResponseDto>(`/offers/${offerId}/nudge`, { method: "POST" }));
}

/** POST /api/offers/:id/escalate — recruiter/officer escalates a pending offer approaching deadline to Placement Cell. */
export async function escalateOffer(offerId: string): Promise<Offer> {
  return mapOffer(await apiRequest<OfferResponseDto>(`/offers/${offerId}/escalate`, { method: "POST" }));
}
