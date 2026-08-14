import { ensureAccessToken } from "@/lib/api/client";
import { runtimeConfig } from "@/lib/config";

export type ClientEventType =
  | "ROOM_JOIN"
  | "ROOM_LEAVE"
  | "ROOM_READY"
  | "ROOM_START"
  | "ROOM_REMATCH"
  | "GAME_INVITE_SEND"
  | "GAME_INVITE_ACCEPT"
  | "GAME_INVITE_DECLINE"
  | "MATCHMAKING_JOIN"
  | "MATCHMAKING_LEAVE"
  | "GAME_INPUT";

export type ServerEventType =
  | "CONNECTED"
  | "ROOM_STATE"
  | "ROOM_LEFT"
  | "MATCHMAKING_STATE"
  | "MATCH_FOUND"
  | "GAME_START"
  | "GAME_STATE"
  | "GAME_OVER"
  | "GAME_INVITE"
  | "GAME_INVITE_SENT"
  | "GAME_INVITE_ACCEPTED"
  | "GAME_INVITE_DECLINED"
  | "OPPONENT_DISCONNECTED"
  | "ERROR";

export type ClientEnvelope<T = unknown> = {
  type: ClientEventType;
  requestId: string;
  roomId?: string;
  gameSlug?: string;
  payload?: T;
  sentAt: string;
};

export type ServerEnvelope<T = unknown> = {
  type: ServerEventType;
  requestId?: string | null;
  roomId?: string | null;
  payload?: T;
  timestamp?: string;
};

export type GameInputPayload = {
  action:
    | "PLACE_PIECE"
    | "MOVE_UP"
    | "MOVE_DOWN"
    | "MOVE_LEFT"
    | "MOVE_RIGHT"
    | "STOP"
    | "SHOOT";
  row?: number;
  column?: number;
  sequence?: number;
};

export type SocketStatus =
  | "idle"
  | "connecting"
  | "connected"
  | "reconnecting"
  | "disconnected"
  | "error";

type EventListener = (event: ServerEnvelope) => void;
type StatusListener = (status: SocketStatus) => void;

export const GAMEIO_WS_PROTOCOL = "gameio.v1";
export const GAMEIO_WS_JWT_PROTOCOL_PREFIX = "gameio.jwt.";

export function createRequestId() {
  if (typeof crypto !== "undefined" && "randomUUID" in crypto) {
    return crypto.randomUUID();
  }
  return (
    Date.now().toString(36) +
    "-" +
    Math.random().toString(36).slice(2, 12)
  );
}

export function createClientEnvelope<T>(
  type: ClientEventType,
  options: {
    roomId?: string;
    gameSlug?: string;
    payload?: T;
    requestId?: string;
    now?: () => Date;
  } = {},
): ClientEnvelope<T> {
  return {
    type,
    requestId: options.requestId ?? createRequestId(),
    ...(options.roomId ? { roomId: options.roomId } : {}),
    ...(options.gameSlug ? { gameSlug: options.gameSlug } : {}),
    ...(options.payload !== undefined ? { payload: options.payload } : {}),
    sentAt: (options.now ?? (() => new Date()))().toISOString(),
  };
}

export function shouldTrackPendingRequest(type: ClientEventType) {
  return type === "GAME_INPUT";
}

export class GameSocketClient {
  private socket: WebSocket | null = null;
  private eventListeners = new Set<EventListener>();
  private statusListeners = new Set<StatusListener>();
  private reconnectAttempts = 0;
  private reconnectTimer: number | null = null;
  private shouldReconnect = false;
  private activeRoomId: string | null = null;
  private currentStatus: SocketStatus = "idle";
  private connectionGeneration = 0;

  get status() {
    return this.currentStatus;
  }

  get roomId() {
    return this.activeRoomId;
  }

  connect(forceRefresh = false) {
    if (
      this.socket?.readyState === WebSocket.OPEN ||
      this.socket?.readyState === WebSocket.CONNECTING
    ) {
      return;
    }
    this.clearReconnectTimer();
    this.shouldReconnect = true;
    const generation = ++this.connectionGeneration;
    this.emitStatus(this.reconnectAttempts ? "reconnecting" : "connecting");
    void this.openConnection(generation, forceRefresh);
  }

  private async openConnection(
    generation: number,
    forceRefresh: boolean,
  ) {
    const token = await ensureAccessToken(forceRefresh);
    if (generation !== this.connectionGeneration || !this.shouldReconnect) {
      return;
    }
    if (!token) {
      this.shouldReconnect = false;
      this.emitStatus("error");
      this.emitEvent({
        type: "ERROR",
        payload: {
          code: "ACCESS_TOKEN_REQUIRED",
          message: "Refresh the authenticated session before opening realtime.",
        },
      });
      return;
    }
    const url = new URL(runtimeConfig.wsUrl);
    const socket = new WebSocket(url, [
      GAMEIO_WS_PROTOCOL,
      GAMEIO_WS_JWT_PROTOCOL_PREFIX + token,
    ]);
    this.socket = socket;
    socket.addEventListener("open", () => {
      if (
        generation !== this.connectionGeneration ||
        this.socket !== socket
      ) {
        socket.close(4001, "Stale connection");
        return;
      }
      this.reconnectAttempts = 0;
      this.emitStatus("connected");
      if (this.activeRoomId) {
        this.sendEnvelope(
          createClientEnvelope("ROOM_JOIN", {
            roomId: this.activeRoomId,
          }),
        );
      }
    });
    socket.addEventListener("message", (message) => {
      if (
        generation !== this.connectionGeneration ||
        this.socket !== socket
      ) {
        return;
      }
      try {
        this.emitEvent(JSON.parse(String(message.data)) as ServerEnvelope);
      } catch {
        this.emitEvent({
          type: "ERROR",
          payload: {
            code: "MALFORMED_SERVER_MESSAGE",
            message: "Realtime server returned malformed JSON.",
          },
        });
      }
    });
    socket.addEventListener("error", () => {
      if (
        generation === this.connectionGeneration &&
        this.socket === socket
      ) {
        this.emitStatus("error");
      }
    });
    socket.addEventListener("close", () => {
      if (
        generation !== this.connectionGeneration ||
        this.socket !== socket
      ) {
        return;
      }
      this.socket = null;
      if (!this.shouldReconnect) {
        this.emitStatus("disconnected");
        return;
      }
      this.scheduleReconnect();
    });
  }

