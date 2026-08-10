import type { Metadata } from "next";
import { PageHeader } from "@/components/ui/page-header";
import { MultiplayerScreen } from "@/features/multiplayer/multiplayer-screen";

export const metadata: Metadata = { title: "Multiplayer" };

export default async function MultiplayerPage({
  searchParams,
}: {
  searchParams: Promise<{ game?: string }>;
}) {
  const { game = "" } = await searchParams;
  return (
    <>
      <PageHeader
        index="04"
        eyebrow="Room operations"
        title="Multiplayer"
        description="Create or join a backend room, enter matchmaking, and wait for authoritative GAME_START state. No simulated rooms are inserted when the realtime module is offline."
      />
      <div className="border-x border-b border-[var(--line)] p-4 sm:p-7">
        <MultiplayerScreen initialGameSlug={game} />
      </div>
    </>
  );
}
