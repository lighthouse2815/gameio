"use client";

import type { ReactNode } from "react";
import Link from "next/link";
import {
  AlertTriangle,
  Check,
  LoaderCircle,
  Radio,
  RotateCw,
  ShieldCheck,
  WifiOff,
  X,
} from "lucide-react";
import { LoginRequired } from "@/components/auth/login-required";
import { Button, buttonStyles } from "@/components/ui/button";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { isUnauthenticated } from "@/features/auth/hooks";
import type { RealtimeGameController } from "@/features/multiplayer/realtime/use-realtime-game";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

export function RealtimeStage({
  controller,
  title,
  children,
}: {
  controller: RealtimeGameController;
  title: string;
  children: ReactNode;
}) {
  const { t, formatNumber } = useI18n();
  const { session, state } = controller;
  if (session.isLoading) {
    return <Skeleton className="h-[560px]" />;
  }
  if (session.isError && isUnauthenticated(session.error)) {
    return (
      <LoginRequired
        title={t("Realtime identity required")}
        description={t("Sign in before joining an authoritative game room.")}
      />
    );
  }
  if (session.isError) {
    return (
      <ErrorState
        title="Identity link unavailable"
        description={t(getErrorMessage(session.error))}
        onAction={() => void session.refetch()}
      />
    );
  }

  const room = state.room;
  const currentPlayer = room?.players.find(
    (player) => player.id === session.data?.id,
  );
  const allReady =
    Boolean(room && room.players.length >= 2) &&
    room?.players.every((player) => player.ready && player.connected);
  const progression = state.gameOver?.progression.find(
    (result) => result.userId === session.data?.id,
  );
  const fatalRoomError =
    state.error?.code === "ROOM_EXPIRED" ||
    state.error?.code === "ROOM_FINISHED" ||
    state.error?.code === "ROOM_GAME_MISMATCH" ||
    state.error?.code === "INVALID_GAME_STATE";

  return (
    <section className="border border-[var(--line-strong)] bg-[var(--background)]">
      <header className="grid gap-px border-b border-[var(--line)] bg-[var(--line)] sm:grid-cols-[1fr_auto]">
        <div className="bg-[var(--surface)] p-4">
          <p className="font-telemetry text-[8px] text-[var(--accent)]">
            {t("[ AUTHORITATIVE MATCH ]")}
          </p>
          <h3 className="mt-1 text-xl font-black uppercase tracking-[-0.04em]">
            {t(title)}
          </h3>
        </div>
        <div className="font-telemetry flex min-w-52 items-center justify-between gap-5 bg-[var(--surface)] px-4 py-3 text-[8px]">
          <span
            className={
              state.connection === "connected"
                ? "status-online"
                : "text-[var(--danger)]"
            }
          >
            {t(state.connection)}
          </span>
          <span className="text-[var(--muted)]">
            {room?.roomCode ?? controller.roomId.slice(0, 8)}
          </span>
        </div>
      </header>

      {state.error ? (
        <div className="flex items-start justify-between gap-4 border-b border-[var(--danger)] bg-[var(--surface)] p-4" role="alert">
          <div className="flex gap-3">
            <AlertTriangle size={16} className="mt-0.5 shrink-0 text-[var(--danger)]" aria-hidden="true" />
            <div>
              <p className="font-telemetry text-[8px] text-[var(--danger)]">
                {state.error.code}
              </p>
              <p className="mt-1 text-xs leading-5 text-[var(--muted)]">
                {t(state.error.message)}
              </p>
            </div>
          </div>
          {fatalRoomError ? (
            <Link href="/multiplayer" className={buttonStyles("secondary")}>
              {t("Return to lobby")}
            </Link>
          ) : (
            <button type="button" aria-label={t("Dismiss realtime error")} onClick={controller.clearError}>
              <X size={14} aria-hidden="true" />
            </button>
          )}
        </div>
      ) : null}

      {state.opponentDisconnected ? (
        <div className="font-telemetry flex items-center gap-3 border-b border-[var(--accent)] px-4 py-3 text-[9px] text-[var(--accent)]">
          <WifiOff size={14} aria-hidden="true" />
          {t("OPPONENT DISCONNECTED / SERVER RECONNECT GRACE ACTIVE")}
        </div>
      ) : null}

      {state.connection !== "connected" ? (
        <div className="flex items-center justify-between gap-5 border-b border-[var(--line)] bg-[var(--surface-strong)] p-4">
          <div className="flex items-center gap-3">
            {state.connection === "connecting" ||
            state.connection === "reconnecting" ? (
              <LoaderCircle className="animate-spin text-[var(--accent)]" size={16} aria-hidden="true" />
            ) : (
              <WifiOff className="text-[var(--danger)]" size={16} aria-hidden="true" />
            )}
            <p className="font-telemetry text-[8px]">
              {state.connection === "connecting" ||
              state.connection === "reconnecting"
                ? t("RESTORING ROOM + REQUESTING SERVER SNAPSHOT")
                : t("REALTIME LINK DISCONNECTED")}
            </p>
          </div>
          <Button compact variant="secondary" onClick={controller.reconnect}>
            <RotateCw size={13} aria-hidden="true" />
            {t("Reconnect")}
          </Button>
        </div>
      ) : null}

      {!room && !state.snapshot && !fatalRoomError ? (
        <div className="p-5">
          <EmptyState
            title={t("Waiting for room state")}
            description={t("The client has joined by room UUID and is waiting for the server membership snapshot.")}
          />
        </div>
      ) : null}

      {room?.status === "WAITING" && !state.snapshot ? (
        <div className="grid gap-6 p-5 lg:grid-cols-[1fr_280px]">
          <div>
            <p className="font-telemetry text-[9px] text-[var(--muted)]">
              {t("[ ROOM ASSEMBLY ]")}
            </p>
            <ul className="mt-4 grid gap-px border border-[var(--line)] bg-[var(--line)]">
              {room.players.map((player) => (
                <li className="flex items-center justify-between bg-[var(--surface)] p-4" key={player.id}>
                  <div>
                    <p className="font-bold">
                      {player.owner ? `[${t("OWNER")}] ` : ""}
                      {player.username}
                    </p>
                    <p className="font-telemetry mt-1 text-[8px] text-[var(--muted)]">
                      {t(player.id === session.data?.id ? "THIS CLIENT" : "REMOTE PLAYER")}
                    </p>
                  </div>
                  <span className={"font-telemetry text-[8px] " + (player.ready ? "text-[var(--online)]" : "text-[var(--muted)]")}>
                    {player.ready ? <Check size={12} className="mr-1 inline" aria-hidden="true" /> : null}
                    {t(player.ready ? "READY" : "STANDBY")}
                  </span>
                </li>
              ))}
            </ul>
          </div>
          <aside className="border border-[var(--line)] bg-[var(--surface)] p-4">
            <Radio className="text-[var(--accent)]" size={20} aria-hidden="true" />
            <h4 className="mt-5 text-xl font-black uppercase tracking-[-0.04em]">
              {t("Waiting room")}
            </h4>
            <p className="mt-2 text-xs leading-5 text-[var(--muted)]">
              {t("Every player must signal ready. Only the room owner may issue ROOM_START.")}
            </p>
            {!currentPlayer?.ready ? (
              <Button className="mt-5 w-full" onClick={controller.ready}>
                {t("Signal ready")}
              </Button>
            ) : null}
            {currentPlayer?.owner ? (
              <Button
                className="mt-2 w-full"
                variant="secondary"
                onClick={controller.start}
                disabled={!allReady}
              >
                {t("Start match")}
              </Button>
            ) : null}
          </aside>
        </div>
      ) : null}

      {room?.status === "PLAYING" && !state.snapshot ? (
        <div className="grid min-h-80 place-items-center p-5">
          <div className="text-center">
            <LoaderCircle className="mx-auto animate-spin text-[var(--accent)]" aria-hidden="true" />
            <p className="font-telemetry mt-4 text-[9px] text-[var(--muted)]">
              {t("REQUESTING AUTHORITATIVE SNAPSHOT")}
            </p>
          </div>
        </div>
      ) : null}

      {state.snapshot ? <div className="relative">{children}</div> : null}

      {state.gameOver ? (
        <div className="border-t border-[var(--accent)] bg-[var(--surface)] p-6">
          <div className="mx-auto max-w-xl text-center">
            <ShieldCheck className="mx-auto text-[var(--accent)]" size={28} aria-hidden="true" />
            <p className="font-telemetry mt-5 text-[9px] text-[var(--accent)]">
              {t("[ SERVER RECORDED RESULT ]")}
            </p>
            <h4 className="mt-2 text-4xl font-black uppercase tracking-[-0.055em]">
              {t(progression?.result ?? "Match over")}
            </h4>
            {progression ? (
              <p className="font-telemetry mt-4 text-[9px] text-[var(--muted)]">
                {t("SCORE")} {formatNumber(progression.score)} / +{formatNumber(progression.expAwarded)} EXP /
                {t("LEVEL")} {progression.level}
              </p>
            ) : null}
            <div className="mt-6 flex flex-wrap justify-center gap-3">
              <Link
                href={
                  "/multiplayer" +
                  (state.gameSlug
                    ? "?game=" + encodeURIComponent(state.gameSlug)
                    : "")
                }
                className={buttonStyles("primary")}
              >
                {t("Open a new room")}
              </Link>
              <Link href="/games" className={buttonStyles("secondary")}>
                {t("Back to games")}
              </Link>
            </div>
          </div>
        </div>
      ) : null}
    </section>
  );
}
