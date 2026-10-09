"use client";

import { useState } from "react";
import Link from "next/link";
import { Network, Users, Award, ArrowUpRight, Building2, ShieldAlert, Plus } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
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
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Progress } from "@/components/ui/progress";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import {
  INSTITUTE_NAME,
  listCampuses,
  createCampus,
  updateCampus,
  setCampusActive,
} from "@/lib/api/campuses";
import { getInstituteAnalytics, listAllCampusAnalytics } from "@/lib/api/analytics";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import type { CampusAnalytics, CampusSummary, InstituteAnalytics } from "@/types";

/**
 * The read type CampusSummary (id/name/city/state) does not carry the backend `active` flag —
 * mapCampus intentionally drops it — so at runtime it is absent. We read it defensively and treat
 * an absent flag as active, so the toggle always works; distinguishing genuinely inactive campuses
 * in the badge needs `active` added to CampusSummary + mapCampus (see report).
 */
const isCampusActive = (c: CampusSummary): boolean =>
  (c as CampusSummary & { active?: boolean }).active ?? true;

export default function CampusManagementPage() {
  const activeCampusId = useAppStore((s) => s.activeCampusId);
  const setActiveCampusId = useAppStore((s) => s.setActiveCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  // Live: institute rollup (totals + per-campus comparison) + each campus's dashboard snapshot
  // (drives, recruiters, offers, CTC) + the editable campus directory (identity + active flag).
  // Every analytics figure is a server-side aggregation; the directory is campus identity.
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [institute, byCampus, campusList] = await Promise.all([
        getInstituteAnalytics().catch(() => null),
        listAllCampusAnalytics().catch(() => ({} as Record<string, CampusAnalytics>)),
        listCampuses().catch(() => [] as CampusSummary[]),
      ]);
      return { institute, byCampus, campusList };
    },
    [],
    {
      institute: null as InstituteAnalytics | null,
      byCampus: {} as Record<string, CampusAnalytics>,
      campusList: [] as CampusSummary[],
    },
  );

  const institute = data.institute;
  const byCampus = data.byCampus;
  const campusList = data.campusList;
  const campuses = institute?.campuses ?? [];
  const totals = institute?.totals;

  const switchTo = (id: string, name: string) => {
    setActiveCampusId(id);
    pushToast("Campus scope changed", `Now showing ${name} metrics.`);
  };

  // ---- CRUD dialog state ----
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<CampusSummary | null>(null);
  const [deactivateTarget, setDeactivateTarget] = useState<CampusSummary | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [formId, setFormId] = useState("");
  const [formName, setFormName] = useState("");
  const [formCity, setFormCity] = useState("");
  const [formState, setFormState] = useState("");

  const openCreate = () => {
    setEditing(null);
    setFormId("");
    setFormName("");
    setFormCity("");
    setFormState("");
    setCreating(true);
  };

  const openEdit = (c: CampusSummary) => {
    setEditing(c);
    setFormId(c.id);
    setFormName(c.name);
    setFormCity(c.city ?? "");
    setFormState(c.state ?? "");
    setCreating(true);
  };

  const closeForm = () => {
    setCreating(false);
    setEditing(null);
  };

  const submit = async () => {
    const id = formId.trim();
    const name = formName.trim();
    if (!editing && !id) {
      pushToast("Missing details", "A campus id (primary key) is required.");
      return;
    }
    if (!name) {
      pushToast("Missing details", "Campus name is required.");
      return;
    }
    setSubmitting(true);
    try {
      const identity = {
        name,
        city: formCity.trim() || null,
        state: formState.trim() || null,
      };
      if (editing) {
        await updateCampus(editing.id, identity);
        pushToast("Campus updated", `${name} saved.`);
      } else {
        await createCampus({ id, ...identity });
        pushToast("Campus created", `${name} added.`);
      }
      reload();
      closeForm();
    } catch (err) {
      pushToast("Save failed", err instanceof Error ? err.message : "Could not save the campus.");
    } finally {
      setSubmitting(false);
    }
  };

  const confirmDeactivate = async () => {
    if (!deactivateTarget) return;
    setSubmitting(true);
    try {
      await setCampusActive(deactivateTarget.id, false);
      pushToast("Campus deactivated", `${deactivateTarget.name} is now hidden; its history is preserved.`);
      reload();
      setDeactivateTarget(null);
    } catch (err) {
      pushToast(
        "Deactivation failed",
        err instanceof Error ? err.message : "Could not deactivate the campus.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const activate = async (c: CampusSummary) => {
    setSubmitting(true);
    try {
      await setCampusActive(c.id, true);
      pushToast("Campus reactivated", `${c.name} is visible again.`);
      reload();
    } catch (err) {
      pushToast(
        "Activation failed",
        err instanceof Error ? err.message : "Could not reactivate the campus.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Campuses"
        title="Campus Management"
        subtitle={`${INSTITUTE_NAME} — every campus, its cohort and its placement operations.`}
        actions={
          <Button variant="glow" onClick={openCreate}>
            <Plus className="h-4 w-4" /> New Campus
          </Button>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!institute && campusList.length === 0}
        onRetry={reload}
        emptyTitle="No campus data"
        emptyMessage="No campuses have been created yet. Use “New Campus” to add one."
      >
        <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Campuses" value={campusList.length || campuses.length} icon={Network} accent="violet" />
          <StatCard label="Students" value={totals?.totalStudents ?? 0} icon={Users} />
          <StatCard label="Offers" value={totals?.offers ?? 0} icon={Award} accent="success" />
          <StatCard label="At Risk" value={totals?.atRisk ?? 0} icon={ShieldAlert} accent="risk" />
        </div>

        <DataReveal stagger className="mb-5 grid gap-4 md:grid-cols-3">
          {campuses.map((c) => {
            const isActive = c.campusId === activeCampusId;
            const snap = byCampus[c.campusId]?.snapshot;
            const readyPct = c.students ? Math.round((c.ready / c.students) * 100) : 0;
            return (
              <DataRevealItem key={c.campusId}>
                <DepthCard className={isActive ? "h-full border-violet/40 p-5 shadow-glow" : "h-full p-5"}>
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <CardTitle className="truncate">{c.campus}</CardTitle>
                      <p className="text-xs text-muted-foreground">{c.city}</p>
                    </div>
                    {isActive && <Badge variant="default">Active</Badge>}
                  </div>

                  <div className="mt-4">
                    <div className="mb-1 flex items-center justify-between text-xs text-muted-foreground">
                      <span>Placement ready</span>
                      <span>{readyPct}%</span>
                    </div>
                    <Progress value={readyPct} />
                  </div>

                  <div className="mt-4 space-y-1.5 text-xs text-muted-foreground">
                    <p className="flex items-center gap-1.5">
                      <Users className="h-3.5 w-3.5" /> {c.students.toLocaleString()} students ·{" "}
                      {c.ready.toLocaleString()} placement ready
                    </p>
                    <p className="flex items-center gap-1.5">
                      <Building2 className="h-3.5 w-3.5" /> {snap?.activeDrives ?? 0} active drives ·{" "}
                      {snap?.recruitersCount ?? 0} recruiters
                    </p>
                    <p className="flex items-center gap-1.5">
                      <Award className="h-3.5 w-3.5" /> {snap?.offersCount ?? 0} offers · avg{" "}
                      {c.averageCtc}
                    </p>
                  </div>

                  <div className="mt-4 flex flex-wrap gap-2">
                    <Button
                      variant={isActive ? "outline" : "glow"}
                      size="sm"
                      onClick={() => switchTo(c.campusId, c.campus)}
                      disabled={isActive}
                    >
                      {isActive ? "Current scope" : "Switch campus"}
                    </Button>
                    <Button asChild variant="outline" size="sm">
                      <Link href="/officer">
                        Operations <ArrowUpRight className="h-3.5 w-3.5" />
                      </Link>
                    </Button>
                  </div>
                </DepthCard>
              </DataRevealItem>
            );
          })}
        </DataReveal>

        {/* Editable campus directory (identity + lifecycle), driven by listCampuses(). */}
        <Card className="mb-5 overflow-x-auto p-5">
          <div className="mb-3 flex items-center justify-between">
            <CardTitle>Campus Directory</CardTitle>
          </div>
          {campusList.length === 0 ? (
            <p className="py-6 text-center text-sm text-muted-foreground">
              No campuses in the directory yet. Use “New Campus” to add one.
            </p>
          ) : (
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>Campus</TableHead>
                  <TableHead>ID</TableHead>
                  <TableHead>City</TableHead>
                  <TableHead>State</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead className="text-right">Actions</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {campusList.map((c) => {
                  const active = isCampusActive(c);
                  return (
                    <TableRow
                      key={c.id}
                      className={c.id === activeCampusId ? "bg-violet/5" : undefined}
                    >
                      <TableCell className="font-medium">{c.name}</TableCell>
                      <TableCell className="font-mono text-xs text-muted-foreground">{c.id}</TableCell>
                      <TableCell className="text-muted-foreground">{c.city || "—"}</TableCell>
                      <TableCell className="text-muted-foreground">{c.state || "—"}</TableCell>
                      <TableCell>
                        <Badge variant={active ? "success" : "muted"}>
                          {active ? "Active" : "Inactive"}
                        </Badge>
                      </TableCell>
                      <TableCell>
                        <div className="flex flex-wrap justify-end gap-2">
                          <Button variant="outline" size="sm" onClick={() => openEdit(c)}>
                            Edit
                          </Button>
                          {active ? (
                            <Button
                              variant="outline"
                              size="sm"
                              className="text-risk"
                              onClick={() => setDeactivateTarget(c)}
                            >
                              Deactivate
                            </Button>
                          ) : (
                            <Button
                              variant="outline"
                              size="sm"
                              onClick={() => activate(c)}
                              disabled={submitting}
                            >
                              Activate
                            </Button>
                          )}
                        </div>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          )}
        </Card>

        <Card className="overflow-x-auto p-5">
          <div className="mb-3 flex items-center justify-between">
            <CardTitle>Campus Comparison</CardTitle>
          </div>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Campus</TableHead>
                <TableHead>City</TableHead>
                <TableHead>Students</TableHead>
                <TableHead>Ready</TableHead>
                <TableHead>Placed</TableHead>
                <TableHead>Placement Rate</TableHead>
                <TableHead>Average CTC</TableHead>
                <TableHead>Highest CTC</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {campuses.map((c) => (
                <TableRow
                  key={c.campusId}
                  className={c.campusId === activeCampusId ? "bg-violet/5" : undefined}
                >
                  <TableCell className="font-medium">{c.campus}</TableCell>
                  <TableCell className="text-muted-foreground">{c.city}</TableCell>
                  <TableCell className="tabular-nums">{c.students.toLocaleString()}</TableCell>
                  <TableCell className="tabular-nums">{c.ready.toLocaleString()}</TableCell>
                  <TableCell className="tabular-nums">{c.placed.toLocaleString()}</TableCell>
                  <TableCell className="tabular-nums text-violet-bright">{c.placementRate}%</TableCell>
                  <TableCell className="tabular-nums">{c.averageCtc}</TableCell>
                  <TableCell className="tabular-nums">
                    {byCampus[c.campusId]?.snapshot.highestCtc ?? "—"}
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      </AsyncBoundary>

      {/* Create / edit campus */}
      <Dialog open={creating} onOpenChange={(open) => !open && closeForm()}>
        <DialogContent className="max-w-lg">
          <DialogHeader>
            <DialogTitle>{editing ? "Edit Campus" : "New Campus"}</DialogTitle>
            <DialogDescription>
              {editing
                ? "Update the campus's identity. The id is the primary key and cannot change."
                : "Create a campus. The id is a client-supplied primary key and is permanent."}
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="campus-id">
                Campus ID <span className="text-risk">*</span>
              </Label>
              <Input
                id="campus-id"
                value={formId}
                onChange={(e) => setFormId(e.target.value)}
                placeholder="e.g. blr-main"
                disabled={!!editing}
              />
              <p className="text-xs text-muted-foreground">
                Client-supplied primary key — a short, permanent slug/code (e.g. blr-main). Cannot be
                changed after creation.
              </p>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="campus-name">
                Name <span className="text-risk">*</span>
              </Label>
              <Input
                id="campus-name"
                value={formName}
                onChange={(e) => setFormName(e.target.value)}
                placeholder="e.g. Bangalore Main Campus"
              />
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="campus-city">City</Label>
                <Input
                  id="campus-city"
                  value={formCity}
                  onChange={(e) => setFormCity(e.target.value)}
                  placeholder="e.g. Bengaluru"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="campus-state">State</Label>
                <Input
                  id="campus-state"
                  value={formState}
                  onChange={(e) => setFormState(e.target.value)}
                  placeholder="e.g. Karnataka"
                />
              </div>
            </div>
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={closeForm} disabled={submitting}>
              Cancel
            </Button>
            <Button variant="glow" onClick={submit} disabled={submitting}>
              {submitting ? "Saving…" : editing ? "Save Changes" : "Create Campus"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Deactivate confirmation */}
      <Dialog open={!!deactivateTarget} onOpenChange={(open) => !open && setDeactivateTarget(null)}>
        <DialogContent className="max-w-md">
          {deactivateTarget && (
            <>
              <DialogHeader>
                <DialogTitle>Deactivate campus?</DialogTitle>
                <DialogDescription>
                  Deactivate{" "}
                  <span className="font-medium text-foreground">{deactivateTarget.name}</span>? This
                  is a soft-delete: the campus is hidden from active scopes but all of its history —
                  students, drives, offers and analytics — is preserved. You can reactivate it later.
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button
                  variant="outline"
                  onClick={() => setDeactivateTarget(null)}
                  disabled={submitting}
                >
                  Cancel
                </Button>
                <Button
                  variant="outline"
                  className="text-risk"
                  onClick={confirmDeactivate}
                  disabled={submitting}
                >
                  {submitting ? "Deactivating…" : "Deactivate"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>
    </div>
  );
}
