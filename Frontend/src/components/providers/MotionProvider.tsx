"use client";

import { MotionConfig } from "framer-motion";
import { ReducedMotionProvider } from "@/components/providers/ReducedMotionProvider";

// Root-level composition of the two motion primitives every animated
// surface in the app depends on:
//  - ReducedMotionProvider: shared prefers-reduced-motion state, read via
//    the useReducedMotion() hook for JS-driven animation (rAF counters,
//    R3F scenes, canvas particles).
//  - MotionConfig: framer-motion's own reducedMotion="user" flag, which
//    automatically short-circuits every `motion.*` component's animation
//    to an instant transition when the OS setting is on, and sets the
//    shared default transition curve used across the app.
export function MotionProvider({ children }: { children: React.ReactNode }) {
  return (
    <ReducedMotionProvider>
      <MotionConfig reducedMotion="user" transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}>
        {children}
      </MotionConfig>
    </ReducedMotionProvider>
  );
}
