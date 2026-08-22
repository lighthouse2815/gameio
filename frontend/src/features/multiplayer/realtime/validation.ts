import type {
  ConnectFourSnapshot,
  DotsAndBoxesSnapshot,
  GameSnapshot,
  HexSnapshot,
  MancalaSnapshot,
  ReversiSnapshot,
  RpsSnapshot,
  SosSnapshot,
  TankSnapshot,
  TicTacToeSnapshot,
  TypingRaceSnapshot,
  UltimateTicTacToeSnapshot,
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

function integerBetween(value: unknown, minimum: number, maximum: number) {
  return Number.isSafeInteger(value) &&
    (value as number) >= minimum &&
    (value as number) <= maximum;
}

function terminalPlayersValid(snapshot: Record<string, unknown>) {
  const winnerId = typeof snapshot.winnerId === "string" ? snapshot.winnerId : null;
  const draw = snapshot.draw === true;
  const currentTurn = typeof snapshot.currentTurnPlayerId === "string";
  return !(winnerId && draw) && (winnerId || draw ? !currentTurn : currentTurn);
}

function lastMoveValid(
  snapshot: Record<string, unknown>,
  rows: number,
  columns: number,
) {
  const sequence = snapshot.sequence as number;
  const rowMissing = snapshot.lastMoveRow === undefined || snapshot.lastMoveRow === null;
  const columnMissing = snapshot.lastMoveColumn === undefined || snapshot.lastMoveColumn === null;
  if (sequence === 0) return rowMissing && columnMissing;
  return integerBetween(snapshot.lastMoveRow, 0, rows - 1) &&
    integerBetween(snapshot.lastMoveColumn, 0, columns - 1);
}

export function isConnectFourSnapshot(value: unknown): value is ConnectFourSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 42) ||
    !Array.isArray(snapshot.board) ||
    snapshot.board.length !== 6 ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId) ||
    !terminalPlayersValid(snapshot) ||
    !lastMoveValid(snapshot, 6, 7)
  ) {
    return false;
  }
  let occupied = 0;
  const boardValid = snapshot.board.every((row) =>
    Array.isArray(row) &&
    row.length === 7 &&
    row.every((cell) => {
      if (cell === "R" || cell === "Y") occupied++;
      return cell === "" || cell === "R" || cell === "Y";
    }),
  );
  return boardValid && occupied === snapshot.sequence;
}

export function isReversiSnapshot(value: unknown): value is ReversiSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 60) ||
    !Array.isArray(snapshot.board) ||
    snapshot.board.length !== 8 ||
    !integerBetween(snapshot.blackCount, 0, 64) ||
    !integerBetween(snapshot.whiteCount, 0, 64) ||
    !Array.isArray(snapshot.legalMoves) ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId) ||
    !terminalPlayersValid(snapshot) ||
    !lastMoveValid(snapshot, 8, 8)
  ) {
    return false;
  }
  let black = 0;
  let white = 0;
  const reversiBoard = snapshot.board as unknown[][];
  const boardValid = reversiBoard.every((row) =>
    Array.isArray(row) &&
    row.length === 8 &&
    row.every((cell) => {
      if (cell === "B") black++;
      if (cell === "W") white++;
      return cell === "" || cell === "B" || cell === "W";
    }),
  );
  const legalMoveKeys = new Set<string>();
  const legalMovesValid = snapshot.legalMoves.every((rawMove) => {
    const move = record(rawMove);
    if (!move || !integerBetween(move.row, 0, 7) || !integerBetween(move.column, 0, 7)) {
      return false;
    }
    const key = `${move.row}:${move.column}`;
    if (legalMoveKeys.has(key) || reversiBoard[move.row as number]?.[move.column as number] !== "") {
      return false;
    }
    legalMoveKeys.add(key);
    return true;
  });
  const terminal = Boolean(snapshot.winnerId || snapshot.draw);
  return boardValid && legalMovesValid &&
    black === snapshot.blackCount &&
    white === snapshot.whiteCount &&
    black + white === (snapshot.sequence as number) + 4 &&
    (terminal ? snapshot.legalMoves.length === 0 : snapshot.legalMoves.length > 0);
}

