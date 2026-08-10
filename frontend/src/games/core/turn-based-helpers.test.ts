import { describe, expect, it } from "vitest";
import type { TicTacToeSnapshot } from "@/features/multiplayer/realtime/types";
import {
  canPlacePiece,
  isTurnBasedSnapshot,
  markerForPlayer,
} from "@/games/core/turn-based-helpers";
import type { GameRoom } from "@/features/multiplayer/types";

const snapshot: TicTacToeSnapshot = {
  sequence: 1,
  board: [
    ["X", "", ""],
    ["", "", ""],
    ["", "", ""],
  ],
  currentTurnPlayerId: "user-2",
  winnerId: null,
  draw: false,
};

describe("turn-based input guards", () => {
  it("rejects crafted boards with the wrong dimensions", () => {
    expect(isTurnBasedSnapshot(snapshot, 3)).toBe(true);
    expect(
      isTurnBasedSnapshot({ ...snapshot, board: [["", "", ""]] }, 3),
    ).toBe(false);
    expect(isTurnBasedSnapshot(snapshot, 15)).toBe(false);
  });

  it("allows only the named turn holder to place in an empty cell", () => {
    expect(canPlacePiece(snapshot, "user-2", 1, 1, false)).toBe(true);
    expect(canPlacePiece(snapshot, "user-1", 1, 1, false)).toBe(false);
    expect(canPlacePiece(snapshot, "user-2", 0, 0, false)).toBe(false);
    expect(canPlacePiece(snapshot, "user-2", 1, 1, true)).toBe(false);
  });

  it("maps player order to the server X and O markers", () => {
    const room = {
      players: [{ id: "user-1" }, { id: "user-2" }],
    } as GameRoom;
    expect(markerForPlayer(room, "user-1")).toBe("X");
    expect(markerForPlayer(room, "user-2")).toBe("O");
    expect(markerForPlayer(room, "spectator")).toBeNull();
  });
});
