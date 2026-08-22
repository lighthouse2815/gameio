"use client";

import Link from "next/link";
import dynamic from "next/dynamic";
import { Radio, ShieldCheck } from "lucide-react";
import type { ComponentType } from "react";
import { buttonStyles } from "@/components/ui/button";
import { EmptyState } from "@/components/ui/states";
import { getRegisteredGame } from "@/games/core/game-registry";
import { useI18n } from "@/lib/i18n/use-i18n";

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
const TypingRaceOnline = dynamic(
  () => import("@/games/typing-race/typing-race-online"),
  { ssr: false },
);
const ConnectFourGame = dynamic(
  () => import("@/games/connect-four/connect-four-game"),
  { ssr: false },
);
const ReversiGame = dynamic(() => import("@/games/reversi/reversi-game"), {
  ssr: false,
});
const RockPaperScissorsGame = dynamic(
  () => import("@/games/rock-paper-scissors/rock-paper-scissors-game"),
  { ssr: false },
);
const UltimateTicTacToeGame = dynamic(
  () => import("@/games/ultimate-tic-tac-toe/ultimate-tic-tac-toe-game"),
  { ssr: false },
);
const DotsAndBoxesGame = dynamic(
  () => import("@/games/dots-and-boxes/dots-and-boxes-game"),
  { ssr: false },
);
const MancalaGame = dynamic(() => import("@/games/mancala/mancala-game"), {
  ssr: false,
});
const HexGame = dynamic(() => import("@/games/hex/hex-game"), {
  ssr: false,
});
const SosGame = dynamic(() => import("@/games/sos/sos-game"), {
  ssr: false,
});

type ServerGameProps = {
  roomId: string;
  spectator?: boolean;
};

const SERVER_GAME_RENDERERS: Readonly<
  Record<string, ComponentType<ServerGameProps>>
> = {
  "tic-tac-toe": TicTacToeGame,
  caro: CaroGame,
  "tank-battle": TankBattle,
  "connect-four": ConnectFourGame,
  reversi: ReversiGame,
  "rock-paper-scissors": RockPaperScissorsGame,
  "ultimate-tic-tac-toe": UltimateTicTacToeGame,
  "dots-and-boxes": DotsAndBoxesGame,
  mancala: MancalaGame,
  hex: HexGame,
  sos: SosGame,
};

export function GameRuntime({
  slug,
  roomId,
  spectator = false,
}: {
  slug: string;
  roomId?: string;
  spectator?: boolean;
}) {
  const { t } = useI18n();
  const registration = getRegisteredGame(slug);
  if (!registration) {
    return (
      <EmptyState
        title={t("Web engine not installed")}
        description={t("This catalog record exists, but the current frontend build has no registered renderer for it.")}
      />
    );
  }
  if (registration.engine === "hybrid" && roomId && slug === "typing-race") {
    return <TypingRaceOnline roomId={roomId} spectator={spectator} />;
  }
  if (registration.engine === "server-multiplayer") {
    if (roomId) {
      const ServerGame = SERVER_GAME_RENDERERS[slug];
      if (ServerGame) {
        return <ServerGame roomId={roomId} spectator={spectator} />;
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
            {t("[ AUTHORITATIVE MULTIPLAYER ]")}
          </p>
          <h3 className="mt-3 text-4xl font-black uppercase tracking-[-0.055em]">
            {t("Room required")}
          </h3>
          <p className="mt-4 text-sm leading-6 text-[var(--muted)]">
            {t("This game starts from a server-created room or matchmaking result. Open the room link carrying its UUID; no local simulation is shown before validated membership exists.")}
          </p>
          <Link
            href={"/multiplayer?game=" + encodeURIComponent(slug)}
            className={buttonStyles("primary") + " mt-7"}
          >
            <ShieldCheck size={14} aria-hidden="true" />
            {t("Open multiplayer lobby")}
          </Link>
        </div>
      </div>
    );
  }
  const Engine = registration.component;
  return Engine ? <Engine /> : null;
}