function threeInLine(cells: string[][], marker: string) {
  for (let index = 0; index < 3; index++) {
    if (cells[index].every((cell) => cell === marker)) return true;
    if (cells.every((row) => row[index] === marker)) return true;
  }
  return (
    (cells[0][0] === marker && cells[1][1] === marker && cells[2][2] === marker) ||
    (cells[0][2] === marker && cells[1][1] === marker && cells[2][0] === marker)
  );
}

function ultimateSubBoard(
  board: string[][],
  subBoardRow: number,
  subBoardColumn: number,
) {
  const cells = Array.from({ length: 3 }, (_, row) =>
    Array.from(
      { length: 3 },
      (_, column) => board[subBoardRow * 3 + row][subBoardColumn * 3 + column],
    ),
  );
  const xWins = threeInLine(cells, "X");
  const oWins = threeInLine(cells, "O");
  if (xWins && oWins) return null;
  if (xWins) return "X";
  if (oWins) return "O";
  return cells.every((row) => row.every(Boolean)) ? "D" : "";
}

export function isUltimateTicTacToeSnapshot(
  value: unknown,
): value is UltimateTicTacToeSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 81) ||
    !Array.isArray(snapshot.board) ||
    snapshot.board.length !== 9 ||
    !Array.isArray(snapshot.subBoards) ||
    snapshot.subBoards.length !== 3 ||
    !Array.isArray(snapshot.legalMoves) ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId) ||
    !terminalPlayersValid(snapshot) ||
    !lastMoveValid(snapshot, 9, 9)
  ) {
    return false;
  }

  let xCount = 0;
  let oCount = 0;
  const board = snapshot.board as unknown[][];
  const boardValid = board.every(
    (row) =>
      Array.isArray(row) &&
      row.length === 9 &&
      row.every((cell) => {
        if (cell === "X") xCount++;
        if (cell === "O") oCount++;
        return cell === "" || cell === "X" || cell === "O";
      }),
  );
  if (
    !boardValid ||
    xCount + oCount !== snapshot.sequence ||
    xCount < oCount ||
    xCount > oCount + 1
  ) {
    return false;
  }

  const typedBoard = board as string[][];
  const subBoards = snapshot.subBoards as unknown[][];
  const derivedSubBoards: string[][] = Array.from({ length: 3 }, () =>
    Array(3).fill(""),
  );
  const subBoardsValid = subBoards.every(
    (row, subBoardRow) =>
      Array.isArray(row) &&
      row.length === 3 &&
      row.every((cell, subBoardColumn) => {
        if (!["", "X", "O", "D"].includes(String(cell))) return false;
        const derived = ultimateSubBoard(
          typedBoard,
          subBoardRow,
          subBoardColumn,
        );
        if (derived === null || cell !== derived) return false;
        derivedSubBoards[subBoardRow][subBoardColumn] = derived;
        return true;
      }),
  );
  if (!subBoardsValid) return false;

  const xWins = threeInLine(derivedSubBoards, "X");
  const oWins = threeInLine(derivedSubBoards, "O");
  const allSubBoardsClosed = derivedSubBoards.every((row) => row.every(Boolean));
  const terminal = Boolean(snapshot.winnerId || snapshot.draw);
  if (
    xWins && oWins ||
    terminal !== (xWins || oWins || allSubBoardsClosed) ||
    snapshot.draw !== (allSubBoardsClosed && !xWins && !oWins)
  ) {
    return false;
  }

  const forcedRowMissing =
    snapshot.forcedBoardRow === undefined || snapshot.forcedBoardRow === null;
  const forcedColumnMissing =
    snapshot.forcedBoardColumn === undefined || snapshot.forcedBoardColumn === null;
  if (forcedRowMissing !== forcedColumnMissing) return false;
  if (
    !forcedRowMissing &&
    (!integerBetween(snapshot.forcedBoardRow, 0, 2) ||
      !integerBetween(snapshot.forcedBoardColumn, 0, 2) ||
      derivedSubBoards[snapshot.forcedBoardRow as number][
        snapshot.forcedBoardColumn as number
      ] !== "")
  ) {
    return false;
  }
  if (terminal && !forcedRowMissing) return false;

  const legalMoveKeys = new Set<string>();
  const legalMovesValid = snapshot.legalMoves.every((rawMove) => {
    const move = record(rawMove);
    if (
      !move ||
      !integerBetween(move.row, 0, 8) ||
      !integerBetween(move.column, 0, 8)
    ) {
      return false;
    }
    const row = move.row as number;
    const column = move.column as number;
    const key = `${row}:${column}`;
    const inForcedBoard =
      forcedRowMissing ||
      (Math.floor(row / 3) === snapshot.forcedBoardRow &&
        Math.floor(column / 3) === snapshot.forcedBoardColumn);
    if (
      legalMoveKeys.has(key) ||
      typedBoard[row][column] !== "" ||
      derivedSubBoards[Math.floor(row / 3)][Math.floor(column / 3)] !== "" ||
      !inForcedBoard
    ) {
      return false;
    }
    legalMoveKeys.add(key);
    return true;
  });
  if (!legalMovesValid) return false;

  const expectedLegalMoves = new Set<string>();
  if (!terminal) {
    for (let row = 0; row < 9; row++) {
      for (let column = 0; column < 9; column++) {
        const subBoardRow = Math.floor(row / 3);
        const subBoardColumn = Math.floor(column / 3);
        if (
          typedBoard[row][column] === "" &&
          derivedSubBoards[subBoardRow][subBoardColumn] === "" &&
          (forcedRowMissing ||
            (subBoardRow === snapshot.forcedBoardRow &&
              subBoardColumn === snapshot.forcedBoardColumn))
        ) {
          expectedLegalMoves.add(`${row}:${column}`);
        }
      }
    }
  }
  return (
    expectedLegalMoves.size === legalMoveKeys.size &&
    [...expectedLegalMoves].every((move) => legalMoveKeys.has(move))
  );
}

