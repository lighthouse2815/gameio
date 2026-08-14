"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { Radio, X } from "lucide-react";
import { Button } from "@/components/ui/button";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import type { GameRoom } from "@/features/multiplayer/types";
import { gameSocketClient } from "@/lib/socket/game-socket-client";
import { useI18n } from "@/lib/i18n/use-i18n";

type GameInvite = {
  inviteId: string;
  roomId: string;
  roomCode: string;
  gameId: string;
  gameSlug: string;
  gameName: string;
  senderUsername: string;
  recipientId: string;
  expiresAt: string;
};

type InviteStatus = {
  inviteId: string;
  username: string;
  room?: GameRoom | null;
};

export function RealtimeNotifications() {
  const { t } = useI18n();
  const session = useSession();
  const toast = useToast();
  const router = useRouter();
  const [invites, setInvites] = useState<GameInvite[]>([]);
  const pendingAcceptance = useRef<string | null>(null);

  useEffect(() => {
    if (!session.data) {
      return;
    }
    const unsubscribe = gameSocketClient.subscribeGameState((event) => {
      if (event.type === "GAME_INVITE") {
        const invite = event.payload as GameInvite;
        if (!invite?.inviteId || !invite.roomId || !invite.gameSlug) return;
        setInvites((current) => [
          ...current.filter((item) => item.inviteId !== invite.inviteId),
          invite,
        ]);
        return;
      }
      if (event.type === "GAME_INVITE_SENT") {
        toast({
          title: t("Game invite sent"),
          description: t("The invite remains valid for 60 seconds."),
          tone: "success",
        });
        return;
      }
      if (
        event.type === "GAME_INVITE_ACCEPTED" ||
        event.type === "GAME_INVITE_DECLINED"
      ) {
        const status = event.payload as InviteStatus;
        setInvites((current) =>
          current.filter((invite) => invite.inviteId !== status.inviteId),
        );
        if (event.type === "GAME_INVITE_DECLINED") {
          toast({
            title: t("Game invite declined"),
            description: status.username,
            tone: "info",
          });
          return;
        }
        toast({
          title: t("Game invite accepted"),
          description: status.username,
          tone: "success",
        });
        if (
          pendingAcceptance.current === status.inviteId &&
          status.room
        ) {
          pendingAcceptance.current = null;
          router.push(
            "/game/" +
              encodeURIComponent(status.room.gameSlug) +
              "?room=" +
              encodeURIComponent(status.room.roomId),
          );
        }
      }
    });
    gameSocketClient.connect();
    return unsubscribe;
  }, [router, session.data, t, toast]);

  useEffect(() => {
    const timer = window.setInterval(() => {
      const now = Date.now();
      setInvites((current) =>
        current.filter((invite) => Date.parse(invite.expiresAt) > now),
      );
    }, 1_000);
    return () => window.clearInterval(timer);
  }, []);

  function accept(invite: GameInvite) {
    try {
      pendingAcceptance.current = invite.inviteId;
      gameSocketClient.acceptGameInvite(invite.inviteId, invite.roomId);
    } catch {
      pendingAcceptance.current = null;
      gameSocketClient.connect(true);
      toast({
        title: t("Invite connection unavailable"),
        description: t("Reconnect and accept the invite again."),
        tone: "error",
      });
    }
  }

  function decline(inviteId: string) {
    try {
      gameSocketClient.declineGameInvite(inviteId);
      setInvites((current) =>
        current.filter((invite) => invite.inviteId !== inviteId),
      );
    } catch {
      gameSocketClient.connect(true);
    }
  }

  const visibleInvites = invites.filter(
    (invite) => invite.recipientId === session.data?.id,
  );
  if (!visibleInvites.length) return null;

  return (
    <aside
      className="fixed right-4 top-20 z-[90] grid w-[min(410px,calc(100vw-2rem))] gap-2"
      aria-label={t("Game invitations")}
    >
      {visibleInvites.map((invite) => (
        <article
          className="surface-shadow border border-[var(--accent)] bg-[var(--surface)] p-4"
          key={invite.inviteId}
        >
          <div className="flex items-start justify-between gap-4">
            <div className="flex gap-3">
              <Radio className="mt-0.5 shrink-0 text-[var(--accent)]" size={18} aria-hidden="true" />
              <div>
                <p className="font-telemetry text-[9px] text-[var(--accent)]">
                  {t("GAME INVITE")} / {invite.roomCode}
                </p>
                <h2 className="mt-1 font-black uppercase">
                  {t(invite.gameName)}
                </h2>
                <p className="mt-1 text-xs text-[var(--muted)]">
                  {t("{username} invited you to play.", {
                    username: invite.senderUsername,
                  })}
                </p>
              </div>
            </div>
            <button
              type="button"
              aria-label={t("Decline game invite")}
              onClick={() => decline(invite.inviteId)}
            >
              <X size={15} aria-hidden="true" />
            </button>
          </div>
          <div className="mt-4 grid grid-cols-2 gap-2">
            <Button compact onClick={() => accept(invite)}>
              {t("Accept and join")}
            </Button>
            <Button compact variant="ghost" onClick={() => decline(invite.inviteId)}>
              {t("Decline")}
            </Button>
          </div>
        </article>
      ))}
    </aside>
  );
}
