import { PageHeader } from "@/components/ui/page-header";
import { DailyChallengeScreen } from "@/features/daily-challenge/daily-challenge-screen";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Daily Challenge");

export default function DailyChallengePage() {
  return (
    <>
      <PageHeader
        index="03"
        eyebrow="Shared seed operation"
        title="Daily Challenge"
        description="One verified solo operation every day. Everyone receives the same server seed and competes on a ranking that resets at midnight in Vietnam."
      />
      <DailyChallengeScreen />
    </>
  );
}
