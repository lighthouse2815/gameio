import { AlertTriangle, Inbox, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/cn";

export function Skeleton({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "block animate-pulse bg-[var(--surface-strong)]",
        className,
      )}
      aria-hidden="true"
    />
  );
}

export function LoadingGrid({ count = 6 }: { count?: number }) {
  return (
    <div
      className="grid grid-cols-1 gap-px bg-[var(--line)] sm:grid-cols-2 xl:grid-cols-3"
      aria-label="Loading data"
      aria-busy="true"
    >
      {Array.from({ length: count }).map((_, index) => (
        <div
          className="min-h-56 bg-[var(--surface)] p-5"
          key={"skeleton-" + index}
        >
          <Skeleton className="mb-8 h-5 w-24" />
          <Skeleton className="mb-3 h-10 w-4/5" />
          <Skeleton className="mb-2 h-3 w-full" />
          <Skeleton className="h-3 w-2/3" />
        </div>
      ))}
    </div>
  );
}

type StateProps = {
  title: string;
  description: string;
  actionLabel?: string;
  onAction?: () => void;
  className?: string;
};

export function EmptyState({
  title,
  description,
  actionLabel,
  onAction,
  className,
}: StateProps) {
  return (
    <div
      className={cn(
        "flex min-h-64 flex-col items-start justify-center border border-dashed border-[var(--line-strong)] bg-[var(--surface)] p-7",
        className,
      )}
    >
      <Inbox aria-hidden="true" className="mb-5 text-[var(--accent)]" />
      <p className="font-telemetry mb-2 text-[10px] text-[var(--muted)]">
        [ NO RECORDS ]
      </p>
      <h2 className="text-2xl font-black uppercase tracking-[-0.04em]">
        {title}
      </h2>
      <p className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted)]">
        {description}
      </p>
      {actionLabel && onAction ? (
        <Button className="mt-6" onClick={onAction}>
          {actionLabel}
        </Button>
      ) : null}
    </div>
  );
}

export function ErrorState({
  title,
  description,
  actionLabel = "Retry link",
  onAction,
  className,
}: StateProps) {
  return (
    <div
      className={cn(
        "flex min-h-64 flex-col items-start justify-center border border-[var(--danger)] bg-[var(--surface)] p-7",
        className,
      )}
      role="alert"
    >
      <AlertTriangle
        aria-hidden="true"
        className="mb-5 text-[var(--danger)]"
      />
      <p className="font-telemetry mb-2 text-[10px] text-[var(--danger)]">
        [ LINK FAILURE ]
      </p>
      <h2 className="text-2xl font-black uppercase tracking-[-0.04em]">
        {title}
      </h2>
      <p className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted)]">
        {description}
      </p>
      {onAction ? (
        <Button className="mt-6" onClick={onAction} variant="secondary">
          <RotateCw size={14} aria-hidden="true" />
          {actionLabel}
        </Button>
      ) : null}
    </div>
  );
}
