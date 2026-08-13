export type TileAppearance = {
  backgroundColor: string;
  color: string;
};

const TILE_APPEARANCES: Record<number, TileAppearance> = {
  2: { backgroundColor: "#f5e9d7", color: "#191714" },
  4: { backgroundColor: "#e4cfad", color: "#191714" },
  8: { backgroundColor: "#f3b13f", color: "#211a0c" },
  16: { backgroundColor: "#b84a0e", color: "#ffffff" },
  32: { backgroundColor: "#c9342e", color: "#ffffff" },
  64: { backgroundColor: "#981b2e", color: "#ffffff" },
  128: { backgroundColor: "#e0b400", color: "#1a1708" },
  256: { backgroundColor: "#7bae23", color: "#13200a" },
  512: { backgroundColor: "#0b6b5e", color: "#ffffff" },
  1024: { backgroundColor: "#1769aa", color: "#ffffff" },
  2048: { backgroundColor: "#5f3dc4", color: "#ffffff" },
  4096: { backgroundColor: "#8c1d6b", color: "#ffffff" },
  8192: { backgroundColor: "#4c1d95", color: "#ffffff" },
  16384: { backgroundColor: "#111827", color: "#ffffff" },
};

export function getTileAppearance(value: number): TileAppearance {
  if (value === 0) {
    return {
      backgroundColor: "var(--background)",
      color: "transparent",
    };
  }

  const configuredAppearance = TILE_APPEARANCES[value];
  if (configuredAppearance) return configuredAppearance;

  const exponent = Math.max(1, Math.round(Math.log2(value)));
  const hue = (211 + exponent * 37) % 360;
  return {
    backgroundColor: `hsl(${hue} 72% 25%)`,
    color: "#ffffff",
  };
}
