// ============================================================
// THIN FETCH WRAPPER FOR THE LIVE API
// Every src/lib/api/*.ts domain function routes through apiRequest() to the
// Spring Boot backend. This is the single source of truth: the running app
// never fabricates business data — when the server has no data, pages show
// an empty state (see components/common/AsyncBoundary).
//
// Access tokens are short-lived (1h). When a protected call comes back 401/403
// and we hold a refresh token, we transparently exchange it at /api/auth/refresh
// for a fresh access token (single-flight) and retry the request once. Only if
// the refresh itself fails do we clear the session and send the user to /login.
// ============================================================

import {
  getAccessToken,
  getRefreshToken,
  setAccessToken,
  setRefreshToken,
  clearTokens,
} from "@/lib/api/authToken";

// The Spring Boot backend serves every endpoint under /api on port 8080.
const BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api";

export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

interface ApiRequestOptions {
  method?: "GET" | "POST" | "PUT" | "PATCH" | "DELETE";
  body?: unknown;
  params?: Record<string, string | number | boolean | undefined>;
  /** Internal: skip the 401/403 refresh-and-retry (used by the auth endpoints themselves). */
  skipAuthRetry?: boolean;
}

function baseWithSlash(): string {
  return BASE_URL.endsWith("/") ? BASE_URL : `${BASE_URL}/`;
}

// ---- Transparent access-token refresh -----------------------------------

let refreshPromise: Promise<boolean> | null = null;

/** Single-flight refresh: concurrent 401/403s share one /auth/refresh round-trip. */
function refreshAccessToken(): Promise<boolean> {
  if (!refreshPromise) {
    refreshPromise = doRefresh().finally(() => {
      refreshPromise = null;
    });
  }
  return refreshPromise;
}

async function doRefresh(): Promise<boolean> {
  const refreshToken = getRefreshToken();
  if (!refreshToken) return false;
  try {
    const response = await fetch(new URL("auth/refresh", baseWithSlash()).toString(), {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ refreshToken }),
    });
    if (!response.ok) return false;
    const tokens = (await response.json()) as { accessToken: string; refreshToken: string };
    setAccessToken(tokens.accessToken);
    setRefreshToken(tokens.refreshToken);
    return true;
  } catch {
    return false;
  }
}

/** Refresh failed (or none available): drop the dead session and bounce to login. */
function handleSessionExpired(): void {
  clearTokens();
  if (typeof window !== "undefined" && !window.location.pathname.startsWith("/login")) {
    window.location.assign("/login");
  }
}

/**
 * Runs a fetch with the current Bearer token attached. On 401/403 with a refresh
 * token available, renews the access token once and replays the request; if the
 * renewal fails, clears the session and redirects to login.
 */
async function fetchWithAuth(
  url: string,
  init: RequestInit,
  extraHeaders: Record<string, string>,
  skipAuthRetry: boolean,
): Promise<Response> {
  const build = (): RequestInit => {
    const headers: Record<string, string> = { ...extraHeaders };
    const token = getAccessToken();
    if (token) headers.Authorization = `Bearer ${token}`;
    return { ...init, headers };
  };

  let response = await fetch(url, build());

  if (!skipAuthRetry && (response.status === 401 || response.status === 403) && getRefreshToken()) {
    const renewed = await refreshAccessToken();
    if (renewed) {
      response = await fetch(url, build());
    } else {
      handleSessionExpired();
    }
  }

  return response;
}

export async function apiRequest<T>(path: string, options: ApiRequestOptions = {}): Promise<T> {
  const { method = "GET", body, params, skipAuthRetry = false } = options;

  const url = new URL(path.replace(/^\//, ""), baseWithSlash());
  if (params) {
    for (const [key, value] of Object.entries(params)) {
      if (value !== undefined) url.searchParams.set(key, String(value));
    }
  }

  const response = await fetchWithAuth(
    url.toString(),
    { method, body: body !== undefined ? JSON.stringify(body) : undefined },
    { "Content-Type": "application/json" },
    skipAuthRetry,
  );

  if (!response.ok) {
    const message = await response.text().catch(() => response.statusText);
    throw new ApiError(response.status, message || `Request to ${path} failed with ${response.status}`);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/**
 * Multipart file upload. Unlike apiRequest (JSON only), this sends a FormData body and lets the
 * browser set the multipart Content-Type/boundary. Auth header (and 401/403 refresh) work the same.
 */
export async function apiUpload<T>(path: string, formData: FormData): Promise<T> {
  return apiUploadForm<T>(path, formData, "POST");
}

/**
 * Multipart upload with a configurable verb — POST to create, PUT to update. Same auth/refresh and
 * error handling as {@link apiUpload}; the browser sets the multipart Content-Type/boundary itself.
 */
export async function apiUploadForm<T>(
  path: string,
  formData: FormData,
  method: "POST" | "PUT" = "POST",
): Promise<T> {
  const url = new URL(path.replace(/^\//, ""), baseWithSlash());

  const response = await fetchWithAuth(url.toString(), { method, body: formData }, {}, false);

  if (!response.ok) {
    const message = await response.text().catch(() => response.statusText);
    throw new ApiError(response.status, message || `Upload to ${path} failed with ${response.status}`);
  }

  if (response.status === 204) return undefined as T;
  return (await response.json()) as T;
}

/**
 * Authenticated GET that returns the response body as a Blob — for viewing/downloading a file the
 * server streams (e.g. a certificate document). Bearer auth and the 401/403 refresh-and-retry behave
 * exactly as in {@link apiRequest}.
 */
export async function apiFetchBlob(path: string): Promise<Blob> {
  const url = new URL(path.replace(/^\//, ""), baseWithSlash());

  const response = await fetchWithAuth(url.toString(), { method: "GET" }, {}, false);

  if (!response.ok) {
    const message = await response.text().catch(() => response.statusText);
    throw new ApiError(response.status, message || `Request to ${path} failed with ${response.status}`);
  }

  return response.blob();
}
