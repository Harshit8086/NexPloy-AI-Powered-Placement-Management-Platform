"use client";

import { useMemo, useState } from "react";
import { motion } from "framer-motion";
import {
  MapPin,
  IndianRupee,
  Calendar,
  Users,
  Plus,
  Settings2,
  AlertTriangle,
  ShieldCheck,
  Building2,
  Sparkles,
} from "lucide-react";
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
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { listApplications } from "@/lib/api/applications";
import { DriveSkillRequirementsDialog } from "@/components/officer/DriveSkillRequirementsDialog";
import { listScheduleEvents } from "@/lib/api/schedules";
import { listDrives, createDrive as createDriveApi, updateDrive, updateDriveStatus, type CreateDriveRequest } from "@/lib/api/drives";
import { parseJobDescription } from "@/lib/api/ai";
import { listStudents } from "@/lib/api/students";
import { listRecruiters } from "@/lib/api/recruiters";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import { driveStatusTone } from "@/lib/status";
import { useAppStore } from "@/hooks/useAppStore";
import type { Drive, DriveStatus, EligibilityCriteria, Recruiter, Student } from "@/types";

const BRANCH_OPTIONS = ["CSE", "IT", "ECE", "EEE", "MECH", "CIVIL"];

/** Drive type values are stored free-form on the backend; these are the exact strings the
 * frontend union (Drive["driveType"]) uses — sent verbatim, no enum translation. */
const DRIVE_TYPES: Drive["driveType"][] = ["On-Campus", "Off-Campus", "Virtual", "Pooled"];

/** Parse a display package string like "₹7.5 LPA" back to a numeric LPA value. */
function parsePackageLpa(display: string): number {
  const match = display.replace(/,/g, "").match(/[\d.]+/);
  return match ? Number(match[0]) : 0;
}

/** Editable copy of a drive's criteria — the officer-facing eligibility
 * configuration. Changes live in component state only (prototype). */
function CriteriaEditor({
  criteria,
  onChange,
}: {
  criteria: EligibilityCriteria;
  onChange: (next: EligibilityCriteria) => void;
}) {
  const toggleBranch = (code: string) => {
    const next = criteria.allowedBranches.includes(code)
      ? criteria.allowedBranches.filter((b) => b !== code)
      : [...criteria.allowedBranches, code];
    onChange({ ...criteria, allowedBranches: next });
  };

  return (
    <div className="space-y-4">
      <div className="grid gap-3 sm:grid-cols-3">
        <div className="space-y-1.5">
          <Label htmlFor="minCgpa">Minimum CGPA</Label>
          <Input
            id="minCgpa"
            type="number"
            step="0.1"
            min="0"
            max="10"
            value={criteria.minCgpa}
            onChange={(e) => onChange({ ...criteria, minCgpa: Number(e.target.value) })}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="maxBacklogs">Max Active Backlogs</Label>
          <Input
            id="maxBacklogs"
            type="number"
            min="0"
            max="10"
            value={criteria.maxBacklogs}
            onChange={(e) => onChange({ ...criteria, maxBacklogs: Number(e.target.value) })}
          />
        </div>
        <div className="space-y-1.5">
          <Label htmlFor="gradYear">Graduation Year</Label>
          <Input
            id="gradYear"
            type="number"
            value={criteria.graduationYear}
            onChange={(e) => onChange({ ...criteria, graduationYear: Number(e.target.value) })}
          />
        </div>
      </div>
      <div>
        <Label className="mb-2 block">Allowed Branches</Label>
        <div className="flex flex-wrap gap-1.5">
          {BRANCH_OPTIONS.map((b) => {
            const on = criteria.allowedBranches.includes(b);
            return (
              <button
                key={b}
                type="button"
                onClick={() => toggleBranch(b)}
                aria-pressed={on}
                className={
                  on
                    ? "rounded-full border border-violet/50 bg-violet/10 px-3 py-1 text-xs font-medium text-violet-bright"
                    : "rounded-full border border-border px-3 py-1 text-xs font-medium text-muted-foreground hover:bg-accent"
                }
              >
                {b}
              </button>
            );
          })}
        </div>
      </div>
    </div>
  );
}

