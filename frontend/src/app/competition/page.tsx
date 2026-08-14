import { PageHeader } from "@/components/ui/page-header";
import { CompetitionScreen } from "@/features/competition/competition-screen";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Competitive arena");

export default function CompetitionPage() {
  return (
    <>
      <PageHeader
        index="07"
        eyebrow="Season operations"
        title="Competitive arena"
        description="Track game-specific seasonal Elo, inspect the live ladder, and run server-authoritative single-elimination tournaments."
      />
      <div className="border-x border-b border-[var(--line)] p-4 sm:p-7">
        <CompetitionScreen />
      </div>
    </>
  );
}

