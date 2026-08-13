"use client";

import { useI18n } from "@/lib/i18n/use-i18n";
import type { TranslationValues } from "@/lib/i18n/types";

export function LocalizedText({
  text,
  values,
}: {
  text: string;
  values?: TranslationValues;
}) {
  const { t } = useI18n();
  return <>{t(text, values)}</>;
}
