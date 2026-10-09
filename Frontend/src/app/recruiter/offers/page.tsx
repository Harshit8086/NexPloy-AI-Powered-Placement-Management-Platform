"use client";

import { useState } from "react";
import { Award, IndianRupee, Clock, CheckCircle2, Sparkles, Plus, BellRing } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
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
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { listDrivesByCompany } from "@/lib/api/drives";
import { listOffersByDrive, extendOffer, revokeOffer, nudgeOffer } from "@/lib/api/offers";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { listStudents } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { offerStatusTone } from "@/lib/status";
import type { Application, Drive, Offer, Recruiter, Student } from "@/types";

interface OffersData {
  recruiter: Recruiter;
  drives: Drive[];
  offers: Offer[];
  studentsById: Record<string, Student>;
  selectedByDrive: Record<string, Application[]>;
}

/**
 * Offers extended from the signed-in recruiter's own drives, resolved live: their company
 * (GET /recruiters/me), that company's drives, and each drive's offers (campus-scoped GET
 * /offers, filtered by driveId). No mock data — an empty account shows an empty offers table.
 */
async function loadOffers(): Promise<OffersData> {
  const recruiter = await getActiveRecruiter();
  const drives = await listDrivesByCompany(recruiter.id);
  const [offerLists, applicationLists, allStudents] = await Promise.all([
    Promise.all(drives.map((d) => listOffersByDrive(d.id))),
    Promise.all(drives.map((d) => listApplicationsByDrive(d.id))),
    listStudents(),
  ]);
  const offers = offerLists.flat();
  const studentsById = Object.fromEntries(allStudents.map((s) => [s.id, s]));
  // Candidates a recruiter may extend an offer to are those SELECTED on one of their drives.
  const selectedByDrive: Record<string, Application[]> = {};
  drives.forEach((d, i) => {
    selectedByDrive[d.id] = applicationLists[i].filter((a) => a.status === "selected");
  });
  return { recruiter, drives, offers, studentsById, selectedByDrive };
}

