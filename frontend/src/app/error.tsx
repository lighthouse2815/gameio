"use client";

import { ErrorState } from "@/components/ui/states";
import { getErrorMessage } from "@/lib/api/api-error";
import { useI18n } from "@/lib/i18n/use-i18n";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  const { t } = useI18n();
  return (
    <div className="border-x border-[var(--line)] p-5 sm:p-8">
      <ErrorState
        title="Interface process interrupted"
        description={t(getErrorMessage(error))}
        onAction={reset}
      />
    </div>
  );
}
