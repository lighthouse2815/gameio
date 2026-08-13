"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import {
  LOCALE_COOKIE_NAME,
  type Locale,
} from "@/lib/i18n/types";

type I18nContextValue = {
  locale: Locale;
  setLocale: (locale: Locale) => void;
  toggleLocale: () => void;
};

const I18nContext = createContext<I18nContextValue | null>(null);

function persistLocale(locale: Locale) {
  document.documentElement.lang = locale;
  document.documentElement.dataset.locale = locale;
  document.cookie = `${LOCALE_COOKIE_NAME}=${locale}; Path=/; Max-Age=31536000; SameSite=Lax`;
}

export function I18nProvider({
  children,
  initialLocale,
}: {
  children: ReactNode;
  initialLocale: Locale;
}) {
  const [locale, setLocaleState] = useState(initialLocale);
  const setLocale = useCallback((nextLocale: Locale) => {
    persistLocale(nextLocale);
    setLocaleState(nextLocale);
  }, []);
  const toggleLocale = useCallback(() => {
    setLocale(locale === "en" ? "vi" : "en");
  }, [locale, setLocale]);
  const value = useMemo(
    () => ({ locale, setLocale, toggleLocale }),
    [locale, setLocale, toggleLocale],
  );

  return <I18nContext.Provider value={value}>{children}</I18nContext.Provider>;
}

export function useLocaleContext() {
  const context = useContext(I18nContext);
  if (!context) {
    throw new Error("useLocaleContext must be used inside I18nProvider.");
  }
  return context;
}
