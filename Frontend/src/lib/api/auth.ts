// ============================================================
// AUTH API — always talks to the real Spring Boot backend.
//
// Registration policy (enforced authoritatively by the backend):
// only STUDENT and RECRUITER may self-register — there are dedicated
// endpoints for each and none for OFFICER / MENTOR / ADMIN, which are
// invite-only. SELF_REGISTERABLE_ROLES mirrors that so the UI never
// offers a role the server would reject.
// ============================================================

import { apiRequest } from "@/lib/api/client";
import { clearTokens, getRefreshToken, setAccessToken, setRefreshToken } from "@/lib/api/authToken";
import type { CurrentUser, Role } from "@/types";

/** Roles a member of the public may create for themselves. Mirrors the backend. */
export const SELF_REGISTERABLE_ROLES: Role[] = ["student", "recruiter"];

export function isSelfRegisterable(role: Role): boolean {
  return SELF_REGISTERABLE_ROLES.includes(role);
}

/** A campus option for the registration picker (public, pre-auth). */
export interface PublicCampus {
  id: string;
  name: string;
  city: string;
  state: string;
}

interface PublicCampusDto {
  id: string;
  name: string;
  city: string;
  state: string;
  active: boolean;
}

export interface AuthTokens {
  accessToken: string;
  refreshToken: string;
  tokenType: string;
}

/**
 * Student sign-up fields (V60). Identity/academic details come from the campus
 * directory — the registrant only proves ownership of the directory email with an
 * OTP. Confirm-password is checked in the form and never sent.
 */
export interface StudentRegisterInput {
  email: string;
  password: string;
  phone?: string;
  campusId: string;
  /** The emailed verification code; omitted when OTP verification is disabled. */
  otp?: string;
}

/** POST /api/auth/otp/send — requests the registration OTP for a directory email. */
export async function sendOtp(campusId: string, email: string): Promise<{ otpRequired: boolean }> {
  return apiRequest<{ otpRequired: boolean }>("/auth/otp/send", {
    method: "POST",
    body: { campusId, email },
    skipAuthRetry: true,
  });
}

/** Recruiter sign-up fields — person + company. */
export interface RecruiterRegisterInput {
  fullName: string;
  email: string;
  phone: string;
  password: string;
  campusId: string;
  companyName: string;
  companyWebsite?: string;
  companyIndustry: string;
  companyLocation: string;
  designation: string;
}

/** GET /api/campuses/public — active campuses for the registration form. */
export async function listPublicCampuses(): Promise<PublicCampus[]> {
  const dtos = await apiRequest<PublicCampusDto[]>("/campuses/public");
  return dtos.map(({ id, name, city, state }) => ({ id, name, city, state }));
}

/** POST /api/auth/register/student. Creates the account + student profile and signs the user in. */
export async function registerStudent(input: StudentRegisterInput): Promise<AuthTokens> {
  const tokens = await apiRequest<AuthTokens>("/auth/register/student", {
    method: "POST",
    body: input,
    skipAuthRetry: true,
  });
  setAccessToken(tokens.accessToken);
  setRefreshToken(tokens.refreshToken);
  return tokens;
}

/** POST /api/auth/register/recruiter. Creates the account + recruiter/company profile and signs in. */
export async function registerRecruiter(input: RecruiterRegisterInput): Promise<AuthTokens> {
  const tokens = await apiRequest<AuthTokens>("/auth/register/recruiter", {
    method: "POST",
    body: input,
    skipAuthRetry: true,
  });
  setAccessToken(tokens.accessToken);
  setRefreshToken(tokens.refreshToken);
  return tokens;
}

/** Login credentials for the real backend (username == email). */
export interface LoginInput {
  username: string;
  password: string;
}

/**
 * POST /api/auth/login. Authenticates against the backend and stores the issued access token so
 * subsequent protected requests carry a Bearer header (see client.ts). There is no mock branch:
 * a session is only ever established by the real server.
 */
export async function login(input: LoginInput): Promise<AuthTokens> {
  const tokens = await apiRequest<AuthTokens>("/auth/login", {
    method: "POST",
    body: input,
    skipAuthRetry: true,
  });
  setAccessToken(tokens.accessToken);
  setRefreshToken(tokens.refreshToken);
  return tokens;
}

/** Requests a password-reset OTP. The response is intentionally generic for account privacy. */
export async function sendPasswordResetOtp(email: string): Promise<{ message: string }> {
  return apiRequest<{ message: string }>("/auth/password-reset/otp", {
    method: "POST",
    body: { email },
    skipAuthRetry: true,
  });
}

/** Completes password reset with the OTP sent to the account email. */
export async function resetPassword(input: {
  email: string;
  otp: string;
  newPassword: string;
}): Promise<void> {
  await apiRequest<void>("/auth/password-reset", {
    method: "POST",
    body: input,
    skipAuthRetry: true,
  });
}

/**
 * Clears the stored session (sign-out). Best-effort revokes the refresh token server-side so it
 * can't be used to mint new access tokens, then drops both tokens locally. Synchronous by design —
 * callers (Topbar, settings) fire it without awaiting; the local clear is what matters for the UI.
 */
export function logout(): void {
  const refreshToken = getRefreshToken();
  if (refreshToken) {
    // Fire-and-forget; a failed revoke must not block sign-out. skipAuthRetry so a 401/403 here
    // never triggers the refresh-and-redirect path.
    void apiRequest<void>("/auth/logout", {
      method: "POST",
      body: { refreshToken },
      skipAuthRetry: true,
    }).catch(() => {});
  }
  clearTokens();
  try {
    for (let i = localStorage.length - 1; i >= 0; i--) {
      const key = localStorage.key(i);
      if (key && key.startsWith("nexploy.resumeBuilder.draft")) {
        localStorage.removeItem(key);
      }
    }
  } catch {
    // ignore in case storage is unavailable
  }
}

/**
 * POST /api/auth/change-password — changes the signed-in user's password. The backend re-verifies
 * the current password (a mismatch is a 400) and revokes all refresh tokens on success. Passwords
 * are never persisted client-side. Returns nothing on success (204).
 */
export async function changePassword(input: { currentPassword: string; newPassword: string }): Promise<void> {
  await apiRequest<void>("/auth/change-password", { method: "POST", body: input });
}

/**
 * POST /api/auth/logout-all — signs the user out of every device by revoking all refresh tokens.
 * Note: access tokens are stateless JWTs and stay valid until they expire; this stops session
 * renewal everywhere. Callers should follow this with a local `logout()` to clear the current token.
 */
export async function logoutAllDevices(): Promise<void> {
  await apiRequest<void>("/auth/logout-all", { method: "POST" });
}

/** Backend GET /api/auth/me response — role is the UserRole enum name (e.g. "MENTOR"). */
interface CurrentUserDto {
  username: string;
  role: string;
  campusId: string;
}

/**
 * GET /api/auth/me — the authenticated caller's own identity (username, role, campus). This is
 * the single source of identity for the shared header and any role-scoped view; there is no
 * display name because a user account has none. Throws if unauthenticated (the backend requires
 * a valid token). No mock branch: identity must come from the real session.
 */
export async function getCurrentUser(): Promise<CurrentUser> {
  const dto = await apiRequest<CurrentUserDto>("/auth/me");
  return {
    username: dto.username,
    role: dto.role.toLowerCase() as Role,
    campusId: dto.campusId,
  };
}
