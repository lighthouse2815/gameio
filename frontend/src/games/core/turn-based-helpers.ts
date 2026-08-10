import type {
  BoardMarker,
  TicTacToeSnapshot,
} from "@/features/multiplayer/realtime/types";
import type { GameRoom } from "@/features/multiplayer/types";

export function isTurnBasedSnapshot(
  snapshot: unknown,
  expectedSize: 3 | 15,
): snapshot is TicTacToeSnapshot {
  if (
    typeof snapshot !== "object" ||
    snapshot === null ||
    !("board" in snapshot) ||
    !Array.isArray(snapshot.board)
  ) {
    return false;
  }
  return (
    snapshot.board.length === expectedSize &&
    snapshot.board.every(
    (row) =>
      Array.isArray(row) &&
      row.length === expectedSize &&
      row.every(
        (cell) => cell === "" || cell === "X" || cell === "O",
      ),
    )
  );
}

export function markerForPlayer(
  room: GameRoom | null,
  userId: string | undefined,
): BoardMarker | null {
  if (!room || !userId) return null;
  const index = room.players.findIndex((player) => player.id === userId);
  if (index === 0) return "X";
  if (index === 1) return "O";
  return null;
}

export function canPlacePiece(
  snapshot: TicTacToeSnapshot,
  userId: string | undefined,
  row: number,
  column: number,
  hasPendingRequest: boolean,
) {
  return Boolean(
    userId &&
      !hasPendingRequest &&
      !snapshot.winnerId &&
      !snapshot.draw &&
      snapshot.currentTurnPlayerId === userId &&
      row >= 0 &&
      column >= 0 &&
      row < snapshot.board.length &&
      column < (snapshot.board[row]?.length ?? 0) &&
      snapshot.board[row][column] === "",
  );
}
