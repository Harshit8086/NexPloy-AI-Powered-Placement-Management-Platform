"use client";

import { usePathname } from "next/navigation";
import { motion } from "framer-motion";
import { useReducedMotion } from "@/hooks/useReducedMotion";

/**
 * Shared route-transition wrapper. Mount once around routed content
 * (AppShell's <main>, and directly around top-level public pages) — do
 * not build page-specific enter/exit animations elsewhere.
 *
 * Subtle fade + small vertical settle + a hair of blur, ~300ms. No slides,
 * no page flips. Falls back to an instant swap under reduced motion.
 *
 * The incoming route is remounted per `pathname` (via key) so the enter
 * animation runs on every navigation. We deliberately do NOT use
 * AnimatePresence `mode="wait"` here: under the Next.js App Router the
 * outgoing route's exit promise can fail to resolve, which leaves the
 * incoming page pinned at its `initial` (opacity 0) state — a blank screen
 * until the next refresh. Remount-on-key has no exit phase to deadlock on.
 */
export function PageTransition({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const reduced = useReducedMotion();

  if (reduced) {
    return <div key={pathname}>{children}</div>;
  }

  return (
    <motion.div
      key={pathname}
      initial={{ opacity: 0, y: 8, filter: "blur(2px)" }}
      animate={{ opacity: 1, y: 0, filter: "blur(0px)" }}
      transition={{ duration: 0.3, ease: [0.22, 1, 0.36, 1] }}
    >
      {children}
    </motion.div>
  );
}
