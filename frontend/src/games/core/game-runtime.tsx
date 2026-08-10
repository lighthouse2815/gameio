"use client";

import Link from "next/link";
import dynamic from "next/dynamic";
import { Radio, ShieldCheck } from "lucide-react";
import { buttonStyles } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/states";
import { getRegisteredGame } from "@/games/core/game-registry";

const TicTacToeGame = dynamic(
  () => import("@/games/tictactoe/tic-tac-toe-game"),
  { ssr: false },
);
const CaroGame = dynamic(() => import("@/games/caro/caro-game"), {
  ssr: false,
});
const TankBattle = dynamic(() => import("@/games/tank/tank-battle"), {
  ssr: false,
});

export function GameRuntime({
  slug,
  roomId,
}: {
  slug: string;
  roomId?: string;
}) {
  const registration = getRegisteredGame(slug);
  if (!registration) {
    return (
      <EmptyState
        title="Web engine not installed"
        description="This catalog record exists, but the current frontend build has no registered renderer for it."
      />
    );
  }
  if (registration.engine === "server-multiplayer") {
    if (roomId) {
      if (slug === "tic-tac-toe") {
        return <TicTacToeGame roomId={roomId} />;
      }
      if (slug === "caro") {
        return <CaroGame roomId={roomId} />;
      }
      if (slug === "tank-battle") {
        return <TankBattle roomId={roomId} />;
      }
    }
    return (
      <div className="grid min-h-96 place-items-center border border-[var(--line-strong)] bg-[var(--background)] p-6 text-center">
        <div className="max-w-xl">
          <Radio
            size={38}
            className="mx-auto text-[var(--accent)]"
            aria-hidden="true"
          />
          <p className="font-telemetry mt-6 text-[9px] text-[var(--muted)]">
            [ AUTHORITATIVE MULTIPLAYER ]
          </p>
          <h3 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em]">
            Room required
          </h3>
          <p className="mt-4 text-sm leading-6 text-[var(--muted)]">
            This game starts from a server-created room or matchmaking result.
            Open the room link carrying its UUID; no local simulation is shown
            before validated membership exists.
          </p>
          <Link
            href={"/multiplayer?game=" + encodeURIComponent(slug)}
            className={buttonStyles("primary") + " mt-7"}
          >
            <ShieldCheck size={14} aria-hidden="true" />
            Open multiplayer lobby
          </Link>
        </div>
      </div>
    );
  }
  const Engine = registration.component;
  return Engine ? <Engine /> : null;
}
