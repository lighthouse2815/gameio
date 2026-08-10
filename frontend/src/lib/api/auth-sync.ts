const AUTH_CHANNEL_NAME = "gameio-auth-v1";
const AUTH_LOCK_NAME = "gameio-refresh-rotation";
const FALLBACK_LEASE_KEY = "gameio.auth.refresh-lease";
const FALLBACK_LEASE_MS = 60_000;
const FALLBACK_WAIT_MS = 20_000;
const REQUEST_STALE_MS = 65_000;

type AccessTokenMessage = {
  type: "ACCESS_TOKEN";
  senderId: string;
  token: string;
};

type LogoutMessage = {
  type: "LOGOUT";
  senderId: string;
};

type LockMessage = {
  type: "LOCK_REQUEST" | "LOCK_RELEASE";
  senderId: string;
  requestId: string;
};

type AuthMessage = AccessTokenMessage | LogoutMessage | LockMessage;

export type AuthSyncEvent =
  | { type: "access-token"; token: string }
  | { type: "logout" };

type RefreshLease = {
  owner: string;
  expiresAt: number;
};

const tabId =
  typeof crypto !== "undefined" && "randomUUID" in crypto
    ? crypto.randomUUID()
    : Math.random().toString(36).slice(2) + Date.now().toString(36);

const listeners = new Set<(event: AuthSyncEvent) => void>();
const activeLockRequests = new Map<string, number>();
let channel: BroadcastChannel | null | undefined;
let currentRequestId: string | null = null;
let recentToken: { token: string; receivedAt: number } | null = null;

function isAuthMessage(value: unknown): value is AuthMessage {
  if (typeof value !== "object" || value === null || !("type" in value)) {
    return false;
  }
  const candidate = value as Partial<AuthMessage>;
  if (
    candidate.type === "ACCESS_TOKEN" &&
    typeof (candidate as Partial<AccessTokenMessage>).senderId === "string" &&
    typeof (candidate as Partial<AccessTokenMessage>).token === "string"
  ) {
    return true;
  }
  if (
    candidate.type === "LOGOUT" &&
    typeof (candidate as Partial<LogoutMessage>).senderId === "string"
  ) {
    return true;
  }
  return Boolean(
    (candidate.type === "LOCK_REQUEST" ||
      candidate.type === "LOCK_RELEASE") &&
      typeof (candidate as Partial<LockMessage>).senderId === "string" &&
      typeof (candidate as Partial<LockMessage>).requestId === "string",
  );
}

function post(message: AuthMessage) {
  try {
    getChannel()?.postMessage(message);
  } catch {
    // Coordination callers fail closed when no usable channel is available.
  }
}

function getChannel() {
  if (channel !== undefined) return channel;
  if (
    typeof window === "undefined" ||
    typeof window.BroadcastChannel === "undefined"
  ) {
    channel = null;
    return channel;
  }
  channel = new window.BroadcastChannel(AUTH_CHANNEL_NAME);
  channel.addEventListener("message", (event: MessageEvent<unknown>) => {
    if (!isAuthMessage(event.data) || event.data.senderId === tabId) return;
    const message = event.data;
    if (message.type === "ACCESS_TOKEN") {
      recentToken = { token: message.token, receivedAt: Date.now() };
      listeners.forEach((listener) =>
        listener({ type: "access-token", token: message.token }),
      );
      return;
    }
    if (message.type === "LOGOUT") {
      recentToken = null;
      listeners.forEach((listener) => listener({ type: "logout" }));
      return;
    }
    if (message.type === "LOCK_RELEASE") {
      activeLockRequests.delete(message.requestId);
      return;
    }
    activeLockRequests.set(message.requestId, Date.now());
    if (currentRequestId) {
      post({
        type: "LOCK_REQUEST",
        senderId: tabId,
        requestId: currentRequestId,
      });
    }
  });
  return channel;
}

function storage() {
  if (typeof window === "undefined") return null;
  try {
    return window.localStorage;
  } catch {
    return null;
  }
}

