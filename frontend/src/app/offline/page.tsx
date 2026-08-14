import { PageHeader } from "@/components/ui/page-header";
import { OfflineHub } from "@/features/pwa/offline-hub";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Offline games");

export default async function OfflinePage({
  searchParams,
}: {
  searchParams: Promise<{ game?: string }>;
}) {
  const { game } = await searchParams;
  return (
    <>
      <PageHeader
        index="OFF"
        eyebrow="Installable local engine pack"
        title="Offline games"
        description="Play the complete solo engine pack without an API connection. Local runs remain separate from server-authoritative progression and rankings."
      />
      <OfflineHub initialSlug={game} />
    </>
  );
}
