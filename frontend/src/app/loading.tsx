import { LoadingGrid } from "@/components/ui/states";

export default function Loading() {
  return (
    <div className="border-x border-[var(--line)] p-5 sm:p-8">
      <p className="font-telemetry mb-5 text-[10px] text-[var(--accent)]">
        [ INDEXING NETWORK ]
      </p>
      <LoadingGrid />
    </div>
  );
}
