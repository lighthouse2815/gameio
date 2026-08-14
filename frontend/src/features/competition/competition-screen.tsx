"use client";

import { useMemo, useState, type FormEvent } from "react";
import Link from "next/link";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import {
  CalendarRange,
  ChevronRight,
  Crown,
  Gauge,
  Play,
  ShieldCheck,
  Swords,
  Trophy,
  Users,
} from "lucide-react";
import { Button, buttonStyles } from "@/components/ui/button";
import { Field, SelectField } from "@/components/ui/field";
import { EmptyState, ErrorState, Skeleton } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import { useSession } from "@/features/auth/hooks";
import { competitionApi } from "@/features/competition/api";
import {
  competitionKeys,
  useCompetitionRatings,
  useCurrentSeason,
  useMyRatings,
  useTournament,
  useTournaments,
} from "@/features/competition/hooks";
import type { TournamentDetail } from "@/features/competition/types";
import { useGames } from "@/features/games/hooks";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

function statusTone(status: string) {
  if (status === "COMPLETED") return "text-[var(--online)]";
  if (status === "IN_PROGRESS" || status === "ACTIVE") return "text-[var(--accent)]";
  return "text-[var(--muted)]";
}

export function CompetitionScreen() {
  const { t, formatDate, formatNumber } = useI18n();
  const session = useSession();
  const season = useCurrentSeason();
  const games = useGames({ page: 0, size: 100 });
  const tournaments = useTournaments();
  const queryClient = useQueryClient();
  const toast = useToast();
  const multiplayerGames = useMemo(
    () => games.data?.content.filter((game) => game.gameType !== "SINGLE_PLAYER") ?? [],
    [games.data],
  );
  const [selectedGameId, setSelectedGameId] = useState("");
  const effectiveGameId = selectedGameId || multiplayerGames[0]?.id || "";
  const ratings = useCompetitionRatings(effectiveGameId);
  const myRatings = useMyRatings(Boolean(session.data));
  const personalRating = myRatings.data?.find((rating) => rating.gameId === effectiveGameId);
  const [selectedTournamentId, setSelectedTournamentId] = useState("");
  const effectiveTournamentId = selectedTournamentId || tournaments.data?.content[0]?.id || "";
  const tournament = useTournament(effectiveTournamentId);
  const [name, setName] = useState("");
  const [capacity, setCapacity] = useState<"4" | "8" | "16">("4");

  async function refreshCompetition(detail?: TournamentDetail) {
    if (detail) {
      setSelectedTournamentId(detail.tournament.id);
      queryClient.setQueryData(
        competitionKeys.tournament(detail.tournament.id),
        detail,
      );
    }
    await queryClient.invalidateQueries({ queryKey: competitionKeys.all });
  }

  const createTournament = useMutation({
    mutationFn: competitionApi.createTournament,
    onSuccess: async (detail) => {
      setName("");
      await refreshCompetition(detail);
      toast({ title: t("Tournament created"), description: t("Registration is now open."), tone: "success" });
    },
    onError: (error) => toast({ title: t("Tournament creation failed"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const joinTournament = useMutation({
    mutationFn: competitionApi.joinTournament,
    onSuccess: async (detail) => {
      await refreshCompetition(detail);
      toast({ title: t("Tournament joined"), description: t("Your bracket seed is reserved."), tone: "success" });
    },
    onError: (error) => toast({ title: t("Could not join tournament"), description: t(getErrorMessage(error)), tone: "error" }),
  });
  const startTournament = useMutation({
    mutationFn: competitionApi.startTournament,
    onSuccess: async (detail) => {
      await refreshCompetition(detail);
      toast({ title: t("Bracket started"), description: t("Private authoritative match rooms were allocated."), tone: "success" });
    },
    onError: (error) => toast({ title: t("Could not start tournament"), description: t(getErrorMessage(error)), tone: "error" }),
  });

  function submitTournament(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const normalized = name.trim();
    if (!normalized || !effectiveGameId) return;
    createTournament.mutate({ name: normalized, gameId: effectiveGameId, maxPlayers: capacity });
  }

  const rounds = useMemo(() => {
    const grouped = new Map<number, TournamentDetail["matches"]>();
    tournament.data?.matches.forEach((match) => {
      grouped.set(match.roundNumber, [...(grouped.get(match.roundNumber) ?? []), match]);
    });
    return [...grouped.entries()].sort(([left], [right]) => left - right);
  }, [tournament.data]);
  const currentEntry = tournament.data?.players.find((player) => player.userId === session.data?.id);
  const summary = tournament.data?.tournament;

  return (
    <div className="grid gap-8">
      <section className="grid gap-px border border-[var(--line)] bg-[var(--line)] md:grid-cols-[1.2fr_0.8fr_0.8fr]">
        <div className="bg-[var(--surface)] p-5 sm:p-7">
          <CalendarRange size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-6 text-[8px] text-[var(--muted)]">{t("[ CURRENT COMPETITIVE SEASON ]")}</p>
          {season.isLoading ? <Skeleton className="mt-3 h-16" /> : null}
          {season.data ? (
            <>
              <h2 className="mt-2 text-3xl font-black uppercase tracking-[-0.05em]">{t(season.data.name)}</h2>
              <p className="font-telemetry mt-3 text-[8px] text-[var(--muted)]">
                {formatDate(season.data.startsAt)} — {formatDate(season.data.endsAt)}
              </p>
            </>
          ) : null}
        </div>
        <dl className="bg-[var(--background)] p-5 sm:p-7">
          <dt className="font-telemetry flex items-center justify-between text-[8px] text-[var(--muted)]">
            {t("Your selected-game rating")} <Gauge size={15} className="text-[var(--accent)]" aria-hidden="true" />
          </dt>
          <dd className="mt-7 text-4xl font-black">{personalRating ? formatNumber(personalRating.rating) : "—"}</dd>
          <p className="font-telemetry mt-2 text-[8px] text-[var(--muted)]">
            {personalRating ? t("{count} rated matches", { count: personalRating.gamesPlayed }) : t("Complete a multiplayer match to receive 1000 Elo.")}
          </p>
        </dl>
        <dl className="bg-[var(--surface)] p-5 sm:p-7">
          <dt className="font-telemetry flex items-center justify-between text-[8px] text-[var(--muted)]">
            {t("Open tournaments")} <Swords size={15} className="text-[var(--accent)]" aria-hidden="true" />
          </dt>
          <dd className="mt-7 text-4xl font-black">
            {formatNumber(tournaments.data?.content.filter((item) => item.status === "REGISTRATION").length ?? 0)}
          </dd>
          <p className="font-telemetry mt-2 text-[8px] text-[var(--muted)]">{t("SINGLE ELIMINATION / 4–16 PLAYERS")}</p>
        </dl>
      </section>

      <section className="grid border border-[var(--line)] bg-[var(--surface)] xl:grid-cols-[minmax(0,1fr)_340px]">
        <div className="min-w-0 xl:border-r xl:border-[var(--line)]">
          <header className="flex flex-wrap items-end justify-between gap-4 border-b border-[var(--line)] p-5">
            <div>
              <p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ SEASON ELO LADDER ]")}</p>
              <h2 className="mt-2 text-2xl font-black uppercase tracking-[-0.04em]">{t("Competitive ratings")}</h2>
            </div>
            <SelectField label={t("Game")} value={effectiveGameId} onChange={(event) => setSelectedGameId(event.target.value)}>
              {multiplayerGames.map((game) => <option key={game.id} value={game.id}>{t(game.name)}</option>)}
            </SelectField>
          </header>
          {ratings.isLoading || games.isLoading ? <Skeleton className="m-5 h-72" /> : null}
          {ratings.isError ? <div className="p-5"><ErrorState title={t("Rating ladder unavailable")} description={t(getErrorMessage(ratings.error))} onAction={() => void ratings.refetch()} /></div> : null}
          {ratings.data && !ratings.data.content.length ? <div className="p-5"><EmptyState title={t("No season ratings yet")} description={t("The first completed authoritative match will open this ladder.")} /></div> : null}
          {ratings.data?.content.length ? (
            <div className="overflow-x-auto">
              <table className="w-full min-w-[680px] border-collapse text-left">
                <thead className="font-telemetry text-[8px] text-[var(--muted)]"><tr>{["Rank", "Player", "Elo", "Matches", "W / L / D", "Win rate"].map((label) => <th className="border-b border-[var(--line)] px-4 py-3 font-normal" key={label}>{t(label)}</th>)}</tr></thead>
                <tbody>{ratings.data.content.map((entry) => {
                  const winRate = entry.gamesPlayed ? (entry.wins / entry.gamesPlayed) * 100 : 0;
                  return <tr key={entry.userId} className="border-b border-[var(--line)] last:border-b-0 hover:bg-[var(--background)]">
                    <td className="p-4 font-mono text-sm">#{entry.rank}</td>
                    <td className="p-4"><Link className="font-black uppercase hover:text-[var(--accent)]" href={`/profile/${encodeURIComponent(entry.username)}`}>{entry.username}</Link></td>
                    <td className="p-4 text-xl font-black text-[var(--accent)]">{formatNumber(entry.rating)}</td>
                    <td className="p-4 font-mono text-sm">{formatNumber(entry.gamesPlayed)}</td>
                    <td className="p-4 font-mono text-sm">{entry.wins} / {entry.losses} / {entry.draws}</td>
                    <td className="p-4 font-mono text-sm">{winRate.toFixed(1)}%</td>
                  </tr>;
                })}</tbody>
              </table>
            </div>
          ) : null}
        </div>
        <aside className="border-t border-[var(--line)] p-5 xl:border-t-0">
          <ShieldCheck size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <h3 className="mt-5 text-xl font-black uppercase tracking-[-0.04em]">{t("How Elo changes")}</h3>
          <p className="mt-3 text-xs leading-5 text-[var(--muted)]">
            {t("Every authoritative multiplayer result updates a game-specific seasonal rating. New ratings start at 1000; the K-factor is 32 and draws count as 0.5.")}
          </p>
        </aside>
      </section>

      <section className="grid gap-6 xl:grid-cols-[380px_1fr]">
        <div className="grid content-start gap-6">
          <section className="border border-[var(--line)] bg-[var(--surface)] p-5">
            <Trophy size={20} className="text-[var(--accent)]" aria-hidden="true" />
            <h2 className="mt-5 text-2xl font-black uppercase tracking-[-0.04em]">{t("Create tournament")}</h2>
            {session.data ? (
              <form className="mt-5 grid gap-4" onSubmit={submitTournament}>
                <Field label={t("Tournament name")} value={name} onChange={(event) => setName(event.target.value)} maxLength={100} required />
                <SelectField label={t("Game")} value={effectiveGameId} onChange={(event) => setSelectedGameId(event.target.value)}>
                  {multiplayerGames.map((game) => <option key={game.id} value={game.id}>{t(game.name)}</option>)}
                </SelectField>
                <SelectField label={t("Maximum bracket size")} value={capacity} onChange={(event) => setCapacity(event.target.value as "4" | "8" | "16")}>
                  <option value="4">4</option><option value="8">8</option><option value="16">16</option>
                </SelectField>
                <Button type="submit" busy={createTournament.isPending} disabled={!effectiveGameId}>{t("Open registration")}</Button>
              </form>
            ) : (
              <div className="mt-5"><Link href="/login" className={buttonStyles("primary")}>{t("Sign in to create")}</Link></div>
            )}
          </section>

          <section className="border border-[var(--line)] bg-[var(--surface)]">
            <header className="border-b border-[var(--line)] p-5">
              <p className="font-telemetry text-[8px] text-[var(--accent)]">{t("[ TOURNAMENT DIRECTORY ]")}</p>
              <h2 className="mt-2 text-2xl font-black uppercase tracking-[-0.04em]">{t("Brackets")}</h2>
            </header>
            {tournaments.isLoading ? <Skeleton className="m-5 h-52" /> : null}
            {tournaments.isError ? <div className="p-5"><ErrorState title={t("Tournament directory unavailable")} description={t(getErrorMessage(tournaments.error))} onAction={() => void tournaments.refetch()} /></div> : null}
            {tournaments.data && !tournaments.data.content.length ? <div className="p-5"><EmptyState title={t("No tournaments yet")} description={t("Create the first single-elimination bracket.")} /></div> : null}
            <ul>{tournaments.data?.content.map((item) => (
              <li key={item.id}>
                <button type="button" onClick={() => setSelectedTournamentId(item.id)} className={"grid w-full grid-cols-[1fr_auto] gap-3 border-b border-[var(--line)] p-4 text-left last:border-b-0 hover:bg-[var(--background)] " + (effectiveTournamentId === item.id ? "bg-[var(--background)]" : "")}>
                  <span><strong className="block uppercase">{item.name}</strong><span className="font-telemetry mt-2 block text-[8px] text-[var(--muted)]">{t(item.gameName)} / {item.joinedPlayers}/{item.maxPlayers}</span></span>
                  <span className="grid justify-items-end gap-2"><span className={"font-telemetry text-[8px] " + statusTone(item.status)}>{t(item.status)}</span><ChevronRight size={14} aria-hidden="true" /></span>
                </button>
              </li>
            ))}</ul>
          </section>
        </div>

        <section className="min-w-0 border border-[var(--line)] bg-[var(--surface)]">
          {tournament.isLoading ? <Skeleton className="m-5 h-[620px]" /> : null}
          {tournament.isError ? <div className="p-5"><ErrorState title={t("Tournament bracket unavailable")} description={t(getErrorMessage(tournament.error))} onAction={() => void tournament.refetch()} /></div> : null}
          {summary ? (
            <>
              <header className="flex flex-wrap items-start justify-between gap-5 border-b border-[var(--line)] p-5 sm:p-7">
                <div><p className={"font-telemetry text-[8px] " + statusTone(summary.status)}>{t(summary.status)} / {t(summary.gameName)}</p><h2 className="mt-2 text-3xl font-black uppercase tracking-[-0.05em]">{summary.name}</h2><p className="mt-3 text-xs text-[var(--muted)]">{t("Created by {username}", { username: summary.createdByUsername })} · {formatDate(summary.createdAt)}</p></div>
                <div className="flex flex-wrap gap-2">
                  {session.data && summary.status === "REGISTRATION" && !currentEntry && summary.joinedPlayers < summary.maxPlayers ? <Button onClick={() => joinTournament.mutate(summary.id)} busy={joinTournament.isPending}>{t("Join bracket")}</Button> : null}
                  {session.data?.id === summary.createdById && summary.status === "REGISTRATION" ? <Button variant="secondary" onClick={() => startTournament.mutate(summary.id)} busy={startTournament.isPending} disabled={summary.joinedPlayers < 2}><Play size={13} aria-hidden="true" />{t("Start bracket")}</Button> : null}
                </div>
              </header>

              {summary.winnerUsername ? <div className="flex items-center gap-4 border-b border-[var(--accent)] bg-[var(--background)] p-5"><Crown size={24} className="text-[var(--accent)]" aria-hidden="true" /><div><p className="font-telemetry text-[8px] text-[var(--muted)]">{t("TOURNAMENT CHAMPION")}</p><p className="mt-1 text-xl font-black uppercase">{summary.winnerUsername}</p></div></div> : null}

              <div className="grid gap-px border-b border-[var(--line)] bg-[var(--line)] sm:grid-cols-3">
                <dl className="bg-[var(--background)] p-4"><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Players")}</dt><dd className="mt-2 text-xl font-black">{summary.joinedPlayers}/{summary.maxPlayers}</dd></dl>
                <dl className="bg-[var(--background)] p-4"><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Current round")}</dt><dd className="mt-2 text-xl font-black">{summary.currentRound || "—"}</dd></dl>
                <dl className="bg-[var(--background)] p-4"><dt className="font-telemetry text-[8px] text-[var(--muted)]">{t("Your seed")}</dt><dd className="mt-2 text-xl font-black">{currentEntry ? `#${currentEntry.seedNumber}` : "—"}</dd></dl>
              </div>

              <div className="border-b border-[var(--line)] p-5">
                <p className="font-telemetry flex items-center gap-2 text-[8px] text-[var(--muted)]"><Users size={13} aria-hidden="true" />{t("Registered players")}</p>
                <div className="mt-3 flex flex-wrap gap-2">{tournament.data?.players.map((player) => <span key={player.userId} className={"font-telemetry border px-3 py-2 text-[8px] " + (player.eliminated ? "border-[var(--line)] text-[var(--muted)] line-through" : "border-[var(--line-strong)]")}>#{player.seedNumber} {player.username}</span>)}</div>
              </div>

              {rounds.length ? (
                <div className="overflow-x-auto p-5 sm:p-7">
                  <div className="flex min-w-max items-start gap-5">
                    {rounds.map(([roundNumber, matches]) => (
                      <section className="w-64" key={roundNumber}>
                        <p className="font-telemetry mb-3 text-[8px] text-[var(--accent)]">{t("ROUND {round}", { round: roundNumber })}</p>
                        <div className="grid gap-4">{matches.map((match) => {
                          const participant = session.data && (match.playerOneId === session.data.id || match.playerTwoId === session.data.id);
                          return <article key={match.id} className="border border-[var(--line)] bg-[var(--background)] p-3">
                            <p className="font-telemetry text-[7px] text-[var(--muted)]">MATCH {match.bracketIndex + 1} / <span className={statusTone(match.status)}>{t(match.status)}</span></p>
                            <div className="mt-3 grid gap-2 text-sm"><p className={match.winnerId === match.playerOneId ? "font-black text-[var(--accent)]" : "font-bold"}>{match.playerOneUsername}</p><p className={match.winnerId === match.playerTwoId ? "font-black text-[var(--accent)]" : "font-bold"}>{match.playerTwoUsername ?? t("BYE")}</p></div>
                            {match.status === "ACTIVE" && match.roomId && participant ? <Link className={buttonStyles("secondary") + " mt-4 w-full"} href={`/game/${encodeURIComponent(summary.gameSlug)}?room=${encodeURIComponent(match.roomId)}`}>{t("Enter match room")}</Link> : null}
                            {match.status === "ACTIVE" && !participant ? <p className="font-telemetry mt-4 text-[7px] text-[var(--muted)]">{t("PRIVATE BRACKET MATCH")}</p> : null}
                          </article>;
                        })}</div>
                      </section>
                    ))}
                  </div>
                </div>
              ) : <div className="p-5"><EmptyState title={t("Bracket not started")} description={t("Registration stays open until the creator starts with at least two players.")} /></div>}
            </>
          ) : null}
        </section>
      </section>
    </div>
  );
}
