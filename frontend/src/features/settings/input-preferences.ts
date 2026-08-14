import { playerPreferencesSnapshot } from "@/stores/player-preferences-store";

export function movementKeyAllowed(key: string) {
  const normalized = key.toLowerCase();
  const isArrow = normalized.startsWith("arrow");
  const isWasd = ["w", "a", "s", "d"].includes(normalized);
  if (!isArrow && !isWasd) return true;
  const preset = playerPreferencesSnapshot().keyPreset;
  return preset === "both" || (preset === "arrows" ? isArrow : isWasd);
}

export function shouldPresentFrame(time: number, previousPresentation: number) {
  const target = playerPreferencesSnapshot().targetFps;
  if (target === "auto") return true;
  return time - previousPresentation >= 1000 / target - 0.5;
}
