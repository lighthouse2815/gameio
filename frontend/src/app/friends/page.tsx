import { PageHeader } from "@/components/ui/page-header";
import { FriendsScreen } from "@/features/friends/friends-screen";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Friends");

export default function FriendsPage() {
  return (
    <>
      <PageHeader
        index="03"
        eyebrow="Player network"
        title="Friends"
        description="Manage accepted player links and incoming requests. Presence and current-game signals are read from the realtime backend when that module is available."
      />
      <div className="border-x border-b border-[var(--line)] p-4 sm:p-7">
        <FriendsScreen />
      </div>
    </>
  );
}
