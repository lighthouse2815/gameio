"use client";

import { useState, type FormEvent } from "react";
import Link from "next/link";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { Check, Radio, UserMinus, UserPlus, X } from "lucide-react";
import { LoginRequired } from "@/components/auth/login-required";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { friendApi } from "@/features/friends/api";
import { isUnauthenticated, useSession } from "@/features/auth/hooks";
import { getErrorMessage } from "@/lib/api/api-error";

export function FriendsScreen() {
  const session = useSession();
  const toast = useToast();
  const queryClient = useQueryClient();
  const [username, setUsername] = useState("");
  const enabled = Boolean(session.data);
  const friends = useQuery({
    queryKey: ["friends", "accepted"],
    queryFn: friendApi.list,
    enabled,
  });
  const requests = useQuery({
    queryKey: ["friends", "incoming"],
    queryFn: friendApi.requests,
    select: (response) => response.incoming,
    enabled,
  });

  const refresh = () =>
    queryClient.invalidateQueries({ queryKey: ["friends"] });

  const send = useMutation({
    mutationFn: friendApi.send,
    onSuccess: () => {
      setUsername("");
      toast({
        title: "Request transmitted",
        description: "The backend accepted the friend request.",
        tone: "success",
      });
      void refresh();
    },
    onError: (error) =>
      toast({
        title: "Request rejected",
        description: getErrorMessage(error),
        tone: "error",
      }),
  });
  const accept = useMutation({
    mutationFn: friendApi.accept,
    onSuccess: () => void refresh(),
    onError: (error) =>
      toast({ title: "Accept failed", description: getErrorMessage(error), tone: "error" }),
  });
  const reject = useMutation({
    mutationFn: friendApi.reject,
    onSuccess: () => void refresh(),
    onError: (error) =>
      toast({ title: "Reject failed", description: getErrorMessage(error), tone: "error" }),
  });
  const remove = useMutation({
    mutationFn: friendApi.remove,
    onSuccess: () => void refresh(),
    onError: (error) =>
      toast({ title: "Remove failed", description: getErrorMessage(error), tone: "error" }),
  });

  function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = username.trim();
    if (normalized) send.mutate(normalized);
  }

  if (session.isLoading) return <Skeleton className="h-72" />;
  if (session.isError && isUnauthenticated(session.error)) {
    return <LoginRequired title="Player network locked" />;
  }
  if (session.isError) {
    return (
      <ErrorState
        title="Identity link unavailable"
        description={getErrorMessage(session.error)}
        onAction={() => void session.refetch()}
      />
    );
  }

  return (
    <div className="grid gap-6 lg:grid-cols-[1fr_360px]">
      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="flex items-center justify-between border-b border-[var(--line)] p-5">
          <div>
            <p className="font-telemetry text-[8px] text-[var(--muted)]">[ ACCEPTED LINKS ]</p>
            <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">Your network</h2>
          </div>
          <span className="font-telemetry text-[9px] text-[var(--accent)]">{friends.data?.length ?? "—"} NODES</span>
        </header>
        {friends.isLoading ? <Skeleton className="m-5 h-64" /> : null}
        {friends.isError ? (
          <div className="p-5">
            <ErrorState
              title="Friend module unavailable"
              description={getErrorMessage(friends.error)}
              onAction={() => void friends.refetch()}
            />
          </div>
        ) : null}
        {friends.data && !friends.data.length ? (
          <div className="p-5">
            <EmptyState title="No accepted links" description="Send a request by exact username to build your player network." />
          </div>
        ) : null}
        {friends.data?.length ? (
          <ul>
            {friends.data.map((friend) => (
              <li className="grid gap-4 border-b border-[var(--line)] p-4 last:border-b-0 sm:grid-cols-[1fr_auto] sm:items-center" key={friend.id}>
                <div className="flex min-w-0 items-center gap-3">
                  <span className="font-telemetry grid h-10 w-10 shrink-0 place-items-center border border-[var(--line-strong)] bg-[var(--background)] text-[9px]">
                    {friend.username.slice(0, 2).toUpperCase()}
                  </span>
                  <div className="min-w-0">
                    <Link href={"/profile/" + friend.username} className="truncate font-bold hover:text-[var(--accent)]">{friend.username}</Link>
                    <p className={"font-telemetry mt-1 text-[8px] " + (friend.online ? "status-online" : "text-[var(--muted)]")}>
                      {friend.online ? (friend.currentGameName ? "PLAYING " + friend.currentGameName : "ONLINE") : "OFFLINE"}
                    </p>
                  </div>
                </div>
                <div className="flex gap-2">
                  {friend.online && friend.currentGameSlug ? (
                    <Link
                      href={"/multiplayer?game=" + friend.currentGameSlug}
                      className="font-telemetry inline-flex min-h-9 items-center gap-2 border border-[var(--line-strong)] px-3 text-[8px] hover:border-[var(--accent)]"
                    >
                      <Radio size={12} aria-hidden="true" />
                      Open same-game lobby
                    </Link>
                  ) : null}
                  <Button compact variant="danger" aria-label={"Remove " + friend.username} onClick={() => remove.mutate(friend.username)} busy={remove.isPending}>
                    <UserMinus size={13} aria-hidden="true" />
                  </Button>
                </div>
              </li>
            ))}
          </ul>
        ) : null}
      </section>

      <aside className="grid content-start gap-6">
        <form className="border border-[var(--line)] bg-[var(--surface)] p-5" onSubmit={submit}>
          <UserPlus size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <h2 className="mt-5 text-xl font-black uppercase tracking-[-0.04em]">Add player</h2>
          <div className="mt-5">
            <Field
              label="Exact username"
              name="friendUsername"
              value={username}
              onChange={(event) => setUsername(event.target.value)}
              placeholder="player_call_sign"
            />
          </div>
          <Button className="mt-4 w-full" busy={send.isPending}>Transmit request</Button>
        </form>

        <section className="border border-[var(--line)] bg-[var(--surface)]">
          <header className="border-b border-[var(--line)] p-5">
            <p className="font-telemetry text-[8px] text-[var(--muted)]">[ INCOMING ]</p>
            <h2 className="mt-1 text-xl font-black uppercase tracking-[-0.04em]">Requests</h2>
          </header>
          {requests.isLoading ? <Skeleton className="m-4 h-32" /> : null}
          {requests.isError ? (
            <p className="p-4 text-xs leading-5 text-[var(--danger)]">{getErrorMessage(requests.error)}</p>
          ) : null}
          {requests.data && !requests.data.length ? (
            <p className="p-5 text-xs leading-5 text-[var(--muted)]">No pending requests.</p>
          ) : null}
          {requests.data?.map((request) => (
            <div className="border-b border-[var(--line)] p-4 last:border-b-0" key={request.id}>
              <p className="font-bold">{request.sender.username}</p>
              <p className="font-telemetry mt-1 text-[8px] text-[var(--muted)]">{new Date(request.createdAt).toLocaleDateString()}</p>
              <div className="mt-4 flex gap-2">
                <Button compact onClick={() => accept.mutate(request.id)} busy={accept.isPending}>
                  <Check size={13} aria-hidden="true" /> Accept
                </Button>
                <Button compact variant="ghost" onClick={() => reject.mutate(request.id)} busy={reject.isPending}>
                  <X size={13} aria-hidden="true" /> Reject
                </Button>
              </div>
            </div>
          ))}
        </section>
      </aside>
    </div>
  );
}
