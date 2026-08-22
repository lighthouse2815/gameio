"use client";

import { ScanLine, Shapes } from "lucide-react";
import { useState } from "react";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isSosSnapshot } from "@/features/multiplayer/realtime/validation";
import type { SosSnapshot } from "@/features/multiplayer/realtime/types";
import { playFeedback } from "@/features/settings/player-feedback";
import { useI18n } from "@/lib/i18n/use-i18n";

type SosLetter = "S" | "O";

export default function SosGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const [letter, setLetter] = useState<SosLetter>("S");
  const controller = useRealtimeGame(
    roomId,
    "sos",
    spectator ? "spectator" : "player",
  );
  const snapshot: SosSnapshot | null = isSosSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;
  const ownScore =
    snapshot?.players.find((player) => player.userId === userId)?.score ?? 0;
  const rivalScore =
    snapshot?.players.find((player) => player.userId !== userId)?.score ?? 0;

  function placeLetter(row: number, column: number) {
    playFeedback("move");
    controller.sendInput({
      action: letter === "S" ? "PLACE_S" : "PLACE_O",
      row,
      column,
    });
  }

  return (
    <RealtimeStage controller={controller} title="SOS / 6×6">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(360px,620px)_300px] lg:justify-center">
          <div className="min-w-0">
            <div
              className="mb-4 grid grid-cols-2 gap-px border border-[var(--line)] bg-[var(--line)]"
              role="group"
              aria-label={t("Choose S or O")}
            >
              {(["S", "O"] as const).map((choice) => (
                <button
                  key={choice}
                  type="button"
                  aria-pressed={letter === choice}
                  onClick={() => setLetter(choice)}
                  disabled={spectator || Boolean(snapshot.winnerId) || snapshot.draw}
                  className={
                    "min-h-11 touch-manipulation px-4 py-3 text-xl font-black transition-colors duration-200 disabled:cursor-not-allowed " +
                    (letter === choice
                      ? "bg-[var(--accent)] text-[var(--background)]"
                      : "bg-[var(--surface)] text-[var(--foreground)] hover:bg-[var(--surface-strong)]")
                  }
                >
                  {choice}
                </button>
              ))}
            </div>

            <div className="overflow-x-auto overscroll-x-contain pb-2">
              <div
                className="mx-auto grid aspect-square w-full min-w-[360px] max-w-[620px] grid-cols-6 gap-px border-2 border-[var(--line-strong)] bg-[var(--line-strong)]"
                role="grid"
                aria-label={t("Authoritative SOS board")}
              >
                {snapshot.board.flatMap((row, rowIndex) =>
                  row.map((cell, columnIndex) => {
                    const latest =
                      snapshot.lastMoveRow === rowIndex &&
                      snapshot.lastMoveColumn === columnIndex;
                    const enabled =
                      !spectator &&
                      yourTurn &&
                      cell === "" &&
                      !pending &&
                      !snapshot.winnerId &&
                      !snapshot.draw;
                    return (
                      <button
                        key={`${rowIndex}-${columnIndex}`}
                        type="button"
                        role="gridcell"
                        disabled={!enabled}
                        onClick={() => placeLetter(rowIndex, columnIndex)}
                        aria-label={
                          cell
                            ? t("SOS row {row}, column {column}: {letter}", {
                                row: rowIndex + 1,
                                column: columnIndex + 1,
                                letter: cell,
                              })
                            : t("Place {letter} at SOS row {row}, column {column}", {
                                letter,
                                row: rowIndex + 1,
                                column: columnIndex + 1,
                              })
                        }
                        className={
                          "relative grid aspect-square min-h-11 min-w-11 touch-manipulation place-items-center bg-[var(--surface)] text-2xl font-black transition-[background-color,transform] duration-200 disabled:cursor-not-allowed sm:text-4xl " +
                          (enabled
                            ? "cursor-pointer hover:bg-[var(--surface-strong)] active:scale-95"
                            : "") +
                          (cell === "S"
                            ? " text-[var(--accent)]"
                            : " text-[var(--foreground)]")
                        }
                      >
                        {cell || "·"}
                        {latest ? (
                          <span
                            className="pointer-events-none absolute inset-1 border border-[var(--online)]"
                            aria-hidden="true"
                          />
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
              {t("[ PATTERN TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(
                snapshot.winnerId
                  ? snapshot.winnerId === userId
                    ? "You scored the most SOS lines"
                    : "Rival scored the most SOS lines"
                  : snapshot.draw
                    ? "SOS draw"
                    : yourTurn
                      ? "Complete an SOS"
                      : "Opponent turn",
              )}
            </p>

            <div className="mt-7 grid grid-cols-2 gap-px border border-[var(--line)] bg-[var(--line)]">
              <ScorePanel label={t("You")} score={ownScore} accent />
              <ScorePanel label={t("Opponent")} score={rivalScore} alignRight />
            </div>

            <dl className="mt-px grid gap-px border border-[var(--line)] bg-[var(--line)] font-telemetry text-[8px]">
              <TelemetryRow label={t("Selected letter")} value={letter} />
              <TelemetryRow
                label={t("Last move points")}
                value={String(snapshot.lastMovePoints)}
              />
              <TelemetryRow
                label={t("Open cells")}
                value={String(36 - snapshot.sequence)}
              />
              <TelemetryRow
                label={t("Server sequence")}
                value={String(snapshot.sequence)}
              />
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <ScanLine
                size={15}
                className="mt-0.5 shrink-0 text-[var(--accent)]"
                aria-hidden="true"
              />
              {t("Create SOS horizontally, vertically, or diagonally. Scoring keeps the turn.")}
            </p>
            <Shapes
              size={20}
              className="mt-5 text-[var(--muted)]"
              aria-hidden="true"
            />
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}

function ScorePanel({
  label,
  score,
  accent = false,
  alignRight = false,
}: {
  label: string;
  score: number;
  accent?: boolean;
  alignRight?: boolean;
}) {
  return (
    <div className={`bg-[var(--background)] p-4 ${alignRight ? "text-right" : ""}`}>
      <p className="font-telemetry text-[8px] text-[var(--muted)]">{label}</p>
      <p className={`mt-2 text-3xl font-black ${accent ? "text-[var(--accent)]" : ""}`}>
        {score}
      </p>
    </div>
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
