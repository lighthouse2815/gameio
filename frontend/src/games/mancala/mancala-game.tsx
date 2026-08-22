"use client";

import { CircleDot, Route } from "lucide-react";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isMancalaSnapshot } from "@/features/multiplayer/realtime/validation";
import type { MancalaSnapshot } from "@/features/multiplayer/realtime/types";
import { playFeedback } from "@/features/settings/player-feedback";
import { useI18n } from "@/lib/i18n/use-i18n";

const FIRST_SIDE = [0, 1, 2, 3, 4, 5] as const;
const SECOND_SIDE = [7, 8, 9, 10, 11, 12] as const;

export default function MancalaGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const controller = useRealtimeGame(
    roomId,
    "mancala",
    spectator ? "spectator" : "player",
  );
  const snapshot: MancalaSnapshot | null = isMancalaSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const playerIndex = controller.state.room?.players.findIndex(
    (player) => player.id === userId,
  ) ?? -1;
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;
  const viewFromSecondSide = playerIndex === 1;
  const ownPits = viewFromSecondSide ? [...SECOND_SIDE] : [...FIRST_SIDE];
  const opponentPits = viewFromSecondSide
    ? [...FIRST_SIDE].reverse()
    : [...SECOND_SIDE].reverse();
  const ownStore = viewFromSecondSide ? 13 : 6;
  const opponentStore = viewFromSecondSide ? 6 : 13;
  const ownScore = playerIndex >= 0 ? snapshot?.scores[playerIndex] ?? 0 : snapshot?.scores[0] ?? 0;
  const opponentScore = playerIndex >= 0
    ? snapshot?.scores[playerIndex === 0 ? 1 : 0] ?? 0
    : snapshot?.scores[1] ?? 0;

  function sow(column: number) {
    playFeedback("move");
    controller.sendInput({ action: "SOW_PIT", column });
  }

  return (
    <RealtimeStage controller={controller} title="Mancala / Kalah">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(520px,760px)_280px] lg:justify-center">
          <div className="min-w-0">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2 font-telemetry text-[8px] uppercase text-[var(--muted)]">
              <span>{t("Opponent side")}</span>
              <span>{t("Sow counter-clockwise")}</span>
              <span>{t("Your side")}</span>
            </div>

            <div className="overflow-x-auto overscroll-x-contain pb-2">
              <div
                className="mx-auto grid min-h-[236px] w-full min-w-[520px] max-w-[760px] grid-cols-[72px_1fr_72px] gap-2 border border-[var(--line-strong)] bg-[var(--surface)] p-3 sm:gap-3 sm:p-4"
                role="group"
                aria-label={t("Authoritative Mancala board")}
              >
                <Store
                  label={t("Opponent store")}
                  stones={snapshot.pits[opponentStore]}
                  latest={snapshot.lastPit === opponentStore}
                />

                <div className="grid grid-cols-6 grid-rows-2 gap-2 sm:gap-3">
                  {opponentPits.map((pit, column) => (
                    <Pit
                      key={`opponent-${pit}`}
                      label={t("Opponent pit {pit}: {stones} stones", {
                        pit: 6 - column,
                        stones: snapshot.pits[pit],
                      })}
                      stones={snapshot.pits[pit]}
                      latest={snapshot.lastPit === pit}
                    />
                  ))}

                  {ownPits.map((pit, column) => {
                    const legal = snapshot.legalPits.includes(pit) || snapshot.legalPits.includes(column);
                    const enabled = !spectator && yourTurn && legal && !pending &&
                      !snapshot.winnerId && !snapshot.draw;
                    return (
                      <button
                        key={`own-${pit}`}
                        type="button"
                        disabled={!enabled}
                        onClick={() => sow(column)}
                        aria-label={t("Sow your pit {pit} with {stones} stones", {
                          pit: column + 1,
                          stones: snapshot.pits[pit],
                        })}
                        className={
                          "relative grid min-h-[88px] min-w-11 touch-manipulation place-items-center border bg-[var(--background)] p-2 transition-[background-color,border-color,transform] duration-200 disabled:cursor-not-allowed " +
                          (enabled
                            ? "cursor-pointer border-[var(--accent)] hover:-translate-y-0.5 hover:bg-[var(--surface-strong)] active:scale-[0.98]"
                            : "border-[var(--line)]") +
                          (snapshot.lastPit === pit
                            ? " ring-2 ring-[var(--online)] ring-offset-1 ring-offset-[var(--surface)]"
                            : "")
                        }
                      >
                        <PitContent stones={snapshot.pits[pit]} />
                        <span className="font-telemetry absolute bottom-1 right-1 text-[7px] text-[var(--muted)]" aria-hidden="true">
                          0{column + 1}
                        </span>
                      </button>
                    );
                  })}
                </div>

                <Store
                  label={t("Your store")}
                  stones={snapshot.pits[ownStore]}
                  latest={snapshot.lastPit === ownStore}
                  accent
                />
              </div>
            </div>
          </div>

          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ SOWING TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(snapshot.winnerId
                ? snapshot.winnerId === userId ? "You harvested the board" : "Rival harvested the board"
                : snapshot.draw ? "Mancala draw"
                : yourTurn ? "Choose your pit" : "Opponent turn")}
            </p>

            <div className="mt-7 grid grid-cols-2 gap-px border border-[var(--line)] bg-[var(--line)]">
              <ScorePanel label={t("You")} score={ownScore} accent />
              <ScorePanel label={t("Opponent")} score={opponentScore} alignRight />
            </div>

            <dl className="mt-px grid gap-px border border-[var(--line)] bg-[var(--line)] font-telemetry text-[8px]">
              <TelemetryRow label={t("Legal pits")} value={String(snapshot.legalPits.length)} />
              <TelemetryRow label={t("Last landing pit")} value={snapshot.lastPit == null ? "—" : String(snapshot.lastPit + 1)} />
              <TelemetryRow label={t("Server sequence")} value={String(snapshot.sequence)} />
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <Route size={15} className="mt-0.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
              {t("Choose only from your six pits. The server owns sowing, captures, bonus turns, and scoring.")}
            </p>
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}

