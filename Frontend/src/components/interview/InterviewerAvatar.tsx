"use client";

import { useEffect, useRef } from "react";

// Two prerecorded AI-interviewer avatars ship in /public/interview. They are
// ambient/presentational only — muted, looping video, no audio track relied upon,
// and nothing about the interview logic depends on them.
const AVATARS = {
  female: "/interview/female-ai.mp4",
  male: "/interview/male-ai.mp4",
} as const;

export type AvatarVariant = keyof typeof AVATARS;

/**
 * A small looping video of the AI interviewer. `speaking` gently signals activity
 * (a subtle ring) but the clip loops regardless — it never carries answer data.
 */
export function InterviewerAvatar({
  variant = "female",
  speaking = false,
  className,
}: {
  variant?: AvatarVariant;
  speaking?: boolean;
  className?: string;
}) {
  const videoRef = useRef<HTMLVideoElement>(null);

  // Autoplay policies require muted + programmatic play; guard against rejection.
  useEffect(() => {
    const v = videoRef.current;
    if (!v) return;
    v.play().catch(() => {
      // autoplay blocked — the poster frame is fine, this is decorative
    });
  }, [variant]);

  return (
    <div
      className={`relative overflow-hidden rounded-xl border border-border bg-muted/40 ${
        speaking ? "ring-2 ring-violet/40" : ""
      } ${className ?? ""}`}
    >
      <video
        ref={videoRef}
        src={AVATARS[variant]}
        muted
        loop
        playsInline
        autoPlay
        className="h-full w-full object-cover"
        aria-hidden="true"
      />
    </div>
  );
}
