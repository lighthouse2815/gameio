"use client";

import Link from "next/link";
import { buttonStyles } from "@/components/ui/button";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function NotFound() {
  const { t } = useI18n();
  return (
    <div className="grid min-h-[70vh] place-items-center border-x border-[var(--line)] p-5">
      <section className="technical-frame w-full max-w-3xl border border-[var(--line-strong)] bg-[var(--surface)] p-7 sm:p-12">
        <p className="font-telemetry text-[10px] text-[var(--accent)]">
          {t("ERR / ROUTE-404")}
        </p>
        <h1 className="macro-title my-9">{t("Lost Signal.")}</h1>
        <p className="max-w-lg text-sm leading-6 text-[var(--muted)]">
          {t("The requested operation node does not exist in the current Gameio index.")}
        </p>
        <Link href="/" className={buttonStyles("primary") + " mt-8"}>
          {t("Return to base")}
        </Link>
      </section>
    </div>
  );
}
