"use client";

import { ErrorState } from "@/components/ui/states";
import { getErrorMessage } from "@/lib/api/api-error";

export default function GlobalError({
  error,
  reset,
}: {
  error: Error & { digest?: string };
  reset: () => void;
}) {
  return (
    <div className="border-x border-[var(--line)] p-5 sm:p-8">
      <ErrorState
        title="Interface process interrupted"
        description={getErrorMessage(error)}
        onAction={reset}
      />
    </div>
  );
}
