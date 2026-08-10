import { describe, expect, it } from "vitest";
import {
  isSnapshotForGame,
  isTurnBoardSnapshot,
  roomPayloadMatches,
} from "@/features/multiplayer/realtime/validation";

function board(size: number) {
  return Array.from({ length: size }, () => Array(size).fill(""));
}

describe("realtime payload validation", () => {
  it("requires exact turn-board dimensions", () => {
    const tic = {
      sequence: 0,
      board: board(3),
      currentTurnPlayerId: "user-1",
      winnerId: null,
      draw: false,
    };
    expect(isTurnBoardSnapshot(tic, 3)).toBe(true);
    expect(
      isTurnBoardSnapshot(
        {
          sequence: 0,
          board: board(3),
          currentTurnPlayerId: "user-1",
          draw: false,
        },
        3,
      ),
    ).toBe(true);
    expect(isTurnBoardSnapshot({ ...tic, board: board(4) }, 3)).toBe(false);
    expect(
      isTurnBoardSnapshot({ ...tic, boardSize: 15, board: board(15) }, 15),
    ).toBe(true);
    expect(
      isTurnBoardSnapshot({ ...tic, boardSize: 14, board: board(15) }, 15),
    ).toBe(false);
  });

  it("does not accept one multiplayer engine's snapshot as another", () => {
    const tic = {
      sequence: 0,
      board: board(3),
      currentTurnPlayerId: null,
      winnerId: null,
      draw: false,
    };
    expect(isSnapshotForGame("tic-tac-toe", tic)).toBe(true);
    expect(isSnapshotForGame("caro", tic)).toBe(false);
    expect(isSnapshotForGame("tank-battle", tic)).toBe(false);
  });

  it("requires both room UUID and page game slug to match", () => {
    const room = {
      roomId: "room-1",
      gameSlug: "tank-battle",
      players: [],
    };
    expect(roomPayloadMatches(room, "room-1", "tank-battle")).toBe(true);
    expect(roomPayloadMatches(room, "room-2", "tank-battle")).toBe(false);
    expect(roomPayloadMatches(room, "room-1", "tic-tac-toe")).toBe(false);
  });
});
