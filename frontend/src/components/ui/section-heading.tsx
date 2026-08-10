import Link from "next/link";
import { ArrowRight } from "lucide-react";

export function SectionHeading({
  index,
  eyebrow,
  title,
  href,
  actionLabel = "View index",
}: {
  index: string;
  eyebrow: string;
  title: string;
  href?: string;
  actionLabel?: string;
}) {
  return (
    <header className="flex flex-wrap items-end justify-between gap-5 border-x border-t border-[var(--line)] bg-[var(--surface)] px-5 py-6 sm:px-7">
      <div className="flex items-end gap-5">
        <span className="font-telemetry text-[9px] text-[var(--accent)]">
          {index}
        </span>
        <div>
          <p className="font-telemetry mb-1 text-[8px] text-[var(--muted)]">
            {eyebrow}
          </p>
          <h2 className="text-2xl font-black uppercase tracking-[-0.045em] sm:text-3xl">
            {title}
          </h2>
        </div>
      </div>
      {href ? (
        <Link
          href={href}
          className="font-telemetry flex items-center gap-2 text-[9px] text-[var(--muted)] hover:text-[var(--accent)]"
        >
          {actionLabel}
          <ArrowRight size={13} aria-hidden="true" />
        </Link>
      ) : null}
    </header>
  );
}
