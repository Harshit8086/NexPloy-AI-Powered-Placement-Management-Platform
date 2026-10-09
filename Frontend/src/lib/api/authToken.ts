// ============================================================
// ACCESS-TOKEN STORE (live mode)
// Holds the JWT the backend issues at login/registration so client.ts can
// attach it as a Bearer header on protected requests. In "mock" mode nothing
// calls this. Persisted to localStorage so a reload keeps the session, with an
// in-memory fallback for SSR / private-mode where localStorage is unavailable.
// ============================================================

const STORAGE_KEY = "nexploy.accessToken";
const REFRESH_STORAGE_KEY = "nexploy.refreshToken";

let inMemoryToken: string | null = null;
let inMemoryRefreshToken: string | null = null;

export function setAccessToken(token: string | null): void {
  inMemoryToken = token;
  if (typeof window === "undefined") return;
  try {
    if (token) window.localStorage.setItem(STORAGE_KEY, token);
    else window.localStorage.removeItem(STORAGE_KEY);
  } catch {
    // localStorage may be blocked (private mode); the in-memory value still serves this session.
  }
}

export function getAccessToken(): string | null {
  if (inMemoryToken) return inMemoryToken;
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(STORAGE_KEY);
  } catch {
    return null;
  }
}

export function clearAccessToken(): void {
  setAccessToken(null);
}

// ---- Refresh token -------------------------------------------------------
// The long-lived, single-use token exchanged at /api/auth/refresh for a fresh
// access token when the short-lived (1h) access token expires. Stored the same
// way as the access token so a reload can silently renew the session.

export function setRefreshToken(token: string | null): void {
  inMemoryRefreshToken = token;
  if (typeof window === "undefined") return;
  try {
    if (token) window.localStorage.setItem(REFRESH_STORAGE_KEY, token);
    else window.localStorage.removeItem(REFRESH_STORAGE_KEY);
  } catch {
    // localStorage may be blocked (private mode); the in-memory value still serves this session.
  }
}

export function getRefreshToken(): string | null {
  if (inMemoryRefreshToken) return inMemoryRefreshToken;
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage.getItem(REFRESH_STORAGE_KEY);
  } catch {
    return null;
  }
}

/** Clears both tokens — full local sign-out. */
export function clearTokens(): void {
  setAccessToken(null);
  setRefreshToken(null);
}
