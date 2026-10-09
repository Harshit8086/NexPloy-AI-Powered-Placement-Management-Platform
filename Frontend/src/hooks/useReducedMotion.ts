"use client";

import { useContext, useEffect, useState } from "react";
import { ReducedMotionContext } from "@/components/providers/ReducedMotionProvider";

// Single shared hook for respecting prefers-reduced-motion. Reuse this
// everywhere instead of re-querying the media list per component. Reads
// from ReducedMotionProvider when the tree has one (one listener for the
// whole app); falls back to its own listener when it doesn't, so this
// hook keeps working standalone.
export function useReducedMotion(): boolean {
  const fromContext = useContext(ReducedMotionContext);
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    if (fromContext !== null) return;
    const mql = window.matchMedia("(prefers-reduced-motion: reduce)");
    setReduced(mql.matches);
    const listener = (e: MediaQueryListEvent) => setReduced(e.matches);
    mql.addEventListener("change", listener);
    return () => mql.removeEventListener("change", listener);
  }, [fromContext]);

  return fromContext !== null ? fromContext : reduced;
}