export function DriveManagement() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);
  const [configuring, setConfiguring] = useState<Drive | null>(null);
  const [skillsDrive, setSkillsDrive] = useState<Drive | null>(null);
  const [draftCriteria, setDraftCriteria] = useState<EligibilityCriteria | null>(null);
  const [creating, setCreating] = useState(false);
  const [editing, setEditing] = useState<Drive | null>(null);
  const [statusTarget, setStatusTarget] = useState<{ drive: Drive; status: DriveStatus } | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const [newCompany, setNewCompany] = useState("");
  const [newRole, setNewRole] = useState("");
  const [newRecruiterId, setNewRecruiterId] = useState("");
  const [newPackage, setNewPackage] = useState("₹7.0 LPA");
  const [newHiring, setNewHiring] = useState(10);
  const [newCriteria, setNewCriteria] = useState<EligibilityCriteria>({
    minCgpa: 7.0,
    allowedBranches: ["CSE", "IT"],
    maxBacklogs: 1,
    graduationYear: 2027,
  });
  const [newSkills, setNewSkills] = useState<string[]>([]);
  const [skillDraft, setSkillDraft] = useState("");
  const [newDescription, setNewDescription] = useState("");
  const [newLocation, setNewLocation] = useState("");
  const [newDriveType, setNewDriveType] = useState<Drive["driveType"]>("On-Campus");
  const [newVenue, setNewVenue] = useState("");
  const [newDeadline, setNewDeadline] = useState(""); // YYYY-MM-DD (date-only, no tz)
  const [newRounds, setNewRounds] = useState<{ name: string; description: string }[]>([]);
  const [jdText, setJdText] = useState("");
  const [parsingJd, setParsingJd] = useState(false);

  const addSkill = (raw: string) => {
    const skill = raw.trim();
    if (!skill) return;
    setNewSkills((prev) =>
      prev.some((s) => s.toLowerCase() === skill.toLowerCase()) ? prev : [...prev, skill],
    );
    setSkillDraft("");
  };
  const removeSkill = (skill: string) =>
    setNewSkills((prev) => prev.filter((s) => s !== skill));

  /** Auto-fill the form from raw JD text via POST /api/ai/job-description/parse. Extraction is
   * bounded and evidence-bearing: extracted required skills are merged into the skill list and an
   * empty description is seeded from the pasted text — the officer reviews everything before save. */
  const extractFromJd = async () => {
    const text = jdText.trim();
    if (!text) return;
    setParsingJd(true);
    try {
      const result = await parseJobDescription(text);
      const extracted = result.requiredSkills.map((s) => s.normalizedValue).filter(Boolean);
      setNewSkills((prev) => {
        const merged = [...prev];
        for (const skill of extracted) {
          if (!merged.some((s) => s.toLowerCase() === skill.toLowerCase())) merged.push(skill);
        }
        return merged;
      });
      if (!newDescription.trim()) setNewDescription(text);
      pushToast(
        extracted.length ? "JD parsed" : "JD parsed — no skills found",
        extracted.length
          ? `${extracted.length} required skill${extracted.length === 1 ? "" : "s"} extracted. Review before saving.`
          : "No required skills were found in the text.",
      );
    } catch (err) {
      pushToast("JD parse failed", err instanceof Error ? err.message : "Could not parse the job description.");
    } finally {
      setParsingJd(false);
    }
  };

  const addRound = () => setNewRounds((prev) => [...prev, { name: "", description: "" }]);
  const updateRound = (index: number, field: "name" | "description", value: string) =>
    setNewRounds((prev) => prev.map((r, i) => (i === index ? { ...r, [field]: value } : r)));
  const removeRound = (index: number) =>
    setNewRounds((prev) => prev.filter((_, i) => i !== index));
  const moveRound = (index: number, dir: -1 | 1) =>
    setNewRounds((prev) => {
      const to = index + dir;
      if (to < 0 || to >= prev.length) return prev;
      const next = [...prev];
      [next[index], next[to]] = [next[to], next[index]];
      return next;
    });

  // Drives + students from the API layer (live backend).
  const { data: liveDrives, loading, reload: reloadDrives } = useApiResource<Drive[]>(
    () => listDrives(),
    [],
    [],
  );
  const { data: liveStudents } = useApiResource<Student[]>(() => listStudents(), [], []);
  const { data: recruiters } = useApiResource<Recruiter[]>(() => listRecruiters(), [], []);
  // Live schedule events carry a real conflict flag per drive.
  const { data: scheduleEvents } = useApiResource(() => listScheduleEvents(), [], []);
  const eventByDrive = useMemo(
    () => new Map(scheduleEvents.map((e) => [e.driveId, e] as const)),
    [scheduleEvents],
  );
  // Live applications (campus-scoped server-side) → applicant count per drive.
  const { data: applications } = useApiResource(() => listApplications(), [], []);
  const applicationCountByDrive = useMemo(() => {
    const m = new Map<string, number>();
    applications.forEach((a) => m.set(a.driveId, (m.get(a.driveId) ?? 0) + 1));
    return m;
  }, [applications]);

  const campusDrives = liveDrives.filter((d) => d.campusId === campusId);
  const campusStudents = useMemo(
    () => liveStudents.filter((s) => s.campusId === campusId),
    [liveStudents, campusId],
  );

  const active = campusDrives.filter((d) => d.status === "ACTIVE");
  const draft = campusDrives.filter((d) => d.status === "DRAFT");
  const completed = campusDrives.filter((d) => d.status === "COMPLETED" || d.status === "CLOSED");

  const openConfig = (drive: Drive) => {
    setConfiguring(drive);
    setDraftCriteria({ ...drive.criteria });
  };

  /** Build the backend request body from a drive plus (optionally) overriding criteria.
   * Used by the Configure-Eligibility save, which must NOT wipe non-criteria fields. */
  const driveToRequest = (drive: Drive, criteria?: EligibilityCriteria): CreateDriveRequest => {
    const c = criteria ?? drive.criteria;
    return {
      recruiterId: drive.companyId,
      companyName: drive.companyName,
      role: drive.role,
      location: drive.location || null,
      packageLpa: parsePackageLpa(drive.package),
      minimumCgpa: c.minCgpa,
      maximumBacklogs: c.maxBacklogs,
      graduationYear: c.graduationYear,
      minimumTenthPercentage: c.minTenthPercentage ?? null,
      minimumTwelfthPercentage: c.minTwelfthPercentage ?? null,
      deadline: drive.deadlineIso ?? null,
      driveType: drive.driveType || null,
      description: drive.description || null,
      expectedHiring: drive.expectedHiring,
      venue: drive.venue || null,
      requiredSkills: drive.requiredSkills,
      rounds: drive.rounds,
      allowedBranches: c.allowedBranches,
    };
  };

  const saveConfig = async () => {
    if (!configuring || !draftCriteria) return;
    setSubmitting(true);
    try {
      await updateDrive(configuring.id, driveToRequest(configuring, draftCriteria));
      const eligible = campusStudents.filter(
        (s) => evaluateEligibility(s, { ...configuring, criteria: draftCriteria }).eligible,
      ).length;
      pushToast(
        "Eligibility criteria updated",
        `${configuring.companyName}: ${eligible} of ${campusStudents.length} tracked students now qualify.`,
      );
      reloadDrives();
      setConfiguring(null);
    } catch (err) {
      pushToast("Update failed", err instanceof Error ? err.message : "Could not update criteria.");
    } finally {
      setSubmitting(false);
    }
  };

  const openCreate = () => {
    setEditing(null);
    setNewCompany("");
    setNewRole("");
    setNewRecruiterId(recruiters[0]?.id ?? "");
    setNewPackage("₹7.0 LPA");
    setNewHiring(10);
    setNewCriteria({ minCgpa: 7.0, allowedBranches: ["CSE", "IT"], maxBacklogs: 1, graduationYear: 2027 });
    setNewSkills([]);
    setSkillDraft("");
    setNewDescription("");
    setNewLocation("");
    setNewDriveType("On-Campus");
    setNewVenue("");
    setNewDeadline("");
    setNewRounds([]);
    setJdText("");
    setCreating(true);
  };

  const openEdit = (drive: Drive) => {
    setEditing(drive);
    setNewCompany(drive.companyName);
    setNewRole(drive.role);
    setNewRecruiterId(drive.companyId);
    setNewPackage(drive.package);
    setNewHiring(drive.expectedHiring);
    setNewCriteria({ ...drive.criteria });
    setNewSkills(drive.requiredSkills ?? []);
    setSkillDraft("");
    setNewDescription(drive.description ?? "");
    setNewLocation(drive.location ?? "");
    setNewDriveType(drive.driveType ?? "On-Campus");
    setNewVenue(drive.venue ?? "");
    setNewDeadline(drive.deadlineIso ?? "");
    setNewRounds((drive.rounds ?? []).map((r) => ({ name: r.name, description: r.description })));
    setJdText("");
    setCreating(true);
  };

  const submitDrive = async () => {
    if (!newRecruiterId) {
      pushToast("Recruiter required", "Select the recruiting company for this drive.");
      return;
    }
    if (!newCompany.trim() || !newRole.trim()) {
      pushToast("Missing details", "Company and role are required.");
      return;
    }
    if (parsePackageLpa(newPackage) <= 0) {
      pushToast("Invalid package", "Enter a package greater than 0 LPA.");
      return;
    }
    if (!Number.isFinite(newHiring) || newHiring < 1) {
      pushToast("Invalid hiring count", "Expected hiring must be at least 1.");
      return;
    }
    if (newDeadline && Number.isNaN(new Date(newDeadline).getTime())) {
      pushToast("Invalid deadline", "Enter a valid application deadline.");
      return;
    }
    const cleanRounds = newRounds
      .map((r) => ({ name: r.name.trim(), description: r.description.trim() }))
      .filter((r) => r.name.length > 0);
    if (newRounds.length > 0 && cleanRounds.length !== newRounds.length) {
      pushToast("Incomplete round", "Every selection round needs a name (or remove it).");
      return;
    }
    const request: CreateDriveRequest = {
      recruiterId: newRecruiterId,
      companyName: newCompany.trim(),
      role: newRole.trim(),
      location: newLocation.trim() || null,
      packageLpa: parsePackageLpa(newPackage),
      expectedHiring: newHiring,
      minimumCgpa: newCriteria.minCgpa,
      maximumBacklogs: newCriteria.maxBacklogs,
      graduationYear: newCriteria.graduationYear,
      allowedBranches: newCriteria.allowedBranches,
      // Eligibility floors the form does not expose — preserve on edit, null on create.
      minimumTenthPercentage: editing?.criteria.minTenthPercentage ?? null,
      minimumTwelfthPercentage: editing?.criteria.minTwelfthPercentage ?? null,
      requiredSkills: newSkills,
      description: newDescription.trim() || null,
      driveType: newDriveType || null,
      venue: newVenue.trim() || null,
      deadline: newDeadline || null, // date-only string; no Date() → no tz shift
      rounds: cleanRounds,
    };
    setSubmitting(true);
    try {
      if (editing) {
        await updateDrive(editing.id, request);
        pushToast("Drive updated", `${request.companyName} — ${request.role} saved.`);
      } else {
        await createDriveApi(request);
        pushToast("Drive created", `${request.companyName} — ${request.role} saved as a DRAFT.`);
      }
      reloadDrives();
      setCreating(false);
      setEditing(null);
    } catch (err) {
      pushToast("Save failed", err instanceof Error ? err.message : "Could not save the drive.");
    } finally {
      setSubmitting(false);
    }
  };

  const confirmStatus = async () => {
    if (!statusTarget) return;
    setSubmitting(true);
    try {
      await updateDriveStatus(statusTarget.drive.id, statusTarget.status);
      pushToast(
        "Status updated",
        `${statusTarget.drive.companyName} moved to ${statusTarget.status}.`,
      );
      reloadDrives();
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

  const renderDrive = (d: Drive) => {
    const applicantCount = applicationCountByDrive.get(d.id) ?? 0;
    const event = eventByDrive.get(d.id);
    const conflict = event?.conflict ?? false;
    const eligibleCount = campusStudents.filter((s) => evaluateEligibility(s, d).eligible).length;

    return (
      <DataRevealItem key={d.id}>
        <DepthCard className={conflict ? "h-full border-risk/30 p-5" : "h-full p-5"}>
          <div className="flex items-start justify-between gap-2">
            <div className="flex min-w-0 items-center gap-3">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-sm font-bold text-violet-bright">
                {d.logoInitial}
              </div>
              <div className="min-w-0">
                <CardTitle className="truncate">{d.companyName}</CardTitle>
                <p className="truncate text-xs text-muted-foreground">{d.role}</p>
              </div>
            </div>
            <div className="flex shrink-0 flex-col items-end gap-1.5">
              <Badge variant={driveStatusTone[d.status]}>{d.status}</Badge>
              {conflict && (
                <Badge variant="risk" className="gap-1">
                  <AlertTriangle className="h-3 w-3" /> Conflict
                </Badge>
              )}
            </div>
          </div>

          <div className="mt-4 flex flex-wrap gap-3 text-xs text-muted-foreground">
            <span className="flex items-center gap-1.5">
              <Building2 className="h-3.5 w-3.5" /> {d.driveType}
            </span>
            <span className="flex items-center gap-1.5">
              <MapPin className="h-3.5 w-3.5" /> {d.location}
            </span>
            <span className="flex items-center gap-1.5">
              <IndianRupee className="h-3.5 w-3.5" /> {d.package}
            </span>
            <span className="flex items-center gap-1.5">
              <Calendar className="h-3.5 w-3.5" /> {d.driveDate}
            </span>
            <span className="flex items-center gap-1.5">
              <Users className="h-3.5 w-3.5" /> {applicantCount} applicants ·{" "}
              {d.expectedHiring} expected hires
            </span>
          </div>

          <div className="mt-3 rounded-lg border border-border p-3">
            <div className="flex flex-wrap items-center justify-between gap-2">
              <p className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                Eligibility Criteria
              </p>
              <Badge variant="muted" className="gap-1">
                <ShieldCheck className="h-3 w-3" /> {eligibleCount} eligible
              </Badge>
            </div>
            <div className="mt-2 grid grid-cols-2 gap-x-4 gap-y-1 text-xs text-muted-foreground">
              <span>
                Minimum CGPA <span className="font-medium text-foreground">{d.criteria.minCgpa}</span>
              </span>
              <span>
                Max Backlogs{" "}
                <span className="font-medium text-foreground">{d.criteria.maxBacklogs}</span>
              </span>
              <span>
                Branches{" "}
                <span className="font-medium text-foreground">
                  {d.criteria.allowedBranches.join(", ")}
                </span>
              </span>
              <span>
                Batch{" "}
                <span className="font-medium text-foreground">{d.criteria.graduationYear}</span>
              </span>
            </div>
          </div>

          <div className="mt-3 flex flex-wrap gap-1.5">
            {d.requiredSkills.slice(0, 5).map((s) => (
              <Badge key={s} variant="muted">
                {s}
              </Badge>
            ))}
          </div>

          <div className="mt-4 flex flex-wrap gap-2">
            <Button variant="outline" size="sm" onClick={() => setSkillsDrive(d)}>
              <Sparkles className="h-3.5 w-3.5 text-violet-bright" /> Skill Requirements
            </Button>
            <Button variant="outline" size="sm" onClick={() => openConfig(d)}>
              <Settings2 className="h-3.5 w-3.5" /> Configure Eligibility
            </Button>
            <Button variant="outline" size="sm" onClick={() => openEdit(d)}>
              Edit
            </Button>
            {d.status === "DRAFT" && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setStatusTarget({ drive: d, status: "ACTIVE" })}
              >
                Publish (Activate)
              </Button>
            )}
            {d.status === "ACTIVE" && (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setStatusTarget({ drive: d, status: "CLOSED" })}
              >
                Close
              </Button>
            )}
            {(d.status === "DRAFT" || d.status === "ACTIVE") && (
              <Button
                variant="outline"
                size="sm"
                className="text-risk"
                onClick={() => setStatusTarget({ drive: d, status: "CANCELLED" })}
              >
                Cancel
              </Button>
            )}
          </div>
        </DepthCard>
      </DataRevealItem>
    );
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Placement"
        title="Placement Drives"
        subtitle="Every drive this cycle, with its eligibility configuration and lifecycle status."
        actions={
          <div className="flex items-center gap-2">
            <Button variant="glow" onClick={openCreate}>
              <Plus className="h-4 w-4" /> Create Drive
            </Button>
          </div>
        }
      />

      <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-4">
        <StatCard label="Active" value={active.length} accent="success" />
        <StatCard label="Draft" value={draft.length} accent="warning" />
        <StatCard label="Completed" value={completed.length} />
        <StatCard
          label="Expected Hires"
          value={active.reduce((n, d) => n + d.expectedHiring, 0)}
          accent="violet"
        />
      </div>

      <Tabs defaultValue="active">
        <TabsList>
          <TabsTrigger value="active">Active ({active.length})</TabsTrigger>
          <TabsTrigger value="draft">Draft ({draft.length})</TabsTrigger>
          <TabsTrigger value="completed">Completed ({completed.length})</TabsTrigger>
        </TabsList>
        {[
          { value: "active", list: active },
          { value: "draft", list: draft },
          { value: "completed", list: completed },
        ].map((tab) => (
          <TabsContent key={tab.value} value={tab.value}>
            {tab.list.length === 0 ? (
              <Card className="p-10 text-center text-sm text-muted-foreground">
                {loading ? "Loading drives…" : "No drives in this state for the selected campus."}
              </Card>
            ) : (
              <DataReveal stagger className="grid gap-4 sm:grid-cols-2">
                {tab.list.map(renderDrive)}
              </DataReveal>
            )}
          </TabsContent>
        ))}
      </Tabs>

      {/* Eligibility configuration */}
      <Dialog open={!!configuring} onOpenChange={(open) => !open && setConfiguring(null)}>
        <DialogContent className="max-w-lg">
          {configuring && draftCriteria && (
            <>
              <DialogHeader>
                <DialogTitle>
                  {configuring.companyName} — Eligibility Configuration
                </DialogTitle>
                <DialogDescription>
                  Criteria are evaluated deterministically against every student record.
                </DialogDescription>
              </DialogHeader>
              <CriteriaEditor criteria={draftCriteria} onChange={setDraftCriteria} />
              <motion.div
                key={JSON.stringify(draftCriteria)}
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                className="rounded-lg border border-violet/30 bg-violet/5 p-3 text-sm"
              >
                <span className="font-semibold text-violet-bright">
                  {
                    campusStudents.filter(
                      (s) => evaluateEligibility(s, { ...configuring, criteria: draftCriteria }).eligible,
                    ).length
                  }
                </span>{" "}
                <span className="text-muted-foreground">
                  of {campusStudents.length} tracked students would be eligible under these criteria.
                </span>
              </motion.div>
              <DialogFooter>
                <Button variant="outline" onClick={() => setConfiguring(null)} disabled={submitting}>
                  Cancel
                </Button>
                <Button variant="glow" onClick={saveConfig} disabled={submitting}>
                  {submitting ? "Saving…" : "Save Criteria"}
                </Button>
              </DialogFooter>
            </>
          )}
        </DialogContent>
      </Dialog>

      {/* Create / edit drive */}
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
            <DialogTitle>{editing ? "Edit Placement Drive" : "Create Placement Drive"}</DialogTitle>
            <DialogDescription>
              {editing
                ? "Update the drive's details and eligibility criteria."
                : "Saved as a DRAFT — publish it to open applications."}
            </DialogDescription>
          </DialogHeader>
          <div className="max-h-[65vh] space-y-4 overflow-y-auto pr-1">
            <div className="space-y-1.5 rounded-lg border border-violet/30 bg-violet/5 p-3">
              <Label htmlFor="jdPaste">Auto-fill from Job Description (AI)</Label>
              <textarea
                id="jdPaste"
                value={jdText}
                onChange={(e) => setJdText(e.target.value)}
                rows={4}
                placeholder="Paste the raw job description, then extract — required skills and the description are filled in for your review."
                className="flex w-full rounded-md border border-border bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              />
              <Button
                type="button"
                variant="outline"
                size="sm"
                onClick={extractFromJd}
                disabled={parsingJd || submitting || !jdText.trim()}
              >
                <Sparkles className="h-3.5 w-3.5" /> {parsingJd ? "Parsing…" : "Extract Criteria"}
              </Button>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="recruiter">
                Recruiting Company <span className="text-risk">*</span>
              </Label>
              <select
                id="recruiter"
                value={newRecruiterId}
                onChange={(e) => {
                  setNewRecruiterId(e.target.value);
                  const rec = recruiters.find((r) => r.id === e.target.value);
                  if (rec && !editing) setNewCompany(rec.companyName);
                }}
                className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
              >
                <option value="">Select a recruiter…</option>
                {recruiters.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.companyName}
                  </option>
                ))}
              </select>
            </div>
            <div className="grid gap-3 sm:grid-cols-2">
              <div className="space-y-1.5">
                <Label htmlFor="company">
                  Company <span className="text-risk">*</span>
                </Label>
                <Input
                  id="company"
                  value={newCompany}
                  onChange={(e) => setNewCompany(e.target.value)}
                  placeholder="e.g. NorthPeak Systems"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="role">
                  Role <span className="text-risk">*</span>
                </Label>
                <Input
                  id="role"
                  value={newRole}
                  onChange={(e) => setNewRole(e.target.value)}
                  placeholder="e.g. Backend Developer"
                />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="ctc">Package</Label>
                <Input id="ctc" value={newPackage} onChange={(e) => setNewPackage(e.target.value)} />
              </div>
              <div className="space-y-1.5">
                <Label htmlFor="hiring">Expected Hiring</Label>
                <Input
                  id="hiring"
                  type="number"
                  min="1"
                  value={newHiring}
                  onChange={(e) => setNewHiring(Number(e.target.value))}
                />
              </div>
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="description">Description</Label>
              <textarea
                id="description"
                value={newDescription}
                onChange={(e) => setNewDescription(e.target.value)}
                rows={3}
                placeholder="Role summary, responsibilities, and what the company is looking for."
                className="flex w-full rounded-md border border-border bg-background px-3 py-2 text-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring"
              />
            </div>

            <div className="border-t border-border pt-4">
              <p className="mb-3 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Eligibility
              </p>
              <CriteriaEditor criteria={newCriteria} onChange={setNewCriteria} />
            <div className="space-y-1.5">
              <Label htmlFor="skills">Required Skills</Label>
              <Input
                id="skills"
                value={skillDraft}
                onChange={(e) => setSkillDraft(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter" || e.key === ",") {
                    e.preventDefault();
                    addSkill(skillDraft);
                  } else if (e.key === "Backspace" && !skillDraft && newSkills.length) {
                    removeSkill(newSkills[newSkills.length - 1]);
                  }
                }}
                onBlur={() => addSkill(skillDraft)}
                placeholder="Type a skill and press Enter (e.g. Java)"
              />
              {newSkills.length > 0 && (
                <div className="flex flex-wrap gap-1.5 pt-1">
                  {newSkills.map((s) => (
                    <button
                      key={s}
                      type="button"
                      onClick={() => removeSkill(s)}
                      className="rounded-full border border-violet/50 bg-violet/10 px-3 py-1 text-xs font-medium text-violet-bright hover:bg-violet/20"
                      aria-label={`Remove ${s}`}
                    >
                      {s} ✕
                    </button>
                  ))}
                </div>
              )}
            </div>
            </div>

            <div className="border-t border-border pt-4 space-y-4">
              <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                Drive Details
              </p>
              <div className="grid gap-3 sm:grid-cols-2">
                <div className="space-y-1.5">
                  <Label htmlFor="driveType">Drive Type</Label>
                  <select
                    id="driveType"
                    value={newDriveType}
                    onChange={(e) => setNewDriveType(e.target.value as Drive["driveType"])}
                    className="flex h-9 w-full rounded-md border border-border bg-background px-3 py-1 text-sm"
                  >
                    {DRIVE_TYPES.map((t) => (
                      <option key={t} value={t}>
                        {t}
                      </option>
                    ))}
                  </select>
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="deadline">Application Deadline</Label>
                  <Input
                    id="deadline"
                    type="date"
                    value={newDeadline}
                    onChange={(e) => setNewDeadline(e.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="location">Location</Label>
                  <Input
                    id="location"
                    value={newLocation}
                    onChange={(e) => setNewLocation(e.target.value)}
                    placeholder="e.g. Bengaluru"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="venue">Venue</Label>
                  <Input
                    id="venue"
                    value={newVenue}
                    onChange={(e) => setNewVenue(e.target.value)}
                    placeholder="e.g. Auditorium / Virtual link"
                  />
                </div>
              </div>
            </div>

            <div className="border-t border-border pt-4">
              <div className="mb-3 flex items-center justify-between">
                <p className="text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                  Selection Process
                </p>
                <Button type="button" variant="outline" size="sm" onClick={addRound}>
                  <Plus className="h-3.5 w-3.5" /> Add Round
                </Button>
              </div>
              {newRounds.length === 0 ? (
                <p className="text-sm text-muted-foreground">
                  No rounds yet. Add the selection rounds candidates will go through.
                </p>
              ) : (
                <div className="space-y-3">
                  {newRounds.map((r, i) => (
                    <div key={i} className="rounded-lg border border-border p-3">
                      <div className="mb-2 flex items-center justify-between gap-2">
                        <span className="flex h-5 w-5 shrink-0 items-center justify-center rounded-full bg-violet/15 text-[10px] font-semibold text-violet-bright">
                          {i + 1}
                        </span>
                        <div className="flex items-center gap-1">
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => moveRound(i, -1)}
                            disabled={i === 0}
                            aria-label="Move round up"
                          >
                            ↑
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            onClick={() => moveRound(i, 1)}
                            disabled={i === newRounds.length - 1}
                            aria-label="Move round down"
                          >
                            ↓
                          </Button>
                          <Button
                            type="button"
                            variant="ghost"
                            size="sm"
                            className="text-risk"
                            onClick={() => removeRound(i)}
                            aria-label="Remove round"
                          >
                            ✕
                          </Button>
                        </div>
                      </div>
                      <div className="space-y-2">
                        <Input
                          value={r.name}
                          onChange={(e) => updateRound(i, "name", e.target.value)}
                          placeholder="Round name (e.g. Technical Interview)"
                        />
                        <Input
                          value={r.description}
                          onChange={(e) => updateRound(i, "description", e.target.value)}
                          placeholder="Short description (optional)"
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
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
            <Button variant="glow" onClick={submitDrive} disabled={submitting}>
              {submitting ? "Saving…" : editing ? "Save Changes" : "Create Drive"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Status transition confirmation */}
      <Dialog open={!!statusTarget} onOpenChange={(open) => !open && setStatusTarget(null)}>
        <DialogContent className="max-w-md">
          {statusTarget && (
            <>
              <DialogHeader>
                <DialogTitle>Confirm status change</DialogTitle>
                <DialogDescription>
                  Move <span className="font-medium text-foreground">{statusTarget.drive.companyName}</span>{" "}
                  to <span className="font-medium text-foreground">{statusTarget.status}</span>?
                  {statusTarget.status === "CANCELLED" &&
                    " This closes the drive to further applications."}
                </DialogDescription>
              </DialogHeader>
              <DialogFooter>
                <Button variant="outline" onClick={() => setStatusTarget(null)} disabled={submitting}>
                  Cancel
                </Button>
                <Button
                  variant={statusTarget.status === "CANCELLED" ? "outline" : "glow"}
                  className={statusTarget.status === "CANCELLED" ? "text-risk" : undefined}
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

      <DriveSkillRequirementsDialog
        drive={skillsDrive}
        open={skillsDrive !== null}
        onOpenChange={(open) => !open && setSkillsDrive(null)}
      />
    </div>
  );
}