  reconnectNow() {
    this.shouldReconnect = true;
    this.reconnectAttempts = 0;
    this.clearReconnectTimer();
    const previous = this.socket;
    this.socket = null;
    this.connectionGeneration += 1;
    previous?.close(4000, "Client reconnect");
    this.connect(true);
  }

  disconnect() {
    this.shouldReconnect = false;
    this.reconnectAttempts = 0;
    this.clearReconnectTimer();
    this.connectionGeneration += 1;
    this.activeRoomId = null;
    this.socket?.close(1000, "Client disconnect");
    this.socket = null;
    this.emitStatus("disconnected");
  }

  joinRoom(roomId: string) {
    this.activeRoomId = roomId;
    if (this.socket?.readyState === WebSocket.OPEN) {
      return this.sendEnvelope(
        createClientEnvelope("ROOM_JOIN", { roomId }),
      );
    }
    this.connect();
    return null;
  }

  clearActiveRoom(roomId?: string) {
    if (!roomId || this.activeRoomId === roomId) {
      this.activeRoomId = null;
    }
  }

  leaveRoom(roomId: string) {
    const requestId = this.sendEnvelope(
      createClientEnvelope("ROOM_LEAVE", { roomId }),
    );
    this.clearActiveRoom(roomId);
    return requestId;
  }

  readyRoom(roomId: string) {
    return this.sendEnvelope(
      createClientEnvelope("ROOM_READY", { roomId }),
    );
  }

  startRoom(roomId: string) {
    return this.sendEnvelope(
      createClientEnvelope("ROOM_START", { roomId }),
    );
  }

  rematchRoom(roomId: string) {
    this.activeRoomId = roomId;
    return this.sendEnvelope(
      createClientEnvelope("ROOM_REMATCH", { roomId }),
    );
  }

  sendGameInvite(roomId: string, recipientUsername: string) {
    return this.sendEnvelope(
      createClientEnvelope("GAME_INVITE_SEND", {
        roomId,
        payload: { recipientUsername },
      }),
    );
  }

  acceptGameInvite(inviteId: string, roomId: string) {
    this.activeRoomId = roomId;
    return this.sendEnvelope(
      createClientEnvelope("GAME_INVITE_ACCEPT", {
        payload: { inviteId },
      }),
    );
  }

  declineGameInvite(inviteId: string) {
    return this.sendEnvelope(
      createClientEnvelope("GAME_INVITE_DECLINE", {
        payload: { inviteId },
      }),
    );
  }

  joinMatchmaking(gameId: string) {
    return this.sendEnvelope(
      createClientEnvelope("MATCHMAKING_JOIN", {
        payload: { gameId },
      }),
    );
  }

  leaveMatchmaking() {
    return this.sendEnvelope(createClientEnvelope("MATCHMAKING_LEAVE"));
  }

  sendGameInput(roomId: string, payload: GameInputPayload) {
    return this.sendEnvelope(
      createClientEnvelope("GAME_INPUT", { roomId, payload }),
    );
  }

  subscribeGameState(listener: EventListener) {
    this.eventListeners.add(listener);
    return () => {
      this.eventListeners.delete(listener);
    };
  }

  subscribeStatus(listener: StatusListener) {
    this.statusListeners.add(listener);
    listener(this.currentStatus);
    return () => {
      this.statusListeners.delete(listener);
    };
  }

  private sendEnvelope(envelope: ClientEnvelope) {
    if (this.socket?.readyState !== WebSocket.OPEN) {
      throw new Error("Realtime link is not connected.");
    }
    this.socket.send(JSON.stringify(envelope));
    return envelope.requestId;
  }

  private scheduleReconnect() {
    if (this.reconnectAttempts >= 8) {
      this.shouldReconnect = false;
      this.emitStatus("disconnected");
      return;
    }
    this.emitStatus("reconnecting");
    const delay = Math.min(500 * 2 ** this.reconnectAttempts, 10_000);
    this.reconnectAttempts += 1;
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.connect(true);
    }, delay);
  }

  private clearReconnectTimer() {
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
  }

  private emitEvent(event: ServerEnvelope) {
    this.eventListeners.forEach((listener) => listener(event));
  }

  private emitStatus(status: SocketStatus) {
    this.currentStatus = status;
    this.statusListeners.forEach((listener) => listener(status));
  }
}

export const gameSocketClient = new GameSocketClient();
