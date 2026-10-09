"use client";

import { useEffect, useState } from "react";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * Shared count-up primitive. Eases a number from its previous value to
 * `value` whenever `value` (or `resetKey`) changes. Used by AnimatedMetric
 * and ScoreRing so every KPI/score in the app counts up the same way.
 */
export function useAnimatedNumber(value: number, durationMs = 800, resetKey?: string | number) {
  const reduced = useReducedMotion();
  const [display, setDisplay] = useState(reduced ? value : 0);

  useEffect(() => {
    if (reduced) {
      setDisplay(value);
      return;
    }
    const start = performance.now();
    const from = 0;
    let raf: number;
    const tick = (now: number) => {
      const t = Math.min(1, (now - start) / durationMs);
      const eased = 1 - Math.pow(1 - t, 3);
      setDisplay(from + eased * (value - from));
      if (t < 1) raf = requestAnimationFrame(tick);
    };
    raf = requestAnimationFrame(tick);
    return () => cancelAnimationFrame(raf);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [value, resetKey, reduced, durationMs]);

  return display;
}
