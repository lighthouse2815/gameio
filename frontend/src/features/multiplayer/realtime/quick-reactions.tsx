"use client";

import { useEffect, useState } from "react";
import { MessageCircle } from "lucide-react";
import type { RealtimeGameController } from "@/features/multiplayer/realtime/use-realtime-game";
import { gameSocketClient } from "@/lib/socket/game-socket-client";
import { useI18n } from "@/lib/i18n/use-i18n";

type Reaction = "GG" | "NICE" | "WOW" | "REMATCH";

type ReactionMessage = {
  id: string;
  username: string;
  reaction: Reaction;
};

const REACTIONS: Reaction[] = ["GG", "NICE", "WOW", "REMATCH"];

export function QuickReactions({
  controller,
}: {
  controller: RealtimeGameController;
}) {
  const { t } = useI18n();
  const [messages, setMessages] = useState<ReactionMessage[]>([]);

  useEffect(() => {
    const timers = new Set<number>();
    const unsubscribe = gameSocketClient.subscribeGameState((event) => {
      if (event.type !== "ROOM_REACTION" || event.roomId !== controller.roomId) {
        return;
      }
      const payload = event.payload as Partial<ReactionMessage> | null;
      if (
        !payload?.username ||
        !payload.reaction ||
        !REACTIONS.includes(payload.reaction)
      ) {
        return;
      }
      const id = event.requestId ?? `${Date.now()}-${Math.random()}`;
      setMessages((current) => [
        ...current.filter((message) => message.id !== id).slice(-3),
        { id, username: payload.username!, reaction: payload.reaction! },
      ]);
      const timer = window.setTimeout(() => {
        timers.delete(timer);
        setMessages((current) => current.filter((message) => message.id !== id));
      }, 3_200);
      timers.add(timer);
    });
    return () => {
      unsubscribe();
      timers.forEach((timer) => window.clearTimeout(timer));
    };
  }, [controller.roomId]);

  return (
    <>
      <div
        className="pointer-events-none absolute right-3 top-3 z-20 grid max-w-[min(300px,80%)] gap-2"
        aria-live="polite"
        aria-atomic="false"
      >
        {messages.map((message) => (
          <p
            key={message.id}
            className="font-telemetry border border-[var(--accent)] bg-[var(--background)] px-3 py-2 text-[9px] shadow-[4px_4px_0_var(--accent)]"
          >
            <span className="text-[var(--muted)]">{message.username}</span>
            {" / "}
            <strong className="text-[var(--accent)]">{message.reaction}</strong>
          </p>
        ))}
      </div>

      <div className="flex flex-wrap items-center gap-2 border-t border-[var(--line)] bg-[var(--background)] p-3">
        <span className="font-telemetry mr-1 flex items-center gap-2 text-[8px] text-[var(--muted)]">
          <MessageCircle size={12} aria-hidden="true" />
          {t("Quick reaction")}
        </span>
        {REACTIONS.map((reaction) => (
          <button
            type="button"
            key={reaction}
            disabled={controller.state.connection !== "connected"}
            onClick={() => controller.react(reaction)}
            className="font-telemetry min-h-9 border border-[var(--line)] px-3 text-[8px] hover:border-[var(--accent)] hover:text-[var(--accent)] disabled:cursor-not-allowed disabled:opacity-40"
          >
            {reaction}
          </button>
        ))}
      </div>
    </>
  );
}
