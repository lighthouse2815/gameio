"use client";

import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import {
  canPlacePiece,
  isTurnBasedSnapshot,
  markerForPlayer,
} from "@/games/core/turn-based-helpers";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function CaroGame({ roomId, spectator = false }: { roomId: string; spectator?: boolean }) {
  const { t } = useI18n();
  const controller = useRealtimeGame(roomId, "caro", spectator ? "spectator" : "player");
  const snapshot = isTurnBasedSnapshot(controller.state.snapshot, 15)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const ownMarker = markerForPlayer(controller.state.room, userId);
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;

  return (
    <RealtimeStage controller={controller} title="Caro / 15×15">
      {snapshot ? (
        <div className="grid gap-5 p-3 sm:p-5 xl:grid-cols-[minmax(0,1fr)_250px]">
          <div className="max-h-[75vh] overflow-auto border border-[var(--line-strong)] bg-[var(--line)]">
            <div
              className="grid min-w-[600px] gap-px bg-[var(--line)]"
              style={{
                gridTemplateColumns: "repeat(" + snapshot.board.length + ", minmax(38px, 1fr))",
              }}
              role="grid"
              aria-label={t("Authoritative 15 by 15 Caro board")}
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
                      className={
                        "aspect-square bg-[var(--surface)] font-mono text-base font-black transition-colors enabled:hover:bg-[var(--surface-strong)] disabled:cursor-not-allowed " +
                        (marker === "X"
                          ? "text-[var(--accent)]"
                          : "text-[var(--foreground)]")
                      }
                      disabled={!enabled}
                      aria-label={
                        marker
                          ? t("Row {row}, column {column}: {marker}", { row: rowIndex + 1, column: columnIndex + 1, marker })
                          : t("Place at row {row}, column {column}", { row: rowIndex + 1, column: columnIndex + 1 })
                      }
                      onClick={() =>
                        controller.sendInput({
                          action: "PLACE_PIECE",
                          row: rowIndex,
                          column: columnIndex,
                        })
                      }
                    >
                      {marker || (
                        <span className="text-[7px] font-normal text-[var(--line-strong)]">
                          {rowIndex + 1}.{columnIndex + 1}
                        </span>
                      )}
                    </button>
                  );
                }),
              )}
            </div>
          </div>
          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ TURN TELEMETRY ]")}
            </p>
            <p className="mt-5 text-3xl font-black uppercase tracking-[-0.05em]">
              {t(snapshot.winnerId
                ? snapshot.winnerId === userId
                  ? "Five secured"
                  : "Line breached"
                : snapshot.draw
                  ? "Grid draw"
                  : yourTurn
                    ? "Place piece"
                    : "Opponent turn")}
            </p>
            <dl className="font-telemetry mt-7 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Your marker")}</dt>
                <dd className="text-[var(--accent)]">{ownMarker ?? "—"}</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Sequence")}</dt>
                <dd>{snapshot.sequence}</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Board")}</dt>
                <dd>{snapshot.board.length}×{snapshot.board.length}</dd>
              </div>
            </dl>
            <p className="mt-6 text-xs leading-5 text-[var(--muted)]">
              {t("Scroll the full tactical grid on smaller screens. Five contiguous markers in any direction are evaluated by the server.")}
            </p>
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}
