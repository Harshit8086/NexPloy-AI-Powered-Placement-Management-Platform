"use client";

import { useState } from "react";
import { Send, CheckCircle2 } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useAppStore } from "@/hooks/useAppStore";
import { applyToDrive } from "@/lib/api/applications";

/** Pulls the human-readable reason out of the backend's JSON ApiError body, if present. */
function readErrorMessage(error: unknown): string {
  if (error instanceof Error) {
    try {
      const parsed = JSON.parse(error.message) as { message?: string };
      if (parsed.message) return parsed.message;
    } catch {
      // Not JSON — fall through to the raw message.
    }
    return error.message;
  }
  return "Could not submit your application. Please try again.";
}

interface ApplyButtonProps {
  driveId: string;
  eligible: boolean;
  /** True when the student already has an application on this drive. */
  alreadyApplied: boolean;
  /** False when the drive is not ACTIVE (closed/draft/completed). */
  driveOpen: boolean;
}

/**
 * Client-side apply control for a drive. The backend is the source of truth — it re-checks
 * eligibility, the drive being open, and duplicates — but we disable the button up front for
 * the states we already know, so the common cases never round-trip to fail.
 */
export function ApplyButton({ driveId, eligible, alreadyApplied, driveOpen }: ApplyButtonProps) {
  const pushToast = useAppStore((s) => s.pushToast);
  const [coverNote, setCoverNote] = useState("");
  const [saving, setSaving] = useState(false);
  const [applied, setApplied] = useState(alreadyApplied);

  if (applied) {
    return (
      <Button variant="outline" disabled className="gap-1.5">
        <CheckCircle2 className="h-3.5 w-3.5 text-success" /> Applied
      </Button>
    );
  }

  if (!driveOpen) {
    return (
      <Button variant="outline" disabled>
        Applications closed
      </Button>
    );
  }

  const submit = async () => {
    setSaving(true);
    try {
      await applyToDrive(driveId, { coverNote: coverNote.trim() || undefined });
      setApplied(true);
      pushToast("Application submitted", "The recruiter will see your profile in their pool.");
    } catch (error) {
      pushToast("Could not apply", readErrorMessage(error));
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="w-full max-w-sm space-y-2">
      <div>
        <Label className="text-xs text-muted-foreground">Cover note (optional)</Label>
        <Input
          value={coverNote}
          maxLength={2000}
          placeholder="One line on why you're a fit"
          onChange={(e) => setCoverNote(e.target.value)}
          className="mt-1 h-8 text-sm"
          disabled={saving}
        />
      </div>
      <Button variant="glow" onClick={submit} disabled={!eligible || saving} className="gap-1.5">
        <Send className="h-3.5 w-3.5" /> {saving ? "Submitting…" : "Apply to this drive"}
      </Button>
      {!eligible && (
        <p className="text-xs text-risk">
          You don&apos;t meet this drive&apos;s eligibility criteria yet.
        </p>
      )}
    </div>
  );
}
