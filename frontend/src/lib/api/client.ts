import { ApiError, type ApiErrorBody } from "@/lib/api/api-error";
import {
  synchronizedTokenSince,
  withCrossTabRefreshLock,
} from "@/lib/api/auth-sync";
import { tokenVault } from "@/lib/api/token-vault";
import { runtimeConfig } from "@/lib/config";

type QueryValue = string | number | boolean | null | undefined;
type Query = Record<string, QueryValue | QueryValue[]>;

export type RequestOptions = Omit<RequestInit, "body"> & {
  body?: unknown;
  query?: Query;
  auth?: boolean;
  retryAuth?: boolean;
};

type RefreshPayload = {
  accessToken: string;
};

let refreshRequest: Promise<string | null> | null = null;

function buildUrl(path: string, query?: Query) {
  const normalizedPath = path.startsWith("/") ? path : "/" + path;
  const endpoint = runtimeConfig.apiUrl + normalizedPath;
  const url = new URL(
    endpoint,
    typeof window === "undefined"
      ? process.env.NEXT_PUBLIC_SITE_URL ?? "http://localhost:3000"
      : window.location.origin,
  );
  Object.entries(query ?? {}).forEach(([key, rawValue]) => {
    const values = Array.isArray(rawValue) ? rawValue : [rawValue];
    values.forEach((value) => {
      if (value !== null && value !== undefined && value !== "") {
        url.searchParams.append(key, String(value));
      }
    });
  });
  return url.toString();
}

async function readResponse<T>(response: Response): Promise<T> {
  if (response.status === 204) {
    return undefined as T;
  }
  const contentType = response.headers.get("content-type") ?? "";
  if (contentType.includes("application/json")) {
    return (await response.json()) as T;
  }
  return (await response.text()) as T;
}

async function refreshAccessToken() {
  if (!refreshRequest) {
    const requestedAt = Date.now();
    refreshRequest = withCrossTabRefreshLock(async () => {
      const synchronized = synchronizedTokenSince(requestedAt);
      if (synchronized && !tokenExpiresSoon(synchronized)) {
        tokenVault.setAccessToken(synchronized, false);
        return synchronized;
      }

      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15_000);
      try {
        const response = await fetch(buildUrl("/auth/refresh"), {
          method: "POST",
          credentials: "include",
          signal: controller.signal,
          headers: {
            Accept: "application/json",
            "X-Gameio-CSRF": "1",
          },
        });
        if (!response.ok) {
          return null;
        }
        const payload = await readResponse<RefreshPayload>(response);
        tokenVault.setAccessToken(payload.accessToken);
        return payload.accessToken;
      } finally {
        clearTimeout(timeout);
      }
    })
      .catch(() => null)
      .finally(() => {
        refreshRequest = null;
      });
  }
  return refreshRequest;
}

function tokenExpiresSoon(token: string) {
  try {
    const payload = token.split(".")[1];
    if (!payload) return false;
    const normalized = payload.replaceAll("-", "+").replaceAll("_", "/");
    const decoded = JSON.parse(atob(normalized)) as { exp?: number };
    return typeof decoded.exp === "number"
      ? decoded.exp * 1000 <= Date.now() + 15_000
      : false;
  } catch {
    return false;
  }
}

export async function ensureAccessToken(forceRefresh = false) {
  const current = tokenVault.getAccessToken();
  if (current && !forceRefresh && !tokenExpiresSoon(current)) {
    return current;
  }
  const refreshed = await refreshAccessToken();
  if (!refreshed) {
    tokenVault.setAccessToken(null);
  }
  return refreshed;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const {
    body,
    query,
    auth = true,
    retryAuth = true,
    headers: suppliedHeaders,
    ...init
  } = options;
  const headers = new Headers(suppliedHeaders);
  headers.set("Accept", "application/json");
  const method = (init.method ?? "GET").toUpperCase();
  if (method !== "GET" && method !== "HEAD") {
    headers.set("X-Gameio-CSRF", "1");
  }

  const accessToken = auth ? tokenVault.getAccessToken() : null;
  if (accessToken) {
    headers.set("Authorization", "Bearer " + accessToken);
  }

  let serializedBody: BodyInit | undefined;
  if (body instanceof FormData || typeof body === "string") {
    serializedBody = body;
  } else if (body !== undefined) {
    headers.set("Content-Type", "application/json");
    serializedBody = JSON.stringify(body);
  }

  let response: Response;
  try {
    response = await fetch(buildUrl(path, query), {
      ...init,
      headers,
      body: serializedBody,
      credentials: "include",
    });
  } catch {
    throw new ApiError(0, {
      code: "NETWORK_UNAVAILABLE",
      message: "Backend link is unavailable. Check the API address and connection.",
    });
  }

  if (response.status === 401 && auth && retryAuth && !path.includes("/auth/")) {
    const synchronized = tokenVault.getAccessToken();
    const freshToken =
      synchronized &&
      synchronized !== accessToken &&
      !tokenExpiresSoon(synchronized)
        ? synchronized
        : await ensureAccessToken(true);
    if (freshToken) {
      return request<T>(path, { ...options, retryAuth: false });
    }
    tokenVault.setAccessToken(null);
  }

  if (!response.ok) {
    let errorBody: ApiErrorBody | undefined;
    try {
      errorBody = await readResponse<ApiErrorBody>(response);
    } catch {
      errorBody = undefined;
    }
    throw new ApiError(response.status, errorBody);
  }

  return readResponse<T>(response);
}

export const apiClient = {
  get: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "GET" }),
  post: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, body, method: "POST" }),
  put: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, body, method: "PUT" }),
  patch: <T>(path: string, body?: unknown, options?: RequestOptions) =>
    request<T>(path, { ...options, body, method: "PATCH" }),
  delete: <T>(path: string, options?: RequestOptions) =>
    request<T>(path, { ...options, method: "DELETE" }),
};