function edgeMoveKey(orientation: string, row: number, column: number) {
  return `${orientation}:${row}:${column}`;
}

export function isDotsAndBoxesSnapshot(
  value: unknown,
): value is DotsAndBoxesSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 40) ||
    !Array.isArray(snapshot.horizontalEdges) ||
    snapshot.horizontalEdges.length !== 5 ||
    !Array.isArray(snapshot.verticalEdges) ||
    snapshot.verticalEdges.length !== 4 ||
    !Array.isArray(snapshot.boxes) ||
    snapshot.boxes.length !== 4 ||
    !Array.isArray(snapshot.scores) ||
    snapshot.scores.length !== 2 ||
    !Array.isArray(snapshot.legalMoves) ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId) ||
    !terminalPlayersValid(snapshot)
  ) {
    return false;
  }

  const horizontal = snapshot.horizontalEdges as unknown[][];
  const vertical = snapshot.verticalEdges as unknown[][];
  let drawnEdges = 0;
  if (
    !horizontal.every(
      (row) =>
        Array.isArray(row) &&
        row.length === 4 &&
        row.every((edge) => {
          if (edge === true) drawnEdges++;
          return typeof edge === "boolean";
        }),
    ) ||
    !vertical.every(
      (row) =>
        Array.isArray(row) &&
        row.length === 5 &&
        row.every((edge) => {
          if (edge === true) drawnEdges++;
          return typeof edge === "boolean";
        }),
    ) ||
    drawnEdges !== snapshot.sequence
  ) {
    return false;
  }

  const scores = snapshot.scores;
  if (!scores.every((score) => integerBetween(score, 0, 16))) return false;
  const countedScores = [0, 0];
  const boxes = snapshot.boxes as unknown[][];
  const boxesValid = boxes.every(
    (row, rowIndex) =>
      Array.isArray(row) &&
      row.length === 4 &&
      row.every((owner, columnIndex) => {
        if (owner !== "" && owner !== "R" && owner !== "B") return false;
        const closed = Boolean(
          horizontal[rowIndex][columnIndex] &&
            horizontal[rowIndex + 1][columnIndex] &&
            vertical[rowIndex][columnIndex] &&
            vertical[rowIndex][columnIndex + 1],
        );
        if (closed !== (owner !== "")) return false;
        if (owner === "R") countedScores[0]++;
        if (owner === "B") countedScores[1]++;
        return true;
      }),
  );
  if (
    !boxesValid ||
    scores[0] !== countedScores[0] ||
    scores[1] !== countedScores[1]
  ) {
    return false;
  }

  const expectedLegalMoves = new Set<string>();
  for (let row = 0; row < 5; row++) {
    for (let column = 0; column < 4; column++) {
      if (!horizontal[row][column]) {
        expectedLegalMoves.add(edgeMoveKey("H", row, column));
      }
    }
  }
  for (let row = 0; row < 4; row++) {
    for (let column = 0; column < 5; column++) {
      if (!vertical[row][column]) {
        expectedLegalMoves.add(edgeMoveKey("V", row, column));
      }
    }
  }
  const legalMoveKeys = new Set<string>();
  const legalMovesValid = snapshot.legalMoves.every((rawMove) => {
    const move = record(rawMove);
    if (!move || (move.orientation !== "H" && move.orientation !== "V")) {
      return false;
    }
    const rowMaximum = move.orientation === "H" ? 4 : 3;
    const columnMaximum = move.orientation === "H" ? 3 : 4;
    if (
      !integerBetween(move.row, 0, rowMaximum) ||
      !integerBetween(move.column, 0, columnMaximum)
    ) {
      return false;
    }
    const key = edgeMoveKey(
      move.orientation,
      move.row as number,
      move.column as number,
    );
    if (legalMoveKeys.has(key) || !expectedLegalMoves.has(key)) return false;
    legalMoveKeys.add(key);
    return true;
  });
  if (
    !legalMovesValid ||
    legalMoveKeys.size !== expectedLegalMoves.size ||
    ![...expectedLegalMoves].every((edge) => legalMoveKeys.has(edge))
  ) {
    return false;
  }

  const lastEdge = record(snapshot.lastEdge);
  if (snapshot.sequence === 0 && lastEdge) return false;
  if (snapshot.sequence > 0) {
    if (!lastEdge || (lastEdge.orientation !== "H" && lastEdge.orientation !== "V")) {
      return false;
    }
    const rowMaximum = lastEdge.orientation === "H" ? 4 : 3;
    const columnMaximum = lastEdge.orientation === "H" ? 3 : 4;
    if (
      !integerBetween(lastEdge.row, 0, rowMaximum) ||
      !integerBetween(lastEdge.column, 0, columnMaximum) ||
      (lastEdge.orientation === "H"
        ? horizontal[lastEdge.row as number][lastEdge.column as number] !== true
        : vertical[lastEdge.row as number][lastEdge.column as number] !== true)
    ) {
      return false;
    }
  }

  const terminal = snapshot.sequence === 40;
  return (
    terminal === Boolean(snapshot.winnerId || snapshot.draw) &&
    (!terminal || snapshot.draw === (scores[0] === scores[1]))
  );
}

