"use client";

import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import {
  activateGoogleIdentity,
  loadGoogleIdentityServices,
  renderGoogleIdentityButton,
  type GoogleIdentityApi,
} from "@/features/auth/google-identity";
import { useI18n } from "@/lib/i18n/use-i18n";

type GoogleAuthButtonProps = {
  mode: "login" | "register";
  onCredential: (idToken: string) => void;
  busy?: boolean;
  disabled?: boolean;
  clientId?: string;
};

type LoadState = "loading" | "ready" | "error";

export function GoogleAuthButton({
  mode,
  onCredential,
  busy = false,
  disabled = false,
  clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID,
}: GoogleAuthButtonProps) {
  const { locale, t } = useI18n();
  const normalizedClientId = clientId?.trim() ?? "";
  const [state, setState] = useState<LoadState>("loading");
  const [message, setMessage] = useState(
    normalizedClientId
      ? "Loading Google identity services."
      : "Google access is not configured for this deployment.",
  );
  const [retryKey, setRetryKey] = useState(0);
  const hostRef = useRef<HTMLDivElement>(null);
  const apiRef = useRef<GoogleIdentityApi | null>(null);
  const credentialHandlerRef = useRef(onCredential);
  const effectiveState = normalizedClientId ? state : "unconfigured";

  useEffect(() => {
    credentialHandlerRef.current = onCredential;
  }, [onCredential]);

  useEffect(() => {
    if (!normalizedClientId) {
      apiRef.current = null;
      return;
    }

    let cancelled = false;
    let deactivate: (() => void) | undefined;
    void loadGoogleIdentityServices()
      .then((api) => {
        if (cancelled) return;
        deactivate = activateGoogleIdentity(
          api,
          normalizedClientId,
          (response) => {
            const credential = response.credential?.trim();
            if (!credential) {
              setState("error");
              setMessage(
                "Google did not return an identity token. Try the connection again.",
              );
              return;
            }
            credentialHandlerRef.current(credential);
          },
        );
        apiRef.current = api;
        setMessage("Google identity services are ready.");
        setState("ready");
      })
      .catch(() => {
        if (cancelled) return;
        apiRef.current = null;
        setState("error");
        setMessage(
          "Google identity could not load. Check your connection and try again.",
        );
      });

    return () => {
      cancelled = true;
      deactivate?.();
    };
  }, [normalizedClientId, retryKey]);

  useEffect(() => {
    if (effectiveState !== "ready" || busy || disabled) return;
    const api = apiRef.current;
    const host = hostRef.current;
    if (!api || !host) return;

    let renderedWidth = 0;
    const render = () => {
      const availableWidth = Math.floor(host.getBoundingClientRect().width);
      const width = Math.max(200, Math.min(400, availableWidth || 400));
      if (width === renderedWidth && host.childElementCount) return;
      renderedWidth = width;
      renderGoogleIdentityButton(
        api,
        host,
        mode === "login" ? "signin_with" : "signup_with",
        width,
        locale === "vi" ? "vi" : "en",
      );
    };
    render();

    if (typeof ResizeObserver === "undefined") return;
    const observer = new ResizeObserver(render);
    observer.observe(host);
    return () => observer.disconnect();
  }, [busy, disabled, effectiveState, locale, mode]);

  const placeholderLabel = busy
    ? "Connecting Google identity"
    : disabled
      ? "Authentication in progress"
      : effectiveState === "loading"
        ? "Loading Google access"
        : effectiveState === "error"
          ? "Retry Google access"
          : "Google access unavailable";

  return (
    <div className="grid gap-3">
      <div
        className="font-telemetry flex items-center gap-3 text-[8px] text-[var(--muted)]"
        aria-hidden="true"
      >
        <span className="h-px flex-1 bg-[var(--line)]" />
        {t("Alternative identity provider")}
        <span className="h-px flex-1 bg-[var(--line)]" />
      </div>

      {effectiveState === "ready" && !busy && !disabled ? (
        <div
          ref={hostRef}
          className="flex min-h-11 w-full justify-center overflow-hidden"
          role="group"
          aria-label={
            t(mode === "login" ? "Sign in with Google" : "Sign up with Google")
          }
        />
      ) : (
        <Button
          type="button"
          variant="secondary"
          className="w-full"
          busy={busy || effectiveState === "loading"}
          disabled={
            busy ||
            disabled ||
            effectiveState === "loading" ||
            effectiveState === "unconfigured"
          }
          onClick={() => {
            setState("loading");
            setMessage("Loading Google identity services.");
            setRetryKey((current) => current + 1);
          }}
        >
          <span
            className="grid h-5 w-5 place-items-center border border-current font-sans text-[11px] font-black normal-case"
            aria-hidden="true"
          >
            G
          </span>
          {t(placeholderLabel)}
        </Button>
      )}

      <p
        className={
          effectiveState === "error" || effectiveState === "unconfigured"
            ? "text-xs leading-5 text-[var(--danger)]"
            : "sr-only"
        }
        role={
          effectiveState === "error" || effectiveState === "unconfigured"
            ? "alert"
            : "status"
        }
        aria-live="polite"
      >
        {t(busy ? "Google identity is being verified." : message)}
      </p>
    </div>
  );
}
