"use client";

import { useCallback, useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { isTypingRaceSnapshot } from "@/features/multiplayer/realtime/validation";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import type { TypingRacePlayerSnapshot } from "@/features/multiplayer/realtime/types";
import { playFeedback } from "@/features/settings/player-feedback";
import { toGraphemes } from "@/games/typing-race/engine";
import { TypingStage, type TypingRacer } from "@/games/typing-race/typing-stage";
import { useI18n } from "@/lib/i18n/use-i18n";

type OptimisticPlayer = Pick<
  TypingRacePlayerSnapshot,
  "progress" | "correctCharacters" | "errors" | "combo" | "bestCombo" | "lastInputSequence"
>;

function optimisticFrom(player: TypingRacePlayerSnapshot): OptimisticPlayer {
  return {
    progress: player.progress,
    correctCharacters: player.correctCharacters,
    errors: player.errors,
    combo: player.combo,
    bestCombo: player.bestCombo,
    lastInputSequence: player.lastInputSequence,
  };
}

export default function TypingRaceOnline({ roomId, spectator = false }: { roomId: string; spectator?: boolean }) {
  const { t } = useI18n();
  const controller = useRealtimeGame(roomId, "typing-race", spectator ? "spectator" : "player");
  const snapshot = isTypingRaceSnapshot(controller.state.snapshot) ? controller.state.snapshot : null;
  const userId = controller.session.data?.id;
  const ownServer = snapshot?.players.find((player) => player.userId === userId);
  const [optimistic, setOptimistic] = useState<OptimisticPlayer | null>(null);
  const [nowMs, setNowMs] = useState(() => Date.now());
  const [focused, setFocused] = useState(false);
  const [feedback, setFeedback] = useState<"idle" | "correct" | "error" | "finish">("idle");
  const [feedbackId, setFeedbackId] = useState(0);
  const inputRef = useRef<HTMLInputElement>(null);
  const sequenceRef = useRef(-1);
  const finishedMatchRef = useRef<string | null>(null);

  useEffect(() => {
    const timer = window.setInterval(() => setNowMs(Date.now()), 50);
    return () => window.clearInterval(timer);
  }, []);

  useEffect(() => {
    const matchId = controller.state.gameOver?.matchId;
    if (!matchId || finishedMatchRef.current === matchId) return;
    finishedMatchRef.current = matchId;
    setFeedback("finish");
    setFeedbackId((value) => value + 1);
    playFeedback("success");
  }, [controller.state.gameOver?.matchId]);

  const startsAtMs = snapshot ? Date.parse(snapshot.startsAt) : Number.POSITIVE_INFINITY;
  const phase = !snapshot
    ? "ready"
    : snapshot.terminal
      ? "complete"
      : nowMs < startsAtMs
        ? "countdown"
        : "playing";
  const active = Boolean(
    snapshot && ownServer && !spectator && phase === "playing" &&
      controller.state.connection === "connected" && !controller.state.gameOver,
  );

  useEffect(() => {
    if (active) requestAnimationFrame(() => inputRef.current?.focus());
  }, [active]);

  const handleCharacter = useCallback((value: string) => {
    if (!active || !snapshot || !ownServer) return;
    const characters = toGraphemes(value);
    if (characters.length !== 1) return;
    const character = characters[0];
    const current = !controller.state.error && optimistic &&
      optimistic.lastInputSequence > ownServer.lastInputSequence
      ? optimistic
      : optimisticFrom(ownServer);
    const correct = toGraphemes(snapshot.passage)[current.progress] === character;
    const nextSequence = Math.max(
      controller.state.error ? ownServer.lastInputSequence : sequenceRef.current,
      ownServer.lastInputSequence,
    ) + 1;
    sequenceRef.current = nextSequence;
    if (controller.state.error) controller.clearError();
    setOptimistic({
      ...current,
      progress: current.progress + (correct ? 1 : 0),
      correctCharacters: current.correctCharacters + (correct ? 1 : 0),
      errors: current.errors + (correct ? 0 : 1),
      combo: correct ? current.combo + 1 : 0,
      bestCombo: Math.max(current.bestCombo, correct ? current.combo + 1 : 0),
      lastInputSequence: nextSequence,
    });
    setFeedback(correct ? "correct" : "error");
    setFeedbackId((id) => id + 1);
    playFeedback(correct ? "typing" : "error");
    controller.sendInput({ action: "TYPE_CHARACTER", character, sequence: nextSequence });
  }, [active, controller, optimistic, ownServer, snapshot]);

  function inputCharacter(event: FormEvent<HTMLInputElement>) {
    if (!active) return;
    const native = event.nativeEvent as InputEvent;
    if (native.inputType !== "insertText" || native.data === null) return;
    handleCharacter(native.data);
  }

  const displayOwn = useMemo(() => {
    if (!ownServer) return undefined;
    if (
      !controller.state.error && optimistic &&
      optimistic.lastInputSequence > ownServer.lastInputSequence
    ) {
      return { ...ownServer, ...optimistic };
    }
    return ownServer;
  }, [controller.state.error, optimistic, ownServer]);
  const racers = useMemo<TypingRacer[]>(() => {
    if (!snapshot) return [];
    const passageLength = toGraphemes(snapshot.passage).length;
    return snapshot.players.map((player) => {
      const display = player.userId === userId && displayOwn ? displayOwn : player;
      return {
        id: player.userId,
        name: controller.state.room?.players.find((roomPlayer) => roomPlayer.id === player.userId)?.username ?? player.userId.slice(0, 8),
        own: player.userId === userId,
        progressPercent: Math.round((display.progress / passageLength) * 100),
        wpm: player.wpm,
        accuracyPercent: player.accuracyPercent,
        finished: player.finished,
      };
    });
  }, [controller.state.room?.players, displayOwn, snapshot, userId]);

  const countdown = Math.max(1, Math.ceil((startsAtMs - nowMs) / 1_000));
  const progression = controller.state.gameOver?.progression.find((entry) => entry.userId === userId);
  const statusText = spectator
    ? t("Spectating server race")
    : progression
      ? t(progression.result === "WIN" ? "You win" : progression.result === "DRAW" ? "Draw" : "Opponent wins")
      : phase === "countdown"
        ? t("Server countdown")
        : active
          ? focused ? t("Authoritative race active") : t("Click the input field to keep racing")
          : t("Waiting for server state");

  const input = (
    <input
      ref={inputRef}
      value=""
      onChange={() => undefined}
      onInput={inputCharacter}
      onPaste={(event) => active && event.preventDefault()}
      onDrop={(event) => event.preventDefault()}
      onFocus={() => setFocused(true)}
      onBlur={() => setFocused(false)}
      disabled={!active}
      autoCapitalize="off"
      autoComplete="off"
      autoCorrect="off"
      spellCheck={false}
      aria-label={t("Authoritative typing input")}
      placeholder={spectator ? t("Spectators cannot send input") : active ? t("Click here and race") : t("Waiting for race start")}
      className="font-telemetry min-h-11 w-full border border-[var(--line-strong)] bg-[var(--surface)] px-3 text-[10px] text-[var(--foreground)] placeholder:text-[var(--muted)] disabled:cursor-not-allowed disabled:opacity-55"
    />
  );

  return (
    <RealtimeStage controller={controller} title="Type Rush / Server Duel">
      {snapshot ? (
        <div className="p-3 sm:p-5">
          <TypingStage
            prompt={snapshot.passage}
            cursor={displayOwn?.progress ?? 0}
            wpm={ownServer?.wpm ?? 0}
            accuracyPercent={ownServer?.accuracyPercent ?? 100}
            mistakes={displayOwn?.errors ?? 0}
            combo={displayOwn?.combo ?? 0}
            bestCombo={displayOwn?.bestCombo ?? 0}
            phase={phase}
            countdown={countdown}
            racers={racers}
            feedback={feedback}
            feedbackId={feedbackId}
            inputSlot={input}
            statusText={statusText}
          />
        </div>
      ) : null}
    </RealtimeStage>
  );
}
