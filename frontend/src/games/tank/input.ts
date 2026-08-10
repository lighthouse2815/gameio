import type {
  TankSnapshot,
} from "@/features/multiplayer/realtime/types";
import type {
  GameInputPayload,
} from "@/lib/socket/game-socket-client";

export type TankAction =
  | "MOVE_UP"
  | "MOVE_DOWN"
  | "MOVE_LEFT"
  | "MOVE_RIGHT"
  | "STOP"
  | "SHOOT";

export function isTankSnapshot(snapshot: unknown): snapshot is TankSnapshot {
  return Boolean(
    typeof snapshot === "object" &&
      snapshot !== null &&
      "tanks" in snapshot &&
      Array.isArray(snapshot.tanks) &&
      "bullets" in snapshot &&
      Array.isArray(snapshot.bullets) &&
      "width" in snapshot &&
      typeof snapshot.width === "number" &&
      "height" in snapshot &&
      typeof snapshot.height === "number",
  );
}

export function nextTankInputSequence(
  localSequence: number,
  snapshot: TankSnapshot | null,
  userId: string | undefined,
) {
  const serverSequence =
    snapshot?.tanks.find((tank) => tank.userId === userId)
      ?.lastInputSequence ?? -1;
  return Math.max(localSequence, serverSequence) + 1;
}

export function createTankInput(
  action: TankAction,
  sequence: number,
): GameInputPayload {
  return { action, sequence };
}

export function tankActionForKey(key: string): TankAction | null {
  const normalized = key.toLowerCase();
  if (normalized === "arrowup" || normalized === "w") return "MOVE_UP";
  if (normalized === "arrowdown" || normalized === "s") return "MOVE_DOWN";
  if (normalized === "arrowleft" || normalized === "a") return "MOVE_LEFT";
  if (normalized === "arrowright" || normalized === "d") return "MOVE_RIGHT";
  if (normalized === " " || normalized === "enter") return "SHOOT";
  return null;
}