export function isMancalaSnapshot(value: unknown): value is MancalaSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 10_000) ||
    !Array.isArray(snapshot.pits) ||
    snapshot.pits.length !== 14 ||
    !Array.isArray(snapshot.scores) ||
    snapshot.scores.length !== 2 ||
    !Array.isArray(snapshot.legalPits) ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId) ||
    !terminalPlayersValid(snapshot)
  ) {
    return false;
  }
  const sequence = snapshot.sequence as number;
  const pits = snapshot.pits;
  if (
    !pits.every((pit) => integerBetween(pit, 0, 48)) ||
    pits.reduce((total, pit) => total + pit, 0) !== 48
  ) {
    return false;
  }
  const scores = snapshot.scores;
  if (
    !scores.every((score) => integerBetween(score, 0, 48)) ||
    scores[0] !== pits[6] ||
    scores[1] !== pits[13]
  ) {
    return false;
  }
  const legalPits = new Set<number>();
  if (
    !snapshot.legalPits.every((pit) => {
      if (!integerBetween(pit, 0, 5) || legalPits.has(pit as number)) return false;
      legalPits.add(pit as number);
      return true;
    })
  ) {
    return false;
  }
  const lastPitMissing = snapshot.lastPit === undefined || snapshot.lastPit === null;
  if (
    (sequence === 0 && !lastPitMissing) ||
    (sequence > 0 && !integerBetween(snapshot.lastPit, 0, 13))
  ) {
    return false;
  }

  const firstSideEmpty = pits.slice(0, 6).every((pit) => pit === 0);
  const secondSideEmpty = pits.slice(7, 13).every((pit) => pit === 0);
  const terminal = Boolean(snapshot.winnerId || snapshot.draw);
  if (
    firstSideEmpty !== secondSideEmpty ||
    terminal !== (firstSideEmpty && secondSideEmpty) ||
    (terminal ? legalPits.size !== 0 : legalPits.size === 0) ||
    (terminal && snapshot.draw !== (scores[0] === scores[1]))
  ) {
    return false;
  }
  if (sequence === 0) {
    return (
      pits.every((pit, index) =>
        index === 6 || index === 13 ? pit === 0 : pit === 4,
      ) &&
      legalPits.size === 6
    );
  }
  return true;
}

