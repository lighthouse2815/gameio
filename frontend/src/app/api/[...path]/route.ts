import { getCloudflareContext } from "@opennextjs/cloudflare";
import type { NextRequest } from "next/server";

export const dynamic = "force-dynamic";
export const revalidate = 0;

type RouteContext = {
  params: Promise<{ path: string[] }>;
};

const MAX_PROXY_BODY_BYTES = 1_048_576;

const REQUEST_HEADERS_TO_STRIP = [
  "connection",
  "content-length",
  "host",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
  "x-forwarded-for",
  "x-forwarded-host",
  "x-forwarded-port",
  "x-forwarded-proto",
];

const RESPONSE_HEADERS_TO_STRIP = [
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
];

async function configuredBackendOrigin() {
  let binding: string | undefined;
  try {
    const cloudflare = await getCloudflareContext({ async: true });
    binding = cloudflare.env.BACKEND_ORIGIN;
  } catch {
    // `next dev` can run before Wrangler has initialized local bindings.
  }

  const raw = binding ?? process.env.BACKEND_ORIGIN;
  if (!raw) {
    throw new Error("BACKEND_ORIGIN is not configured");
  }

  const origin = new URL(raw);
  if (
    (origin.protocol !== "http:" && origin.protocol !== "https:") ||
    origin.username ||
    origin.password ||
    (origin.pathname !== "/" && origin.pathname !== "") ||
    origin.search ||
    origin.hash
  ) {
    throw new Error("BACKEND_ORIGIN must be a plain HTTP(S) origin");
  }
  return origin.origin;
}

async function proxy(request: NextRequest, context: RouteContext) {
  let backendOrigin: string;
  try {
    backendOrigin = await configuredBackendOrigin();
  } catch {
    return Response.json(
      {
        code: "BACKEND_NOT_CONFIGURED",
        message: "The API gateway is not configured for this deployment.",
      },
      { status: 503, headers: { "Cache-Control": "no-store" } },
    );
  }

  const { path } = await context.params;
  const encodedPath = path.map(encodeURIComponent).join("/");
  const upstreamUrl = new URL("/api/" + encodedPath, backendOrigin);
  upstreamUrl.search = request.nextUrl.search;

  const requestHeaders = new Headers(request.headers);
  REQUEST_HEADERS_TO_STRIP.forEach((header) => requestHeaders.delete(header));
  requestHeaders.set("Accept-Encoding", "identity");

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  const declaredLength = Number(request.headers.get("content-length") ?? 0);
  if (
    Number.isFinite(declaredLength) &&
    declaredLength > MAX_PROXY_BODY_BYTES
  ) {
    return Response.json(
      {
        code: "REQUEST_TOO_LARGE",
        message: "The API gateway accepts request bodies up to 1 MiB.",
      },
      { status: 413, headers: { "Cache-Control": "no-store" } },
    );
  }
  const body = hasBody ? await request.arrayBuffer() : undefined;
  if (body && body.byteLength > MAX_PROXY_BODY_BYTES) {
    return Response.json(
      {
        code: "REQUEST_TOO_LARGE",
        message: "The API gateway accepts request bodies up to 1 MiB.",
      },
      { status: 413, headers: { "Cache-Control": "no-store" } },
    );
  }

  let upstream: Response;
  try {
    upstream = await fetch(upstreamUrl, {
      method: request.method,
      headers: requestHeaders,
      body: body?.byteLength ? body : undefined,
      cache: "no-store",
      redirect: "manual",
    });
  } catch {
    return Response.json(
      {
        code: "BACKEND_UNAVAILABLE",
        message: "The API gateway could not reach the backend.",
      },
      { status: 502, headers: { "Cache-Control": "no-store" } },
    );
  }

  const responseHeaders = new Headers(upstream.headers);
  RESPONSE_HEADERS_TO_STRIP.forEach((header) =>
    responseHeaders.delete(header),
  );
  responseHeaders.set("Cache-Control", "no-store");

  return new Response(request.method === "HEAD" ? null : upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

export const GET = proxy;
export const POST = proxy;
export const PUT = proxy;
export const PATCH = proxy;
export const DELETE = proxy;
export const OPTIONS = proxy;
export const HEAD = proxy;
