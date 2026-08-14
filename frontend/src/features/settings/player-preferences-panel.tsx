"use client";

import { useEffect, useState } from "react";
import { Accessibility, Download, Gauge, Maximize, RotateCcw, Volume2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { SelectField } from "@/components/ui/field";
import { playFeedback } from "@/features/settings/player-feedback";
import { usePwaStore } from "@/features/pwa/pwa-store";
import { useI18n } from "@/lib/i18n/use-i18n";
import {
  usePlayerPreferencesStore,
  type ColorVisionMode,
  type KeyPreset,
  type TargetFps,
} from "@/stores/player-preferences-store";

function Toggle({ checked, label, description, onChange }: { checked: boolean; label: string; description: string; onChange: (checked: boolean) => void }) {
  return (
    <label className="flex cursor-pointer items-start justify-between gap-5 border-b border-[var(--line)] p-4 last:border-b-0">
      <span>
        <strong className="block text-sm uppercase">{label}</strong>
        <span className="mt-1 block text-xs leading-5 text-[var(--muted)]">{description}</span>
      </span>
      <input
        type="checkbox"
        checked={checked}
        onChange={(event) => onChange(event.target.checked)}
        className="mt-1 h-5 w-5 accent-[var(--accent)]"
      />
    </label>
  );
}

export function PlayerPreferencesPanel() {
  const { t } = useI18n();
  const preferences = usePlayerPreferencesStore();
  const installPrompt = usePwaStore((state) => state.installPrompt);
  const install = usePwaStore((state) => state.install);
  const [fullscreen, setFullscreen] = useState(false);

  useEffect(() => {
    const update = () => setFullscreen(Boolean(document.fullscreenElement));
    document.addEventListener("fullscreenchange", update);
    return () => document.removeEventListener("fullscreenchange", update);
  }, []);

  async function toggleFullscreen() {
    if (document.fullscreenElement) await document.exitFullscreen();
    else await document.documentElement.requestFullscreen();
  }

  return (
    <div className="grid gap-6 xl:grid-cols-2">
      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="border-b border-[var(--line)] p-5">
          <Volume2 size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">{t("[ FEEDBACK CHANNELS ]")}</p>
          <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Sound and haptics")}</h2>
        </header>
        <Toggle checked={preferences.soundEnabled} onChange={(value) => preferences.update("soundEnabled", value)} label={t("Game sound")} description={t("Procedural interface and result tones; no audio file download is required.")} />
        <div className="border-b border-[var(--line)] p-4">
          <label className="text-sm font-bold uppercase" htmlFor="sound-volume">{t("Effects volume")} / {Math.round(preferences.soundVolume * 100)}%</label>
          <input id="sound-volume" type="range" min="0" max="1" step="0.05" value={preferences.soundVolume} onChange={(event) => preferences.update("soundVolume", Number(event.target.value))} className="mt-3 block w-full accent-[var(--accent)]" />
        </div>
        <Toggle checked={preferences.hapticsEnabled} onChange={(value) => preferences.update("hapticsEnabled", value)} label={t("Haptic feedback")} description={t("Uses short vibration patterns only when the device and browser support them.")} />
        <div className="p-4"><Button variant="secondary" onClick={() => playFeedback("success")}>{t("Preview feedback")}</Button></div>
      </section>

      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="border-b border-[var(--line)] p-5">
          <Accessibility size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">{t("[ ACCESSIBILITY ]")}</p>
          <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Visual and controls")}</h2>
        </header>
        <Toggle checked={preferences.reducedMotion} onChange={(value) => preferences.update("reducedMotion", value)} label={t("Reduce motion")} description={t("Stops decorative transitions while preserving game-state simulation.")} />
        <Toggle checked={preferences.highContrast} onChange={(value) => preferences.update("highContrast", value)} label={t("High contrast")} description={t("Strengthens borders, foreground text, and focus indicators.")} />
        <Toggle checked={preferences.largeControls} onChange={(value) => preferences.update("largeControls", value)} label={t("Large controls")} description={t("Increases minimum touch targets across game and navigation controls.")} />
        <div className="p-4">
          <SelectField label={t("Color vision palette")} value={preferences.colorVision} onChange={(event) => preferences.update("colorVision", event.target.value as ColorVisionMode)}>
            <option value="standard">{t("Standard red")}</option>
            <option value="protanopia">{t("Blue / amber")}</option>
            <option value="deuteranopia">{t("Violet / yellow")}</option>
            <option value="tritanopia">{t("Red / cyan")}</option>
          </SelectField>
        </div>
      </section>

      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="border-b border-[var(--line)] p-5">
          <Gauge size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">{t("[ RENDER PERFORMANCE ]")}</p>
          <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Frame rate")}</h2>
        </header>
        <div className="grid gap-4 p-4">
          <SelectField label={t("Target FPS")} value={String(preferences.targetFps)} onChange={(event) => preferences.update("targetFps", event.target.value === "auto" ? "auto" : Number(event.target.value) as TargetFps)}>
            <option value="auto">{t("Auto / native display")}</option>
            <option value="30">30 FPS</option>
            <option value="60">60 FPS</option>
            <option value="120">120 FPS</option>
          </SelectField>
          <p className="text-xs leading-5 text-[var(--muted)]">{t("Auto follows requestAnimationFrame at the display's native refresh rate. Physics remains deterministic and does not speed up with FPS.")}</p>
        </div>
        <Toggle checked={preferences.showFps} onChange={(value) => preferences.update("showFps", value)} label={t("Show FPS monitor")} description={t("Displays measured browser frames per second in the lower-right corner.")} />
      </section>

      <section className="border border-[var(--line)] bg-[var(--surface)]">
        <header className="border-b border-[var(--line)] p-5">
          <Maximize size={20} className="text-[var(--accent)]" aria-hidden="true" />
          <p className="font-telemetry mt-5 text-[8px] text-[var(--muted)]">{t("[ DEVICE MODE ]")}</p>
          <h2 className="mt-1 text-2xl font-black uppercase tracking-[-0.04em]">{t("Display and install")}</h2>
        </header>
        <div className="grid gap-4 p-4">
          <SelectField label={t("Keyboard preset")} value={preferences.keyPreset} onChange={(event) => preferences.update("keyPreset", event.target.value as KeyPreset)}>
            <option value="both">{t("Arrow keys + WASD")}</option>
            <option value="arrows">{t("Arrow keys only")}</option>
            <option value="wasd">{t("WASD only")}</option>
          </SelectField>
          <div className="flex flex-wrap gap-2">
            <Button variant="secondary" onClick={() => void toggleFullscreen()}><Maximize size={14} aria-hidden="true" />{t(fullscreen ? "Exit fullscreen" : "Enter fullscreen")}</Button>
            {installPrompt ? <Button onClick={() => void install()}><Download size={14} aria-hidden="true" />{t("Install Gameio")}</Button> : null}
            <Button variant="ghost" onClick={preferences.reset}><RotateCcw size={14} aria-hidden="true" />{t("Reset player settings")}</Button>
          </div>
          {!installPrompt ? <p className="text-xs leading-5 text-[var(--muted)]">{t("Install becomes available when the browser confirms PWA eligibility. Offline games remain accessible from the navigation.")}</p> : null}
        </div>
      </section>
    </div>
  );
}
