import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { GamesCatalog } from "@/features/games/catalog";

export const metadata: Metadata = { title: "Game index" };

export default async function GamesPage({
  searchParams,
}: {
  searchParams: Promise<{ q?: string }>;
}) {
  const { q = "" } = await searchParams;
  return (
    <>
      <PageHeader
        index="01"
        eyebrow="Playable operations"
        title="Game Index"
        description="Search the live backend catalog. Every title declares its player mode, classification, player capacity, and implementation route."
        aside={
          <dl className="font-telemetry grid h-full content-between gap-5 text-[9px]">
            <div>
              <dt className="text-[var(--muted)]">Registry</dt>
              <dd className="mt-1">API / LIVE</dd>
            </div>
            <div>
              <dt className="text-[var(--muted)]">Local engines</dt>
              <dd className="mt-1 text-[var(--accent)]">2048 + SNAKE</dd>
            </div>
          </dl>
        }
      />
      <GamesCatalog key={q} initialQuery={q} />
    </>
  );
}
