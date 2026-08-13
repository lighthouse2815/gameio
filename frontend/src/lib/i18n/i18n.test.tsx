import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import { LanguageToggle } from "@/components/layout/language-toggle";
import { I18nProvider } from "@/lib/i18n/i18n-provider";
import { translate } from "@/lib/i18n/messages";
import { LOCALE_COOKIE_NAME, parseLocale } from "@/lib/i18n/types";

const refresh = vi.fn();

vi.mock("next/navigation", () => ({
  useRouter: () => ({ refresh }),
}));

describe("Vietnamese locale", () => {
  afterEach(() => {
    cleanup();
    refresh.mockClear();
    document.cookie = `${LOCALE_COOKIE_NAME}=; Path=/; Max-Age=0`;
    document.documentElement.lang = "en";
    delete document.documentElement.dataset.locale;
  });

  it("uses Vietnamese with diacritics and interpolates values", () => {
    expect(translate("vi", "Switch to English")).toBe("Chuyển sang tiếng Anh");
    expect(
      translate("vi", "{count} players", { count: 4 }),
    ).toBe("4 người chơi");
    expect(translate("en", "Games")).toBe("Games");
  });

  it("normalizes unsupported cookie values to English", () => {
    expect(parseLocale("vi")).toBe("vi");
    expect(parseLocale("fr")).toBe("en");
    expect(parseLocale(undefined)).toBe("en");
  });

  it("switches the document and persists the selected language", () => {
    render(
      <I18nProvider initialLocale="en">
        <LanguageToggle />
      </I18nProvider>,
    );

    fireEvent.click(screen.getByRole("button", { name: "Switch to Vietnamese" }));

    expect(document.documentElement).toHaveAttribute("lang", "vi");
    expect(document.documentElement).toHaveAttribute("data-locale", "vi");
    expect(document.cookie).toContain(`${LOCALE_COOKIE_NAME}=vi`);
    expect(refresh).toHaveBeenCalledOnce();
    expect(screen.getByRole("button", { name: "Chuyển sang tiếng Anh" })).toHaveTextContent("EN");
  });
});
