"use client";

import { AlertTriangle, Inbox, RotateCw } from "lucide-react";
import { Button } from "@/components/ui/button";
import { cn } from "@/lib/cn";
import { useI18n } from "@/lib/i18n/use-i18n";

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
  const { t } = useI18n();
  return (
    <div
      className="grid grid-cols-1 gap-px bg-[var(--line)] sm:grid-cols-2 xl:grid-cols-3"
      aria-label={t("Loading data")}
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
  const { t } = useI18n();
  return (
    <div
      className={cn(
        "flex min-h-64 flex-col items-start justify-center border border-dashed border-[var(--line-strong)] bg-[var(--surface)] p-7",
        className,
      )}
    >
      <Inbox aria-hidden="true" className="mb-5 text-[var(--accent)]" />
      <p className="font-telemetry mb-2 text-[10px] text-[var(--muted)]">
        {t("[ NO RECORDS ]")}
      </p>
      <h2 className="text-2xl font-black uppercase tracking-[-0.04em]">
        {t(title)}
      </h2>
      <p className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted)]">
        {t(description)}
      </p>
      {actionLabel && onAction ? (
        <Button className="mt-6" onClick={onAction}>
          {t(actionLabel)}
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
  const { t } = useI18n();
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
        {t("[ LINK FAILURE ]")}
      </p>
      <h2 className="text-2xl font-black uppercase tracking-[-0.04em]">
        {t(title)}
      </h2>
      <p className="mt-2 max-w-xl text-sm leading-6 text-[var(--muted)]">
        {t(description)}
      </p>
      {onAction ? (
        <Button className="mt-6" onClick={onAction} variant="secondary">
          <RotateCw size={14} aria-hidden="true" />
          {t(actionLabel)}
        </Button>
      ) : null}
    </div>
  );
}
