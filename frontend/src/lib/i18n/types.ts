export type Locale = "en" | "vi";

export const LOCALE_COOKIE_NAME = "gameio.locale";

export function parseLocale(value: string | undefined): Locale {
  return value === "vi" ? "vi" : "en";
}

export type TranslationValues = Record<string, string | number>;

export type MessageCatalog = Readonly<Record<string, string>>;
