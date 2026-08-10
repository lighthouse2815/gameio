import { describe, expect, it } from "vitest";
import type { TankSnapshot } from "@/features/multiplayer/realtime/types";
import {
  createTankInput,
  nextTankInputSequence,
  tankActionForKey,
} from "@/games/tank/input";

const snapshot: TankSnapshot = {
  sequence: 20,
  width: 100,
  height: 100,
  tanks: [
    {
      userId: "user-1",
      x: 10,
      y: 12,
      rotation: 0,
      hp: 100,
      alive: true,
      kills: 0,
      lastInputSequence: 8,
    },
  ],
  bullets: [],
  winnerId: null,
  draw: false,
};

describe("Tank Battle input protocol", () => {
  it("advances monotonically from the greater local or acknowledged sequence", () => {
    expect(nextTankInputSequence(4, snapshot, "user-1")).toBe(9);
    expect(nextTankInputSequence(12, snapshot, "user-1")).toBe(13);
  });

  it("emits actions without client-authoritative state", () => {
    expect(createTankInput("MOVE_RIGHT", 14)).toEqual({
      action: "MOVE_RIGHT",
      sequence: 14,
    });
    expect(createTankInput("SHOOT", 15)).not.toHaveProperty("x");
    expect(createTankInput("SHOOT", 15)).not.toHaveProperty("hp");
  });

  it("maps keyboard controls to the server action vocabulary", () => {
    expect(tankActionForKey("ArrowUp")).toBe("MOVE_UP");
    expect(tankActionForKey("d")).toBe("MOVE_RIGHT");
    expect(tankActionForKey(" ")).toBe("SHOOT");
    expect(tankActionForKey("q")).toBeNull();
  });
});
