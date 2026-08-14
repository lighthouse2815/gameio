"use client";

import { create } from "zustand";
import type { GamePreference } from "@/features/game-preferences/types";

const STORAGE_KEY = "gameio.game-preferences.v1";
const MAX_RECENT = 12;

type StoredPreference = GamePreference & { localOnly?: boolean };

type GamePreferencesState = {
  hydrated: boolean;
  records: Record<string, StoredPreference>;
  hydrate: () => void;
  mergeServer: (records: GamePreference[]) => void;
  setFavorite: (gameId: string, gameSlug: string, favorite: boolean) => void;
  markPlayed: (gameId: string, gameSlug: string) => void;
};

function save(records: Record<string, StoredPreference>) {
  if (typeof window !== "undefined") {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(records));
  }
}

function trimRecent(records: Record<string, StoredPreference>) {
  const recentIds = Object.values(records)
    .filter((record) => record.lastPlayedAt)
    .sort((left, right) =>
      String(right.lastPlayedAt).localeCompare(String(left.lastPlayedAt)),
    )
    .slice(MAX_RECENT)
    .map((record) => record.gameId);
  if (!recentIds.length) return records;
  const next = { ...records };
  recentIds.forEach((gameId) => {
    if (next[gameId]?.favorite) {
      next[gameId] = { ...next[gameId], lastPlayedAt: null };
    } else {
      delete next[gameId];
    }
  });
  return next;
}

export const useGamePreferencesStore = create<GamePreferencesState>((set) => ({
  hydrated: false,
  records: {},
  hydrate: () => {
    if (typeof window === "undefined") return;
    let records: Record<string, StoredPreference> = {};
    try {
      records = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? "{}") as Record<string, StoredPreference>;
    } catch {
      records = {};
    }
    set({ records, hydrated: true });
  },
  mergeServer: (serverRecords) =>
    set((state) => {
      const merged = { ...state.records };
      serverRecords.forEach((server) => {
        const local = merged[server.gameId];
        if (!local || server.updatedAt >= local.updatedAt || !local.localOnly) {
          merged[server.gameId] = { ...server, localOnly: false };
        }
      });
      save(merged);
      return { records: merged };
    }),
  setFavorite: (gameId, gameSlug, favorite) =>
    set((state) => {
      const now = new Date().toISOString();
      const records = {
        ...state.records,
        [gameId]: {
          gameId,
          gameSlug,
          lastPlayedAt: state.records[gameId]?.lastPlayedAt ?? null,
          favorite,
          updatedAt: now,
          localOnly: true,
        },
      };
      save(records);
      return { records };
    }),
  markPlayed: (gameId, gameSlug) =>
    set((state) => {
      const now = new Date().toISOString();
      const records = trimRecent({
        ...state.records,
        [gameId]: {
          gameId,
          gameSlug,
          favorite: state.records[gameId]?.favorite ?? false,
          lastPlayedAt: now,
          updatedAt: now,
          localOnly: true,
        },
      });
      save(records);
      return { records };
    }),
}));

export function favoriteGameIds(records: Record<string, StoredPreference>) {
  return new Set(
    Object.values(records)
      .filter((record) => record.favorite)
      .map((record) => record.gameId),
  );
}

export function recentGameIds(records: Record<string, StoredPreference>) {
  return Object.values(records)
    .filter((record) => record.lastPlayedAt)
    .sort((left, right) =>
      String(right.lastPlayedAt).localeCompare(String(left.lastPlayedAt)),
    )
    .map((record) => record.gameId);
}