const HEX_NEIGHBORS = [
  [-1, 0],
  [-1, 1],
  [0, -1],
  [0, 1],
  [1, -1],
  [1, 0],
] as const;

function hasHexConnection(board: string[][], marker: "R" | "B") {
  const pending: Array<[number, number]> = [];
  const visited = new Set<string>();
  if (marker === "R") {
    for (let column = 0; column < 9; column++) {
      if (board[0][column] === marker) pending.push([0, column]);
    }
  } else {
    for (let row = 0; row < 9; row++) {
      if (board[row][0] === marker) pending.push([row, 0]);
    }
  }
  while (pending.length > 0) {
    const [row, column] = pending.shift()!;
    const key = `${row}:${column}`;
    if (visited.has(key)) continue;
    visited.add(key);
    if ((marker === "R" && row === 8) || (marker === "B" && column === 8)) {
      return true;
    }
    for (const [rowOffset, columnOffset] of HEX_NEIGHBORS) {
      const nextRow = row + rowOffset;
      const nextColumn = column + columnOffset;
      if (
        nextRow >= 0 &&
        nextRow < 9 &&
        nextColumn >= 0 &&
        nextColumn < 9 &&
        board[nextRow][nextColumn] === marker
      ) {
        pending.push([nextRow, nextColumn]);
      }
    }
  }
  return false;
}

export function isHexSnapshot(value: unknown): value is HexSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 81) ||
    !Array.isArray(snapshot.board) ||
    snapshot.board.length !== 9 ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId) ||
    !lastMoveValid(snapshot, 9, 9)
  ) {
    return false;
  }
  let red = 0;
  let blue = 0;
  const board = snapshot.board as unknown[][];
  const boardValid = board.every(
    (row) =>
      Array.isArray(row) &&
      row.length === 9 &&
      row.every((cell) => {
        if (cell === "R") red++;
        if (cell === "B") blue++;
        return cell === "" || cell === "R" || cell === "B";
      }),
  );
  if (
    !boardValid ||
    red + blue !== snapshot.sequence ||
    red < blue ||
    red > blue + 1
  ) {
    return false;
  }
  const typedBoard = board as string[][];
  const redWins = hasHexConnection(typedBoard, "R");
  const blueWins = hasHexConnection(typedBoard, "B");
  const terminal = typeof snapshot.winnerId === "string";
  if (
    redWins && blueWins ||
    terminal !== (redWins || blueWins) ||
    terminal === (typeof snapshot.currentTurnPlayerId === "string") ||
    (!terminal && snapshot.sequence === 81)
  ) {
    return false;
  }
  if (snapshot.sequence > 0) {
    const lastMarker = typedBoard[snapshot.lastMoveRow as number][
      snapshot.lastMoveColumn as number
    ];
    const expectedMarker = snapshot.sequence % 2 === 1 ? "R" : "B";
    if (lastMarker !== expectedMarker || (terminal && !hasHexConnection(typedBoard, expectedMarker))) {
      return false;
    }
  }
  return true;
}

