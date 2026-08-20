"use client";

import { CircleDotDashed, ScanLine } from "lucide-react";
import { playFeedback } from "@/features/settings/player-feedback";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isReversiSnapshot } from "@/features/multiplayer/realtime/validation";
import type { ReversiMarker } from "@/features/multiplayer/realtime/types";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function ReversiGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const controller = useRealtimeGame(
    roomId,
    "reversi",
    spectator ? "spectator" : "player",
  );
  const snapshot = isReversiSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const playerIndex = controller.state.room?.players.findIndex(
    (player) => player.id === userId,
  ) ?? -1;
  const ownDisc: ReversiMarker = playerIndex === 0 ? "B" : playerIndex === 1 ? "W" : "";
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;
  const legalMoves = new Set(
    snapshot?.legalMoves.map((move) => `${move.row}:${move.column}`) ?? [],
  );

  function place(row: number, column: number) {
    playFeedback("move");
    controller.sendInput({ action: "PLACE_DISC", row, column });
  }

  return (
    <RealtimeStage controller={controller} title="Reversi / 8×8">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(300px,640px)_280px] lg:justify-center">
          <div className="overflow-x-auto pb-1">
            <div
              className="mx-auto grid aspect-square w-full min-w-[352px] max-w-[640px] grid-cols-8 gap-px border border-[var(--line-strong)] bg-[var(--line-strong)]"
              role="grid"
              aria-label={t("Authoritative Reversi board")}
            >
            {snapshot.board.flatMap((row, rowIndex) =>
              row.map((disc, columnIndex) => {
                const legal = legalMoves.has(`${rowIndex}:${columnIndex}`);
                const enabled = !spectator && yourTurn && legal && !pending &&
                  !snapshot.winnerId && !snapshot.draw;
                const latest = snapshot.lastMoveRow === rowIndex &&
                  snapshot.lastMoveColumn === columnIndex;
                return (
                  <button
                    type="button"
                    role="gridcell"
                    key={`${rowIndex}-${columnIndex}`}
                    className="relative grid min-h-11 touch-manipulation place-items-center bg-[var(--surface)] transition-colors enabled:cursor-pointer enabled:hover:bg-[var(--surface-strong)] enabled:active:bg-[var(--background)] disabled:cursor-not-allowed"
                    disabled={!enabled}
                    aria-label={disc
                      ? t("Reversi row {row}, column {column}: {disc}", {
                          row: rowIndex + 1,
                          column: columnIndex + 1,
                          disc: t(disc === "B" ? "Black disc" : "White disc"),
                        })
                      : legal
                        ? t("Legal Reversi move at row {row}, column {column}", {
                            row: rowIndex + 1,
                            column: columnIndex + 1,
                          })
                        : t("Empty Reversi cell at row {row}, column {column}", {
                            row: rowIndex + 1,
                            column: columnIndex + 1,
                          })}
                    onClick={() => place(rowIndex, columnIndex)}
                  >
                    {disc ? (
                      <span
                        className={
                          "h-[70%] w-[70%] rounded-full border transition-transform duration-200 " +
                          (disc === "B"
                            ? "border-[var(--foreground)] bg-[var(--foreground)]"
                            : "border-[var(--muted)] bg-[var(--background)]") +
                          (latest ? " ring-2 ring-[var(--accent)] ring-offset-1 ring-offset-[var(--surface)] motion-safe:scale-90" : "")
                        }
                        aria-hidden="true"
                      />
                    ) : legal ? (
                      <span className="h-[24%] w-[24%] rounded-full bg-[var(--accent)] opacity-70" aria-hidden="true" />
                    ) : null}
                  </button>
                );
              }),
            )}
            </div>
          </div>

          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ FLIP TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(snapshot.winnerId
                ? snapshot.winnerId === userId ? "You control the board" : "Rival controls the board"
                : snapshot.draw ? "Territory draw"
                : yourTurn ? "Your flip" : "Opponent turn")}
            </p>
            <div className="mt-7 grid grid-cols-2 gap-px border border-[var(--line)] bg-[var(--line)]">
              <div className="bg-[var(--background)] p-4">
                <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("Black")}</p>
                <p className="mt-2 text-3xl font-black">{snapshot.blackCount}</p>
              </div>
              <div className="bg-[var(--background)] p-4 text-right">
                <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("White")}</p>
                <p className="mt-2 text-3xl font-black">{snapshot.whiteCount}</p>
              </div>
            </div>
            <dl className="font-telemetry mt-px grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Your disc")}</dt>
                <dd className="flex items-center gap-2">
                  <CircleDotDashed size={12} aria-hidden="true" />
                  {ownDisc || "—"}
                </dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Legal moves")}</dt>
                <dd>{snapshot.legalMoves.length}</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Server sequence")}</dt>
                <dd>{snapshot.sequence}</dd>
              </div>
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <ScanLine size={15} className="mt-0.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
              {t("Only server-marked legal cells are active. A blocked player is passed automatically.")}
            </p>
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}
