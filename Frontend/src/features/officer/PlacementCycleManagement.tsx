"use client";

import { useState } from "react";
import { Plus, CalendarClock } from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
  DialogFooter,
} from "@/components/ui/dialog";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { DepthCard } from "@/components/intelligence/DepthCard";
import {
  listPlacementCycles,
  createPlacementCycle,
  updatePlacementCycle,
  updatePlacementCycleStatus,
} from "@/lib/api/placement-cycles";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import type { PlacementCycle, PlacementCycleStatus } from "@/types";
import type { BadgeTone } from "@/lib/status";

const STATUS_TONE: Record<PlacementCycleStatus, BadgeTone> = {
  PLANNING: "muted",
  ACTIVE: "success",
  CLOSED: "warning",
  ARCHIVED: "default",
};

/** Allowed forward transitions, mirroring PlacementCycleService on the backend. */
const NEXT_STATUS: Record<PlacementCycleStatus, PlacementCycleStatus[]> = {
  PLANNING: ["ACTIVE", "ARCHIVED"],
  ACTIVE: ["CLOSED", "ARCHIVED"],
  CLOSED: ["ACTIVE", "ARCHIVED"],
  ARCHIVED: [],
};

export function PlacementCycleManagement() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  const { data: cycles, loading, error, reload } = useApiResource<PlacementCycle[]>(
    () => listPlacementCycles(campusId),
    [campusId],
    [],
  );

  const [editing, setEditing] = useState<PlacementCycle | null>(null);
  const [creating, setCreating] = useState(false);
  const [statusTarget, setStatusTarget] = useState<{
    cycle: PlacementCycle;
    status: PlacementCycleStatus;
  } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [name, setName] = useState("");
  const [academicYear, setAcademicYear] = useState("");
  const [startsOn, setStartsOn] = useState("");
  const [endsOn, setEndsOn] = useState("");

  const openCreate = () => {
    setEditing(null);
    setName("");
    setAcademicYear("");
    setStartsOn("");
    setEndsOn("");
    setCreating(true);
  };

  const openEdit = (cycle: PlacementCycle) => {
    setEditing(cycle);
    setName(cycle.name);
    setAcademicYear(cycle.academicYear);
    setStartsOn(cycle.startsOn ?? "");
    setEndsOn(cycle.endsOn ?? "");
    setCreating(true);
  };

  const submit = async () => {
    if (!name.trim() || !academicYear.trim()) {
      pushToast("Missing details", "Name and academic year are required.");
      return;
    }
    setSubmitting(true);
    try {
      const body = {
        name: name.trim(),
        academicYear: academicYear.trim(),
        startsOn: startsOn || null,
        endsOn: endsOn || null,
      };
      if (editing) {
        await updatePlacementCycle(editing.id, body);
        pushToast("Cycle updated", `${body.name} saved.`);
      } else {
        await createPlacementCycle({ ...body, campusId });
        pushToast("Cycle created", `${body.name} created in PLANNING.`);
      }
      reload();
      setCreating(false);
      setEditing(null);
    } catch (err) {
      pushToast("Save failed", err instanceof Error ? err.message : "Could not save the cycle.");
    } finally {
      setSubmitting(false);
    }
  };

  const confirmStatus = async () => {
    if (!statusTarget) return;
    setSubmitting(true);
    try {
      await updatePlacementCycleStatus(statusTarget.cycle.id, statusTarget.status);
      pushToast("Status updated", `${statusTarget.cycle.name} moved to ${statusTarget.status}.`);
      reload();
      setStatusTarget(null);
    } catch (err) {
      pushToast(
        "Status change failed",
        err instanceof Error ? err.message : "The transition was rejected.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-4xl">
      <PageHeader
        eyebrow="Placement"
        title="Placement Cycles"
        subtitle="Group each academic year's drives and history under a lifecycle-managed cycle."
        actions={
          <Button variant="glow" onClick={openCreate}>
            <Plus className="h-4 w-4" /> New Cycle
          </Button>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={cycles.length === 0}
        onRetry={reload}
        emptyTitle="No placement cycles yet"
        emptyMessage="Create a cycle to organise this year's drives and reporting."
      >
        <div className="space-y-4">
          {cycles.map((c) => (
            <DepthCard key={c.id} className="p-5">
              <div className="flex flex-wrap items-start justify-between gap-3">
                <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-violet/10 text-violet-bright">
                    <CalendarClock className="h-5 w-5" />
                  </div>
                  <div>
                    <CardTitle>{c.name}</CardTitle>
                    <p className="text-xs text-muted-foreground">
                      {c.academicYear}
                      {c.startsOn && ` · ${c.startsOn}`}
                      {c.endsOn && ` → ${c.endsOn}`}
                    </p>
                  </div>
                </div>
                <Badge variant={STATUS_TONE[c.status]}>{c.status}</Badge>
              </div>

              <div className="mt-4 flex flex-wrap gap-2">
                {c.status !== "ARCHIVED" && (
                  <Button variant="outline" size="sm" onClick={() => openEdit(c)}>
                    Edit
                  </Button>
                )}
                {NEXT_STATUS[c.status].map((s) => (
                  <Button
                    key={s}
                    variant="outline"
                    size="sm"
                    className={s === "ARCHIVED" ? "text-risk" : undefined}
                    onClick={() => setStatusTarget({ cycle: c, status: s })}
                  >
                    {s === "ACTIVE" && c.status === "CLOSED"
                      ? "Reopen (Activate)"
                      : `Move to ${s}`}
                  </Button>
                ))}
              </div>
            </DepthCard>
          ))}
        </div>
      </AsyncBoundary>

      {/* Create / edit */}
      <Dialog
        open={creating}
        onOpenChange={(open) => {
          if (!open) {
            setCreating(false);
            setEditing(null);
          }
        }}
      >
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit Placement Cycle" : "New Placement Cycle"}</DialogTitle>
            <DialogDescription>
              {editing
                ? "Update the cycle's details."
                : "A new cycle starts in PLANNING; activate it when drives begin."}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="cyc-name">
                Name <span className="text-risk">*</span>
              </Label>
              <Input
                id="cyc-name"
                value={name}
                onChange={(e) => setName(e.target.value)}
                placeholder="e.g. Placements 2026-27"
              />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="cyc-year">
                Academic Year <span className="text-risk">*</span>
              </Label>
              <Input
                id="cyc-year"
                value={academicYear}
                onChange={(e) => setAcademicYear(e.target.value)}
                placeholder="e.g. 2026-27"
              />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="cyc-start">Starts On</Label>
                <Input
                  id="cyc-start"
                  type="date"
                  value={startsOn}
                  onChange={(e) => setStartsOn(e.target.value)}
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="cyc-end">Ends On</Label>
                <Input
                  id="cyc-end"
                  type="date"
                  value={endsOn}
                  onChange={(e) => setEndsOn(e.target.value)}
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => {
                setCreating(false);
                setEditing(null);
              }}
              disabled={submitting}
            >
              Cancel
            </Button>
            <Button variant="glow" onClick={submit} disabled={submitting}>
              {submitting ? "Saving…" : editing ? "Save Changes" : "Create Cycle"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Status confirmation */}
      <Dialog open={!!statusTarget} onOpenChange={(open) => !open && setStatusTarget(null)}>
        <DialogContent className="max-w-md">
          {statusTarget && (
            <>
              <DialogHeader>
                <DialogTitle>Confirm status change</DialogTitle>
                <DialogDescription>
                  Move <span className="font-medium text-foreground">{statusTarget.cycle.name}</span>{" "}
                  to <span className="font-medium text-foreground">{statusTarget.status}</span>?
                  {statusTarget.status === "ARCHIVED" && " Archived cycles are read-only and terminal."}
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setStatusTarget(null)} disabled={submitting}>
                  Cancel
                </Button>
                <Button
                  variant={statusTarget.status === "ARCHIVED" ? "outline" : "glow"}
                  className={statusTarget.status === "ARCHIVED" ? "text-risk" : undefined}
                  onClick={confirmStatus}
                  disabled={submitting}
                >
                  {submitting ? "Applying…" : `Move to ${statusTarget.status}`}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
