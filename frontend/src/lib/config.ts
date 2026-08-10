const trimTrailingSlash = (value: string) => value.replace(/\/+$/, "");

export const runtimeConfig = {
  apiUrl: trimTrailingSlash(
    process.env.NEXT_PUBLIC_API_URL ?? "/api",
  ),
  wsUrl: trimTrailingSlash(
    process.env.NEXT_PUBLIC_WS_URL ?? "ws://localhost:8080/ws",
  ),
} as const;
