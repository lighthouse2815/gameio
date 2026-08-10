"use client";

import { useState, type FormEvent } from "react";
import { useRouter } from "next/navigation";
import { useMutation, useQueryClient } from "@tanstack/react-query";
import { LogOut, MonitorCog, UserRound } from "lucide-react";
import { LoginRequired } from "@/components/auth/login-required";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { ErrorState, Skeleton } from "@/components/ui/states";
import { useToast } from "@/components/ui/toast";
import {
  isUnauthenticated,
  sessionQueryKey,
  useLogout,
  useSession,
} from "@/features/auth/hooks";
import { profileApi } from "@/features/profile/api";
import { getErrorMessage } from "@/lib/api/api-error";
import { useThemeStore, type ThemeMode } from "@/stores/theme-store";

export function SettingsScreen() {
  const router = useRouter();
  const toast = useToast();
  const queryClient = useQueryClient();
  const session = useSession();
  const logout = useLogout();
  const mode = useThemeStore((state) => state.mode);
  const setMode = useThemeStore((state) => state.setMode);
  const [avatarOverride, setAvatarOverride] = useState<string | null>(null);
  const avatarUrl = avatarOverride ?? session.data?.avatarUrl ?? "";

  const updateProfile = useMutation({
    mutationFn: () =>
      profileApi.updateMe({ avatarUrl: avatarUrl.trim() || null }),
    onSuccess: (user) => {
      setAvatarOverride(user.avatarUrl ?? "");
      queryClient.setQueryData(sessionQueryKey, user);
      void queryClient.invalidateQueries({ queryKey: ["profile", user.username] });
      toast({ title: "Profile updated", description: "Avatar record synchronized.", tone: "success" });
    },
    onError: (error) =>
      toast({ title: "Update rejected", description: getErrorMessage(error), tone: "error" }),
  });

  function saveAvatar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const value = avatarUrl.trim();
    if (value && !value.startsWith("https://")) {
      toast({ title: "Invalid avatar URL", description: "Use an HTTPS image URL or leave the field empty.", tone: "error" });
      return;
    }
    updateProfile.mutate();
  }

  async function signOut() {
    await logout.mutateAsync().catch(() => undefined);
    router.replace("/");
    toast({ title: "Session closed", description: "Refresh cookie and runtime token cleared.", tone: "info" });
  }

  if (session.isLoading) return <Skeleton className="h-96" />;
  if (session.isError && isUnauthenticated(session.error)) {
    return <LoginRequired title="Settings locked" />;
  }
  if (session.isError) {
    return <ErrorState title="Identity link unavailable" description={getErrorMessage(session.error)} onAction={() => void session.refetch()} />;
  }

  return (
    <div className="grid gap-6 lg:grid-cols-2">
      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="border-b border-[var(--line)] p-5">
          <UserRound size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">[ PLAYER RECORD ]</p>
          <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">Identity</h2>
        </header>
        <form className="grid gap-5 p-5" onSubmit={saveAvatar}>
          <Field label="Username" value={session.data?.username ?? ""} disabled readOnly />
          <Field label="Email" type="email" value={session.data?.email ?? ""} disabled readOnly hint="Email changes are not exposed by the current backend contract." />
          <Field
            label="Avatar HTTPS URL"
            name="avatarUrl"
            type="url"
            value={avatarUrl}
            onChange={(event) => setAvatarOverride(event.target.value)}
            placeholder="https://..."
            hint="Leave empty to remove the custom avatar."
          />
          <Button busy={updateProfile.isPending}>Save player record</Button>
        </form>
      </section>

      <div className="grid content-start gap-6">
        <section className="border border-[var(--line)] bg-[var(--surface)]">
          <header className="border-b border-[var(--line)] p-5">
            <MonitorCog size={20} className="text-[var(--accent)]" aria-hidden="true" />
            <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">[ DISPLAY SUBSTRATE ]</p>
            <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">Interface theme</h2>
          </header>
          <div className="grid gap-px bg-[var(--line)] sm:grid-cols-2">
            {(["dark", "light"] as ThemeMode[]).map((theme) => (
              <button
                type="button"
                key={theme}
                onClick={() => setMode(theme)}
                className={
                  "min-h-36 bg-[var(--background)] p-5 text-left transition-colors " +
                  (mode === theme ? "shadow-[inset_0_-4px_0_var(--accent)]" : "hover:bg-[var(--surface-strong)]")
                }
                aria-pressed={mode === theme}
              >
                <span className="font-telemetry text-[9px] text-[var(--muted)]">[ {theme} ]</span>
                <span className="mt-12 flex items-center justify-between text-xl font-black uppercase">
                  {theme} substrate
                  <span className={"h-3 w-3 " + (mode === theme ? "bg-[var(--accent)]" : "border border-[var(--line-strong)]")} />
                </span>
              </button>
            ))}
          </div>
        </section>

        <section className="border border-[var(--danger)] bg-[var(--surface)] p-5">
          <LogOut size={20} className="text-[var(--danger)]" aria-hidden="true" />
          <h2 className="mt-5 text-xl font-black uppercase tracking-[-0.04em]">Close session</h2>
          <p className="mt-2 text-xs leading-5 text-[var(--muted)]">
            Revokes the server refresh cookie and clears the access token held in runtime memory.
          </p>
          <Button className="mt-5" variant="danger" onClick={signOut} busy={logout.isPending}>
            Sign out
          </Button>
        </section>
      </div>
    </div>
  );
}
