"use client";

import { useEffect } from "react";
import { usePwaStore, type InstallPromptEvent } from "@/features/pwa/pwa-store";

export function ServiceWorkerRegistration() {
  const setInstallPrompt = usePwaStore((state) => state.setInstallPrompt);

  useEffect(() => {
    if ("serviceWorker" in navigator && process.env.NODE_ENV === "production") {
      void navigator.serviceWorker.register("/sw.js", { scope: "/" });
    }
    const beforeInstall = (event: Event) => {
      event.preventDefault();
      setInstallPrompt(event as InstallPromptEvent);
    };
    const installed = () => setInstallPrompt(null);
    window.addEventListener("beforeinstallprompt", beforeInstall);
    window.addEventListener("appinstalled", installed);
    return () => {
      window.removeEventListener("beforeinstallprompt", beforeInstall);
      window.removeEventListener("appinstalled", installed);
    };
  }, [setInstallPrompt]);

  return null;
}
