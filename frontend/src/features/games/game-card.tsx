"use client";

import Link from "next/link";
import { ArrowUpRight, Users } from "lucide-react";
import type { GameSummary } from "@/features/games/types";
import { getGameArtwork } from "@/games/core/artwork";
import { useI18n } from "@/lib/i18n/use-i18n";
import { FavoriteButton } from "@/features/game-preferences/favorite-button";

type GameCardProps = {
  game: GameSummary;
  index?: number;
  priority?: boolean;
};

function gameTypeLabel(type: GameSummary["gameType"]) {
  if (type === "SINGLE_PLAYER") return "Solo";
  if (type === "TURN_BASED_MULTIPLAYER") return "Turn / PVP";
  return "Realtime / PVP";
}

export function GameCard({ game, index = 0, priority = false }: GameCardProps) {
  const { t, formatNumber } = useI18n();
  const artwork = getGameArtwork(game.slug, game.thumbnailUrl);
  return (
    <article className="group relative flex min-h-[300px] flex-col overflow-hidden bg-[var(--surface)]">
      <FavoriteButton
        gameId={game.id}
        gameSlug={game.slug}
        className="absolute right-3 top-3 z-10 h-9 min-h-9 w-9 bg-black/80 px-0 text-white"
      />
      <Link
        href={"/game/" + encodeURIComponent(game.slug)}
        className="flex h-full flex-1 flex-col"
        aria-label={t("Open {game}", { game: t(game.name) })}
      >
        <div className="relative h-36 overflow-hidden border-b border-[var(--line)] bg-[var(--surface-strong)]">
          {artwork ? (
            // Dynamic image hosts are controlled by the backend catalog.
            // eslint-disable-next-line @next/next/no-img-element
            <img
              src={artwork}
              alt=""
              loading={priority ? "eager" : "lazy"}
              className="h-full w-full object-cover grayscale transition-[filter,transform] duration-300 group-hover:scale-[1.02] group-hover:grayscale-0"
            />
          ) : (
            <div className="grid h-full grid-cols-[1fr_auto]">
              <span className="flex items-end p-4 text-7xl font-black leading-none tracking-[-0.08em] text-[var(--line-strong)]">
                {String(index + 1).padStart(2, "0")}
              </span>
              <span className="font-telemetry flex w-10 items-center justify-center border-l border-[var(--line)] text-[8px] text-[var(--muted)] [writing-mode:vertical-rl]">
                {t("NO VISUAL SIGNAL")}
              </span>
            </div>
          )}
          <span className="font-telemetry absolute left-3 top-3 bg-[var(--accent)] px-2 py-1 text-[8px] font-bold text-white">
            {t(game.category)}
          </span>
        </div>
        <div className="flex flex-1 flex-col p-4 sm:p-5">
          <div className="flex items-start justify-between gap-5">
            <div>
              <p className="font-telemetry text-[8px] text-[var(--muted)]">
                UNIT / {game.slug.toUpperCase()}
              </p>
              <h3 className="mt-2 text-2xl font-black uppercase tracking-[-0.045em]">
                {t(game.name)}
              </h3>
            </div>
            <ArrowUpRight
              size={18}
              aria-hidden="true"
              className="shrink-0 text-[var(--muted)] transition-colors group-hover:text-[var(--accent)]"
            />
          </div>
          <p className="mt-3 line-clamp-2 text-xs leading-5 text-[var(--muted)]">
            {t(game.description)}
          </p>
          <div className="font-telemetry mt-auto grid grid-cols-2 gap-x-3 gap-y-2 pt-6 text-[8px]">
            <span>{t(gameTypeLabel(game.gameType))}</span>
            <span className="text-right text-[var(--muted)]">
              {formatNumber(game.playsCount)} {t("PLAYS")}
            </span>
            <span className="text-[var(--muted)]">
              <Users size={9} className="mr-1 inline" aria-hidden="true" />
              {game.minPlayers}–{game.maxPlayers} {t("PLAYERS")}
            </span>
            <span
              className={
                "text-right " +
                (game.onlinePlayers > 0
                  ? "status-online"
                  : "text-[var(--muted)]")
              }
            >
              {formatNumber(game.onlinePlayers)} {t("LIVE")}
            </span>
          </div>
        </div>
      </Link>
    </article>
  );
}
