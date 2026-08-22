"use client";

import { CircleDotDashed, ScanLine } from "lucide-react";
import { RealtimeStage } from "@/features/multiplayer/realtime/realtime-stage";
import { useRealtimeGame } from "@/features/multiplayer/realtime/use-realtime-game";
import { isDotsAndBoxesSnapshot } from "@/features/multiplayer/realtime/validation";
import type { DotsAndBoxesSnapshot } from "@/features/multiplayer/realtime/types";
import { playFeedback } from "@/features/settings/player-feedback";
import { useI18n } from "@/lib/i18n/use-i18n";

type EdgeOrientation = "H" | "V";

export default function DotsAndBoxesGame({
  roomId,
  spectator = false,
}: {
  roomId: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const controller = useRealtimeGame(
    roomId,
    "dots-and-boxes",
    spectator ? "spectator" : "player",
  );
  const snapshot: DotsAndBoxesSnapshot | null = isDotsAndBoxesSnapshot(controller.state.snapshot)
    ? controller.state.snapshot
    : null;
  const userId = controller.session.data?.id;
  const playerIndex = controller.state.room?.players.findIndex(
    (player) => player.id === userId,
  ) ?? -1;
  const ownBox = playerIndex === 0 ? "R" : playerIndex === 1 ? "B" : "";
  const pending = controller.state.pendingRequestIds.length > 0;
  const yourTurn = snapshot?.currentTurnPlayerId === userId;
  const legalEdges = new Set(
    snapshot?.legalMoves.map((edge) => `${edge.orientation}:${edge.row}:${edge.column}`) ?? [],
  );

  function drawEdge(orientation: EdgeOrientation, row: number, column: number) {
    playFeedback("move");
    controller.sendInput({
      action: orientation === "H" ? "DRAW_HORIZONTAL" : "DRAW_VERTICAL",
      row,
      column,
    });
  }

  return (
    <RealtimeStage controller={controller} title="Dots and Boxes / 4×4">
      {snapshot ? (
        <div className="grid gap-5 p-4 sm:p-6 lg:grid-cols-[minmax(396px,680px)_280px] lg:justify-center">
          <div className="min-w-0">
            <div className="overflow-x-auto overscroll-x-contain pb-2">
              <div
                className="mx-auto grid aspect-square w-full min-w-[396px] max-w-[680px] grid-cols-9 bg-[var(--surface)] p-2 sm:p-3"
                role="grid"
                aria-label={t("Authoritative Dots and Boxes board")}
              >
                {Array.from({ length: 9 }, (_, gridRow) =>
                  Array.from({ length: 9 }, (_, gridColumn) => {
                    const horizontal = gridRow % 2 === 0 && gridColumn % 2 === 1;
                    const vertical = gridRow % 2 === 1 && gridColumn % 2 === 0;
                    const dot = gridRow % 2 === 0 && gridColumn % 2 === 0;

                    if (dot) {
                      return (
                        <span
                          key={`${gridRow}-${gridColumn}`}
                          className="grid aspect-square min-h-11 min-w-11 place-items-center"
                          role="presentation"
                        >
                          <span className="h-3 w-3 rounded-full border-2 border-[var(--foreground)] bg-[var(--background)]" aria-hidden="true" />
                        </span>
                      );
                    }

                    if (horizontal || vertical) {
                      const orientation: EdgeOrientation = horizontal ? "H" : "V";
                      const row = horizontal ? gridRow / 2 : (gridRow - 1) / 2;
                      const column = horizontal ? (gridColumn - 1) / 2 : gridColumn / 2;
                      const drawn = horizontal
                        ? snapshot.horizontalEdges[row][column]
                        : snapshot.verticalEdges[row][column];
                      const legal = legalEdges.has(`${orientation}:${row}:${column}`);
                      const latest = snapshot.lastEdge?.orientation === orientation &&
                        snapshot.lastEdge.row === row && snapshot.lastEdge.column === column;
                      const enabled = !spectator && yourTurn && legal && !drawn && !pending &&
                        !snapshot.winnerId && !snapshot.draw;

                      return (
                        <button
                          key={`${gridRow}-${gridColumn}`}
                          type="button"
                          role="gridcell"
                          disabled={!enabled}
                          aria-label={t(
                            orientation === "H"
                              ? "Horizontal edge row {row}, column {column}: {state}"
                              : "Vertical edge row {row}, column {column}: {state}",
                            {
                              row: row + 1,
                              column: column + 1,
                              state: t(drawn ? "drawn" : legal ? "available" : "unavailable"),
                            },
                          )}
                          onClick={() => drawEdge(orientation, row, column)}
                          className="group grid aspect-square min-h-11 min-w-11 touch-manipulation place-items-center disabled:cursor-not-allowed enabled:cursor-pointer"
                        >
                          <span
                            className={
                              "block transition-[background-color,transform] duration-200 group-enabled:group-hover:bg-[var(--accent)] group-enabled:group-active:scale-90 " +
                              (horizontal ? "h-1 w-full" : "h-full w-1") +
                              (drawn
                                ? latest ? " bg-[var(--online)]" : " bg-[var(--foreground)]"
                                : legal ? " bg-[var(--line-strong)]" : " bg-[var(--line)]")
                            }
                            aria-hidden="true"
                          />
                        </button>
                      );
                    }

                    const boxRow = (gridRow - 1) / 2;
                    const boxColumn = (gridColumn - 1) / 2;
                    const owner = snapshot.boxes[boxRow][boxColumn];
                    return (
                      <span
                        key={`${gridRow}-${gridColumn}`}
                        role="gridcell"
                        aria-label={t("Box row {row}, column {column}: {owner}", {
                          row: boxRow + 1,
                          column: boxColumn + 1,
                          owner: owner ? t(owner === "R" ? "Red player" : "Blue player") : t("unclaimed"),
                        })}
                        className={
                          "grid aspect-square min-h-11 min-w-11 place-items-center border text-lg font-black " +
                          (owner === "R"
                            ? "border-[var(--accent)] bg-[var(--accent)] text-[var(--background)]"
                            : owner === "B"
                              ? "border-[var(--foreground)] bg-[var(--foreground)] text-[var(--background)]"
                              : "border-transparent bg-[var(--background)] text-[var(--muted)]")
                        }
                      >
                        {owner || "·"}
                      </span>
                    );
                  }),
                )}
              </div>
            </div>
          </div>

          <aside className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">
              {t("[ EDGE TELEMETRY ]")}
            </p>
            <p className="mt-5 text-4xl font-black uppercase tracking-[-0.05em]">
              {t(snapshot.winnerId
                ? snapshot.winnerId === userId ? "You claimed the grid" : "Rival claimed the grid"
                : snapshot.draw ? "Box grid draw"
                : yourTurn ? "Draw an edge" : "Opponent turn")}
            </p>

            <div className="mt-7 grid grid-cols-2 gap-px border border-[var(--line)] bg-[var(--line)]">
              <ScorePanel label={t("Red")} score={snapshot.scores[0]} accent />
              <ScorePanel label={t("Blue")} score={snapshot.scores[1]} alignRight />
            </div>

            <dl className="mt-px grid gap-px border border-[var(--line)] bg-[var(--line)] font-telemetry text-[8px]">
              <TelemetryRow label={t("Your box")} value={ownBox || "—"} />
              <TelemetryRow label={t("Legal edges")} value={String(snapshot.legalMoves.length)} />
              <TelemetryRow label={t("Claimed boxes")} value={`${snapshot.scores[0] + snapshot.scores[1]}/16`} />
              <TelemetryRow label={t("Server sequence")} value={String(snapshot.sequence)} />
            </dl>
            <p className="mt-6 flex gap-3 text-xs leading-5 text-[var(--muted)]">
              <ScanLine size={15} className="mt-0.5 shrink-0 text-[var(--accent)]" aria-hidden="true" />
              {t("Close a box to claim it and keep the turn. Every edge and score is owned by the server.")}
            </p>
            <CircleDotDashed size={20} className="mt-5 text-[var(--muted)]" aria-hidden="true" />
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
