"use client";

import {
  createContext,
  useCallback,
  useContext,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { Check, Info, X, XCircle } from "lucide-react";
import { cn } from "@/lib/cn";

type ToastTone = "success" | "error" | "info";
type ToastInput = {
  title: string;
  description?: string;
  tone?: ToastTone;
};
type ToastItem = ToastInput & { id: number };

const ToastContext = createContext<((toast: ToastInput) => void) | null>(null);

export function ToastProvider({ children }: { children: ReactNode }) {
  const [items, setItems] = useState<ToastItem[]>([]);

  const push = useCallback((toast: ToastInput) => {
    const id = Date.now() + Math.random();
    setItems((current) => [...current.slice(-2), { ...toast, id }]);
    window.setTimeout(() => {
      setItems((current) => current.filter((item) => item.id !== id));
    }, 4200);
  }, []);

  const value = useMemo(() => push, [push]);
  return (
    <ToastContext.Provider value={value}>
      {children}
      <div
        className="fixed bottom-4 right-4 z-[80] grid w-[min(390px,calc(100vw-2rem))] gap-2"
        aria-live="polite"
        aria-label="Notifications"
      >
        {items.map((item) => {
          const Icon =
            item.tone === "success"
              ? Check
              : item.tone === "error"
                ? XCircle
                : Info;
          return (
            <div
              className={cn(
                "surface-shadow grid grid-cols-[24px_1fr_28px] gap-3 border bg-[var(--surface)] p-4",
                item.tone === "error"
                  ? "border-[var(--danger)]"
                  : "border-[var(--line-strong)]",
              )}
              key={item.id}
              role={item.tone === "error" ? "alert" : "status"}
            >
              <Icon
                size={18}
                className={
                  item.tone === "error"
                    ? "text-[var(--danger)]"
                    : "text-[var(--accent)]"
                }
                aria-hidden="true"
              />
              <div>
                <p className="font-telemetry text-[10px] font-bold">
                  {item.title}
                </p>
                {item.description ? (
                  <p className="mt-1 text-xs leading-5 text-[var(--muted)]">
                    {item.description}
                  </p>
                ) : null}
              </div>
              <button
                type="button"
                aria-label="Dismiss notification"
                className="grid h-7 w-7 place-items-center text-[var(--muted)] hover:text-[var(--foreground)]"
                onClick={() =>
                  setItems((current) =>
                    current.filter((currentItem) => currentItem.id !== item.id),
                  )
                }
              >
                <X size={14} aria-hidden="true" />
              </button>
            </div>
          );
        })}
      </div>
    </ToastContext.Provider>
  );
}

export function useToast() {
  const context = useContext(ToastContext);
  if (!context) {
    throw new Error("useToast must be used inside ToastProvider.");
  }
  return context;
}
