"use client";

import { useEffect, useRef } from "react";
import { useSession } from "@/features/auth/hooks";
import { gamePreferencesApi } from "@/features/game-preferences/api";
import { useGamePreferencesStore } from "@/stores/game-preferences-store";

export function GamePreferenceTracker({ gameId, gameSlug }: { gameId: string; gameSlug: string }) {
  const session = useSession();
  const markPlayed = useGamePreferencesStore((state) => state.markPlayed);
  const tracked = useRef<string | null>(null);

  useEffect(() => {
    if (tracked.current === gameId) return;
    tracked.current = gameId;
    markPlayed(gameId, gameSlug);
    if (session.data) void gamePreferencesApi.played(gameId).catch(() => undefined);
  }, [gameId, gameSlug, markPlayed, session.data]);

  return null;
}
