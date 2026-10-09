import { apiRequest } from "@/lib/api/client";

/**
 * A company's presence at a campus, aggregated on the backend from the recruiter row + its real
 * drives (GET /api/companies, /companies/{id}). Live-only — there is no client-side simulation to
 * fall back to. The company {@code id} is the recruiter id. Fields the backend does not store
 * (industry/website/location) arrive as null and the UI omits them rather than inventing a value.
 */
export interface CompanyDriveSummary {
  driveId: string;
  role: string;
  status: string | null;
  location: string | null;
  packageLpa: number | null;
  /** ISO date (yyyy-mm-dd) or null when the drive sets no deadline. */
  deadline: string | null;
}

export interface Company {
  id: string;
  campusId: string;
  name: string;
  industry: string | null;
  website: string | null;
  location: string | null;
  activeDriveCount: number;
  totalDriveCount: number;
  drives: CompanyDriveSummary[];
}

/**
 * GET /api/companies — companies recruiting at the caller's campus (OFFICER/ADMIN see all campuses).
 * Derived from real recruiter + drive rows only; an empty campus resolves to an empty array.
 */
export async function listCompanies(): Promise<Company[]> {
  return apiRequest<Company[]>("/companies");
}

/** GET /api/companies/{id} — one company; 404 (unknown id) resolves to undefined for empty states. */
export async function getCompany(id: string): Promise<Company | undefined> {
  try {
    return await apiRequest<Company>(`/companies/${id}`);
  } catch (error) {
    if (error instanceof Error && "status" in error && (error as { status: number }).status === 404) {
      return undefined;
    }
    throw error;
  }
}
