"use client";

import {
  forwardRef,
  type InputHTMLAttributes,
  type SelectHTMLAttributes,
} from "react";
import { cn } from "@/lib/cn";
import { useI18n } from "@/lib/i18n/use-i18n";

type InputProps = InputHTMLAttributes<HTMLInputElement> & {
  label: string;
  error?: string;
  hint?: string;
};

export const Field = forwardRef<HTMLInputElement, InputProps>(function Field(
  { label, error, hint, id, className, ...props },
  ref,
) {
  const { t } = useI18n();
  const inputId = id ?? props.name;
  const detailId = inputId ? inputId + "-detail" : undefined;
  return (
    <label className="block" htmlFor={inputId}>
      <span className="font-telemetry mb-2 block text-[10px] text-[var(--muted)]">
        {t(label)}
      </span>
      <input
        ref={ref}
        id={inputId}
        className={cn(
          "min-h-12 w-full border bg-[var(--background)] px-4 text-sm text-[var(--foreground)] outline-none transition-colors placeholder:text-[var(--muted)] focus:border-[var(--accent)]",
          error ? "border-[var(--danger)]" : "border-[var(--line-strong)]",
          className,
        )}
        aria-invalid={Boolean(error)}
        aria-describedby={error || hint ? detailId : undefined}
        {...props}
        placeholder={props.placeholder ? t(String(props.placeholder)) : undefined}
      />
      {error || hint ? (
        <span
          id={detailId}
          className={cn(
            "mt-2 block text-xs",
            error ? "text-[var(--danger)]" : "text-[var(--muted)]",
          )}
        >
          {t(error ?? hint ?? "")}
        </span>
      ) : null}
    </label>
  );
});

type SelectProps = SelectHTMLAttributes<HTMLSelectElement> & {
  label: string;
};

export function SelectField({ label, id, children, ...props }: SelectProps) {
  const { t } = useI18n();
  const selectId = id ?? props.name;
  return (
    <label className="block" htmlFor={selectId}>
      <span className="font-telemetry mb-2 block text-[10px] text-[var(--muted)]">
        {t(label)}
      </span>
      <select
        id={selectId}
        className="min-h-12 w-full border border-[var(--line-strong)] bg-[var(--background)] px-4 text-sm outline-none focus:border-[var(--accent)]"
        {...props}
      >
        {children}
      </select>
    </label>
  );
}