function readLease(now = Date.now()) {
  const localStorage = storage();
  if (!localStorage) return null;
  try {
    const raw = localStorage.getItem(FALLBACK_LEASE_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as Partial<RefreshLease>;
    if (
      typeof parsed.owner !== "string" ||
      typeof parsed.expiresAt !== "number" ||
      parsed.expiresAt <= now
    ) {
      localStorage.removeItem(FALLBACK_LEASE_KEY);
      return null;
    }
    return parsed as RefreshLease;
  } catch {
    return null;
  }
}

function writeLease(owner: string) {
  const localStorage = storage();
  if (!localStorage) return true;
  try {
    localStorage.setItem(
      FALLBACK_LEASE_KEY,
      JSON.stringify({ owner, expiresAt: Date.now() + FALLBACK_LEASE_MS }),
    );
    return readLease()?.owner === owner;
  } catch {
    return false;
  }
}

function releaseLease(owner: string) {
  const localStorage = storage();
  if (!localStorage) return;
  try {
    if (readLease()?.owner === owner) {
      localStorage.removeItem(FALLBACK_LEASE_KEY);
    }
  } catch {
    // An expired lease safely prevents this tab from deleting another owner.
  }
}

function delay(milliseconds: number) {
  return new Promise<void>((resolve) => setTimeout(resolve, milliseconds));
}

async function withBroadcastLock<T>(task: () => Promise<T>) {
  if (!getChannel()) {
    throw new Error("Cross-tab refresh coordination is unavailable");
  }
  const requestId = tabId + ":" + crypto.randomUUID();
  currentRequestId = requestId;
  activeLockRequests.set(requestId, Date.now());
  post({ type: "LOCK_REQUEST", senderId: tabId, requestId });

  const startedAt = Date.now();
  let heartbeat: ReturnType<typeof setInterval> | null = null;
  try {
    await delay(120);
    while (Date.now() - startedAt < FALLBACK_WAIT_MS) {
      const now = Date.now();
      for (const [candidate, seenAt] of activeLockRequests) {
        if (candidate !== requestId && now - seenAt > REQUEST_STALE_MS) {
          activeLockRequests.delete(candidate);
        }
      }
      activeLockRequests.set(requestId, now);
      post({ type: "LOCK_REQUEST", senderId: tabId, requestId });

      const lease = readLease(now);
      const firstRequest = [...activeLockRequests.keys()].sort()[0];
      if ((!lease || lease.owner === requestId) && firstRequest === requestId) {
        if (writeLease(requestId)) {
          await delay(60);
          const confirmedLease = readLease();
          const confirmedFirst = [...activeLockRequests.keys()].sort()[0];
          if (
            (!confirmedLease || confirmedLease.owner === requestId) &&
            confirmedFirst === requestId
          ) {
            heartbeat = setInterval(() => {
              activeLockRequests.set(requestId, Date.now());
              writeLease(requestId);
              post({ type: "LOCK_REQUEST", senderId: tabId, requestId });
            }, 5_000);
            return await task();
          }
        }
      }
      await delay(80);
    }
    throw new Error("Timed out waiting for cross-tab refresh coordination");
  } finally {
    if (heartbeat) clearInterval(heartbeat);
    releaseLease(requestId);
    activeLockRequests.delete(requestId);
    if (currentRequestId === requestId) currentRequestId = null;
    post({ type: "LOCK_RELEASE", senderId: tabId, requestId });
  }
}

export function subscribeAuthSync(listener: (event: AuthSyncEvent) => void) {
  listeners.add(listener);
  getChannel();
  return () => listeners.delete(listener);
}

export function publishAccessToken(token: string) {
  recentToken = { token, receivedAt: Date.now() };
  post({ type: "ACCESS_TOKEN", senderId: tabId, token });
}

export function publishLogout() {
  recentToken = null;
  post({ type: "LOGOUT", senderId: tabId });
}

export function synchronizedTokenSince(timestamp: number) {
  return recentToken && recentToken.receivedAt >= timestamp
    ? recentToken.token
    : null;
}

export async function withCrossTabRefreshLock<T>(task: () => Promise<T>) {
  if (typeof window === "undefined") return task();
  if (navigator.locks?.request) {
    return navigator.locks.request(
      AUTH_LOCK_NAME,
      { mode: "exclusive" },
      task,
    );
  }
  return withBroadcastLock(task);
}
