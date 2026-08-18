import { describe, expect, it } from "vitest";
import {
  isSnapshotForGame,
  isTypingRaceSnapshot,
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

  it("validates bounded authoritative typing snapshots", () => {
    const typing = {
      sequence: 3,
      passageId: "home-row-01",
      passage: "abc",
      startsAt: "2026-08-18T00:00:03Z",
      deadline: "2026-08-18T00:01:33Z",
      players: [
        { userId: "user-1", progress: 2, correctCharacters: 2, errors: 1, combo: 1, bestCombo: 2, lastInputSequence: 2, wpm: 24, accuracyPercent: 67, finished: false },
        { userId: "user-2", progress: 1, correctCharacters: 1, errors: 0, combo: 1, bestCombo: 1, lastInputSequence: 0, wpm: 15, accuracyPercent: 100, finished: false },
      ],
      winnerId: null,
      draw: false,
      terminal: false,
    };
    expect(isTypingRaceSnapshot(typing)).toBe(true);
    expect(isSnapshotForGame("typing-race", typing)).toBe(true);
    expect(isSnapshotForGame("tank-battle", typing)).toBe(false);
    expect(isTypingRaceSnapshot({ ...typing, players: [typing.players[0]] })).toBe(false);
    expect(isTypingRaceSnapshot({ ...typing, passage: "a", players: typing.players })).toBe(false);
    expect(isTypingRaceSnapshot({
      ...typing,
      players: [{ ...typing.players[0], lastInputSequence: 20 }, typing.players[1]],
    })).toBe(false);
    expect(isTypingRaceSnapshot({ ...typing, winnerId: "outsider", terminal: true })).toBe(false);
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
