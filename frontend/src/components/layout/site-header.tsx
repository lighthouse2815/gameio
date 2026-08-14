"use client";

import { useEffect, useRef, useState, type FormEvent } from "react";
import Link from "next/link";
import { usePathname, useRouter } from "next/navigation";
import {
  BarChart3,
  CalendarDays,
  CloudOff,
  Gamepad2,
  LogOut,
  Menu,
  Radio,
  Search,
  Settings,
  UserRound,
  Users,
  X,
} from "lucide-react";
import { ThemeToggle } from "@/components/layout/theme-toggle";
import { LanguageToggle } from "@/components/layout/language-toggle";
import { useLogout, useSession } from "@/features/auth/hooks";
import { cn } from "@/lib/cn";
import { useI18n } from "@/lib/i18n/use-i18n";

const NAVIGATION = [
  { href: "/games", label: "Games", icon: Gamepad2 },
  { href: "/daily-challenge", label: "Daily", icon: CalendarDays },
  { href: "/multiplayer", label: "Live", icon: Radio },
  { href: "/leaderboard", label: "Ranks", icon: Users },
  { href: "/offline", label: "Offline", icon: CloudOff },
] as const;

export function SiteHeader() {
  const { t } = useI18n();
  const pathname = usePathname();
  const router = useRouter();
  const [menuOpen, setMenuOpen] = useState(false);
  const [search, setSearch] = useState("");
  const session = useSession();
  const logout = useLogout();
  const accountMenu = useRef<HTMLDetailsElement>(null);

  useEffect(() => {
    if (accountMenu.current) accountMenu.current.open = false;
  }, [pathname]);

  function submitSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const query = search.trim();
    router.push(query ? "/games?q=" + encodeURIComponent(query) : "/games");
    setMenuOpen(false);
  }

  return (
    <>
      <div className="hazard-bar" aria-hidden="true" />
      <header className="sticky top-0 z-50 border-b border-[var(--line)] bg-[var(--background)]">
        <div className="mx-auto grid max-w-[1600px] grid-cols-[auto_1fr_auto]">
          <Link
            href="/"
            className="flex h-16 items-center gap-3 border-x border-[var(--line)] px-4 sm:px-5"
            aria-label={t("Gameio home")}
          >
            <span className="grid h-7 w-7 place-items-center bg-[var(--accent)] font-mono text-xs font-black text-white">
              G
            </span>
            <span className="text-lg font-black uppercase tracking-[-0.05em]">
              Gameio
            </span>
            <sup className="font-telemetry hidden text-[7px] text-[var(--muted)] sm:block">
              TM
            </sup>
          </Link>

          <div className="hidden min-w-0 grid-cols-[auto_minmax(180px,440px)] justify-between border-r border-[var(--line)] lg:grid">
            <nav className="flex" aria-label={t("Primary navigation")}>
              {NAVIGATION.map(({ href, label, icon: Icon }) => {
                const active = pathname.startsWith(href);
                return (
                  <Link
                    key={href}
                    href={href}
                    className={cn(
                      "font-telemetry flex min-w-24 items-center justify-center gap-2 border-r border-[var(--line)] px-4 text-[10px] transition-colors hover:bg-[var(--surface)]",
                      active &&
                        "bg-[var(--surface)] text-[var(--accent)] shadow-[inset_0_-3px_0_var(--accent)]",
                    )}
                  >
                    <Icon size={13} aria-hidden="true" />
                    {t(label)}
                  </Link>
                );
              })}
            </nav>
            <form
              className="grid grid-cols-[1fr_52px] border-l border-[var(--line)]"
              role="search"
              onSubmit={submitSearch}
            >
              <label className="sr-only" htmlFor="site-search">
                {t("Search games")}
              </label>
              <input
                id="site-search"
                type="search"
                value={search}
                onChange={(event) => setSearch(event.target.value)}
                placeholder={t("SEARCH GAME INDEX...")}
                className="font-telemetry min-w-0 bg-transparent px-4 text-[10px] outline-none placeholder:text-[var(--muted)] focus:bg-[var(--surface)]"
              />
              <button
                type="submit"
                aria-label={t("Search games")}
                className="grid place-items-center border-l border-[var(--line)] text-[var(--muted)] hover:bg-[var(--surface)] hover:text-[var(--accent)]"
              >
                <Search size={15} aria-hidden="true" />
              </button>
            </form>
          </div>

          <div className="flex border-r border-[var(--line)]">
            {session.data ? (
              <details className="group relative" ref={accountMenu}>
                <summary
                  className="font-telemetry flex h-16 cursor-pointer list-none items-center gap-2 px-3 text-[9px] hover:bg-[var(--surface)] focus-visible:outline focus-visible:outline-2 focus-visible:outline-[var(--accent)] sm:px-4 [&::-webkit-details-marker]:hidden"
                  aria-label={t("Account menu for {username}", {
                    username: session.data.username,
                  })}
                >
                  <UserRound size={15} aria-hidden="true" />
                  <span className="hidden max-w-24 truncate sm:block">
                    {session.data.username}
                  </span>
                </summary>
                <nav
                  className="absolute right-0 top-full z-[60] grid w-56 gap-px border border-[var(--line-strong)] bg-[var(--line)] shadow-[8px_8px_0_rgba(0,0,0,0.18)]"
                  aria-label={t("Account navigation")}
                >
                  {[
                    {
                      href:
                        "/profile/" +
                        encodeURIComponent(session.data.username),
                      label: "Profile",
                      icon: UserRound,
                    },
                    { href: "/friends", label: "Friends", icon: Users },
                    { href: "/stats", label: "Stats", icon: BarChart3 },
                    { href: "/settings", label: "Settings", icon: Settings },
                  ].map(({ href, label, icon: Icon }) => (
                    <Link
                      key={href}
                      href={href}
                      className="font-telemetry flex min-h-11 items-center justify-between bg-[var(--surface)] px-4 text-[9px] hover:text-[var(--accent)]"
                    >
                      {t(label)}
                      <Icon size={13} aria-hidden="true" />
                    </Link>
                  ))}
                  <button
                    type="button"
                    className="font-telemetry flex min-h-11 items-center justify-between bg-[var(--surface)] px-4 text-left text-[9px] text-[var(--danger)] disabled:opacity-50"
                    disabled={logout.isPending}
                    onClick={() =>
                      logout.mutate(undefined, {
                        onSettled: () => router.push("/"),
                      })
                    }
                  >
                    {t(logout.isPending ? "Closing session" : "Sign out")}
                    <LogOut size={13} aria-hidden="true" />
                  </button>
                </nav>
              </details>
            ) : (
              <Link
                href="/login"
                className="font-telemetry flex h-16 items-center gap-2 px-3 text-[9px] hover:bg-[var(--surface)] sm:px-4"
              >
                <UserRound size={15} aria-hidden="true" />
                <span className="hidden max-w-24 truncate sm:block">
                  {t("Sign in")}
                </span>
              </Link>
            )}
            <LanguageToggle />
            <ThemeToggle />
            <button
              type="button"
              className="grid h-16 w-12 place-items-center border-l border-[var(--line)] lg:hidden"
              onClick={() => setMenuOpen((open) => !open)}
              aria-label={t(menuOpen ? "Close navigation" : "Open navigation")}
              aria-expanded={menuOpen}
            >
              {menuOpen ? (
                <X size={18} aria-hidden="true" />
              ) : (
                <Menu size={18} aria-hidden="true" />
              )}
            </button>
          </div>
        </div>
      </header>

      {menuOpen ? (
        <div className="fixed inset-x-0 top-[69px] z-40 border-b border-[var(--line-strong)] bg-[var(--background)] p-4 lg:hidden">
          <form
            className="mb-3 grid grid-cols-[1fr_48px] border border-[var(--line)]"
            role="search"
            onSubmit={submitSearch}
          >
            <label className="sr-only" htmlFor="mobile-site-search">
              {t("Search games")}
            </label>
            <input
              id="mobile-site-search"
              type="search"
              value={search}
              onChange={(event) => setSearch(event.target.value)}
              placeholder={t("SEARCH GAME INDEX...")}
              className="font-telemetry min-w-0 bg-[var(--surface)] px-4 text-[10px] outline-none"
            />
            <button
              type="submit"
              aria-label={t("Search games")}
              className="grid h-12 place-items-center border-l border-[var(--line)]"
            >
              <Search size={15} aria-hidden="true" />
            </button>
          </form>
          <nav className="grid gap-px bg-[var(--line)]" aria-label={t("Mobile navigation")}>
            {[...NAVIGATION, { href: "/stats", label: "Stats", icon: BarChart3 }, { href: "/friends", label: "Friends", icon: Users }].map(
              ({ href, label, icon: Icon }) => (
                <Link
                  key={href}
                  href={href}
                  onClick={() => setMenuOpen(false)}
                  className="font-telemetry flex min-h-12 items-center justify-between bg-[var(--surface)] px-4 text-[10px]"
                >
                  {t(label)}
                  <Icon size={14} aria-hidden="true" />
                </Link>
              ),
            )}
          </nav>
        </div>
      ) : null}
    </>
  );
}
