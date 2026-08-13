"use client";

import Link from "next/link";
import { LockKeyhole } from "lucide-react";
import { buttonStyles } from "@/components/ui/button";
import { useI18n } from "@/lib/i18n/use-i18n";

export function LoginRequired({
  title = "Player session required",
  description = "Sign in to open this protected operation channel.",
}: {
  title?: string;
  description?: string;
}) {
  const { t } = useI18n();
  return (
    <div className="flex min-h-72 flex-col items-start justify-center border border-[var(--line-strong)] bg-[var(--surface)] p-7">
      <LockKeyhole
        size={24}
        className="mb-5 text-[var(--accent)]"
        aria-hidden="true"
      />
      <p className="font-telemetry text-[9px] text-[var(--muted)]">
        {t("[ ACCESS RESTRICTED ]")}
      </p>
      <h2 className="mt-2 text-3xl font-black uppercase tracking-[-0.05em]">
        {t(title)}
      </h2>
      <p className="mt-3 max-w-lg text-sm leading-6 text-[var(--muted)]">
        {t(description)}
      </p>
      <Link href="/login" className={buttonStyles("primary") + " mt-7"}>
        {t("Establish session")}
      </Link>
    </div>
  );
}
