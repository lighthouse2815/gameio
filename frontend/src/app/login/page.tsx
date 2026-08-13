import { AuthForm } from "@/features/auth/auth-form";
import { LocalizedText } from "@/components/i18n/localized-text";
import { localizedMetadata } from "@/lib/i18n/server";

export const generateMetadata = () => localizedMetadata("Sign in");

export default function LoginPage() {
  return (
    <div className="grid min-h-[76vh] border-x border-[var(--line)] lg:grid-cols-[1.15fr_0.85fr]">
      <section className="flex flex-col justify-between border-b border-[var(--line)] bg-[var(--surface)] p-6 sm:p-10 lg:border-b-0 lg:border-r lg:p-14">
        <p className="font-telemetry text-[10px] text-[var(--accent)]">
          <LocalizedText text="AUTH CHANNEL / 01" />
        </p>
        <h1 className="macro-title my-16"><LocalizedText text="Resume" /><br /><LocalizedText text="Play." /></h1>
        <p className="max-w-xl text-sm leading-6 text-[var(--muted)]">
          <LocalizedText text="Restore your progress, verified scores, rooms, and player network. Access tokens stay only in runtime memory; refresh is handled by the secure HttpOnly server cookie channel." />
        </p>
      </section>
      <section className="flex items-center bg-[var(--background)] p-6 sm:p-10 lg:p-14">
        <div className="w-full">
          <p className="font-telemetry mb-3 text-[9px] text-[var(--muted)]">
            <LocalizedText text="[ PLAYER IDENTIFICATION ]" />
          </p>
          <h2 className="mb-9 text-3xl font-black uppercase tracking-[-0.05em]">
            <LocalizedText text="Sign in" />
          </h2>
          <AuthForm mode="login" />
        </div>
      </section>
    </div>
  );
}
