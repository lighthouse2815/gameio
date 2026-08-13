import "server-only";

import type { Metadata } from "next";
import { cookies } from "next/headers";
import { translate } from "@/lib/i18n/messages";
import {
  LOCALE_COOKIE_NAME,
  parseLocale,
  type Locale,
} from "@/lib/i18n/types";

export async function getRequestLocale(): Promise<Locale> {
  const cookieStore = await cookies();
  return parseLocale(cookieStore.get(LOCALE_COOKIE_NAME)?.value);
}

export async function localizedMetadata(
  title: string,
  description?: string,
): Promise<Metadata> {
  const locale = await getRequestLocale();
  return {
    title: translate(locale, title),
    ...(description ? { description: translate(locale, description) } : {}),
  };
}
