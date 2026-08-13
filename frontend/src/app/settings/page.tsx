import { PageHeader } from "@/components/ui/page-header";
import { SettingsScreen } from "@/features/settings/settings-screen";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Settings");

export default function SettingsPage() {
  return (
    <>
      <PageHeader
        index="05"
        eyebrow="Local and account controls"
        title="Settings"
        description="Adjust the visual substrate, update the avatar field supported by the backend, or close the secure browser session."
      />
      <div className="border-x border-b border-[var(--line)] p-4 sm:p-7">
        <SettingsScreen />
      </div>
    </>
  );
}
