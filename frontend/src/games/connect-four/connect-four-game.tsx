"use client";

import { CircleDot, Radio } from "lucide-react";
import { playFeedback } from "@/features/settings/player-feedback";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isConnectFourSnapshot } from "@/features/multiplayer/realtime/validation";
import type { ConnectFourMarker } from "@/features/multiplayer/realtime/types";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function ConnectFourGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const controller = useRealtimeGame(
    roomId,
    "connect-four",
    spectator ? "spectator" : "player",
  );
  const snapshot = isConnectFourSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const playerIndex = controller.state.room?.players.findIndex(
    (player) => player.id === userId,
  ) ?? -1;
  const ownDisc: ConnectFourMarker = playerIndex === 0 ? "R" : playerIndex === 1 ? "Y" : "";
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;

  function drop(column: number) {
    playFeedback("move");
    controller.sendInput({ action: "DROP_DISC", column });
  }

  return (
    <RealtimeStage controller={controller} title="Connect Four / 7×6">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(300px,640px)_280px] lg:justify-center">
          <div className="mx-auto w-full max-w-[640px]">
            <div className="mb-2 grid grid-cols-7 gap-1" aria-label={t("Connect Four column controls")}>
              {Array.from({ length: 7 }, (_, column) => {
                const enabled = !spectator && yourTurn && !pending &&
                  !snapshot.winnerId && !snapshot.draw && snapshot.board[0][column] === "";
                return (
                  <button
                    key={column}
                    type="button"
                    className="font-telemetry min-h-11 touch-manipulation border border-[var(--line)] bg-[var(--surface)] text-[9px] transition-colors enabled:cursor-pointer enabled:hover:border-[var(--accent)] enabled:hover:text-[var(--accent)] enabled:active:bg-[var(--surface-strong)] disabled:cursor-not-allowed disabled:text-[var(--muted)]"
                    disabled={!enabled}
                    aria-label={t("Drop disc in column {column}", { column: column + 1 })}
                    onClick={() => drop(column)}
                  >
                    {String(column + 1).padStart(2, "0")}
                  </button>
                );
              })}
            </div>
            <div
              className="grid grid-cols-7 gap-1 border border-[var(--line-strong)] bg-[var(--line-strong)] p-1"
              role="grid"
              aria-label={t("Authoritative Connect Four board")}
            >
              {snapshot.board.flatMap((row, rowIndex) =>
                row.map((disc, columnIndex) => {
                  const latest = snapshot.lastMoveRow === rowIndex &&
                    snapshot.lastMoveColumn === columnIndex;
                  return (
                    <div
                      key={`${rowIndex}-${columnIndex}`}
                      role="gridcell"
                      aria-label={disc
                        ? t("Row {row}, column {column}: {disc}", {
                            row: rowIndex + 1,
                            column: columnIndex + 1,
                            disc: t(disc === "R" ? "Red disc" : "Yellow disc"),
                          })
                        : t("Row {row}, column {column}: empty", {
                            row: rowIndex + 1,
                            column: columnIndex + 1,
                          })}
                      className="grid aspect-square place-items-center bg-[var(--surface)]"
                    >
                      <span
                        className={
                          "h-[72%] w-[72%] rounded-full border transition-transform duration-200 " +
                          (disc === "R"
                            ? "border-[var(--accent)] bg-[var(--accent)]"
                            : disc === "Y"
                              ? "border-[var(--foreground)] bg-[var(--foreground)]"
                              : "border-[var(--line)] bg-[var(--background)]") +
                          (latest ? " ring-2 ring-[var(--online)] ring-offset-2 ring-offset-[var(--surface)] motion-safe:scale-90" : "")
                        }
                        aria-hidden="true"
                      />
                    </div>
                  );
                }),
              )}
            </div>
          </div>

          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ DROP TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(snapshot.winnerId
                ? snapshot.winnerId === userId ? "You connected four" : "Rival connected four"
                : snapshot.draw ? "Grid draw"
                : yourTurn ? "Choose a column" : "Opponent turn")}
            </p>
            <dl className="font-telemetry mt-7 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
              <div className="flex items-center justify-between bg-[var(--background)] p-3">
                <dt>{t("Your disc")}</dt>
                <dd className="flex items-center gap-2">
                  <CircleDot size={12} className={ownDisc === "R" ? "text-[var(--accent)]" : "text-[var(--foreground)]"} aria-hidden="true" />
                  {ownDisc || "—"}
                </dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Server sequence")}</dt>
                <dd>{snapshot.sequence}</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Open columns")}</dt>
                <dd>{snapshot.board[0].filter((cell) => cell === "").length}/7</dd>
              </div>
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <Radio size={15} className="mt-0.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
              {t("Pick a column. The server applies gravity and checks every four-disc line.")}
            </p>
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}
