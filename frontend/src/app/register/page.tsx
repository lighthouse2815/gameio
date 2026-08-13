import { AuthForm } from "@/features/auth/auth-form";
import { LocalizedText } from "@/components/i18n/localized-text";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Create player");

export default function RegisterPage() {
  return (
    <div className="grid min-h-[76vh] border-x border-[var(--line)] lg:grid-cols-[1.15fr_0.85fr]">
      <section className="flex flex-col justify-between border-b border-[var(--line)] bg-[var(--surface)] p-6 sm:p-10 lg:border-b-0 lg:border-r lg:p-14">
        <p className="font-telemetry text-[10px] text-[var(--accent)]">
          <LocalizedText text="IDENTITY REGISTRY / 02" />
        </p>
        <h1 className="macro-title my-16"><LocalizedText text="Join" /><br /><LocalizedText text="The Grid." /></h1>
        <p className="max-w-xl text-sm leading-6 text-[var(--muted)]">
          <LocalizedText text="One identity links your games, achievements, match history, and verified global rank." />
        </p>
      </section>
      <section className="flex items-center bg-[var(--background)] p-6 sm:p-10 lg:p-14">
        <div className="w-full">
          <p className="font-telemetry mb-3 text-[9px] text-[var(--muted)]">
            <LocalizedText text="[ NEW PLAYER RECORD ]" />
          </p>
          <h2 className="mb-9 text-3xl font-black uppercase tracking-[-0.05em]">
            <LocalizedText text="Register" />
          </h2>
          <AuthForm mode="register" />
        </div>
      </section>
    </div>
  );
}
