"use client";

import { ShieldCheck, ShieldQuestion, Link2 } from "lucide-react";

/**
 * Small badge describing a skill's verification level — same mapping as My Skills: VERIFIED
 * (passed the AI viva), EVIDENCED (proof link attached), otherwise Self-declared. Renders nothing
 * for a null/empty status (e.g. a skill the student never recorded), so callers can drop it in
 * unconditionally.
 */
export function VerificationBadge({ status }: { status?: string | null }) {
  if (!status) return null;

  const meta =
    status === "VERIFIED"
      ? { label: "Verified", cls: "bg-success/15 text-success", Icon: ShieldCheck }
      : status === "EVIDENCED"
        ? { label: "Evidenced", cls: "bg-cyan-400/15 text-cyan-400", Icon: Link2 }
        : { label: "Self-declared", cls: "bg-muted text-muted-foreground", Icon: ShieldQuestion };

  return (
    <span
      className={`inline-flex items-center gap-1 rounded-full px-1.5 py-0.5 text-[10px] font-medium ${meta.cls}`}
    >
      <meta.Icon className="h-3 w-3" />
      {meta.label}
    </span>
  );
}
