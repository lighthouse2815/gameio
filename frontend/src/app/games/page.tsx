import { PageHeader } from "@/components/ui/page-header";
import { LocalizedText } from "@/components/i18n/localized-text";
import { GamesCatalog } from "@/features/games/catalog";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Game index");

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
              <dt className="text-[var(--muted)]"><LocalizedText text="Registry" /></dt>
              <dd className="mt-1">API / LIVE</dd>
            </div>
            <div>
              <dt className="text-[var(--muted)]"><LocalizedText text="Local engines" /></dt>
              <dd className="mt-1 text-[var(--accent)]">
                <LocalizedText text="6 installed engines" />
              </dd>
            </div>
          </dl>
        }
      />
      <GamesCatalog key={q} initialQuery={q} />
    </>
  );
}
