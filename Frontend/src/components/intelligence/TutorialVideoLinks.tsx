"use client";

import { PlayCircle, ExternalLink } from "lucide-react";
import { isSafeHttpUrl } from "@/lib/url";
import type { TutorialVideo } from "@/lib/api/students";

/**
 * Renders real YouTube tutorial links (never fabricated) for a set of skill gaps. Only https URLs
 * are shown — each is filtered through {@link isSafeHttpUrl}. When nothing safe is available the
 * component renders nothing, so callers can drop it in unconditionally. This is the shared surface
 * behind the "learn your weak areas" links on the mock-interview report and the roadmap.
 */
export function TutorialVideoLinks({ videos }: { videos: TutorialVideo[] }) {
  const safeVideos = (videos ?? []).filter((v) => isSafeHttpUrl(v.url));
  if (safeVideos.length === 0) return null;
  return (
    <div>
      <p className="mb-1.5 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
        <PlayCircle className="h-3.5 w-3.5 text-red-500" /> Video tutorials
      </p>
      <div className="grid gap-2 sm:grid-cols-2">
        {safeVideos.map((v) => (
          <a
            key={v.url}
            href={v.url}
            target="_blank"
            rel="noopener noreferrer"
            className="group flex items-start justify-between gap-2 rounded-lg border border-border p-3 transition-colors hover:border-violet/40"
          >
            <div className="min-w-0">
              <span className="block truncate text-sm font-medium text-foreground group-hover:text-violet-bright">
                {v.title}
              </span>
              {v.channel && (
                <span className="block truncate text-xs text-muted-foreground">{v.channel}</span>
              )}
            </div>
            <ExternalLink className="h-3.5 w-3.5 shrink-0 text-muted-foreground" />
          </a>
        ))}
      </div>
    </div>
  );
}
