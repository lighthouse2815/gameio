"use client";

import { useEffect, useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Eye, Radio, Send, ShieldCheck, Users } from "lucide-react";
import { LoginRequired } from "@/components/auth/login-required";
import { Button, buttonStyles } from "@/components/ui/button";
import { Field, SelectField } from "@/components/ui/field";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { isUnauthenticated, useSession } from "@/features/auth/hooks";
import { useGames } from "@/features/games/hooks";
import { friendApi } from "@/features/friends/api";
import { multiplayerApi } from "@/features/multiplayer/api";
import type { GameRoom } from "@/features/multiplayer/types";
import { getErrorMessage } from "@/lib/api/api-error";
import { asPage } from "@/types/page";
import {
  gameSocketClient,
  type SocketStatus,
} from "@/lib/socket/game-socket-client";
import { useI18n } from "@/lib/i18n/use-i18n";

export function MultiplayerScreen({ initialGameSlug }: { initialGameSlug: string }) {
  const { t } = useI18n();
  const session = useSession();
  const games = useGames({ page: 0, size: 100 });
  const toast = useToast();
  const queryClient = useQueryClient();
  const onlineGames = useMemo(
    () => games.data?.content.filter((game) => game.gameType !== "SINGLE_PLAYER") ?? [],
    [games.data],
  );
  const initialId =
    onlineGames.find((game) => game.slug === initialGameSlug)?.id ?? "";
  const [selectedGameId, setSelectedGameId] = useState("");
  const effectiveGameId = selectedGameId || initialId || onlineGames[0]?.id || "";
  const selectedGame = onlineGames.find(
    (game) => game.id === effectiveGameId,
  );
  const capacityOptions = useMemo(() => {
    if (!selectedGame) return [2];
    const minimum = Math.max(2, selectedGame.minPlayers);
    const maximum = Math.min(4, selectedGame.maxPlayers);
    return Array.from(
      { length: Math.max(1, maximum - minimum + 1) },
      (_, index) => minimum + index,
    );
  }, [selectedGame]);
  const [roomCode, setRoomCode] = useState("");
  const [maxPlayers, setMaxPlayers] = useState(2);
  const [privateRoom, setPrivateRoom] = useState(false);
  const [activeRoom, setActiveRoom] = useState<GameRoom | null>(null);
  const [ticketId, setTicketId] = useState<string | null>(null);
  const [socketStatus, setSocketStatus] = useState<SocketStatus>(
    gameSocketClient.status,
  );
  const enabled = Boolean(session.data && effectiveGameId);
  const friends = useQuery({
    queryKey: ["friends", "accepted"],
    queryFn: friendApi.list,
    enabled: Boolean(session.data),
  });

  const rooms = useQuery({
    queryKey: ["rooms", effectiveGameId],
    queryFn: () => multiplayerApi.rooms(effectiveGameId),
    select: (response) => asPage(response, 0, 30),
    enabled,
    refetchInterval: 10_000,
  });
  const liveRooms = useQuery({
    queryKey: ["rooms", "live", effectiveGameId],
    queryFn: () => multiplayerApi.rooms(effectiveGameId, "PLAYING"),
    select: (response) => asPage(response, 0, 30),
    enabled,
    refetchInterval: 5_000,
  });
  const currentMatch = useQuery({
    queryKey: ["matchmaking", "current"],
    queryFn: multiplayerApi.currentMatch,
    enabled: Boolean(session.data),
    refetchInterval: (query) =>
      query.state.data?.status === "QUEUED" ? 2_000 : false,
  });
  const activeTicketId = ticketId ?? currentMatch.data?.ticketId ?? null;
  const matchedRoomId = currentMatch.data?.roomId;
  const matchedRoom = useQuery({
    queryKey: ["rooms", "member", matchedRoomId],
    queryFn: () => multiplayerApi.room(matchedRoomId ?? ""),
    enabled: Boolean(session.data && matchedRoomId),
    refetchInterval: 3_000,
  });
  const displayedRoom = activeRoom ?? matchedRoom.data ?? null;
  const displayedRoomId = displayedRoom?.roomId;
  const displayedRoomReady =
    Boolean(displayedRoom && displayedRoom.players.length >= 2) &&
    displayedRoom?.players.every((player) => player.ready && player.connected);

  useEffect(() => {
    if (!session.data) return;
    const unsubscribeStatus = gameSocketClient.subscribeStatus(setSocketStatus);
    const unsubscribeEvents = gameSocketClient.subscribeGameState((event) => {
      if (event.type === "MATCH_FOUND") {
        const room = event.payload as GameRoom;
        setActiveRoom(room);
        queryClient.setQueryData(
          ["matchmaking", "current"],
          (
            current:
              | {
                  ticketId: string;
                  gameId: string;
                  joinedAt: string;
                }
              | undefined,
          ) =>
            current
              ? { ...current, status: "MATCH_FOUND", roomId: room.roomId }
              : current,
        );
      }
      if (event.type === "ROOM_LEFT" && event.roomId) {
        const leftRoomId = event.roomId;
        setActiveRoom((current) =>
          current?.roomId === leftRoomId ? null : current,
        );
        gameSocketClient.clearActiveRoom(leftRoomId);
        queryClient.removeQueries({
          queryKey: ["rooms", "member", leftRoomId],
        });
        queryClient.setQueryData(
          ["matchmaking", "current"],
          (current: { roomId?: string } | null | undefined) =>
            current?.roomId === leftRoomId ? null : current,
        );
      }
      if (
        event.type === "ROOM_STATE" &&
        event.payload &&
        (!displayedRoomId ||
          event.roomId === displayedRoomId)
      ) {
        setActiveRoom(event.payload as GameRoom);
      }
    });
    gameSocketClient.connect();
    return () => {
      unsubscribeEvents();
      unsubscribeStatus();
    };
  }, [displayedRoomId, queryClient, session.data]);

  useEffect(() => {
    if (session.data && displayedRoom?.roomId) {
      gameSocketClient.joinRoom(displayedRoom.roomId);
    }
  }, [displayedRoom?.roomId, session.data]);

  const refreshRooms = () =>
    queryClient.invalidateQueries({ queryKey: ["rooms"] });

  const createRoom = useMutation({
    mutationFn: () =>
      multiplayerApi.createRoom({
        gameId: effectiveGameId,
        maxPlayers: capacityOptions.includes(maxPlayers)
          ? maxPlayers
          : capacityOptions[0],
        privateRoom,
      }),
    onSuccess: (room) => {
      setActiveRoom(room);
      gameSocketClient.joinRoom(room.roomId);
      toast({ title: t("Room allocated"), description: t("Code") + ": " + room.roomCode, tone: "success" });
      void refreshRooms();
    },
    onError: (error) =>
      toast({ title: t("Room creation failed"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const joinRoom = useMutation({
    mutationFn: (code: string) => multiplayerApi.joinRoom(code),
    onSuccess: (room) => {
      setActiveRoom(room);
      gameSocketClient.joinRoom(room.roomId);
      setRoomCode("");
      toast({ title: t("Room joined"), description: t("Server membership validated."), tone: "success" });
      void refreshRooms();
    },
    onError: (error) =>
      toast({ title: t("Join failed"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const quickMatch = useMutation({
    mutationFn: () => multiplayerApi.quickMatch(effectiveGameId),
    onSuccess: (ticket) => {
      setTicketId(ticket.ticketId);
      queryClient.setQueryData(["matchmaking", "current"], ticket);
      if (ticket.roomId) {
        void queryClient.invalidateQueries({
          queryKey: ["rooms", "member", ticket.roomId],
        });
      }
      toast({ title: t("Queue joined"), description: t("Waiting for a server match."), tone: "success" });
    },
    onError: (error) =>
      toast({ title: t("Queue rejected"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const leaveQueue = useMutation({
    mutationFn: multiplayerApi.leaveQueue,
    onSuccess: () => {
      setTicketId(null);
      queryClient.setQueryData(["matchmaking", "current"], null);
    },
    onError: (error) =>
      toast({ title: t("Queue leave failed"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const readyRoom = useMutation({
    mutationFn: (roomId: string) => multiplayerApi.ready(roomId),
    onSuccess: (room) => setActiveRoom(room),
    onError: (error) =>
      toast({ title: t("Ready signal rejected"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const startRoom = useMutation({
    mutationFn: (roomId: string) => multiplayerApi.start(roomId),
    onSuccess: (room) => setActiveRoom(room),
    onError: (error) =>
      toast({ title: t("Start rejected"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const leaveRoom = useMutation({
    mutationFn: (roomId: string) => multiplayerApi.leaveRoom(roomId),
    onSuccess: async (_, roomId) => {
      setActiveRoom(null);
      gameSocketClient.clearActiveRoom(roomId);
      gameSocketClient.disconnect();
      gameSocketClient.connect(true);
      if (currentMatch.data?.roomId === roomId) {
        await multiplayerApi.leaveQueue().catch(() => undefined);
        setTicketId(null);
        queryClient.setQueryData(["matchmaking", "current"], null);
      }
      queryClient.removeQueries({ queryKey: ["rooms", "member"] });
      await refreshRooms();
    },
    onError: (error) =>
      toast({ title: t("Leave failed"), description: t(getErrorMessage(error)), tone: "error" }),
  });

  function submitCode(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = roomCode.trim().toUpperCase();
    if (normalized) joinRoom.mutate(normalized);
  }

  function selectGame(gameId: string) {
    const nextGame = onlineGames.find((game) => game.id === gameId);
    setSelectedGameId(gameId);
    setMaxPlayers(nextGame ? Math.max(2, nextGame.minPlayers) : 2);
  }

  if (session.isLoading || games.isLoading) return <Skeleton className="h-96" />;
  if (session.isError && isUnauthenticated(session.error)) {
    return <LoginRequired title={t("Multiplayer link locked")} description={t("Sign in before creating rooms, joining queues, or opening a realtime connection.")} />;
  }
  if (session.isError) {
    return <ErrorState title={t("Identity link unavailable")} description={t(getErrorMessage(session.error))} onAction={() => void session.refetch()} />;
  }
  if (!onlineGames.length) {
    return <EmptyState title={t("No multiplayer games enabled")} description={t("The backend catalog has no enabled multiplayer records.")} />;
  }

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_380px]">
      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="grid gap-4 border-b border-[var(--line)] p-5 sm:grid-cols-[1fr_260px] sm:items-end">
          <div>
            <p className="font-telemetry text-[8px] text-[var(--muted)]">{t("[ PUBLIC LOBBY ]")}</p>
            <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Waiting rooms")}</h2>
          </div>
          <SelectField
            label={t("Operation")}
            value={effectiveGameId}
            onChange={(event) => selectGame(event.target.value)}
          >
            {onlineGames.map((game) => (
              <option value={game.id} key={game.id}>{t(game.name)}</option>
            ))}
          </SelectField>
        </header>
        {rooms.isLoading ? <Skeleton className="m-5 h-72" /> : null}
        {rooms.isError ? (
          <div className="p-5">
            <ErrorState
              title={t("Room module unavailable")}
              description={t(getErrorMessage(rooms.error))}
              onAction={() => void rooms.refetch()}
            />
          </div>
        ) : null}
        {rooms.data && !rooms.data.content.length ? (
          <div className="p-5">
            <EmptyState title={t("No public waiting rooms")} description={t("Create a room, join by private code, or enter the matchmaking queue.")} />
          </div>
        ) : null}
        {rooms.data?.content.length ? (
          <ul>
            {rooms.data.content.map((room) => (
              <li className="grid gap-4 border-b border-[var(--line)] p-5 last:border-b-0 sm:grid-cols-[1fr_auto] sm:items-center" key={room.roomId}>
                <div>
                  <p className="font-telemetry text-[8px] text-[var(--accent)]">{room.roomCode} / {room.status}</p>
                  <p className="mt-2 font-bold uppercase">{room.gameSlug}</p>
                  <p className="font-telemetry mt-2 text-[8px] text-[var(--muted)]">
                    {room.players.length} / {room.maxPlayers} {t("PLAYERS")}
                  </p>
                </div>
                <Button compact onClick={() => joinRoom.mutate(room.roomCode)} busy={joinRoom.isPending}>
                  {t("Join room")}
                </Button>
              </li>
            ))}
          </ul>
        ) : null}

        <div className="border-t border-[var(--line)]">
          <header className="flex items-center justify-between gap-4 border-b border-[var(--line)] p-5">
            <div>
              <p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ LIVE SPECTATOR CHANNEL ]")}</p>
              <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Matches in progress")}</h2>
            </div>
            <Eye size={20} className="text-[var(--accent)]" aria-hidden="true" />
          </header>
          {liveRooms.isLoading ? <Skeleton className="m-5 h-32" /> : null}
          {liveRooms.isError ? (
            <div className="p-5">
              <ErrorState
                title={t("Live match channel unavailable")}
                description={t(getErrorMessage(liveRooms.error))}
                onAction={() => void liveRooms.refetch()}
              />
            </div>
          ) : null}
          {liveRooms.data && !liveRooms.data.content.length ? (
            <div className="p-5">
              <EmptyState
                title={t("No public matches in progress")}
                description={t("Public matches appear here while their server-authoritative engine is running.")}
              />
            </div>
          ) : null}
          {liveRooms.data?.content.length ? (
            <ul>
              {liveRooms.data.content.map((room) => {
                const participating = room.players.some((player) => player.id === session.data?.id);
                return (
                  <li className="grid gap-4 border-b border-[var(--line)] p-5 last:border-b-0 sm:grid-cols-[1fr_auto] sm:items-center" key={room.roomId}>
                    <div>
                      <p className="font-telemetry text-[8px] text-[var(--accent)]">{room.roomCode} / {t("PLAYING")}</p>
                      <p className="mt-2 font-bold uppercase">{t(room.gameName)}</p>
                      <p className="font-telemetry mt-2 text-[8px] text-[var(--muted)]">
                        {room.players.map((player) => player.username).join(" VS ")}
                      </p>
                    </div>
                    <Link
                      href={`/game/${encodeURIComponent(room.gameSlug)}?room=${encodeURIComponent(room.roomId)}${participating ? "" : "&spectate=1"}`}
                      className={buttonStyles("secondary")}
                    >
                      <Eye size={13} aria-hidden="true" />
                      {t(participating ? "Resume match" : "Watch live")}
                    </Link>
                  </li>
                );
              })}
            </ul>
          ) : null}
        </div>
      </section>

      <aside className="grid content-start gap-6">
        {displayedRoom ? (
          <section className="border border-[var(--accent)] bg-[var(--surface)] p-5">
            <ShieldCheck size={20} className="text-[var(--accent)]" aria-hidden="true" />
            <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">{t("[ ACTIVE MEMBERSHIP ]")}</p>
            <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{displayedRoom.roomCode}</h2>
            <p className="mt-3 text-xs leading-5 text-[var(--muted)]">
              {t("Room membership exists. The game interface opens only after server GAME_START.")}
            </p>
            <ul className="mt-5 grid gap-px border border-[var(--line)] bg-[var(--line)]">
              {displayedRoom.players.map((player) => (
                <li className="font-telemetry flex items-center justify-between bg-[var(--background)] px-3 py-2 text-[8px]" key={player.id}>
                  <span>{player.owner ? `[${t("OWNER")}] ` : ""}{player.username}</span>
                  <span className={player.connected ? "status-online" : "text-[var(--muted)]"}>
                    {t(player.ready ? "READY" : player.connected ? "CONNECTED" : "DISCONNECTED")}
                  </span>
                </li>
              ))}
            </ul>
            <div className="mt-5 grid gap-2">
              {displayedRoom.status === "WAITING" &&
              !displayedRoom.players.find((player) => player.id === session.data?.id)?.ready ? (
                <Button
                  onClick={() => readyRoom.mutate(displayedRoom.roomId)}
                  busy={readyRoom.isPending}
                >
                  {t("Signal ready")}
                </Button>
              ) : null}
              {displayedRoom.status === "WAITING" &&
              displayedRoom.ownerId === session.data?.id ? (
                <Button
                  variant="secondary"
                  onClick={() => startRoom.mutate(displayedRoom.roomId)}
                  busy={startRoom.isPending}
                  disabled={!displayedRoomReady}
                >
                  {t("Start room")}
                </Button>
              ) : null}
              <Link
                href={
                  "/game/" +
                  displayedRoom.gameSlug +
                  "?room=" +
                  encodeURIComponent(displayedRoom.roomId)
                }
                className={buttonStyles("secondary") + " w-full"}
              >
                {t("Open room stage")}
              </Link>
              <Button
                variant="ghost"
                compact
                onClick={() => leaveRoom.mutate(displayedRoom.roomId)}
                busy={leaveRoom.isPending}
              >
                {t("Leave room")}
              </Button>
            </div>
            {displayedRoom.status === "WAITING" ? (
              <div className="mt-5 border-t border-[var(--line)] pt-5">
                <p className="font-telemetry text-[8px] text-[var(--muted)]">
                  {t("[ INVITE ONLINE FRIENDS ]")}
                </p>
                <div className="mt-3 grid gap-2">
                  {friends.data
                    ?.filter(
                      (friend) =>
                        friend.online &&
                        !displayedRoom.players.some(
                          (player) => player.id === friend.id,
                        ),
                    )
                    .map((friend) => (
                      <Button
                        compact
                        variant="secondary"
                        key={friend.id}
                        onClick={() => {
                          try {
                            gameSocketClient.sendGameInvite(
                              displayedRoom.roomId,
                              friend.username,
                            );
                          } catch {
                            gameSocketClient.connect(true);
                            toast({
                              title: t("Invite connection unavailable"),
                              description: t("Reconnect and send the invite again."),
                              tone: "error",
                            });
                          }
                        }}
                      >
                        <Send size={12} aria-hidden="true" />
                        {t("Invite {username}", { username: friend.username })}
                      </Button>
                    ))}
                  {friends.data &&
                  !friends.data.some(
                    (friend) =>
                      friend.online &&
                      !displayedRoom.players.some(
                        (player) => player.id === friend.id,
                      ),
                  ) ? (
                    <p className="text-xs leading-5 text-[var(--muted)]">
                      {t("No available online friends.")}
                    </p>
                  ) : null}
                </div>
              </div>
            ) : null}
          </section>
        ) : null}

        <section className="border border-[var(--line)] bg-[var(--surface)] p-5">
          <Radio size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <h2 className="mt-5 text-xl font-black uppercase tracking-[-0.04em]">{t("Quick match")}</h2>
          <p className="mt-2 text-xs leading-5 text-[var(--muted)]">
            {t("Queue for the selected operation. Match state remains server-authoritative.")}
          </p>
          {activeTicketId ? (
            <div className="mt-5 border border-[var(--accent)] p-4">
              <p className="font-telemetry text-[8px] text-[var(--accent)]">
                {t("MATCHMAKING")} / {t(currentMatch.data?.status ?? "QUEUED")}
              </p>
              <p className="font-telemetry mt-2 break-all text-[8px] text-[var(--muted)]">{activeTicketId}</p>
              <Button className="mt-4 w-full" variant="ghost" compact onClick={() => leaveQueue.mutate()} busy={leaveQueue.isPending}>{t("Leave queue")}</Button>
            </div>
          ) : (
            <Button className="mt-5 w-full" onClick={() => quickMatch.mutate()} busy={quickMatch.isPending}>{t("Join queue")}</Button>
          )}
          <p className="font-telemetry mt-4 text-[8px] text-[var(--muted)]">
            {t("REALTIME")} / {t(socketStatus)}
          </p>
        </section>

        <section className="border border-[var(--line)] bg-[var(--surface)] p-5">
          <Users size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <h2 className="mt-5 text-xl font-black uppercase tracking-[-0.04em]">{t("Create room")}</h2>
          <div className="mt-5 grid gap-4">
            <SelectField label={t("Capacity")} value={maxPlayers} onChange={(event) => setMaxPlayers(Number(event.target.value))}>
              {capacityOptions.map((count) => <option value={count} key={count}>{t("{count} players", { count })}</option>)}
            </SelectField>
            <label className="font-telemetry flex items-center gap-3 text-[9px]">
              <input type="checkbox" checked={privateRoom} onChange={(event) => setPrivateRoom(event.target.checked)} className="h-4 w-4 accent-[var(--accent)]" />
              {t("Private room code")}
            </label>
            <Button onClick={() => createRoom.mutate()} busy={createRoom.isPending}>{t("Allocate room")}</Button>
          </div>
        </section>

        <form className="border border-[var(--line)] bg-[var(--surface)] p-5" onSubmit={submitCode}>
          <Field label={t("Private room code")} name="roomCode" value={roomCode} onChange={(event) => setRoomCode(event.target.value.toUpperCase())} maxLength={8} placeholder="A7FK2D" />
          <Button className="mt-4 w-full" variant="secondary" busy={joinRoom.isPending}>{t("Join by code")}</Button>
        </form>
      </aside>
    </div>
  );
}
