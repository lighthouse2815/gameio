"use client";

import { Hexagon, Route } from "lucide-react";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isHexSnapshot } from "@/features/multiplayer/realtime/validation";
import type { HexSnapshot } from "@/features/multiplayer/realtime/types";
import { playFeedback } from "@/features/settings/player-feedback";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function HexGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const controller = useRealtimeGame(
    roomId,
    "hex",
    spectator ? "spectator" : "player",
  );
  const snapshot: HexSnapshot | null = isHexSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const playerIndex =
    controller.state.room?.players.findIndex((player) => player.id === userId) ??
    -1;
  const ownStone = playerIndex === 0 ? "R" : playerIndex === 1 ? "B" : "";
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;

  function placeStone(row: number, column: number) {
    playFeedback("move");
    controller.sendInput({ action: "PLACE_STONE", row, column });
  }

  return (
    <RealtimeStage controller={controller} title="Hex / 9×9">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(520px,720px)_280px] lg:justify-center">
          <div className="min-w-0">
            <div className="mb-3 grid grid-cols-3 items-center font-telemetry text-[8px] uppercase text-[var(--muted)]">
              <span>{t("Blue edge")}</span>
              <span className="text-center">{t("Red edge")}</span>
              <span className="text-right">{t("Blue edge")}</span>
            </div>

            <div className="overflow-x-auto overscroll-x-contain pb-2">
              <div
                className="mx-auto min-w-[520px] max-w-[720px] border-y-2 border-[var(--accent)] bg-[var(--surface)] px-4 py-5"
                role="grid"
                aria-label={t("Authoritative Hex board")}
              >
                {snapshot.board.map((row, rowIndex) => (
                  <div
                    key={rowIndex}
                    className="flex gap-1"
                    role="row"
                    style={{ marginLeft: `${rowIndex * 18}px` }}
                  >
                    {row.map((stone, columnIndex) => {
                      const latest =
                        snapshot.lastMoveRow === rowIndex &&
                        snapshot.lastMoveColumn === columnIndex;
                      const enabled =
                        !spectator &&
                        yourTurn &&
                        stone === "" &&
                        !pending &&
                        !snapshot.winnerId;
                      return (
                        <button
                          key={`${rowIndex}-${columnIndex}`}
                          type="button"
                          role="gridcell"
                          disabled={!enabled}
                          onClick={() => placeStone(rowIndex, columnIndex)}
                          aria-label={
                            stone
                              ? t("Hex row {row}, column {column}: {stone}", {
                                  row: rowIndex + 1,
                                  column: columnIndex + 1,
                                  stone,
                                })
                              : t("Place Hex stone at row {row}, column {column}", {
                                  row: rowIndex + 1,
                                  column: columnIndex + 1,
                                })
                          }
                          className={
                            "relative grid h-11 w-11 shrink-0 touch-manipulation place-items-center [clip-path:polygon(25%_5%,75%_5%,100%_50%,75%_95%,25%_95%,0_50%)] transition-[background-color,transform] duration-200 disabled:cursor-not-allowed " +
                            (stone === "R"
                              ? "bg-[var(--accent)] text-[var(--background)]"
                              : stone === "B"
                                ? "bg-[var(--foreground)] text-[var(--background)]"
                                : enabled
                                  ? "cursor-pointer bg-[var(--surface-strong)] hover:bg-[var(--online)] active:scale-90"
                                  : "bg-[var(--line)] text-[var(--muted)]")
                          }
                        >
                          <span className="text-sm font-black" aria-hidden="true">
                            {stone || "·"}
                          </span>
                          {latest ? (
                            <span
                              className="absolute h-2 w-2 rounded-full bg-[var(--background)]"
                              aria-hidden="true"
                            />
                          ) : null}
                        </button>
                      );
                    })}
                  </div>
                ))}
              </div>
            </div>

            <p className="mt-2 text-center font-telemetry text-[8px] uppercase text-[var(--muted)]">
              {t("Red connects top to bottom. Blue connects left to right.")}
            </p>
          </div>

          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ CONNECTION TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(
                snapshot.winnerId
                  ? snapshot.winnerId === userId
                    ? "You connected the edges"
                    : "Rival connected the edges"
                  : yourTurn
                    ? "Place a stone"
                    : "Opponent turn",
              )}
            </p>

            <dl className="mt-7 grid gap-px border border-[var(--line)] bg-[var(--line)] font-telemetry text-[8px]">
              <TelemetryRow label={t("Your stone")} value={ownStone || "—"} />
              <TelemetryRow
                label={t("Open cells")}
                value={String(81 - snapshot.sequence)}
              />
              <TelemetryRow
                label={t("Server sequence")}
                value={String(snapshot.sequence)}
              />
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <Route
                size={15}
                className="mt-0.5 shrink-0 text-[var(--accent)]"
                aria-hidden="true"
              />
              {t("Every stone and six-direction connection check is validated by the server.")}
            </p>
            <Hexagon
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

function TelemetryRow({ label, value }: { label: string; value: string }) {
  return (
    <div className="flex items-center justify-between bg-[var(--background)] p-3">
      <dt>{label}</dt>
      <dd>{value}</dd>
    </div>
  );
}
