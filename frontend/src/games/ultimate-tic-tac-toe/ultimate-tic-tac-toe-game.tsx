"use client";

import { LayoutGrid, Radio } from "lucide-react";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isUltimateTicTacToeSnapshot } from "@/features/multiplayer/realtime/validation";
import type { UltimateTicTacToeSnapshot } from "@/features/multiplayer/realtime/types";
import { playFeedback } from "@/features/settings/player-feedback";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function UltimateTicTacToeGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const controller = useRealtimeGame(
    roomId,
    "ultimate-tic-tac-toe",
    spectator ? "spectator" : "player",
  );
  const snapshot: UltimateTicTacToeSnapshot | null = isUltimateTicTacToeSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const playerIndex = controller.state.room?.players.findIndex(
    (player) => player.id === userId,
  ) ?? -1;
  const ownMark = playerIndex === 0 ? "X" : playerIndex === 1 ? "O" : "";
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;
  const legalMoves = new Set(
    snapshot?.legalMoves.map((move) => `${move.row}:${move.column}`) ?? [],
  );
  const forcedBoardRow = typeof snapshot?.forcedBoardRow === "number"
    ? snapshot.forcedBoardRow
    : null;
  const forcedBoardColumn = typeof snapshot?.forcedBoardColumn === "number"
    ? snapshot.forcedBoardColumn
    : null;

  function place(row: number, column: number) {
    playFeedback("move");
    controller.sendInput({ action: "PLACE_MARK", row, column });
  }

  return (
    <RealtimeStage controller={controller} title="Ultimate Tic-Tac-Toe / 9×9">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(360px,680px)_280px] lg:justify-center">
          <div className="min-w-0">
            <div className="mb-3 flex flex-wrap items-center gap-2 font-telemetry text-[8px] uppercase text-[var(--muted)]">
              <span className="border border-[var(--line)] bg-[var(--surface)] px-3 py-2">
                {t("Your mark")}: <strong className="text-[var(--foreground)]">{ownMark || "—"}</strong>
              </span>
              <span className="border border-[var(--line)] bg-[var(--surface)] px-3 py-2">
                {forcedBoardRow === null || forcedBoardColumn === null
                  ? t("Any open sub-board")
                  : t("Forced sub-board {row}-{column}", {
                      row: forcedBoardRow + 1,
                      column: forcedBoardColumn + 1,
                    })}
              </span>
            </div>

            <div className="overflow-x-auto overscroll-x-contain pb-2">
              <div
                className="mx-auto grid aspect-square w-full min-w-[414px] max-w-[680px] grid-cols-9 gap-px border-2 border-[var(--line-strong)] bg-[var(--line-strong)]"
                role="grid"
                aria-label={t("Authoritative Ultimate Tic-Tac-Toe board")}
              >
                {snapshot.board.flatMap((row, rowIndex) =>
                  row.map((mark, columnIndex) => {
                    const legal = legalMoves.has(`${rowIndex}:${columnIndex}`);
                    const subBoardRow = Math.floor(rowIndex / 3);
                    const subBoardColumn = Math.floor(columnIndex / 3);
                    const forced = forcedBoardRow === subBoardRow &&
                      forcedBoardColumn === subBoardColumn;
                    const latest = snapshot.lastMoveRow === rowIndex &&
                      snapshot.lastMoveColumn === columnIndex;
                    const enabled = !spectator && yourTurn && legal && !pending &&
                      !snapshot.winnerId && !snapshot.draw;

                    return (
                      <button
                        key={`${rowIndex}-${columnIndex}`}
                        type="button"
                        role="gridcell"
                        disabled={!enabled}
                        onClick={() => place(rowIndex, columnIndex)}
                        aria-label={mark
                          ? t("Ultimate row {row}, column {column}: {mark}", {
                              row: rowIndex + 1,
                              column: columnIndex + 1,
                              mark,
                            })
                          : legal
                            ? t("Legal Ultimate move at row {row}, column {column}", {
                                row: rowIndex + 1,
                                column: columnIndex + 1,
                              })
                            : t("Empty Ultimate cell at row {row}, column {column}", {
                                row: rowIndex + 1,
                                column: columnIndex + 1,
                              })}
                        className={
                          "relative grid aspect-square min-h-11 min-w-11 touch-manipulation place-items-center transition-colors duration-200 disabled:cursor-not-allowed " +
                          (forced ? "bg-[var(--surface-strong)]" : "bg-[var(--surface)]") +
                          (enabled
                            ? " cursor-pointer hover:bg-[var(--background)] active:bg-[var(--surface-strong)]"
                            : "") +
                          (columnIndex === 3 || columnIndex === 6
                            ? " border-l-2 border-l-[var(--foreground)]"
                            : "") +
                          (rowIndex === 3 || rowIndex === 6
                            ? " border-t-2 border-t-[var(--foreground)]"
                            : "")
                        }
                      >
                        {mark ? (
                          <span
                            className={
                              "text-xl font-black sm:text-2xl " +
                              (mark === "X" ? "text-[var(--accent)]" : "text-[var(--foreground)]") +
                              (latest ? " motion-safe:scale-90 motion-safe:transition-transform" : "")
                            }
                            aria-hidden="true"
                          >
                            {mark}
                          </span>
                        ) : legal ? (
                          <span
                            className="h-2 w-2 rounded-full bg-[var(--accent)] opacity-70"
                            aria-hidden="true"
                          />
                        ) : null}
                        {latest ? (
                          <span className="pointer-events-none absolute inset-1 border border-[var(--online)]" aria-hidden="true" />
                        ) : null}
                      </button>
                    );
                  }),
                )}
              </div>
            </div>
          </div>

          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ NESTED GRID TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(snapshot.winnerId
                ? snapshot.winnerId === userId ? "You won the ultimate grid" : "Rival won the ultimate grid"
                : snapshot.draw ? "Ultimate grid draw"
                : yourTurn ? "Choose a legal cell" : "Opponent turn")}
            </p>

            <div className="mt-7 grid grid-cols-3 gap-px border border-[var(--line)] bg-[var(--line)]" aria-label={t("Sub-board results")}>
              {snapshot.subBoards.flatMap((row, rowIndex) =>
                row.map((result, columnIndex) => (
                  <span
                    key={`${rowIndex}-${columnIndex}`}
                    className={
                      "grid aspect-square place-items-center bg-[var(--background)] text-lg font-black " +
                      (result === "X" ? "text-[var(--accent)]" : "text-[var(--foreground)]")
                    }
                    aria-label={t("Sub-board {row}-{column}: {result}", {
                      row: rowIndex + 1,
                      column: columnIndex + 1,
                      result: result || t("open"),
                    })}
                  >
                    {result || "·"}
                  </span>
                )),
              )}
            </div>

            <dl className="mt-px grid gap-px border border-[var(--line)] bg-[var(--line)] font-telemetry text-[8px]">
              <TelemetryRow label={t("Your mark")} value={ownMark || "—"} />
              <TelemetryRow label={t("Legal moves")} value={String(snapshot.legalMoves.length)} />
              <TelemetryRow label={t("Server sequence")} value={String(snapshot.sequence)} />
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <Radio size={15} className="mt-0.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
              {t("Your move sends the rival to its matching sub-board. The server validates both grids.")}
            </p>
            <LayoutGrid size={20} className="mt-5 text-[var(--muted)]" aria-hidden="true" />
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}

function TelemetryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between bg-[var(--background)] p-3">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
