import type { ButtonHTMLAttributes } from "react";
import { cn } from "@/lib/cn";

export type ButtonVariant = "primary" | "secondary" | "ghost" | "danger";

export function buttonStyles(
  variant: ButtonVariant = "primary",
  compact = false,
) {
  return cn(
    "font-telemetry inline-flex min-h-11 items-center justify-center gap-2 border px-5 text-[11px] font-bold transition-[background,color,transform] duration-150 disabled:cursor-not-allowed disabled:opacity-45",
    "active:translate-x-px active:translate-y-px",
    compact && "min-h-9 px-3 text-[10px]",
    variant === "primary" &&
      "border-[var(--accent)] bg-[var(--accent)] text-[var(--accent-ink)] hover:bg-[var(--foreground)] hover:text-[var(--background)]",
    variant === "secondary" &&
      "border-[var(--line-strong)] bg-[var(--surface)] text-[var(--foreground)] hover:border-[var(--accent)] hover:text-[var(--accent)]",
    variant === "ghost" &&
      "border-transparent bg-transparent text-[var(--muted)] hover:border-[var(--line)] hover:text-[var(--foreground)]",
    variant === "danger" &&
      "border-[var(--danger)] bg-transparent text-[var(--danger)] hover:bg-[var(--danger)] hover:text-white",
  );
}

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  compact?: boolean;
  busy?: boolean;
};

export function Button({
  className,
  variant = "primary",
  compact = false,
  busy = false,
  children,
  disabled,
  ...props
}: ButtonProps) {
  return (
    <button
      className={cn(buttonStyles(variant, compact), className)}
      disabled={disabled || busy}
      aria-busy={busy}
      {...props}
    >
      {busy ? <span aria-hidden="true">{"///"}</span> : null}
      {children}
    </button>
  );
}