export function isSosSnapshot(value: unknown): value is SosSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 36) ||
    !Array.isArray(snapshot.board) ||
    snapshot.board.length !== 6 ||
    !Array.isArray(snapshot.players) ||
    snapshot.players.length !== 2 ||
    typeof snapshot.draw !== "boolean" ||
    !optionalNullableString(snapshot.currentTurnPlayerId) ||
    !optionalNullableString(snapshot.winnerId) ||
    !integerBetween(snapshot.lastMovePoints, 0, 12) ||
    !lastMoveValid(snapshot, 6, 6)
  ) {
    return false;
  }
  let occupied = 0;
  const boardValid = snapshot.board.every(
    (row) =>
      Array.isArray(row) &&
      row.length === 6 &&
      row.every((cell) => {
        if (cell === "S" || cell === "O") occupied++;
        return cell === "" || cell === "S" || cell === "O";
      }),
  );
  if (!boardValid || occupied !== snapshot.sequence) return false;

  const userIds = new Set<string>();
  const playersValid = snapshot.players.every((rawPlayer) => {
    const player = record(rawPlayer);
    if (
      !player ||
      typeof player.userId !== "string" ||
      userIds.has(player.userId) ||
      !integerBetween(player.score, 0, 80)
    ) {
      return false;
    }
    userIds.add(player.userId);
    return true;
  });
  if (!playersValid) return false;

  const currentTurn =
    typeof snapshot.currentTurnPlayerId === "string"
      ? snapshot.currentTurnPlayerId
      : null;
  const winner = typeof snapshot.winnerId === "string" ? snapshot.winnerId : null;
  const terminal = snapshot.sequence === 36;
  if (
    (currentTurn !== null && !userIds.has(currentTurn)) ||
    (winner !== null && !userIds.has(winner)) ||
    terminal !== Boolean(winner || snapshot.draw) ||
    terminal === Boolean(currentTurn) ||
    (snapshot.sequence === 0 && snapshot.lastMovePoints !== 0)
  ) {
    return false;
  }
  if (terminal) {
    const firstScore = (snapshot.players[0] as { score: number }).score;
    const secondScore = (snapshot.players[1] as { score: number }).score;
    if (snapshot.draw !== (firstScore === secondScore)) return false;
    if (winner && firstScore === secondScore) return false;
  }
  return true;
}

export function isRpsSnapshot(value: unknown): value is RpsSnapshot {
  const snapshot = record(value);
  if (
    !snapshot ||
    !integerBetween(snapshot.sequence, 0, 10) ||
    !integerBetween(snapshot.round, 1, 5) ||
    snapshot.targetWins !== 3 ||
    !Array.isArray(snapshot.players) ||
    snapshot.players.length !== 2 ||
    typeof snapshot.draw !== "boolean" ||
    snapshot.draw ||
    !optionalNullableString(snapshot.winnerId)
  ) {
    return false;
  }
  const userIds = new Set<string>();
  let submitted = 0;
  let totalWins = 0;
  const playersValid = snapshot.players.every((rawPlayer) => {
    const player = record(rawPlayer);
    if (!player || typeof player.userId !== "string" || userIds.has(player.userId)
      || !integerBetween(player.wins, 0, 3) || typeof player.submitted !== "boolean") {
      return false;
    }
    userIds.add(player.userId);
    if (player.submitted) submitted++;
    totalWins += player.wins as number;
    return true;
  });
  if (!playersValid) return false;
  const winnerId = typeof snapshot.winnerId === "string" ? snapshot.winnerId : null;
  const completedRounds = winnerId ? snapshot.round as number : (snapshot.round as number) - 1;
  const winnerPlayer = winnerId
    ? snapshot.players.find((player) => player.userId === winnerId)
    : null;
  if (
    (winnerId && (!winnerPlayer || winnerPlayer.wins !== 3 || submitted !== 0)) ||
    (!winnerId && snapshot.players.some((player) => player.wins === 3)) ||
    totalWins > completedRounds ||
    snapshot.sequence !== completedRounds * 2 + submitted
  ) {
    return false;
  }
  const lastRound = record(snapshot.lastRound);
  if (completedRounds === 0) return !lastRound;
  if (
    !lastRound ||
    lastRound.round !== completedRounds ||
    !["ROCK", "PAPER", "SCISSORS"].includes(String(lastRound.firstChoice)) ||
    !["ROCK", "PAPER", "SCISSORS"].includes(String(lastRound.secondChoice)) ||
    typeof lastRound.draw !== "boolean" ||
    !optionalNullableString(lastRound.winnerId)
  ) {
    return false;
  }
  const lastWinner = typeof lastRound.winnerId === "string" ? lastRound.winnerId : null;
  return (lastRound.draw ? !lastWinner : Boolean(lastWinner && userIds.has(lastWinner)));
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
  if (gameSlug === "connect-four") return isConnectFourSnapshot(value);
  if (gameSlug === "reversi") return isReversiSnapshot(value);
  if (gameSlug === "rock-paper-scissors") return isRpsSnapshot(value);
  if (gameSlug === "ultimate-tic-tac-toe") {
    return isUltimateTicTacToeSnapshot(value);
  }
  if (gameSlug === "dots-and-boxes") return isDotsAndBoxesSnapshot(value);
  if (gameSlug === "mancala") return isMancalaSnapshot(value);
  if (gameSlug === "hex") return isHexSnapshot(value);
  if (gameSlug === "sos") return isSosSnapshot(value);
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
