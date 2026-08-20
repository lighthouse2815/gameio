import { describe, expect, it } from "vitest";
import {
  isConnectFourSnapshot,
  isReversiSnapshot,
  isRpsSnapshot,
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

  it("validates Connect Four dimensions, move count, and terminal ownership", () => {
    const grid = Array.from({ length: 6 }, () => Array(7).fill(""));
    grid[5][0] = "R";
    const snapshot = {
      sequence: 1,
      board: grid,
      currentTurnPlayerId: "user-2",
      winnerId: null,
      draw: false,
      lastMoveRow: 5,
      lastMoveColumn: 0,
    };
    expect(isConnectFourSnapshot(snapshot)).toBe(true);
    expect(isSnapshotForGame("connect-four", snapshot)).toBe(true);
    expect(isConnectFourSnapshot({ ...snapshot, sequence: 2 })).toBe(false);
    expect(isConnectFourSnapshot({ ...snapshot, currentTurnPlayerId: null })).toBe(false);
    expect(isConnectFourSnapshot({ ...snapshot, lastMoveColumn: 7 })).toBe(false);
  });

  it("validates Reversi counts and server-provided legal cells", () => {
    const grid = board(8);
    grid[3][3] = "W";
    grid[3][4] = "B";
    grid[4][3] = "B";
    grid[4][4] = "W";
    const snapshot = {
      sequence: 0,
      board: grid,
      currentTurnPlayerId: "user-1",
      winnerId: null,
      draw: false,
      blackCount: 2,
      whiteCount: 2,
      legalMoves: [{ row: 2, column: 3 }],
      lastMoveRow: null,
      lastMoveColumn: null,
    };
    expect(isReversiSnapshot(snapshot)).toBe(true);
    expect(isSnapshotForGame("reversi", snapshot)).toBe(true);
    expect(isReversiSnapshot({ ...snapshot, blackCount: 3 })).toBe(false);
    expect(isReversiSnapshot({ ...snapshot, legalMoves: [{ row: 3, column: 3 }] })).toBe(false);
  });

  it("validates hidden simultaneous Rock Paper Scissors rounds", () => {
    const waiting = {
      sequence: 1,
      round: 1,
      targetWins: 3,
      players: [
        { userId: "user-1", wins: 0, submitted: true },
        { userId: "user-2", wins: 0, submitted: false },
      ],
      winnerId: null,
      draw: false,
    };
    expect(isRpsSnapshot(waiting)).toBe(true);
    expect(isSnapshotForGame("rock-paper-scissors", waiting)).toBe(true);
    const resolved = {
      ...waiting,
      sequence: 2,
      round: 2,
      players: waiting.players.map((player, index) => ({
        ...player,
        wins: index === 0 ? 1 : 0,
        submitted: false,
      })),
      lastRound: {
        round: 1,
        firstChoice: "ROCK",
        secondChoice: "SCISSORS",
        winnerId: "user-1",
        draw: false,
      },
    };
    expect(isRpsSnapshot(resolved)).toBe(true);
    expect(isRpsSnapshot({ ...resolved, sequence: 3 })).toBe(false);
    expect(isRpsSnapshot({
      ...resolved,
      lastRound: { ...resolved.lastRound, winnerId: "outsider" },
    })).toBe(false);
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