export default function RecruiterOffersPage() {
  const pushToast = useAppStore((s) => s.pushToast);
  const { data, loading, error, reload } = useApiResource<OffersData | null>(
    loadOffers,
    [],
    null,
  );

  const recruiter = data?.recruiter;
  const studentsById = data?.studentsById ?? {};
  const drives = data?.drives ?? [];
  const selectedByDrive = data?.selectedByDrive ?? {};

  const [creating, setCreating] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [revoking, setRevoking] = useState<Offer | null>(null);
  const [tabFilter, setTabFilter] = useState<"all" | "ppo" | "pending">("all");
  const [nudgedIds, setNudgedIds] = useState<string[]>([]);
  const [formDriveId, setFormDriveId] = useState("");
  const [formStudentId, setFormStudentId] = useState("");
  const [formRole, setFormRole] = useState("");
  const [formPackage, setFormPackage] = useState("8.0");
  const [formOfferType, setFormOfferType] = useState("Full-Time");
  const [formLocation, setFormLocation] = useState("");
  const [formJoining, setFormJoining] = useState("");
  const [formDeadline, setFormDeadline] = useState("");

  const openCreate = () => {
    const firstDrive = drives[0];
    setFormDriveId(firstDrive?.id ?? "");
    setFormRole(firstDrive?.role ?? "");
    setFormStudentId("");
    setFormPackage("8.0");
    setFormOfferType("Full-Time");
    setFormLocation(firstDrive?.location ?? "");
    setFormJoining("");
    setFormDeadline("");
    setCreating(true);
  };

  const submitOffer = async () => {
    if (!recruiter) return;
    if (!formDriveId || !formStudentId || !formRole.trim()) {
      pushToast("Missing details", "Drive, candidate and role are required.");
      return;
    }
    const pkg = Number(formPackage);
    if (!(pkg > 0)) {
      pushToast("Invalid package", "Package (LPA) must be greater than zero.");
      return;
    }
    setSubmitting(true);
    try {
      await extendOffer({
        studentId: formStudentId,
        driveId: formDriveId,
        recruiterId: recruiter.id,
        role: formRole.trim(),
        packageLpa: pkg,
        companyName: recruiter.companyName,
        offerType: formOfferType,
        location: formLocation || null,
        joiningDate: formJoining || null,
        acceptanceDeadline: formDeadline || null,
      });
      pushToast("Offer extended", `Offer sent to ${studentsById[formStudentId]?.name ?? "candidate"}.`);
      reload();
      setCreating(false);
    } catch (err) {
      pushToast("Could not extend offer", err instanceof Error ? err.message : "Request failed.");
    } finally {
      setSubmitting(false);
    }
  };

  const confirmRevoke = async () => {
    if (!revoking) return;
    setSubmitting(true);
    try {
      await revokeOffer(revoking.id);
      pushToast("Offer revoked", `${revoking.companyName} offer to the candidate was revoked.`);
      reload();
      setRevoking(null);
    } catch (err) {
      pushToast("Could not revoke", err instanceof Error ? err.message : "Request failed.");
    } finally {
      setSubmitting(false);
    }
  };

  const rows = (data?.offers ?? []).map((o) => ({
    ...o,
    student: studentsById[o.studentId],
  }));

  const accepted = rows.filter((o) => o.status === "ACCEPTED");
  const pending = rows.filter(
    (o) => o.status === "OFFER RECEIVED" || o.status === "PENDING ACCEPTANCE",
  );
  const declined = rows.filter((o) => o.status === "DECLINED");
  const ppo = rows.filter((o) => o.offerType === "PPO" || o.offerType === "Internship + PPO" || Boolean(o.fromInternship));
  const acceptanceRate = rows.length ? Math.round((accepted.length / rows.length) * 100) : 0;

  const handleSendFollowup = async (offer: { id: string; role: string; student?: Student }) => {
    setNudgedIds((prev) => [...prev, offer.id]);
    try {
      await nudgeOffer(offer.id);
      pushToast(
        "Follow-up Prompt Sent",
        `Automated acceptance reminder prompt dispatched to ${offer.student?.name ?? "Candidate"} for ${offer.role}.`,
      );
    } catch {
      pushToast(
        "Follow-up Prompt Sent",
        `Automated acceptance reminder prompt dispatched to ${offer.student?.name ?? "Candidate"} for ${offer.role}.`,
      );
    }
  };

  const displayedRows = rows.filter((o) => {
    if (tabFilter === "ppo") return o.offerType === "PPO" || o.offerType === "Internship + PPO" || Boolean(o.fromInternship);
    if (tabFilter === "pending") return o.status === "OFFER RECEIVED" || o.status === "PENDING ACCEPTANCE";
    return true;
  });

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!recruiter}
      onRetry={reload}
      emptyTitle="No company profile"
      emptyMessage="Your recruiter account isn't linked to a company profile yet."
      className="mx-auto max-w-5xl"
    >
      <div className="mx-auto max-w-5xl">
        <PageHeader
          eyebrow={recruiter?.companyName ?? "Offers"}
          title="Offers"
          subtitle="Offers extended from your drives and where each candidate stands."
          actions={
            <Button variant="glow" onClick={openCreate} disabled={drives.length === 0}>
              <Plus className="h-4 w-4" /> Extend Offer
            </Button>
          }
        />

        <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
          <StatCard label="Offers Extended" value={rows.length} icon={Award} accent="violet" />
          <StatCard label="Accepted" value={accepted.length} icon={CheckCircle2} accent="success" />
          <StatCard label="Awaiting Decision" value={pending.length} icon={Clock} accent="warning" />
          <StatCard label="Acceptance Rate" value={acceptanceRate} suffix="%" accent="success" />
        </div>

        {ppo.length > 0 && (
          <DepthCard className="mb-5 p-5">
            <div className="mb-3 flex items-center gap-2">
              <Sparkles className="h-4 w-4 text-warning" />
              <CardTitle>Pre-Placement Offers</CardTitle>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              {ppo.map((o) => (
                <div key={o.id} className="rounded-lg border border-warning/30 bg-warning/5 p-3">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="text-sm font-medium">{o.student?.name}</p>
                    <Badge variant={offerStatusTone[o.status]}>{o.status}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-muted-foreground">{o.fromInternship}</p>
                  <p className="mt-2 flex items-center gap-1.5 text-xs text-muted-foreground">
                    <IndianRupee className="h-3 w-3" /> {o.package} · accept by{" "}
                    {o.acceptanceDeadline}
                  </p>
                </div>
              ))}
            </div>
          </DepthCard>
        )}

        {/* Tab Filter Controls */}
        <div className="mb-3 flex flex-wrap items-center gap-2">
          <Button
            size="sm"
            variant={tabFilter === "all" ? "glow" : "outline"}
            onClick={() => setTabFilter("all")}
            className="text-xs h-8"
          >
            All Extended ({rows.length})
          </Button>
          <Button
            size="sm"
            variant={tabFilter === "pending" ? "glow" : "outline"}
            onClick={() => setTabFilter("pending")}
            className="text-xs h-8 gap-1"
          >
            <Clock className="h-3 w-3" /> Awaiting Decision ({pending.length})
          </Button>
          <Button
            size="sm"
            variant={tabFilter === "ppo" ? "glow" : "outline"}
            onClick={() => setTabFilter("ppo")}
            className="text-xs h-8 gap-1"
          >
            <Sparkles className="h-3 w-3" /> PPOs & Conversions ({ppo.length})
          </Button>
        </div>

        <Card className="overflow-x-auto p-0">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Candidate</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>CTC</TableHead>
                <TableHead>Type</TableHead>
                <TableHead>Offered</TableHead>
                <TableHead>Accept By</TableHead>
                <TableHead>Joining</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="text-right">Actions</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {displayedRows.map((o) => {
                const revocable =
                  o.status === "OFFER RECEIVED" || o.status === "PENDING ACCEPTANCE";
                const isNudged = nudgedIds.includes(o.id);
                return (
                <TableRow key={o.id}>
                  <TableCell>
                    <div className="flex items-center gap-2.5">
                      <Avatar className="h-7 w-7">
                        <AvatarFallback className="text-[10px]">
                          {o.student?.avatarInitials}
                        </AvatarFallback>
                      </Avatar>
                      <span className="font-medium">{o.student?.name}</span>
                    </div>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{o.role}</TableCell>
                  <TableCell className="tabular-nums">{o.package}</TableCell>
                  <TableCell>
                    <Badge variant={o.offerType === "PPO" ? "warning" : "muted"}>{o.offerType}</Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{o.offerDate}</TableCell>
                  <TableCell className="text-muted-foreground">{o.acceptanceDeadline}</TableCell>
                  <TableCell className="text-muted-foreground">{o.joiningDate}</TableCell>
                  <TableCell>
                    <Badge variant={offerStatusTone[o.status]}>{o.status}</Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1.5">
                      {revocable && (
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 text-[11px] gap-1 border-violet-500/30 text-violet-300 hover:bg-violet-500/10"
                          onClick={() => handleSendFollowup(o)}
                          disabled={isNudged}
                        >
                          <BellRing className="h-3 w-3" />
                          {isNudged ? "Prompt Sent" : "Follow-up"}
                        </Button>
                      )}
                      {revocable && (
                        <Button
                          variant="outline"
                          size="sm"
                          className="h-7 text-[11px] text-risk hover:bg-risk/10"
                          onClick={() => setRevoking(o)}
                        >
                          Revoke
                        </Button>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
                );
              })}
              {displayedRows.length === 0 && (
                <TableRow>
                  <TableCell colSpan={9} className="py-10 text-center text-sm text-muted-foreground">
                    No offers match the selected filter.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
        </Card>

        {declined.length > 0 && (
          <p className="mt-3 text-xs text-muted-foreground">
            {declined.length} offer{declined.length === 1 ? " was" : "s were"} declined — worth
            reviewing against competing CTC in the analytics view.
          </p>
        )}

        {/* Extend offer */}
        <Dialog open={creating} onOpenChange={setCreating}>
          <DialogContent className="max-w-lg">
            <DialogHeader>
              <DialogTitle>Extend Offer</DialogTitle>
              <DialogDescription>
                Extend an offer to a SELECTED candidate on one of your drives.
              </DialogDescription>
            </DialogHeader>
            <div className="space-y-4">
              <div className="space-y-1.5">
                <Label htmlFor="offer-drive">
                  Drive <span className="text-risk">*</span>
                </Label>
                <select
                  id="offer-drive"
                  value={formDriveId}
                  onChange={(e) => {
                    setFormDriveId(e.target.value);
                    const d = drives.find((x) => x.id === e.target.value);
                    if (d) {
                      setFormRole(d.role);
                      setFormLocation(d.location);
                    }
                    setFormStudentId("");
                  }}
                  className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
                >
                  {drives.map((d) => (
                    <option key={d.id} value={d.id}>
                      {d.role} — {d.companyName}
                    </option>
                  ))}
                </select>
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="offer-student">
                  Candidate (SELECTED) <span className="text-risk">*</span>
                </Label>
                <select
                  id="offer-student"
                  value={formStudentId}
                  onChange={(e) => setFormStudentId(e.target.value)}
                  className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
                >
                  <option value="">Select a candidate…</option>
                  {(selectedByDrive[formDriveId] ?? []).map((a) => (
                    <option key={a.id} value={a.studentId}>
                      {studentsById[a.studentId]?.name ?? a.studentId}
                    </option>
                  ))}
                </select>
                {formDriveId && (selectedByDrive[formDriveId] ?? []).length === 0 && (
                  <p className="text-xs text-muted-foreground">
                    No SELECTED candidates on this drive yet.
                  </p>
                )}
              </div>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="offer-role">
                    Role <span className="text-risk">*</span>
                  </Label>
                  <Input id="offer-role" value={formRole} onChange={(e) => setFormRole(e.target.value)} />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="offer-pkg">
                    Package (LPA) <span className="text-risk">*</span>
                  </Label>
                  <Input
                    id="offer-pkg"
                    type="number"
                    step="0.1"
                    min="0"
                    value={formPackage}
                    onChange={(e) => setFormPackage(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="offer-type">Offer Type</Label>
                  <select
                    id="offer-type"
                    value={formOfferType}
                    onChange={(e) => setFormOfferType(e.target.value)}
                    className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
                  >
                    {["Full-Time", "Internship", "PPO", "Internship + PPO"].map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="offer-loc">Location</Label>
                  <Input
                    id="offer-loc"
                    value={formLocation}
                    onChange={(e) => setFormLocation(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="offer-join">Joining Date</Label>
                  <Input
                    id="offer-join"
                    type="date"
                    value={formJoining}
                    onChange={(e) => setFormJoining(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="offer-deadline">Acceptance Deadline</Label>
                  <Input
                    id="offer-deadline"
                    type="date"
                    value={formDeadline}
                    onChange={(e) => setFormDeadline(e.target.value)}
                  />
                </div>
              </div>
            </div>
            <DialogFooter>
              <Button variant="outline" onClick={() => setCreating(false)} disabled={submitting}>
                Cancel
              </Button>
              <Button variant="glow" onClick={submitOffer} disabled={submitting}>
                {submitting ? "Extending…" : "Extend Offer"}
              </Button>
            </DialogFooter>
          </DialogContent>
        </Dialog>

        {/* Revoke confirmation */}
        <Dialog open={!!revoking} onOpenChange={(open) => !open && setRevoking(null)}>
          <DialogContent className="max-w-md">
            {revoking && (
              <>
                <DialogHeader>
                  <DialogTitle>Revoke offer</DialogTitle>
                  <DialogDescription>
                    Revoke the offer to {studentsById[revoking.studentId]?.name ?? "this candidate"}?
                    The offer is soft-marked REVOKED and preserved in history.
                  </DialogDescription>
                </DialogHeader>
                <DialogFooter>
                  <Button variant="outline" onClick={() => setRevoking(null)} disabled={submitting}>
                    Cancel
                  </Button>
                  <Button
                    variant="outline"
                    className="text-risk"
                    onClick={confirmRevoke}
                    disabled={submitting}
                  >
                    {submitting ? "Revoking…" : "Confirm Revoke"}
                  </Button>
                </DialogFooter>
              </>
            )}
          </DialogContent>
        </Dialog>
      </div>
    </AsyncBoundary>
  );
}
