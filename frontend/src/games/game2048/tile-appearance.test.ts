import { describe, expect, it } from "vitest";
import { getTileAppearance } from "@/games/game2048/tile-appearance";

const TILE_VALUES = [
  2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384,
];

function relativeLuminance(hex: string) {
  const channels = hex
    .match(/[a-f\d]{2}/gi)!
    .map((channel) => Number.parseInt(channel, 16) / 255)
    .map((channel) =>
      channel <= 0.04045
        ? channel / 12.92
        : ((channel + 0.055) / 1.055) ** 2.4,
    );
  return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastRatio(background: string, foreground: string) {
  const backgroundLuminance = relativeLuminance(background);
  const foregroundLuminance = relativeLuminance(foreground);
  return (
    (Math.max(backgroundLuminance, foregroundLuminance) + 0.05) /
    (Math.min(backgroundLuminance, foregroundLuminance) + 0.05)
  );
}

describe("2048 tile appearance", () => {
  it("gives every playable power of two a distinct background", () => {
    const playableValues = Array.from(
      { length: 52 },
      (_, index) => 2 ** (index + 1),
    );
    const backgrounds = playableValues.map(
      (value) => getTileAppearance(value).backgroundColor,
    );

    expect(new Set(backgrounds).size).toBe(playableValues.length);
  });

  it.each(TILE_VALUES)("keeps %i readable at WCAG AA contrast", (value) => {
    const appearance = getTileAppearance(value);

    expect(
      contrastRatio(appearance.backgroundColor, appearance.color),
    ).toBeGreaterThanOrEqual(4.5);
  });

  it("keeps empty cells neutral and higher values visually distinct", () => {
    expect(getTileAppearance(0)).toEqual({
      backgroundColor: "var(--background)",
      color: "transparent",
    });
    expect(getTileAppearance(32768)).not.toEqual(getTileAppearance(65536));
  });
});
