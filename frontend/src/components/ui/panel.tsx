import type { HTMLAttributes, ReactNode } from "react";
import { cn } from "@/lib/cn";

type PanelProps = HTMLAttributes<HTMLElement> & {
  as?: "section" | "article" | "div";
  eyebrow?: string;
  title?: string;
  action?: ReactNode;
};

export function Panel({
  as: Component = "section",
  eyebrow,
  title,
  action,
  className,
  children,
  ...props
}: PanelProps) {
  return (
    <Component
      className={cn(
        "border border-[var(--line)] bg-[var(--surface)]",
        className,
      )}
      {...props}
    >
      {eyebrow || title || action ? (
        <header className="flex min-h-14 items-center justify-between gap-4 border-b border-[var(--line)] px-4 py-3 sm:px-5">
          <div className="min-w-0">
            {eyebrow ? (
              <p className="font-telemetry text-[9px] text-[var(--accent)]">
                [ {eyebrow} ]
              </p>
            ) : null}
            {title ? (
              <h2 className="truncate text-sm font-extrabold uppercase tracking-[-0.02em]">
                {title}
              </h2>
            ) : null}
          </div>
          {action}
        </header>
      ) : null}
      {children}
    </Component>
  );
}
