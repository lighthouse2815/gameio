"use client";

import { useEffect, useRef, useState } from "react";
import { usePlayerPreferencesStore } from "@/stores/player-preferences-store";

export function PerformanceHud() {
  const enabled = usePlayerPreferencesStore((state) => state.showFps);
  const target = usePlayerPreferencesStore((state) => state.targetFps);
  const [fps, setFps] = useState(0);
  const frameCount = useRef(0);
  const sampledAt = useRef(0);

  useEffect(() => {
    if (!enabled) return;
    let frame = 0;
    const sample = (time: number) => {
      frameCount.current += 1;
      if (!sampledAt.current) sampledAt.current = time;
      const elapsed = time - sampledAt.current;
      if (elapsed >= 500) {
        setFps(Math.round((frameCount.current * 1000) / elapsed));
        frameCount.current = 0;
        sampledAt.current = time;
      }
      frame = requestAnimationFrame(sample);
    };
    frame = requestAnimationFrame(sample);
    return () => cancelAnimationFrame(frame);
  }, [enabled]);

  if (!enabled) return null;
  return (
    <output className="font-telemetry fixed bottom-3 right-3 z-[80] border border-[var(--accent)] bg-[var(--background)] px-3 py-2 text-[9px] text-[var(--accent)] shadow-[3px_3px_0_var(--line-strong)]">
      FPS {fps} / {target === "auto" ? "NATIVE" : target}
    </output>
  );
}
