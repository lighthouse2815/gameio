import { beforeEach, describe, expect, it, vi } from "vitest";

const mocks = vi.hoisted(() => ({
  ensureAccessToken: vi.fn(async () => "access-token"),
}));

vi.mock("@/lib/api/client", () => ({
  ensureAccessToken: mocks.ensureAccessToken,
}));

import {
  createClientEnvelope,
  GAMEIO_WS_JWT_PROTOCOL_PREFIX,
  GAMEIO_WS_PROTOCOL,
  GameSocketClient,
  shouldTrackPendingRequest,
} from "@/lib/socket/game-socket-client";

type Listener = (event: { data?: string }) => void;

class FakeWebSocket {
  static CONNECTING = 0;
  static OPEN = 1;
  static CLOSING = 2;
  static CLOSED = 3;
  static instances: FakeWebSocket[] = [];

  readyState = FakeWebSocket.CONNECTING;
  sent: string[] = [];
  private listeners = new Map<string, Listener[]>();

  readonly url: string;

  constructor(
    url: string | URL,
    readonly protocols?: string | string[],
  ) {
    this.url = String(url);
    FakeWebSocket.instances.push(this);
  }

  addEventListener(type: string, listener: Listener) {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), listener]);
  }

  send(message: string) {
    this.sent.push(message);
  }

  open() {
    this.readyState = FakeWebSocket.OPEN;
    this.emit("open");
  }

  close() {
    this.readyState = FakeWebSocket.CLOSED;
    this.emit("close");
  }

  private emit(type: string, event: { data?: string } = {}) {
    this.listeners.get(type)?.forEach((listener) => listener(event));
  }
}

async function flushConnection() {
  await Promise.resolve();
  await Promise.resolve();
}

describe("GameSocketClient protocol", () => {
  beforeEach(() => {
    FakeWebSocket.instances = [];
    mocks.ensureAccessToken.mockClear();
    mocks.ensureAccessToken.mockResolvedValue("access-token");
    vi.stubGlobal("WebSocket", FakeWebSocket);
  });

  it("builds strict request envelopes and only tracks GAME_INPUT", () => {
    const envelope = createClientEnvelope(
      "GAME_INPUT",
      {
        roomId: "room-1",
        requestId: "request-1",
        payload: { action: "PLACE_PIECE", row: 1, column: 2 },
        now: () => new Date("2026-08-10T00:00:00Z"),
      },
    );
    expect(envelope).toEqual({
      type: "GAME_INPUT",
      requestId: "request-1",
      roomId: "room-1",
      payload: { action: "PLACE_PIECE", row: 1, column: 2 },
      sentAt: "2026-08-10T00:00:00.000Z",
    });
    expect(shouldTrackPendingRequest("GAME_INPUT")).toBe(true);
    expect(shouldTrackPendingRequest("ROOM_READY")).toBe(false);
    expect(shouldTrackPendingRequest("ROOM_START")).toBe(false);
  });

  it("joins the active room after opening a connection", async () => {
    const client = new GameSocketClient();
    client.joinRoom("room-1");
    await flushConnection();
    const socket = FakeWebSocket.instances[0];
    expect(new URL(socket.url).searchParams.has("access_token")).toBe(false);
    expect(socket.protocols).toEqual([
      GAMEIO_WS_PROTOCOL,
      GAMEIO_WS_JWT_PROTOCOL_PREFIX + "access-token",
    ]);
    socket.open();
    const envelope = JSON.parse(socket.sent[0]) as {
      type: string;
      roomId: string;
      requestId: string;
    };
    expect(envelope.type).toBe("ROOM_JOIN");
    expect(envelope.roomId).toBe("room-1");
    expect(envelope.requestId).toBeTruthy();
    client.disconnect();
    expect(client.roomId).toBeNull();
  });

  it("sends invite and rematch commands without trusting client room state", async () => {
    const client = new GameSocketClient();
    client.connect();
    await flushConnection();
    const socket = FakeWebSocket.instances[0];
    socket.open();

    client.sendGameInvite("room-1", "Friend_1");
    client.rematchRoom("room-1");

    expect(JSON.parse(socket.sent[0])).toMatchObject({
      type: "GAME_INVITE_SEND",
      roomId: "room-1",
      payload: { recipientUsername: "Friend_1" },
    });
    expect(JSON.parse(socket.sent[1])).toMatchObject({
      type: "ROOM_REMATCH",
      roomId: "room-1",
    });
    client.disconnect();
  });

  it("forces a token refresh and ignores the old socket close on manual reconnect", async () => {
    vi.useFakeTimers();
    const client = new GameSocketClient();
    client.connect();
    await flushConnection();
    const oldSocket = FakeWebSocket.instances[0];
    oldSocket.open();

    client.reconnectNow();
    await flushConnection();
    expect(FakeWebSocket.instances).toHaveLength(2);
    expect(mocks.ensureAccessToken).toHaveBeenLastCalledWith(true);

    await vi.advanceTimersByTimeAsync(20_000);
    expect(FakeWebSocket.instances).toHaveLength(2);
    client.disconnect();
    vi.useRealTimers();
  });

  it("reconnects exponentially and refreshes the token before the next handshake", async () => {
    vi.useFakeTimers();
    const client = new GameSocketClient();
    client.connect();
    await flushConnection();
    FakeWebSocket.instances[0].open();
    FakeWebSocket.instances[0].close();

    await vi.advanceTimersByTimeAsync(499);
    expect(FakeWebSocket.instances).toHaveLength(1);
    await vi.advanceTimersByTimeAsync(1);
    await flushConnection();
    expect(FakeWebSocket.instances).toHaveLength(2);
    expect(mocks.ensureAccessToken).toHaveBeenLastCalledWith(true);
    client.disconnect();
    vi.useRealTimers();
  });
});
