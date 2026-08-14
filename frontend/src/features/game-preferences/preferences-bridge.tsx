"use client";

import { useEffect, useRef } from "react";
import { useQuery } from "@tanstack/react-query";
import { useSession } from "@/features/auth/hooks";
import { gamePreferencesApi } from "@/features/game-preferences/api";
import { useGamePreferencesStore } from "@/stores/game-preferences-store";

export function PreferencesBridge() {
  const session = useSession();
  const hydrate = useGamePreferencesStore((state) => state.hydrate);
  const hydrated = useGamePreferencesStore((state) => state.hydrated);
  const mergeServer = useGamePreferencesStore((state) => state.mergeServer);
  const syncedUser = useRef<string | null>(null);
  const preferences = useQuery({
    queryKey: ["game-preferences", session.data?.id],
    queryFn: gamePreferencesApi.list,
    enabled: Boolean(session.data && hydrated),
    staleTime: 60_000,
  });

  useEffect(() => {
    hydrate();
  }, [hydrate]);

  useEffect(() => {
    const userId = session.data?.id;
    if (!userId || !preferences.data || syncedUser.current === userId) return;
    syncedUser.current = userId;
    const local = Object.values(useGamePreferencesStore.getState().records);
    const remote = new Map(preferences.data.map((record) => [record.gameId, record]));
    const writes: Promise<unknown>[] = [];
    local.forEach((record) => {
      const server = remote.get(record.gameId);
      if (record.localOnly && record.favorite && !server?.favorite) {
        writes.push(gamePreferencesApi.favorite(record.gameId, true));
      }
      if (
        record.localOnly &&
        record.lastPlayedAt &&
        (!server?.lastPlayedAt || record.lastPlayedAt > server.lastPlayedAt)
      ) {
        writes.push(gamePreferencesApi.played(record.gameId));
      }
    });
    mergeServer(preferences.data);
    if (writes.length) {
      void Promise.allSettled(writes).then(() => preferences.refetch());
    }
  }, [mergeServer, preferences, session.data?.id]);

  useEffect(() => {
    if (preferences.data) mergeServer(preferences.data);
  }, [mergeServer, preferences.data]);

  return null;
}
