"use client";

import { create } from "zustand";

export type ThemeMode = "dark" | "light";

type ThemeState = {
  mode: ThemeMode;
  hydrated: boolean;
  hydrate: () => void;
  setMode: (mode: ThemeMode) => void;
  toggle: () => void;
};

const STORAGE_KEY = "gameio.theme";

function applyTheme(mode: ThemeMode) {
  if (typeof document !== "undefined") {
    document.documentElement.dataset.theme = mode;
    document.documentElement.style.colorScheme = mode;
  }
}

export const useThemeStore = create<ThemeState>((set, get) => ({
  mode: "dark",
  hydrated: false,
  hydrate: () => {
    const stored =
      typeof window !== "undefined"
        ? window.localStorage.getItem(STORAGE_KEY)
        : null;
    const mode: ThemeMode = stored === "light" ? "light" : "dark";
    applyTheme(mode);
    set({ mode, hydrated: true });
  },
  setMode: (mode) => {
    window.localStorage.setItem(STORAGE_KEY, mode);
    applyTheme(mode);
    set({ mode });
  },
  toggle: () => {
    get().setMode(get().mode === "dark" ? "light" : "dark");
  },
}));
