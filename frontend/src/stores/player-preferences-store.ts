"use client";

import { create } from "zustand";

export type ColorVisionMode = "standard" | "protanopia" | "deuteranopia" | "tritanopia";
export type TargetFps = "auto" | 30 | 60 | 120;
export type KeyPreset = "both" | "arrows" | "wasd";

export type PlayerPreferences = {
  soundEnabled: boolean;
  soundVolume: number;
  hapticsEnabled: boolean;
  reducedMotion: boolean;
  highContrast: boolean;
  colorVision: ColorVisionMode;
  largeControls: boolean;
  targetFps: TargetFps;
  showFps: boolean;
  keyPreset: KeyPreset;
};

type PlayerPreferencesState = PlayerPreferences & {
  hydrated: boolean;
  hydrate: () => void;
  update: <Key extends keyof PlayerPreferences>(key: Key, value: PlayerPreferences[Key]) => void;
  reset: () => void;
};

const STORAGE_KEY = "gameio.player-preferences.v1";
const defaults: PlayerPreferences = {
  soundEnabled: true,
  soundVolume: 0.55,
  hapticsEnabled: true,
  reducedMotion: false,
  highContrast: false,
  colorVision: "standard",
  largeControls: false,
  targetFps: "auto",
  showFps: false,
  keyPreset: "both",
};

function sanitize(candidate: Partial<PlayerPreferences>): PlayerPreferences {
  return {
    soundEnabled: typeof candidate.soundEnabled === "boolean" ? candidate.soundEnabled : defaults.soundEnabled,
    soundVolume: typeof candidate.soundVolume === "number" ? Math.max(0, Math.min(1, candidate.soundVolume)) : defaults.soundVolume,
    hapticsEnabled: typeof candidate.hapticsEnabled === "boolean" ? candidate.hapticsEnabled : defaults.hapticsEnabled,
    reducedMotion: typeof candidate.reducedMotion === "boolean" ? candidate.reducedMotion : defaults.reducedMotion,
    highContrast: typeof candidate.highContrast === "boolean" ? candidate.highContrast : defaults.highContrast,
    colorVision: ["standard", "protanopia", "deuteranopia", "tritanopia"].includes(candidate.colorVision ?? "") ? candidate.colorVision! : defaults.colorVision,
    largeControls: typeof candidate.largeControls === "boolean" ? candidate.largeControls : defaults.largeControls,
    targetFps: ["auto", 30, 60, 120].includes(candidate.targetFps ?? "") ? candidate.targetFps! : defaults.targetFps,
    showFps: typeof candidate.showFps === "boolean" ? candidate.showFps : defaults.showFps,
    keyPreset: ["both", "arrows", "wasd"].includes(candidate.keyPreset ?? "") ? candidate.keyPreset! : defaults.keyPreset,
  };
}

function apply(preferences: PlayerPreferences) {
  if (typeof document === "undefined") return;
  const root = document.documentElement;
  root.dataset.motion = preferences.reducedMotion ? "reduced" : "full";
  root.dataset.contrast = preferences.highContrast ? "high" : "standard";
  root.dataset.colorVision = preferences.colorVision;
  root.dataset.controls = preferences.largeControls ? "large" : "standard";
}

function save(preferences: PlayerPreferences) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(preferences));
  }
}

export const usePlayerPreferencesStore = create<PlayerPreferencesState>((set, get) => ({
  ...defaults,
  hydrated: false,
  hydrate: () => {
    let preferences = defaults;
    if (typeof window !== "undefined") {
      try {
        preferences = sanitize(JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}") as Partial<PlayerPreferences>);
      } catch {
        preferences = defaults;
      }
    }
    apply(preferences);
    set({ ...preferences, hydrated: true });
  },
  update: (key, value) => {
    const preferences = sanitize({ ...get(), [key]: value });
    save(preferences);
    apply(preferences);
    set(preferences);
  },
  reset: () => {
    save(defaults);
    apply(defaults);
    set(defaults);
  },
}));

export function playerPreferencesSnapshot(): PlayerPreferences {
  return sanitize(usePlayerPreferencesStore.getState());
}
