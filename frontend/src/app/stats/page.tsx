import { PageHeader } from "@/components/ui/page-header";
import { StatsScreen } from "@/features/stats/stats-screen";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Player analytics");

export default function StatsPage() {
  return (
    <>
      <PageHeader
        index="06"
        eyebrow="Verified performance record"
        title="Player analytics"
        description="All-time outcomes, 30-day consistency, score trends, achievement completion and per-engine performance derived from server-accepted results."
      />
      <StatsScreen />
    </>
  );
}
