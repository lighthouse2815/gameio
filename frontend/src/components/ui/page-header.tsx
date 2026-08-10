import type { ReactNode } from "react";

type PageHeaderProps = {
  index: string;
  eyebrow: string;
  title: string;
  description: string;
  aside?: ReactNode;
};

export function PageHeader({
  index,
  eyebrow,
  title,
  description,
  aside,
}: PageHeaderProps) {
  return (
    <header className="grid border-x border-b border-[var(--line)] bg-[var(--surface)] lg:grid-cols-[1fr_320px]">
      <div className="min-w-0 p-5 sm:p-8 lg:p-12">
        <p className="font-telemetry mb-8 text-[10px] text-[var(--accent)]">
          {index} {"///"} {eyebrow}
        </p>
        <h1 className="page-title max-w-5xl">{title}</h1>
        <p className="mt-7 max-w-2xl text-sm leading-6 text-[var(--muted)] sm:text-base sm:leading-7">
          {description}
        </p>
      </div>
      <div className="border-t border-[var(--line)] p-5 lg:border-l lg:border-t-0 lg:p-7">
        {aside ?? (
          <div className="font-telemetry flex h-full min-h-28 flex-col justify-between text-[10px] text-[var(--muted)]">
            <span>[ GAMEIO OPERATIONS ]</span>
            <span className="text-[var(--foreground)]">
              NETWORK / ACTIVE
            </span>
          </div>
        )}
      </div>
    </header>
  );
}
