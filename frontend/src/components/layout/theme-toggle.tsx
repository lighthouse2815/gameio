"use client";

import { Moon, Sun } from "lucide-react";
import { useThemeStore } from "@/stores/theme-store";

export function ThemeToggle() {
  const mode = useThemeStore((state) => state.mode);
  const hydrated = useThemeStore((state) => state.hydrated);
  const toggle = useThemeStore((state) => state.toggle);
  const next = mode === "dark" ? "light" : "dark";
  return (
    <button
      type="button"
      onClick={toggle}
      className="grid h-11 w-11 place-items-center border-l border-[var(--line)] text-[var(--muted)] transition-colors hover:bg-[var(--surface-strong)] hover:text-[var(--foreground)]"
      aria-label={"Switch to " + next + " theme"}
      title={"Switch to " + next + " theme"}
    >
      {!hydrated || mode === "dark" ? (
        <Sun size={16} aria-hidden="true" />
      ) : (
        <Moon size={16} aria-hidden="true" />
      )}
    </button>
  );
}
