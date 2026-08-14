"use client";

import { useEffect, useState, type ReactNode } from "react";
import { QueryClientProvider, useQueryClient } from "@tanstack/react-query";
import { ReactQueryDevtools } from "@tanstack/react-query-devtools";
import { ToastProvider } from "@/components/ui/toast";
import { sessionQueryKey } from "@/features/auth/hooks";
import { I18nProvider } from "@/lib/i18n/i18n-provider";
import type { Locale } from "@/lib/i18n/types";
import type { AuthChangeReason } from "@/lib/api/token-vault";
import { createQueryClient } from "@/lib/query-client";
import { gameSocketClient } from "@/lib/socket/game-socket-client";
import { RealtimeNotifications } from "@/features/notifications/realtime-notifications";
import { useThemeStore } from "@/stores/theme-store";
import { usePlayerPreferencesStore } from "@/stores/player-preferences-store";
import { PreferencesBridge } from "@/features/game-preferences/preferences-bridge";
import { ServiceWorkerRegistration } from "@/features/pwa/service-worker-registration";
import { PerformanceHud } from "@/features/settings/performance-hud";
import { playFeedback } from "@/features/settings/player-feedback";

function RuntimeBridge() {
  const queryClient = useQueryClient();
  const hydrateTheme = useThemeStore((state) => state.hydrate);
  const hydratePlayerPreferences = usePlayerPreferencesStore((state) => state.hydrate);

  useEffect(() => {
    hydrateTheme();
    hydratePlayerPreferences();
  }, [hydratePlayerPreferences, hydrateTheme]);

  useEffect(() => {
    const resultFeedback = () => playFeedback("success");
    window.addEventListener("gameio:verified-result", resultFeedback);
    return () => window.removeEventListener("gameio:verified-result", resultFeedback);
  }, []);

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

export function Providers({
  children,
  initialLocale,
}: {
  children: ReactNode;
  initialLocale: Locale;
}) {
  const [queryClient] = useState(createQueryClient);
  return (
    <I18nProvider initialLocale={initialLocale}>
      <QueryClientProvider client={queryClient}>
        <ToastProvider>
          <RuntimeBridge />
          <PreferencesBridge />
          <ServiceWorkerRegistration />
          <RealtimeNotifications />
          <PerformanceHud />
          {children}
        </ToastProvider>
        {process.env.NODE_ENV === "development" ? (
          <ReactQueryDevtools initialIsOpen={false} />
        ) : null}
      </QueryClientProvider>
    </I18nProvider>
  );
}
