import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { LeaderboardScreen } from "@/features/leaderboard/leaderboard-screen";

export const metadata: Metadata = { title: "Leaderboard" };

export default function LeaderboardPage() {
  return (
    <>
      <PageHeader
        index="02"
        eyebrow="Verified telemetry"
        title="Global Rank"
        description="A read-only view of results validated and recorded by the authoritative game server. Filter the field by operation or inspect the global player index."
      />
      <LeaderboardScreen />
    </>
  );
}
