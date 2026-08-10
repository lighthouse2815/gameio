"use client";

import { Circle, X } from "lucide-react";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import {
  canPlacePiece,
  isTurnBasedSnapshot,
  markerForPlayer,
} from "@/games/core/turn-based-helpers";

export default function TicTacToeGame({ roomId }: { roomId: string }) {
  const controller = useRealtimeGame(roomId, "tic-tac-toe");
  const snapshot = isTurnBasedSnapshot(controller.state.snapshot, 3)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const ownMarker = markerForPlayer(controller.state.room, userId);
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;

  return (
    <RealtimeStage controller={controller} title="Tic Tac Toe / 3×3">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(300px,560px)_260px] lg:justify-center">
          <div
            className="grid aspect-square grid-cols-3 gap-px border border-[var(--line-strong)] bg-[var(--line-strong)]"
            role="grid"
            aria-label="Authoritative Tic Tac Toe board"
          >
            {snapshot.board.flatMap((row, rowIndex) =>
              row.map((marker, columnIndex) => {
                const enabled = canPlacePiece(
                  snapshot,
                  userId,
                  rowIndex,
                  columnIndex,
                  pending,
                );
                return (
                  <button
                    type="button"
                    role="gridcell"
                    key={rowIndex + "-" + columnIndex}
                    className="grid place-items-center bg-[var(--surface)] text-[var(--foreground)] transition-colors enabled:hover:bg-[var(--surface-strong)] disabled:cursor-not-allowed"
                    disabled={!enabled}
                    aria-label={
                      marker
                        ? "Row " +
                          (rowIndex + 1) +
                          ", column " +
                          (columnIndex + 1) +
                          ": " +
                          marker
                        : "Place at row " +
                          (rowIndex + 1) +
                          ", column " +
                          (columnIndex + 1)
                    }
                    onClick={() =>
                      controller.sendInput({
                        action: "PLACE_PIECE",
                        row: rowIndex,
                        column: columnIndex,
                      })
                    }
                  >
                    {marker === "X" ? (
                      <X
                        className="h-2/3 w-2/3 text-[var(--accent)]"
                        strokeWidth={1.4}
                        aria-hidden="true"
                      />
                    ) : marker === "O" ? (
                      <Circle
                        className="h-2/3 w-2/3"
                        strokeWidth={1.4}
                        aria-hidden="true"
                      />
                    ) : (
                      <span className="font-telemetry text-[8px] text-[var(--line-strong)]">
                        {rowIndex + 1}.{columnIndex + 1}
                      </span>
                    )}
                  </button>
                );
              }),
            )}
          </div>
          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              [ TURN TELEMETRY ]
            </p>
            <p className="mt-6 text-4xl font-black uppercase tracking-[-0.05em]">
              {snapshot.winnerId
                ? snapshot.winnerId === userId
                  ? "You win"
                  : "Opponent wins"
                : snapshot.draw
                  ? "Draw"
                  : yourTurn
                    ? "Your turn"
                    : "Stand by"}
            </p>
            <dl className="font-telemetry mt-8 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>Your marker</dt>
                <dd className="text-[var(--accent)]">{ownMarker ?? "—"}</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>Server sequence</dt>
                <dd>{snapshot.sequence}</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>Pending input</dt>
                <dd>{pending ? "YES" : "NO"}</dd>
              </div>
            </dl>
            <p className="mt-6 text-xs leading-5 text-[var(--muted)]">
              A cell becomes interactive only when the server names this
              player as the current turn holder.
            </p>
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}
