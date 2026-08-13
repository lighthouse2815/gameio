"use client";

import { Languages } from "lucide-react";
import { useRouter } from "next/navigation";
import { useI18n } from "@/lib/i18n/use-i18n";

export function LanguageToggle() {
  const router = useRouter();
  const { locale, t, toggleLocale } = useI18n();
  const targetLabel = locale === "en" ? "Switch to Vietnamese" : "Switch to English";

  return (
    <button
      type="button"
      onClick={() => {
        toggleLocale();
        router.refresh();
      }}
      className="font-telemetry flex h-16 min-w-14 items-center justify-center gap-1.5 border-l border-[var(--line)] px-2 text-[8px] text-[var(--muted)] transition-colors hover:bg-[var(--surface-strong)] hover:text-[var(--foreground)]"
      aria-label={t(targetLabel)}
      title={t(targetLabel)}
    >
      <Languages size={15} aria-hidden="true" />
      <span>{locale === "en" ? "VI" : "EN"}</span>
    </button>
  );
}