function Pit({
  label,
  stones,
  latest,
}: {
  label: string;
  stones: number;
  latest: boolean;
}) {
  return (
    <div
      role="img"
      aria-label={label}
      className={
        "grid min-h-[88px] min-w-11 place-items-center border border-[var(--line)] bg-[var(--background)] p-2 " +
        (latest ? "ring-2 ring-[var(--online)] ring-offset-1 ring-offset-[var(--surface)]" : "")
      }
    >
      <PitContent stones={stones} />
    </div>
  );
}

function PitContent({ stones }: { stones: number }) {
  return (
    <span className="grid place-items-center" aria-hidden="true">
      <CircleDot size={18} className="text-[var(--accent)]" />
      <strong className="mt-1 text-xl font-black tabular-nums">{stones}</strong>
    </span>
  );
}

function Store({
  label,
  stones,
  latest,
  accent = false,
}: {
  label: string;
  stones: number;
  latest: boolean;
  accent?: boolean;
}) {
  return (
    <div
      role="img"
      aria-label={`${label}: ${stones}`}
      className={
        "grid min-h-44 place-items-center border bg-[var(--background)] p-2 text-center " +
        (accent ? "border-[var(--accent)]" : "border-[var(--line-strong)]") +
        (latest ? " ring-2 ring-[var(--online)] ring-offset-1 ring-offset-[var(--surface)]" : "")
      }
    >
      <span>
        <span className="font-telemetry block text-[7px] text-[var(--muted)]">{label}</span>
        <strong className={`mt-3 block text-3xl font-black tabular-nums ${accent ? "text-[var(--accent)]" : ""}`}>
          {stones}
        </strong>
      </span>
    </div>
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
      <p className={`mt-2 text-3xl font-black ${accent ? "text-[var(--accent)]" : ""}`}>{score}</p>
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
