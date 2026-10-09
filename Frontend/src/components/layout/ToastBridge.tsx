"use client";

import { useEffect, useRef } from "react";
import { toast } from "sonner";
import { useAppStore } from "@/hooks/useAppStore";

/**
 * Bridges pushToast() calls in the zustand store to visible sonner toasts.
 * Any simulated action (assign mentor, apply schedule, accept offer, etc.)
 * should call useAppStore.getState().pushToast(title, message) rather than
 * importing sonner directly, so all toast triggers stay centralized.
 */
export function ToastBridge() {
  const toasts = useAppStore((s) => s.toasts);
  const dismissToast = useAppStore((s) => s.dismissToast);
  const shown = useRef(new Set<string>());

  useEffect(() => {
    toasts.forEach((t) => {
      if (shown.current.has(t.id)) return;
      shown.current.add(t.id);
      toast(t.title, { description: t.message });
      dismissToast(t.id);
    });
  }, [toasts, dismissToast]);

  return null;
}
