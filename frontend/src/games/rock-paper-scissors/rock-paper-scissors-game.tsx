"use client";

import type { ComponentType } from "react";
import { CircleDot, FileText, LockKeyhole, Scissors } from "lucide-react";
import { playFeedback } from "@/features/settings/player-feedback";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isRpsSnapshot } from "@/features/multiplayer/realtime/validation";
import type { RpsChoice } from "@/features/multiplayer/realtime/types";
import { useI18n } from "@/lib/i18n/use-i18n";

const CHOICES: ReadonlyArray<{
  value: RpsChoice;
  label: string;
  icon: ComponentType<{ size?: number; className?: string; "aria-hidden"?: boolean }>;
}> = [
  { value: "ROCK", label: "Rock", icon: CircleDot },
  { value: "PAPER", label: "Paper", icon: FileText },
  { value: "SCISSORS", label: "Scissors", icon: Scissors },
];

export default function RockPaperScissorsGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const controller = useRealtimeGame(
    roomId,
    "rock-paper-scissors",
    spectator ? "spectator" : "player",
  );
  const snapshot = isRpsSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const ownState = snapshot?.players.find((player) => player.userId === userId);
  const opponentState = snapshot?.players.find((player) => player.userId !== userId);
  const pending = controller.state.pendingRequestIds.length > 0;
  const locked = Boolean(ownState?.submitted);

  function choose(index: number) {
    playFeedback("move");
    controller.sendInput({ action: "SELECT_MOVE", column: index });
  }

  return (
    <RealtimeStage controller={controller} title="Rock Paper Scissors / First to 3">
      {snapshot ? (
        <div className="mx-auto grid max-w-5xl gap-5 p-4 sm:p-6 lg:grid-cols-[1fr_300px]">
          <div>
            <div className="grid gap-px border border-[var(--line)] bg-[var(--line)] sm:grid-cols-2">
              <ScorePanel
                label={t("You")}
                score={ownState?.wins ?? 0}
                target={snapshot.targetWins}
                submitted={Boolean(ownState?.submitted)}
              />
              <ScorePanel
                label={t("Opponent")}
                score={opponentState?.wins ?? 0}
                target={snapshot.targetWins}
                submitted={Boolean(opponentState?.submitted)}
                alignRight
              />
            </div>

            <div className="mt-4 grid grid-cols-3 gap-2 sm:gap-3" aria-label={t("Choose Rock, Paper, or Scissors")}>
              {CHOICES.map((choice, index) => {
                const Icon = choice.icon;
                const enabled = !spectator && !snapshot.winnerId && !locked && !pending;
                return (
                  <button
                    key={choice.value}
                    type="button"
                    disabled={!enabled}
                    onClick={() => choose(index)}
                    className="group grid min-h-28 touch-manipulation place-items-center border border-[var(--line-strong)] bg-[var(--surface)] p-3 text-center transition-[background-color,border-color,transform] duration-200 enabled:cursor-pointer enabled:hover:-translate-y-1 enabled:hover:border-[var(--accent)] enabled:hover:bg-[var(--surface-strong)] enabled:active:scale-[0.98] disabled:cursor-not-allowed disabled:opacity-60 sm:min-h-40"
                    aria-label={t("Lock {choice} for round {round}", {
                      choice: t(choice.label),
                      round: snapshot.round,
                    })}
                  >
                    <span>
                      <Icon size={34} className="mx-auto text-[var(--accent)] sm:h-12 sm:w-12" aria-hidden={true} />
                      <span className="mt-3 block text-xs font-black uppercase sm:text-base">
                        {t(choice.label)}
                      </span>
                      <span className="font-telemetry mt-1 block text-[7px] text-[var(--muted)] sm:text-[8px]">
                        0{index + 1} / {t("LOCK")}
                      </span>
                    </span>
                  </button>
                );
              })}
            </div>

            <div className="mt-4 border border-[var(--line)] bg-[var(--surface)] p-4" aria-live="polite">
              <p className="font-telemetry text-[8px] text-[var(--muted)]">
                {t("[ LAST ROUND DECLASSIFIED ]")}
              </p>
              {snapshot.lastRound ? (
                <div className="mt-3 flex flex-wrap items-center gap-3 text-lg font-black uppercase">
                  <span>{t(snapshot.lastRound.firstChoice)}</span>
                  <span className="font-telemetry text-[8px] text-[var(--muted)]">VS</span>
                  <span>{t(snapshot.lastRound.secondChoice)}</span>
                  <span className="ml-auto text-sm text-[var(--accent)]">
                    {t(snapshot.lastRound.draw
                      ? "Round draw"
                      : snapshot.lastRound.winnerId === userId
                        ? "Round secured"
                        : "Round lost")}
                  </span>
                </div>
              ) : (
                <p className="mt-3 text-sm text-[var(--muted)]">
                  {t("Choices stay hidden until both players lock the round.")}
                </p>
              )}
            </div>
          </div>

          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ MIND GAME TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(snapshot.winnerId
                ? snapshot.winnerId === userId ? "You read the rival" : "Rival read you"
                : locked ? "Choice locked" : "Read the rival")}
            </p>
            <dl className="font-telemetry mt-7 grid gap-px border border-[var(--line)] bg-[var(--line)] text-[8px]">
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Round")}</dt>
                <dd>{snapshot.round}/5</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Your signal")}</dt>
                <dd className={locked ? "status-online" : "text-[var(--muted)]"}>
                  {t(locked ? "LOCKED" : "OPEN")}
                </dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Opponent signal")}</dt>
                <dd>{t(opponentState?.submitted ? "LOCKED" : "HIDDEN")}</dd>
              </div>
              <div className="flex justify-between bg-[var(--background)] p-3">
                <dt>{t("Server sequence")}</dt>
                <dd>{snapshot.sequence}</dd>
              </div>
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <LockKeyhole size={15} className="mt-0.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
              {t("The server records a sealed choice and reveals both only after the second lock.")}
            </p>
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}

function ScorePanel({
  label,
  score,
  target,
  submitted,
  alignRight = false,
}: {
  label: string;
  score: number;
  target: number;
  submitted: boolean;
  alignRight?: boolean;
}) {
  return (
    <div className={`bg-[var(--surface)] p-4 ${alignRight ? "text-right" : ""}`}>
      <div className={`flex items-center gap-3 ${alignRight ? "justify-end" : ""}`}>
        <span className="font-telemetry text-[8px] text-[var(--muted)]">{label}</span>
        {submitted ? <LockKeyhole size={12} className="text-[var(--online)]" aria-hidden="true" /> : null}
      </div>
      <div className={`mt-3 flex gap-2 ${alignRight ? "justify-end" : ""}`} aria-label={`${score} / ${target}`}>
        {Array.from({ length: target }, (_, index) => (
          <span
            key={index}
            className={`h-3 w-9 border ${index < score ? "border-[var(--accent)] bg-[var(--accent)]" : "border-[var(--line-strong)]"}`}
            aria-hidden="true"
          />
        ))}
      </div>
    </div>
  );
}
