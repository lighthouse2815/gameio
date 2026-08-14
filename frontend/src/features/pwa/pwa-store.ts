"use client";

import { create } from "zustand";

type InstallPromptEvent = Event & {
  prompt: () => Promise<void>;
  userChoice: Promise<{ outcome: "accepted" | "dismissed" }>;
};

type PwaState = {
  installPrompt: InstallPromptEvent | null;
  setInstallPrompt: (event: InstallPromptEvent | null) => void;
  install: () => Promise<"accepted" | "dismissed" | "unavailable">;
};

export const usePwaStore = create<PwaState>((set, get) => ({
  installPrompt: null,
  setInstallPrompt: (installPrompt) => set({ installPrompt }),
  install: async () => {
    const event = get().installPrompt;
    if (!event) return "unavailable";
    await event.prompt();
    const choice = await event.userChoice;
    set({ installPrompt: null });
    return choice.outcome;
  },
}));

export type { InstallPromptEvent };
