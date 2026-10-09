"use client";

import { createContext, useEffect, useState } from "react";

// Single shared media-query listener for the whole tree, instead of every
// consumer of useReducedMotion() registering its own. useReducedMotion()
// reads this context when present and falls back to its own listener
// otherwise, so existing call sites keep working unchanged.
export const ReducedMotionContext = createContext<boolean | null>(null);

export function ReducedMotionProvider({ children }: { children: React.ReactNode }) {
  const [reduced, setReduced] = useState(false);

  useEffect(() => {
    const mql = window.matchMedia("(prefers-reduced-motion: reduce)");
    setReduced(mql.matches);
    const listener = (e: MediaQueryListEvent) => setReduced(e.matches);
    mql.addEventListener("change", listener);
    return () => mql.removeEventListener("change", listener);
  }, []);

  return <ReducedMotionContext.Provider value={reduced}>{children}</ReducedMotionContext.Provider>;
}
