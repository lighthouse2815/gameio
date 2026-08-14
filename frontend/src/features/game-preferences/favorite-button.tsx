"use client";

import { Heart } from "lucide-react";
import { useSession } from "@/features/auth/hooks";
import { gamePreferencesApi } from "@/features/game-preferences/api";
import { useToast } from "@/components/ui/toast";
import { useI18n } from "@/lib/i18n/use-i18n";
import { cn } from "@/lib/cn";
import { useGamePreferencesStore } from "@/stores/game-preferences-store";

export function FavoriteButton({
  gameId,
  gameSlug,
  className,
  showLabel = false,
}: {
  gameId: string;
  gameSlug: string;
  className?: string;
  showLabel?: boolean;
}) {
  const session = useSession();
  const favorite = useGamePreferencesStore(
    (state) => state.records[gameId]?.favorite ?? false,
  );
  const setFavorite = useGamePreferencesStore((state) => state.setFavorite);
  const toast = useToast();
  const { t } = useI18n();

  async function toggle() {
    const next = !favorite;
    setFavorite(gameId, gameSlug, next);
    if (!session.data) return;
    try {
      await gamePreferencesApi.favorite(gameId, next);
    } catch {
      setFavorite(gameId, gameSlug, favorite);
      toast({
        title: t("Favorite sync failed"),
        description: t("The local selection was restored. Check the connection and try again."),
        tone: "error",
      });
    }
  }

  return (
    <button
      type="button"
      onClick={() => void toggle()}
      className={cn(
        "font-telemetry inline-flex min-h-10 items-center justify-center gap-2 border border-[var(--line)] bg-[var(--background)] px-3 text-[9px] hover:border-[var(--accent)]",
        favorite && "border-[var(--accent)] text-[var(--accent)]",
        className,
      )}
      aria-pressed={favorite}
      aria-label={t(favorite ? "Remove from favorites" : "Add to favorites")}
    >
      <Heart size={14} fill={favorite ? "currentColor" : "none"} aria-hidden="true" />
      {showLabel ? t(favorite ? "Favorited" : "Favorite") : null}
    </button>
  );
}
