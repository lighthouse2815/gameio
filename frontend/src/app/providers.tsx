"use client";

import { useEffect, useState, type ReactNode } from "react";
import { QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { ToastProvider } from "@/components/ui/toast";
import { sessionQueryKey } from "@/features/auth/hooks";
import type { AuthChangeReason } from "@/lib/api/token-vault";
import { createQueryClient } from "@/lib/query-client";
import { gameSocketClient } from "@/lib/socket/game-socket-client";
import { useThemeStore } from "@/stores/theme-store";

function RuntimeBridge() {
  const queryClient = useQueryClient();
  const hydrateTheme = useThemeStore((state) => state.hydrate);

  useEffect(() => {
    hydrateTheme();
  }, [hydrateTheme]);

  useEffect(() => {
    const refreshSession = (event: Event) => {
      const reason = (event as CustomEvent<{ reason?: AuthChangeReason }>).detail
        ?.reason;
      if (reason === "logout") {
        gameSocketClient.disconnect();
        queryClient.clear();
        queryClient.setQueryData(sessionQueryKey, null);
        return;
      }
      void queryClient.invalidateQueries({ queryKey: sessionQueryKey });
    };
    window.addEventListener("gameio:auth-change", refreshSession);
    return () =>
      window.removeEventListener("gameio:auth-change", refreshSession);
  }, [queryClient]);

  return null;
}

export function Providers({ children }: { children: ReactNode }) {
  const [queryClient] = useState(createQueryClient);
  return (
    <QueryClientProvider client={queryClient}>
      <ToastProvider>
        <RuntimeBridge />
        {children}
      </ToastProvider>
      {process.env.NODE_ENV === "development" ? (
        <ReactQueryDevtools initialIsOpen={false} />
      ) : null}
    </QueryClientProvider>
  );
}
