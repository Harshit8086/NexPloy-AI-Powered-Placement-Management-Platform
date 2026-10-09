"use client";

import { useEffect, useState } from "react";

// Shared breakpoint check for gating heavy visual effects (3D scenes,
// particle fields, parallax) on small/touch screens. Mirrors Tailwind's
// `md` breakpoint so behavior lines up with responsive layout classes.
export function useIsMobile(): boolean {
  const [isMobile, setIsMobile] = useState(false);

  useEffect(() => {
    const mql = window.matchMedia("(max-width: 767px)");
    setIsMobile(mql.matches);
    const listener = (e: MediaQueryListEvent) => setIsMobile(e.matches);
    mql.addEventListener("change", listener);
    return () => mql.removeEventListener("change", listener);
  }, []);

  return isMobile;
}
