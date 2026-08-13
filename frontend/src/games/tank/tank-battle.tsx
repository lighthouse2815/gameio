"use client";

import { useCallback, useEffect, useRef } from "react";
import {
  ArrowDown,
  ArrowLeft,
  ArrowRight,
  ArrowUp,
  Crosshair,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { TankCanvas } from "@/games/tank/tank-canvas";
import {
  createTankInput,
  isTankSnapshot,
  nextTankInputSequence,
  tankActionForKey,
  type TankAction,
} from "@/games/tank/input";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function TankBattle({ roomId }: { roomId: string }) {
  const { t } = useI18n();
  const controller = useRealtimeGame(roomId, "tank-battle");
  const snapshot = isTankSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const sendInput = controller.sendInput;
  const sequenceRef = useRef(-1);
  const inputContext = useRef({
    snapshot: null as typeof snapshot,
    userId: undefined as string | undefined,
    enabled: false,
  });

  useEffect(() => {
    const ownTank = snapshot?.tanks.find((tank) => tank.userId === userId);
    inputContext.current = {
      snapshot,
      userId,
      enabled:
        controller.state.connection === "connected" &&
        Boolean(ownTank?.alive) &&
        !controller.state.gameOver,
    };
  }, [
    controller.state.connection,
    controller.state.gameOver,
    snapshot,
    userId,
  ]);

  const sendAction = useCallback(
    (action: TankAction) => {
      const context = inputContext.current;
      if (!context.enabled) return null;
      const sequence = nextTankInputSequence(
        sequenceRef.current,
        context.snapshot,
        context.userId,
      );
      sequenceRef.current = sequence;
      return sendInput(createTankInput(action, sequence));
    },
    [sendInput],
  );

  useEffect(() => {
    const held = new Set<string>();
    const movement = (key: string) => {
      const action = tankActionForKey(key);
      return action?.startsWith("MOVE_") ? action : null;
    };
    const onKeyDown = (event: KeyboardEvent) => {
      const action = tankActionForKey(event.key);
      if (!action) return;
      event.preventDefault();
      if (action === "SHOOT") {
        if (!event.repeat) sendAction("SHOOT");
        return;
      }
      if (event.repeat || held.has(event.key)) return;
      held.add(event.key);
      sendAction(action);
    };
    const onKeyUp = (event: KeyboardEvent) => {
      if (!movement(event.key)) return;
      event.preventDefault();
      held.delete(event.key);
      const remaining = [...held]
        .reverse()
        .map(movement)
        .find((action): action is TankAction => Boolean(action));
      sendAction(remaining ?? "STOP");
    };
    const stop = () => {
      if (held.size) {
        held.clear();
        sendAction("STOP");
      }
    };
    window.addEventListener("keydown", onKeyDown);
    window.addEventListener("keyup", onKeyUp);
    window.addEventListener("blur", stop);
    return () => {
      window.removeEventListener("keydown", onKeyDown);
      window.removeEventListener("keyup", onKeyUp);
      window.removeEventListener("blur", stop);
    };
  }, [sendAction]);

  const ownTank = snapshot?.tanks.find((tank) => tank.userId === userId);
  const room = controller.state.room;
  const movementControls: Array<
    [TankAction, string, typeof ArrowUp]
  > = [
    ["MOVE_UP", "Move up", ArrowUp],
    ["MOVE_LEFT", "Move left", ArrowLeft],
    ["MOVE_DOWN", "Move down", ArrowDown],
    ["MOVE_RIGHT", "Move right", ArrowRight],
  ];

  return (
    <RealtimeStage controller={controller} title="Tank Battle / 100×100">
      {snapshot ? (
        <div className="grid gap-5 p-3 sm:p-5 xl:grid-cols-[minmax(0,760px)_280px] xl:justify-center">
          <TankCanvas snapshot={snapshot} userId={userId} />
          <aside className="grid content-start gap-4">
            <section className="border border-[var(--line)] bg-[var(--surface)] p-4">
              <p className="font-telemetry text-[8px] text-[var(--muted)]">
                {t("[ PLAYER TELEMETRY ]")}
              </p>
              <p className="mt-4 text-3xl font-black tracking-[-0.05em]">
                HP {ownTank?.hp ?? "—"}
              </p>
              <div className="mt-4 h-2 border border-[var(--line-strong)] p-px">
                <div
                  className="h-full bg-[var(--accent)]"
                  style={{ width: (ownTank?.hp ?? 0) + "%" }}
                />
              </div>
              <dl className="font-telemetry mt-5 grid gap-2 text-[8px] text-[var(--muted)]">
                <div className="flex justify-between">
                  <dt>{t("Kills")}</dt>
                  <dd>{ownTank?.kills ?? 0}</dd>
                </div>
                <div className="flex justify-between">
                  <dt>{t("Input ACK")}</dt>
                  <dd>{ownTank?.lastInputSequence ?? "—"}</dd>
                </div>
                <div className="flex justify-between">
                  <dt>{t("Snapshot")}</dt>
                  <dd>{snapshot.sequence}</dd>
                </div>
              </dl>
            </section>

            <section className="border border-[var(--line)] bg-[var(--surface)] p-4">
              <p className="font-telemetry text-[8px] text-[var(--muted)]">
                {t("[ INPUT ARRAY ]")}
              </p>
              <div className="mx-auto mt-5 grid w-40 grid-cols-3 gap-2">
                <span />
                {movementControls.slice(0, 1).map(([action, label, Icon]) => (
                  <Button
                    key={action}
                    compact
                    variant="secondary"
                    aria-label={t(label)}
                    onPointerDown={() => sendAction(action)}
                    onPointerUp={() => sendAction("STOP")}
                    onPointerLeave={() => sendAction("STOP")}
                  >
                    <Icon size={18} aria-hidden="true" />
                  </Button>
                ))}
                <span />
                {movementControls.slice(1).map(([action, label, Icon]) => (
                  <Button
                    key={action}
                    compact
                    variant="secondary"
                    aria-label={t(label)}
                    onPointerDown={() => sendAction(action)}
                    onPointerUp={() => sendAction("STOP")}
                    onPointerLeave={() => sendAction("STOP")}
                  >
                    <Icon size={18} aria-hidden="true" />
                  </Button>
                ))}
              </div>
              <Button
                className="mt-4 w-full"
                onClick={() => sendAction("SHOOT")}
                disabled={!ownTank?.alive}
              >
                <Crosshair size={14} aria-hidden="true" />
                {t("Shoot")}
              </Button>
              <p className="mt-4 text-xs leading-5 text-[var(--muted)]">
                {t("Keyboard: WASD/arrows, Space/Enter to shoot. Movement release sends STOP.")}
              </p>
            </section>

            <section className="border border-[var(--line)] bg-[var(--surface)]">
              <p className="font-telemetry border-b border-[var(--line)] p-3 text-[8px] text-[var(--muted)]">
                {t("[ COMBATANTS ]")}
              </p>
              {snapshot.tanks.map((tank) => (
                <div className="border-b border-[var(--line)] p-3 last:border-b-0" key={tank.userId}>
                  <div className="font-telemetry flex justify-between text-[8px]">
                    <span>
                      {room?.players.find((player) => player.id === tank.userId)
                        ?.username ?? tank.userId.slice(0, 8)}
                    </span>
                    <span className={tank.alive ? "status-online" : "text-[var(--danger)]"}>
                      {tank.alive ? tank.hp + " HP" : t("DESTROYED")}
                    </span>
                  </div>
                </div>
              ))}
            </section>
          </aside>
        </div>
      ) : null}
    </RealtimeStage>
  );
}
