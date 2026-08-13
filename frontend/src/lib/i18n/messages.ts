import { sharedVietnameseMessages } from "@/lib/i18n/catalogs/shared";
import { authSettingsVietnameseMessages } from "@/lib/i18n/catalogs/auth-settings";
import { pageVietnameseMessages } from "@/lib/i18n/catalogs/pages";
import { featureVietnameseMessages } from "@/lib/i18n/catalogs/features";
import { gameVietnameseMessages } from "@/lib/i18n/catalogs/games";
import type {
  Locale,
  MessageCatalog,
  TranslationValues,
} from "@/lib/i18n/types";

const vietnameseMessages: MessageCatalog = {
  ...sharedVietnameseMessages,
  ...authSettingsVietnameseMessages,
  ...pageVietnameseMessages,
  ...featureVietnameseMessages,
  ...gameVietnameseMessages,
};

export function translate(
  locale: Locale,
  message: string,
  values: TranslationValues = {},
) {
  const template = locale === "vi" ? vietnameseMessages[message] ?? message : message;
  return template.replace(/\{([a-zA-Z0-9_]+)\}/g, (token, key: string) =>
    Object.hasOwn(values, key) ? String(values[key]) : token,
  );
}

export function localeTag(locale: Locale) {
  return locale === "vi" ? "vi-VN" : "en-US";
}

export function hasVietnameseTranslation(message: string) {
  return Object.hasOwn(vietnameseMessages, message);
}
