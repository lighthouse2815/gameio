import type {
  GameSnapshot,
  TankSnapshot,
  TicTacToeSnapshot,
  TypingRaceSnapshot,
} from "@/features/multiplayer/realtime/types";

function record(value: unknown): Record<string, unknown> | null {
  return typeof value === "object" && value !== null
    ? (value as Record<string, unknown>)
    : null;
}

function optionalNullableString(value: unknown) {
  // Jackson omits null fields from authoritative snapshots. A missing terminal
  // player is therefore equivalent to null while a match is still in progress.
  return value === undefined || value === null || typeof value === "string";
}

export function isTurnBoardSnapshot(
  value: unknown,
  expectedSize: 3 | 15,
): value is TicTacToeSnapshot {
  const snapshot = record(value);
  if (!snapshot || !Array.isArray(snapshot.board)) return false;
  if (
    !Number.isSafeInteger(snapshot.sequence) ||
    (snapshot.sequence as number) < 0 ||
    snapshot.board.length !== expectedSize ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId)
  ) {
    return false;
  }
  if (
    expectedSize === 15 &&
    (!Number.isInteger(snapshot.boardSize) || snapshot.boardSize !== 15)
  ) {
    return false;
  }
  return snapshot.board.every(
    (row) =>
      Array.isArray(row) &&
      row.length === expectedSize &&
      row.every((cell) => cell === "" || cell === "X" || cell === "O"),
  );
}

function finiteNumber(value: unknown) {
  return typeof value === "number" && Number.isFinite(value);
}

function isTankSnapshot(value: unknown): value is TankSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !Number.isSafeInteger(snapshot.sequence) ||
    (snapshot.sequence as number) < 0 ||
    !finiteNumber(snapshot.width) ||
    !finiteNumber(snapshot.height) ||
    (snapshot.width as number) <= 0 ||
    (snapshot.height as number) <= 0 ||
    !Array.isArray(snapshot.tanks) ||
    !Array.isArray(snapshot.bullets) ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.winnerId)
  ) {
    return false;
  }
  const tanksValid = snapshot.tanks.every((rawTank) => {
    const tank = record(rawTank);
    return Boolean(
      tank &&
        typeof tank.userId === "string" &&
        finiteNumber(tank.x) &&
        finiteNumber(tank.y) &&
        finiteNumber(tank.rotation) &&
        Number.isInteger(tank.hp) &&
        typeof tank.alive === "boolean" &&
        Number.isInteger(tank.kills) &&
        Number.isSafeInteger(tank.lastInputSequence),
    );
  });
  const bulletsValid = snapshot.bullets.every((rawBullet) => {
    const bullet = record(rawBullet);
    return Boolean(
      bullet &&
        typeof bullet.id === "string" &&
        typeof bullet.ownerId === "string" &&
        finiteNumber(bullet.x) &&
        finiteNumber(bullet.y),
    );
  });
  return tanksValid && bulletsValid;
}

export function isTypingRaceSnapshot(value: unknown): value is TypingRaceSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !Number.isSafeInteger(snapshot.sequence) ||
    (snapshot.sequence as number) < 0 ||
    typeof snapshot.passageId !== "string" ||
    snapshot.passageId.length < 1 ||
    snapshot.passageId.length > 64 ||
    typeof snapshot.passage !== "string" ||
    snapshot.passage.length < 1 ||
    Array.from(snapshot.passage).length > 512 ||
    typeof snapshot.startsAt !== "string" ||
    typeof snapshot.deadline !== "string" ||
    !Number.isFinite(Date.parse(snapshot.startsAt)) ||
    !Number.isFinite(Date.parse(snapshot.deadline)) ||
    Date.parse(snapshot.startsAt) >= Date.parse(snapshot.deadline) ||
    !Array.isArray(snapshot.players) ||
    snapshot.players.length !== 2 ||
    typeof snapshot.draw !== "boolean" ||
    typeof snapshot.terminal !== "boolean" ||
    !optionalNullableString(snapshot.winnerId)
  ) {
    return false;
  }
  const passageLength = Array.from(snapshot.passage).length;
  const userIds = new Set<string>();
  const playersValid = snapshot.players.every((rawPlayer) => {
    const player = record(rawPlayer);
    if (!player || typeof player.userId !== "string" || userIds.has(player.userId)) return false;
    userIds.add(player.userId);
    const correctCharacters = player.correctCharacters as number;
    const errors = player.errors as number;
    const lastInputSequence = player.lastInputSequence as number;
    return Boolean(
      Number.isSafeInteger(player.progress) &&
        (player.progress as number) >= 0 &&
        (player.progress as number) <= passageLength &&
        Number.isSafeInteger(player.correctCharacters) &&
        player.correctCharacters === player.progress &&
        Number.isSafeInteger(player.errors) &&
        errors >= 0 &&
        Number.isSafeInteger(player.combo) &&
        (player.combo as number) >= 0 &&
        Number.isSafeInteger(player.bestCombo) &&
        (player.bestCombo as number) >= (player.combo as number) &&
        (player.bestCombo as number) <= correctCharacters &&
        Number.isSafeInteger(player.lastInputSequence) &&
        lastInputSequence >= -1 &&
        correctCharacters + errors === lastInputSequence + 1 &&
        Number.isSafeInteger(player.wpm) &&
        (player.wpm as number) >= 0 &&
        (player.wpm as number) <= 2_000 &&
        Number.isInteger(player.accuracyPercent) &&
        (player.accuracyPercent as number) >= 0 &&
        (player.accuracyPercent as number) <= 100 &&
        typeof player.finished === "boolean" &&
        player.finished === (player.progress === passageLength) &&
        (player.finished
          ? typeof player.finishedAt === "string" && Number.isFinite(Date.parse(player.finishedAt))
          : player.finishedAt === null || player.finishedAt === undefined)
    );
  });
  if (!playersValid) return false;
  const winnerId = typeof snapshot.winnerId === "string" ? snapshot.winnerId : null;
  return (
    (!winnerId || userIds.has(winnerId)) &&
    snapshot.terminal === Boolean(winnerId || snapshot.draw) &&
    !(winnerId && snapshot.draw)
  );
}

export function isSnapshotForGame(
  gameSlug: string,
  value: unknown,
): value is GameSnapshot {
  if (gameSlug === "tic-tac-toe") return isTurnBoardSnapshot(value, 3);
  if (gameSlug === "caro") return isTurnBoardSnapshot(value, 15);
  if (gameSlug === "tank-battle") return isTankSnapshot(value);
  if (gameSlug === "typing-race") return isTypingRaceSnapshot(value);
  return false;
}

export function roomPayloadMatches(
  payload: unknown,
  expectedRoomId: string,
  expectedGameSlug: string,
) {
  const room = record(payload);
  return Boolean(
    room &&
      room.roomId === expectedRoomId &&
      room.gameSlug === expectedGameSlug &&
      Array.isArray(room.players),
  );
}

export function gameStartPayloadMatches(
  payload: unknown,
  expectedGameSlug: string,
) {
  const start = record(payload);
  return Boolean(
    start &&
      start.gameSlug === expectedGameSlug &&
      typeof start.matchId === "string" &&
      isSnapshotForGame(expectedGameSlug, start.state),
  );
}

export function gameOverPayloadMatches(
  payload: unknown,
  expectedGameSlug: string,
) {
  const gameOver = record(payload);
  return Boolean(
    gameOver &&
      typeof gameOver.matchId === "string" &&
      Array.isArray(gameOver.progression) &&
      isSnapshotForGame(expectedGameSlug, gameOver.finalState),
  );
}
