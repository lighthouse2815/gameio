"use client";

import { useCallback, useMemo } from "react";
import { useLocaleContext } from "@/lib/i18n/i18n-provider";
import { localeTag, translate } from "@/lib/i18n/messages";
import type { TranslationValues } from "@/lib/i18n/types";

export function useI18n() {
  const { locale, setLocale, toggleLocale } = useLocaleContext();
  const tag = localeTag(locale);
  const numberFormatter = useMemo(() => new Intl.NumberFormat(tag), [tag]);
  const dateFormatter = useMemo(
    () => new Intl.DateTimeFormat(tag, { timeZone: "Asia/Ho_Chi_Minh" }),
    [tag],
  );
  const dateTimeFormatter = useMemo(
    () =>
      new Intl.DateTimeFormat(tag, {
        dateStyle: "short",
        timeStyle: "short",
        timeZone: "Asia/Ho_Chi_Minh",
      }),
    [tag],
  );
  const t = useCallback(
    (message: string, values?: TranslationValues) =>
      translate(locale, message, values),
    [locale],
  );
  const formatNumber = useCallback(
    (value: number) => numberFormatter.format(value),
    [numberFormatter],
  );
  const formatDate = useCallback(
    (value: string | number | Date) => dateFormatter.format(new Date(value)),
    [dateFormatter],
  );
  const formatDateTime = useCallback(
    (value: string | number | Date) => dateTimeFormatter.format(new Date(value)),
    [dateTimeFormatter],
  );

  return {
    locale,
    tag,
    t,
    setLocale,
    toggleLocale,
    formatNumber,
    formatDate,
    formatDateTime,
  };
}
