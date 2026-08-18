import { playerPreferencesSnapshot } from "@/stores/player-preferences-store";

let audioContext: AudioContext | null = null;

export function playFeedback(kind: "move" | "typing" | "success" | "error" = "move") {
  const preferences = playerPreferencesSnapshot();
  if (kind !== "typing" && preferences.hapticsEnabled && "vibrate" in navigator) {
    navigator.vibrate(kind === "success" ? [20, 35, 45] : kind === "error" ? [45, 30, 45] : 12);
  }
  if (!preferences.soundEnabled || preferences.soundVolume <= 0) return;
  try {
    audioContext ??= new AudioContext();
    const oscillator = audioContext.createOscillator();
    const gain = audioContext.createGain();
    const now = audioContext.currentTime;
    oscillator.type = kind === "error" ? "sawtooth" : "square";
    oscillator.frequency.setValueAtTime(kind === "success" ? 660 : kind === "error" ? 180 : kind === "typing" ? 440 : 360, now);
    if (kind === "success") oscillator.frequency.exponentialRampToValueAtTime(990, now + 0.12);
    gain.gain.setValueAtTime(Math.max(0.001, preferences.soundVolume * 0.08), now);
    const short = kind === "move" || kind === "typing";
    gain.gain.exponentialRampToValueAtTime(0.001, now + (short ? 0.05 : 0.18));
    oscillator.connect(gain);
    gain.connect(audioContext.destination);
    oscillator.start(now);
    oscillator.stop(now + (short ? 0.06 : 0.2));
  } catch {
    // Audio feedback is progressive enhancement; gameplay must never depend on it.
  }
}
