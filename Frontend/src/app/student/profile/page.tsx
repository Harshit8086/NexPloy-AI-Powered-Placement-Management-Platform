"use client";

import { useMemo, useRef, useState } from "react";
import Link from "next/link";
import {
  GraduationCap, Briefcase, Building2, ClipboardCheck, Heart,
  Mail, Phone, Award, FolderGit2, BadgeCheck, Pencil, Check, X,
  Plus, Trash2, Circle, ListChecks, ArrowUpRight, Eye, ExternalLink,
  Loader2, MapPin, Target, Zap, TrendingUp, Star, Shield, ChevronRight, ChevronLeft, Calendar, Upload,
} from "lucide-react";
import { Card } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { Switch } from "@/components/ui/switch";
import { Dialog, DialogContent, DialogFooter, DialogHeader, DialogTitle, DialogDescription } from "@/components/ui/dialog";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { SkillBar } from "@/components/intelligence/SkillBar";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import {
  getPrimaryStudent, updatePrimaryStudent, listSkills, upsertSkill, getMyBacklogHistory,
  type StudentUpdateInput, type StudentSkill, type BacklogHistoryEntry,
} from "@/lib/api/students";
import { getCampus } from "@/lib/api/campuses";
import { listDrives } from "@/lib/api/drives";
import {
  listCertificates, fetchCertificateFile, certificateTypeLabel, certificateHasFile,
  createCertificate, deleteCertificate, updateCertificateMetadata, replaceCertificateFile,
  CERTIFICATE_TYPES, type CertificateInput, type CertificateType, type StudentCertificate,
} from "@/lib/api/certificates";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { evaluateEligibility } from "@/lib/eligibility";
import { readinessBand, placementStatusTone } from "@/lib/status";
import { profileCompleteness } from "@/lib/profile-completeness";
import { proficiencyBand } from "@/lib/skill-band";
import type { Drive, Student, StudentExperience, CampusSummary, StudentProject } from "@/types";

// Brand icons were removed from lucide-react — inline the GitHub and LinkedIn marks instead.
function GitHubIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
      <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
      <path d="M9 18c-4.51 2-5-2-7-2" />
    </svg>
  );
}

function LinkedInIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
      <path d="M16 8a6 6 0 0 1 6 6v7h-4v-7a2 2 0 0 0-2-2 2 2 0 0 0-2 2v7h-4v-7a6 6 0 0 1 6-6z" />
      <rect width="4" height="12" x="2" y="9" />
      <circle cx="4" cy="4" r="2" />
    </svg>
  );
}

// ── Helper sub-components ─────────────────────────────────────────────────────

function SectionHeader({
  icon: Icon, title, subtitle, action,
}: {
  icon: React.ElementType; title: string; subtitle?: string; action?: React.ReactNode;
}) {
  return (
    <div className="mb-4 flex items-center justify-between gap-3">
      <div className="flex items-center gap-3">
        <div className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg bg-violet-bright/10 text-violet-bright">
          <Icon className="h-4 w-4" />
        </div>
        <div>
          <h3 className="text-sm font-semibold text-foreground">{title}</h3>
          {subtitle && <p className="text-xs text-muted-foreground">{subtitle}</p>}
        </div>
      </div>
      {action && <div className="shrink-0">{action}</div>}
    </div>
  );
}

function StatPill({ label, value, tone = "default" }: {
  label: string; value: React.ReactNode;
  tone?: "success" | "warning" | "risk" | "default";
}) {
  const colors = {
    success: "text-success bg-success/10 border-success/20",
    warning: "text-warning bg-warning/10 border-warning/20",
    risk: "text-risk bg-risk/10 border-risk/20",
    default: "text-violet-bright bg-violet/10 border-violet/20",
  };
  return (
    <div className={`flex flex-col items-center gap-0.5 rounded-xl border px-4 py-3 ${colors[tone]}`}>
      <span className="text-xl font-bold tabular-nums">{value}</span>
      <span className="text-[10px] font-medium uppercase tracking-wider opacity-80">{label}</span>
    </div>
  );
}

function DataRow({ label, value }: { label: string; value: React.ReactNode }) {
  return (
    <div className="flex items-baseline justify-between gap-3 border-b border-border/40 py-2.5 last:border-0">
      <span className="shrink-0 text-xs text-muted-foreground">{label}</span>
      <span className="text-right text-sm font-medium tabular-nums">{value}</span>
    </div>
  );
}

function EditField({ label, value, onChange, type = "text", step, min, max }: {
  label: string; value: string; onChange: (v: string) => void;
  type?: "text" | "number"; step?: string; min?: number; max?: number;
}) {
  return (
    <div className="flex items-center justify-between gap-3 border-b border-border/40 py-2 last:border-0">
      <Label className="shrink-0 text-xs text-muted-foreground">{label}</Label>
      <Input type={type} step={step} min={min} max={max} value={value}
        onChange={(e) => onChange(e.target.value)}
        className="h-8 max-w-[180px] text-right text-sm tabular-nums" />
    </div>
  );
}

function ListFieldEdit({ label, hint, value, onChange }: {
  label: string; hint?: string; value: string; onChange: (v: string) => void;
}) {
  return (
    <div>
      <Label className="text-xs text-muted-foreground">{label}</Label>
      <Input value={value} onChange={(e) => onChange(e.target.value)} className="mt-1 h-8 text-sm" />
      {hint && <p className="mt-0.5 text-[11px] text-muted-foreground/70">{hint}</p>}
    </div>
  );
}

const MONTHS = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];

type MonthRef = { year: number; month: number };

// Duration is stored as a plain string ("May 2025 to Jul 2025"); parse what we
// recognise so the calendar can preselect it, and leave anything else as-is.
function parseDuration(duration: string): { start: MonthRef | null; end: MonthRef | null; present: boolean } {
  const m = duration.trim().match(/^([A-Za-z]+)\.?\s+(\d{4})\s+(?:to|-|–|—)\s+(.+)$/);
  if (!m) return { start: null, end: null, present: false };
  const start = monthRef(m[1], m[2]);
  const endRaw = m[3].trim();
  if (/^present$/i.test(endRaw)) return { start, end: null, present: true };
  const em = endRaw.match(/^([A-Za-z]+)\.?\s+(\d{4})$/);
  return { start, end: em ? monthRef(em[1], em[2]) : null, present: false };
}

function monthRef(name: string, year: string): MonthRef | null {
  const month = MONTHS.findIndex((mn) => mn.toLowerCase() === name.slice(0, 3).toLowerCase());
  return month === -1 ? null : { year: Number(year), month };
}

function formatDuration(start: MonthRef | null, end: MonthRef | null, present: boolean): string {
  if (!start) return "";
  const label = (r: MonthRef) => `${MONTHS[r.month]} ${r.year}`;
  if (present) return `${label(start)} to Present`;
  return end ? `${label(start)} to ${label(end)}` : label(start);
}

function MonthGrid({ selected, onPick }: { selected: MonthRef | null; onPick: (r: MonthRef) => void }) {
  const [year, setYear] = useState(selected?.year ?? new Date().getFullYear());
  return (
    <div>
      <div className="mb-1 flex items-center justify-between">
        <button type="button" onClick={() => setYear((y) => y - 1)} className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground" aria-label="Previous year">
          <ChevronLeft className="h-3.5 w-3.5" />
        </button>
        <span className="text-xs font-semibold tabular-nums">{year}</span>
        <button type="button" onClick={() => setYear((y) => y + 1)} className="rounded-md p-1 text-muted-foreground hover:bg-muted hover:text-foreground" aria-label="Next year">
          <ChevronRight className="h-3.5 w-3.5" />
        </button>
      </div>
      <div className="grid grid-cols-4 gap-1">
        {MONTHS.map((mn, month) => (
          <button
            key={mn}
            type="button"
            onClick={() => onPick({ year, month })}
            className={`rounded-md px-1.5 py-1 text-xs ${selected && selected.year === year && selected.month === month ? "bg-violet-bright font-semibold text-white" : "text-muted-foreground hover:bg-muted hover:text-foreground"}`}
          >
            {mn}
          </button>
        ))}
      </div>
    </div>
  );
}

function DurationPicker({ value, onChange }: { value: string; onChange: (next: string) => void }) {
  const [open, setOpen] = useState(false);
  const { start, end, present } = parseDuration(value);
  const commit = (nextStart: MonthRef | null, nextEnd: MonthRef | null, nextPresent: boolean) =>
    onChange(formatDuration(nextStart, nextEnd, nextPresent));
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="outline" size="sm" className="h-8 w-8 shrink-0 p-0" aria-label="Pick duration dates">
          <Calendar className="h-3.5 w-3.5" />
        </Button>
      </PopoverTrigger>
      <PopoverContent className="w-64 space-y-3 p-3">
        <div>
          <p className="mb-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Start</p>
          <MonthGrid selected={start} onPick={(r) => commit(r, end, present)} />
        </div>
        <div>
          <div className="mb-1 flex items-center justify-between">
            <p className="text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">End</p>
            <label className="flex items-center gap-1.5 text-[10px] text-muted-foreground">
              <Switch checked={present} onCheckedChange={(c) => commit(start, c ? null : end, c)} />
              Present
            </label>
          </div>
          {!present && <MonthGrid selected={end} onPick={(r) => commit(start ?? r, r, false)} />}
        </div>
      </PopoverContent>
    </Popover>
  );
}

function ExperienceEditor({ values, onChange }: {
  values: StudentExperience[]; onChange: (next: StudentExperience[]) => void;
}) {
  const update = (i: number, patch: Partial<StudentExperience>) =>
    onChange(values.map((e, idx) => (idx === i ? { ...e, ...patch } : e)));
  const remove = (i: number) => onChange(values.filter((_, idx) => idx !== i));
  const add = () => onChange([...values, { company: "", role: "", duration: "", summary: "" }]);
  return (
    <div className="space-y-3">
      {values.map((e, i) => (
        <div key={i} className="space-y-2 rounded-xl border border-border/60 bg-card/50 p-3">
          <div className="flex items-center justify-between gap-2">
            <Label className="text-xs text-muted-foreground">Experience {i + 1}</Label>
            <button type="button" onClick={() => remove(i)} className="text-muted-foreground hover:text-risk" aria-label={`Remove experience ${i + 1}`}>
              <Trash2 className="h-3.5 w-3.5" />
            </button>
          </div>
          <Input value={e.company} placeholder="Company" onChange={(ev) => update(i, { company: ev.target.value })} className="h-8 text-sm" />
          <Input value={e.role} placeholder="Role, e.g. Backend Engineering Intern" onChange={(ev) => update(i, { role: ev.target.value })} className="h-8 text-sm" />
          <div className="flex items-center gap-2">
            <Input value={e.duration} placeholder="Duration, e.g. May 2025 to Jul 2025" onChange={(ev) => update(i, { duration: ev.target.value })} className="h-8 text-sm" />
            <DurationPicker value={e.duration} onChange={(next) => update(i, { duration: next })} />
          </div>
          <Input value={e.summary} placeholder="What you worked on" onChange={(ev) => update(i, { summary: ev.target.value })} className="h-8 text-sm" />
        </div>
      ))}
      <Button type="button" size="sm" variant="outline" onClick={add} className="gap-1.5">
        <Plus className="h-3.5 w-3.5" /> Add experience
      </Button>
    </div>
  );
}

function CertFormField({ label, required, children }: { label: string; required?: boolean; children: React.ReactNode }) {
  return (
    <div>
      <Label className="mb-1 block text-xs">{label} {required && <span className="text-risk">*</span>}</Label>
      {children}
    </div>
  );
}

type Draft = Record<string, string>;

// ── MAIN PAGE ─────────────────────────────────────────────────────────────────

export default function PlacementProfilePage() {
  const [version, setVersion] = useState(0);
  const { data: s, loading, error, reload } = useApiResource<Student | null>(
    () => getPrimaryStudent(), [version], null,
  );
  const { data: allDrives } = useApiResource<Drive[]>(() => listDrives(), [], []);
  const { data: skills } = useApiResource<StudentSkill[]>(() => listSkills(), [version], []);
  const { data: backlogHistory } = useApiResource<BacklogHistoryEntry[]>(
    () => getMyBacklogHistory(), [version], [],
  );
  const { data: campus } = useApiResource<CampusSummary | null>(
    () => (s?.campusId ? getCampus(s.campusId).then((c) => c ?? null) : Promise.resolve(null)),
    [s?.campusId], null,
  );
  // Fetched at page level so the completeness score can count certificates; the Certificates
  // section below reuses this list and reloads it after its own mutations.
  const { data: certificates, loading: certificatesLoading, reload: reloadCertificates } =
    useApiResource<StudentCertificate[]>(() => listCertificates(), [version], []);

  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState<Draft>({});
  const [experience, setExperience] = useState<StudentExperience[]>([]);
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);

  const beginEdit = (student: Student) => {
    setSaveError(null);
    // Academic/identity fields (branch, CGPA, 10th/12th, backlogs...) are directory-owned (V60) —
    // they come from the campus student directory and are not student-editable here.
    setDraft({
      phone: student.phone ?? "",
      linkedinUrl: student.linkedinUrl ?? "",
      githubUrl: student.githubUrl ?? "",
      targetRole: student.targetRole ?? "",
      preferredRoles: (student.preferredRoles ?? []).join(", "),
      preferredDomains: (student.preferredDomains ?? []).join(", "),
      preferredLocations: (student.preferredLocations ?? []).join(", "),
    });
    setExperience(student.experience ?? []);
    setEditing(true);
  };

  const setField = (key: string, value: string) => setDraft((d) => ({ ...d, [key]: value }));

  const str = (v: string): string | undefined => { const t = v.trim(); return t === "" ? undefined : t; };
  const list = (v: string): string[] => v.split(",").map((t) => t.trim()).filter(Boolean);

  const handleSave = async () => {
    setSaving(true); setSaveError(null);
    // Academic fields are directory-owned (V60) — the backend strips them from
    // StudentUpdateRequest, so the UI doesn't send them either.
    const payload: StudentUpdateInput = {
      phone: str(draft.phone),
      linkedinUrl: normalizeProjectUrl(draft.linkedinUrl),
      githubUrl: normalizeProjectUrl(draft.githubUrl),
      targetRole: str(draft.targetRole),
      preferredRoles: list(draft.preferredRoles),
      preferredDomains: list(draft.preferredDomains),
      preferredLocations: list(draft.preferredLocations),
      experience: experience
        .map((e) => ({ company: e.company.trim(), role: e.role.trim(), duration: e.duration.trim(), summary: e.summary.trim() }))
        .filter((e) => e.company),
    };
    try {
      await updatePrimaryStudent(payload);
      setEditing(false); setVersion((v) => v + 1);
    } catch (err) {
      setSaveError(err instanceof Error ? err.message : "Could not save your profile. Please try again.");
    } finally { setSaving(false); }
  };

  const eligibleCount = useMemo(
    () => allDrives.filter((d) => d.status === "ACTIVE" && s && evaluateEligibility(s, d).eligible).length,
    [allDrives, s],
  );
  const completeness = s ? profileCompleteness(s, skills.length, certificates.length) : null;
  const missing = completeness?.items.filter((i) => !i.done) ?? [];
  const band = s ? readinessBand(s.readiness) : null;

  // ── Loading / error ─────────────────────────────────────────────────────────
  if (!s) {
    return (
      <div className="mx-auto max-w-5xl px-4 py-10">
        <div className="mb-8">
          <p className="text-xs font-semibold uppercase tracking-widest text-violet-bright">Profile</p>
          <h1 className="mt-1 text-3xl font-bold">Placement Profile</h1>
          <p className="mt-1 text-sm text-muted-foreground">
            The single record every eligibility check, match score and readiness calculation reads from.
          </p>
        </div>
        {loading && (
          <div className="flex flex-col items-center gap-4 py-20">
            <div className="relative h-16 w-16">
              <div className="absolute inset-0 animate-spin rounded-full border-2 border-border border-t-violet-bright" />
              <div className="absolute inset-3 rounded-full bg-violet/20" />
            </div>
            <p className="text-sm text-muted-foreground">Loading your profile...</p>
          </div>
        )}
        {!loading && error && (
          <div className="flex flex-col items-center gap-4 py-20 text-center">
            <div className="flex h-16 w-16 items-center justify-center rounded-2xl bg-risk/10 text-risk">
              <Shield className="h-8 w-8" />
            </div>
            <p className="text-sm text-risk" role="alert">{error}</p>
            <Button size="sm" variant="outline" onClick={reload}>Retry</Button>
          </div>
        )}
        {!loading && !error && (
          <p className="py-10 text-center text-sm text-muted-foreground">
            Sign in as a student to view your placement profile.
          </p>
        )}
      </div>
    );
  }

  // ── Edit mode ───────────────────────────────────────────────────────────────
  if (editing) {
    return (
      <div className="mx-auto max-w-3xl px-4 pb-16">
        <div className="sticky top-0 z-10 -mx-4 mb-6 flex items-center justify-between gap-3 border-b border-border/60 bg-background/80 px-4 py-3 backdrop-blur">
          <div>
            <p className="text-xs font-semibold uppercase tracking-widest text-violet-bright">Profile</p>
            <h1 className="text-lg font-bold">Edit Profile</h1>
          </div>
          <div className="flex items-center gap-2">
            <Button size="sm" variant="outline" onClick={() => { setEditing(false); setSaveError(null); }} disabled={saving}>
              <X className="h-3.5 w-3.5" /> Cancel
            </Button>
            <Button size="sm" variant="glow" onClick={handleSave} disabled={saving}>
              {saving ? <Loader2 className="h-3.5 w-3.5 animate-spin" /> : <Check className="h-3.5 w-3.5" />}
              {saving ? "Saving..." : "Save Changes"}
            </Button>
          </div>
        </div>
        {saveError && <p className="mb-4 rounded-lg bg-risk/10 p-3 text-sm text-risk" role="alert">{saveError}</p>}
        <div className="space-y-5">
          <DepthCard className="p-5">
            <SectionHeader icon={Phone} title="Contact" />
            <EditField label="Phone" value={draft.phone} onChange={(v) => setField("phone", v)} />
            <EditField label="LinkedIn URL" value={draft.linkedinUrl} onChange={(v) => setField("linkedinUrl", v)} />
            <EditField label="GitHub URL" value={draft.githubUrl} onChange={(v) => setField("githubUrl", v)} />
          </DepthCard>
          <div className="flex items-center gap-3 rounded-xl border border-border/60 bg-muted/20 px-4 py-3">
            <GraduationCap className="h-4 w-4 shrink-0 text-violet-bright" />
            <p className="text-xs text-muted-foreground">
              Academic records (branch, CGPA, 10th/12th marks, backlogs) come from your campus
              student directory and are managed by your Placement Officer. Contact the placement
              office to correct them.
            </p>
          </div>
          <DepthCard className="p-5">
            <SectionHeader icon={Target} title="Career Intent" />
            <div className="space-y-3">
              <ListFieldEdit label="Target Role" hint="The one role you are aiming for." value={draft.targetRole} onChange={(v) => setField("targetRole", v)} />
              <ListFieldEdit label="Preferred Roles" hint="Comma-separated, e.g. Backend Engineer, Data Analyst" value={draft.preferredRoles} onChange={(v) => setField("preferredRoles", v)} />
              <ListFieldEdit label="Preferred Domains" hint="Comma-separated, e.g. Fintech, Healthcare" value={draft.preferredDomains} onChange={(v) => setField("preferredDomains", v)} />
              <ListFieldEdit label="Preferred Locations" hint="Comma-separated, e.g. Bengaluru, Remote" value={draft.preferredLocations} onChange={(v) => setField("preferredLocations", v)} />
            </div>
          </DepthCard>
          <DepthCard className="p-5">
            <SectionHeader icon={Building2} title="Work Experience" />
            <ExperienceEditor values={experience} onChange={setExperience} />
          </DepthCard>
        </div>
      </div>
    );
  }

  // ── Display mode ────────────────────────────────────────────────────────────
  return (
    <div className="mx-auto max-w-5xl px-4 pb-16">
      <ProfileHero student={s} campus={campus} completeness={completeness} band={band} eligibleCount={eligibleCount} onEdit={() => beginEdit(s)} />
      <div className="mt-5 grid gap-5 md:grid-cols-2">
        <ProfileCompletionCard completeness={completeness} missing={missing} onFix={() => beginEdit(s)} />
        <PlacementReadinessCard student={s} />
      </div>
      <div className="mt-5 grid gap-5 md:grid-cols-2">
        <AcademicSnapshot student={s} campus={campus} backlogHistory={backlogHistory} />
        <AssessmentCard student={s} />
      </div>
      <SkillsSection skills={skills} onReload={() => setVersion((v) => v + 1)} />
      <ProfileCertificatesSection certificates={certificates} loading={certificatesLoading} onReload={reloadCertificates} />
      <WorkExperienceSection student={s} onEdit={() => beginEdit(s)} />
      <ProjectsSection student={s} onReload={() => setVersion((v) => v + 1)} />
      <CareerIntentSection student={s} onEdit={() => beginEdit(s)} />
      <ProfileInsightsSection student={s} skills={skills} eligibleCount={eligibleCount} certificateCount={certificates.length} />
    </div>
  );
}

// ── PROFILE HERO ──────────────────────────────────────────────────────────────

function ProfileHero({
  student: s, campus, completeness, band, eligibleCount, onEdit,
}: {
  student: Student; campus: CampusSummary | null;
  completeness: ReturnType<typeof profileCompleteness> | null;
  band: { label: string; tone: string } | null;
  eligibleCount: number; onEdit: () => void;
}) {
  const placementTone = placementStatusTone[s.placementStatus] ?? "muted";
  const toneClasses: Record<string, string> = {
    success: "border-success/30 bg-success/10 text-success",
    warning: "border-warning/30 bg-warning/10 text-warning",
    risk: "border-risk/30 bg-risk/10 text-risk",
    muted: "border-border bg-muted/20 text-muted-foreground",
    default: "border-violet/30 bg-violet/10 text-violet-bright",
  };
  const bandText: Record<string, string> = {
    success: "text-success", warning: "text-warning", risk: "text-risk", default: "text-violet-bright",
  };
  return (
    <Card className="overflow-hidden p-0 border border-border/80 bg-card shadow-card transition-[border-color,box-shadow] duration-300 hover:border-violet/40 hover:shadow-glow">
      <div className="p-6">
        <div className="flex flex-col gap-5 sm:flex-row sm:items-start sm:gap-6">
          <div className="relative shrink-0">
            <Avatar className="h-20 w-20 ring-2 ring-violet-bright/30 ring-offset-2 ring-offset-card">
              <AvatarFallback className="bg-violet/20 text-xl font-bold text-violet-bright">{s.avatarInitials}</AvatarFallback>
            </Avatar>
            <span className={`absolute -bottom-0.5 -right-0.5 h-4 w-4 rounded-full border-2 border-card ${band?.tone === "success" ? "bg-success" : band?.tone === "warning" ? "bg-warning" : "bg-risk"}`} />
          </div>
          <div className="min-w-0 flex-1">
            <div className="flex flex-wrap items-start justify-between gap-3">
              <div className="min-w-0">
                <h1 className="truncate text-2xl font-bold tracking-tight">{s.name}</h1>
                <p className="mt-0.5 text-sm text-muted-foreground">{s.rollNumber} · {s.branch} · Batch {s.batch}</p>
              </div>
              <Button size="sm" variant="outline" onClick={onEdit} id="edit-profile-btn">
                <Pencil className="h-3.5 w-3.5" /> Edit Profile
              </Button>
            </div>
            <div className="mt-3 flex flex-wrap gap-x-4 gap-y-1.5 text-xs text-muted-foreground">
              <span className="flex items-center gap-1.5"><Mail className="h-3.5 w-3.5 shrink-0 text-violet-bright/70" /><span className="break-all">{s.email}</span></span>
              {s.phone && <span className="flex items-center gap-1.5"><Phone className="h-3.5 w-3.5 shrink-0 text-violet-bright/70" />{s.phone}</span>}
              {campus && <span className="flex items-center gap-1.5"><MapPin className="h-3.5 w-3.5 shrink-0 text-violet-bright/70" />{campus.name}, {campus.city}</span>}
              {s.linkedinUrl && (
                <a href={s.linkedinUrl} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-violet-bright transition-colors hover:text-violet">
                  <LinkedInIcon className="h-3.5 w-3.5 shrink-0" />LinkedIn
                </a>
              )}
              {s.githubUrl && (
                <a href={s.githubUrl} target="_blank" rel="noopener noreferrer" className="flex items-center gap-1.5 text-violet-bright transition-colors hover:text-violet">
                  <GitHubIcon className="h-3.5 w-3.5 shrink-0" />GitHub
                </a>
              )}
            </div>
            {s.targetRole && (
              <div className="mt-3 flex items-center gap-2">
                <Target className="h-3.5 w-3.5 shrink-0 text-violet-bright" />
                <span className="text-sm font-medium">{s.targetRole}</span>
                <Badge variant="default" className="text-[10px]">Target</Badge>
              </div>
            )}
            <div className="mt-4 flex flex-wrap gap-2">
              <span className={`inline-flex items-center gap-1.5 rounded-full border px-3 py-1 text-xs font-medium ${toneClasses[placementTone]}`}>
                <span className="h-1.5 w-1.5 rounded-full bg-current" />{s.placementStatus}
              </span>
              {band && (
                <span className={`inline-flex items-center gap-1.5 rounded-full border border-current/20 bg-current/10 px-3 py-1 text-xs font-medium ${bandText[band.tone]}`}>
                  <Zap className="h-3 w-3" />{band.label}
                </span>
              )}
              <span className="inline-flex items-center gap-1.5 rounded-full border border-border bg-muted/20 px-3 py-1 text-xs font-medium text-muted-foreground">
                <Shield className="h-3 w-3" />{eligibleCount} eligible drives
              </span>
            </div>
          </div>
        </div>
        {completeness && (
          <div className="mt-5 border-t border-border/40 pt-4">
            <div className="flex items-center justify-between gap-2 text-xs">
              <span className="text-muted-foreground">Profile completion</span>
              <span className={`font-semibold tabular-nums ${completeness.score >= 80 ? "text-success" : completeness.score >= 50 ? "text-warning" : "text-risk"}`}>{completeness.score}%</span>
            </div>
            <div className="mt-1.5 h-1.5 w-full overflow-hidden rounded-full bg-muted">
              <div className={`h-full rounded-full transition-all duration-700 ${completeness.score >= 80 ? "bg-success" : completeness.score >= 50 ? "bg-warning" : "bg-risk"}`} style={{ width: `${completeness.score}%` }} />
            </div>
          </div>
        )}
      </div>
    </Card>
  );
}

// ── PROFILE COMPLETION CARD ───────────────────────────────────────────────────

function ProfileCompletionCard({ completeness, missing, onFix }: {
  completeness: ReturnType<typeof profileCompleteness> | null;
  missing: ReturnType<typeof profileCompleteness>["items"];
  onFix: () => void;
}) {
  if (!completeness) return null;
  return (
    <DepthCard className="p-5 flex flex-col justify-between h-full">
      <div>
        <SectionHeader icon={BadgeCheck} title="Profile Completeness" />
        <div className="flex items-center gap-4">
          <ScoreRing
            value={completeness.score}
            size={88}
            strokeWidth={7}
            colorClass={completeness.score >= 80 ? "stroke-success" : completeness.score >= 50 ? "stroke-warning" : "stroke-risk"}
            label="done"
          />
          <div className="flex-1 space-y-1.5">
            <div className="flex items-center gap-2">
              <p className="text-sm font-semibold">{completeness.completed} of {completeness.total} sections complete</p>
              {completeness.score >= 100 && (
                <Badge variant="outline" className="border-success/30 bg-success/10 text-success text-[10px] px-1.5 py-0">
                  Ready
                </Badge>
              )}
            </div>
            {completeness.score >= 100 ? (
              <p className="flex items-center gap-1.5 text-xs text-success font-medium">
                <Check className="h-3.5 w-3.5" /> Profile is complete & verified
              </p>
            ) : (
              <p className="text-xs text-muted-foreground">
                {missing.length} section{missing.length !== 1 ? "s" : ""} remaining
              </p>
            )}
            <p className="text-[11px] text-muted-foreground/80 leading-relaxed">
              {completeness.score >= 100
                ? "All essential placement records are active and matched with recruiter criteria."
                : "Fill remaining profile details to boost your interview recommendation score."}
            </p>
          </div>
        </div>
        {missing.length > 0 && (
          <ul className="mt-3.5 space-y-1.5 rounded-lg border border-border/60 bg-muted/20 p-2.5">
            {missing.slice(0, 3).map((item) => (
              <li key={item.key} className="flex items-start gap-2 text-xs text-muted-foreground">
                <Circle className="mt-0.5 h-2.5 w-2.5 shrink-0 text-warning" />
                <span><span className="font-medium text-foreground">{item.label}</span> — {item.hint}</span>
              </li>
            ))}
          </ul>
        )}
      </div>

      <div className="mt-4 pt-3 border-t border-border/60 flex items-center justify-between">
        <span className="text-xs text-muted-foreground flex items-center gap-1.5">
          {completeness.score >= 100 ? (
            <>
              <span className="h-2 w-2 rounded-full bg-success inline-block" />
              <span className="text-[11px]">Ready for Campus Drives</span>
            </>
          ) : (
            <>
              <span className="h-2 w-2 rounded-full bg-warning inline-block" />
              <span className="text-[11px]">{missing.length} pending update{missing.length !== 1 ? "s" : ""}</span>
            </>
          )}
        </span>
        <Button
          variant="ghost"
          size="sm"
          onClick={onFix}
          className="text-xs text-violet-bright hover:text-white gap-1.5 p-0 h-auto"
        >
          <Pencil className="h-3 w-3" />
          {completeness.score >= 100 ? "Edit Profile" : "Complete Profile"}
        </Button>
      </div>
    </DepthCard>
  );
}

// ── PLACEMENT READINESS CARD ──────────────────────────────────────────────────

function PlacementReadinessCard({ student: s }: { student: Student }) {
  const band = readinessBand(s.readiness);
  return (
    <DepthCard className="p-5 flex flex-col justify-between h-full">
      <div>
        <SectionHeader icon={TrendingUp} title="Placement Readiness" />
        <div className="flex items-center gap-5">
          <ScoreRing
            value={s.readiness}
            size={88}
            strokeWidth={7}
            colorClass={band.tone === "success" ? "stroke-success" : band.tone === "warning" ? "stroke-warning" : "stroke-risk"}
            label="/ 100"
            sublabel="readiness"
          />
          <div className="flex-1 space-y-2">
            <div><p className="text-xs text-muted-foreground">Readiness Band</p><p className={`text-sm font-semibold ${band.tone === "success" ? "text-success" : band.tone === "warning" ? "text-warning" : "text-risk"}`}>{band.label}</p></div>
            <div><p className="text-xs text-muted-foreground">Placement Probability</p><p className="text-sm font-semibold">{s.placementProbability}%</p></div>
            <div><p className="text-xs text-muted-foreground">Risk Level</p><p className={`text-sm font-semibold ${s.riskLevel === "LOW" ? "text-success" : s.riskLevel === "MEDIUM" ? "text-warning" : "text-risk"}`}>{s.riskLevel}</p></div>
          </div>
        </div>
        {s.riskReason && (
          <p className="mt-4 rounded-lg border border-warning/20 bg-warning/5 px-3 py-2 text-xs text-muted-foreground">
            <span className="font-medium text-warning">Risk: </span>{s.riskReason}
          </p>
        )}
      </div>
      <div className="mt-4 pt-3 border-t border-border/60 flex justify-end">
        <Button asChild variant="ghost" size="sm" className="text-xs text-violet-bright hover:text-white gap-1.5 p-0 h-auto">
          <Link href="/student/readiness">
            View Full Readiness Analytics <ArrowUpRight className="h-3 w-3" />
          </Link>
        </Button>
      </div>
    </DepthCard>
  );
}

// ── ACADEMIC SNAPSHOT ─────────────────────────────────────────────────────────

function AcademicSnapshot({ student: s, campus, backlogHistory }: {
  student: Student; campus: CampusSummary | null; backlogHistory: BacklogHistoryEntry[];
}) {
  return (
    <DepthCard className="p-5">
      <SectionHeader
        icon={GraduationCap}
        title="Academic Snapshot"
        subtitle={campus?.name ?? ""}
        action={<Badge variant="outline" className="border-violet/30 bg-violet/10 text-violet-bright text-[10px]">From college records</Badge>}
      />
      <div className="mb-4 grid grid-cols-2 gap-3">
        <StatPill label="CGPA" value={s.cgpa.toFixed(2)} tone={s.cgpa >= 7 ? "success" : s.cgpa >= 5.5 ? "warning" : "risk"} />
        <StatPill label="Backlogs" value={s.backlogs} tone={s.backlogs === 0 ? "success" : s.backlogs <= 2 ? "warning" : "risk"} />
      </div>
      <DataRow label="Branch" value={s.branchCode ? `${s.branch} (${s.branchCode})` : s.branch} />
      <DataRow label="Department" value={s.department || "—"} />
      <DataRow label="Batch / Semester" value={`${s.batch} · Sem ${s.semester}`} />
      <DataRow label="Graduation Year" value={s.graduationYear || "—"} />
      <DataRow label="10th Percentage" value={`${s.tenthPercentage}%`} />
      <DataRow label="12th Percentage" value={`${s.twelfthPercentage}%`} />
      {backlogHistory.length > 0 && (
        <div className="mt-3 border-t border-border/40 pt-3">
          <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Backlog Timeline</p>
          <ul className="space-y-1.5">
            {backlogHistory.slice(0, 4).map((h) => (
              <li key={h.id} className="flex items-center justify-between gap-2 text-xs text-muted-foreground">
                <span className="flex items-center gap-2">
                  <span className={`font-semibold ${h.activeBacklogs > 0 ? "text-risk" : "text-success"}`}>{h.activeBacklogs} active</span>
                  {h.semester != null && <span>· Sem {h.semester}</span>}
                  {h.clearedBacklogs != null && h.clearedBacklogs > 0 && (
                    <Badge variant="success" className="text-[9px]">{h.clearedBacklogs} cleared</Badge>
                  )}
                </span>
                <span>{new Date(h.recordedAt).toLocaleDateString()}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </DepthCard>
  );
}

// ── ASSESSMENT CARD ───────────────────────────────────────────────────────────

function AssessmentCard({ student: s }: { student: Student }) {
  return (
    <DepthCard className="p-5">
      <SectionHeader icon={ClipboardCheck} title="Assessment Performance" />
      <div className="space-y-3">
        <SkillBar label="Aptitude" value={s.assessments.aptitude} colorClass={s.assessments.aptitude >= 70 ? "bg-success" : s.assessments.aptitude >= 50 ? "bg-warning" : "bg-risk"} />
        <SkillBar label="Communication" value={s.assessments.communication} colorClass={s.assessments.communication >= 70 ? "bg-success" : s.assessments.communication >= 50 ? "bg-warning" : "bg-risk"} />
        <SkillBar label="Mock Interview" value={s.assessments.mockInterview} colorClass={s.assessments.mockInterview >= 70 ? "bg-success" : s.assessments.mockInterview >= 50 ? "bg-warning" : "bg-risk"} />
      </div>
      <div className="mt-5 border-t border-border/40 pt-4">
        <div className="mb-3 flex items-center gap-2"><Heart className="h-4 w-4 text-violet-bright" /><p className="text-sm font-semibold">Placement Status</p></div>
        <DataRow label="Status" value={<span className={`font-semibold ${s.placementStatus === "PLACED" ? "text-success" : s.placementStatus === "IN PROCESS" ? "text-warning" : "text-muted-foreground"}`}>{s.placementStatus}</span>} />
        <DataRow label="Resume Score" value={<span className="font-semibold text-violet-bright">{s.resumeScore}/100</span>} />
      </div>
      <Button asChild variant="outline" size="sm" className="mt-4 w-full gap-1.5">
        <Link href="/student/assessments"><ClipboardCheck className="h-3.5 w-3.5" /> View Assessments <ArrowUpRight className="h-3.5 w-3.5" /></Link>
      </Button>
    </DepthCard>
  );
}

// ── SKILLS SECTION ────────────────────────────────────────────────────────────
// Reuses the SAME listSkills/upsertSkill/deleteSkill APIs as My Skills page.

function SkillsSection({ skills, onReload }: { skills: StudentSkill[]; onReload: () => void }) {
  const pushToast = useAppStore((st) => st.pushToast);
  const [addOpen, setAddOpen] = useState(false);
  const [newSkill, setNewSkill] = useState("");
  const [newLevel, setNewLevel] = useState("60");
  const [busy, setBusy] = useState(false);
  const [skillError, setSkillError] = useState<string | null>(null);
  const displayedSkills = [...skills].sort((a, b) => b.proficiency - a.proficiency).slice(0, 9);

  const handleAdd = async () => {
    const skill = newSkill.trim(); const level = Number(newLevel);
    if (!skill) { setSkillError("Enter a skill name."); return; }
    if (!Number.isFinite(level) || level < 0 || level > 100) { setSkillError("Proficiency must be 0-100."); return; }
    setBusy(true); setSkillError(null);
    try {
      await upsertSkill({ skill, proficiency: Math.round(level) });
      setNewSkill(""); setNewLevel("60"); setAddOpen(false);
      pushToast("Skill saved", `${skill} added at ${Math.round(level)}%.`); onReload();
    } catch (err) { setSkillError(err instanceof Error ? err.message : "Could not save the skill."); }
    finally { setBusy(false); }
  };

  return (
    <>
      <Card className="mt-5 p-5 border border-border/80 bg-card shadow-card">
        <SectionHeader
          icon={ListChecks}
          title="Skills & Proficiencies"
          subtitle={`${skills.length} skill${skills.length !== 1 ? "s" : ""} recorded`}
          action={
            <div className="flex items-center gap-2">
              <Button variant="glow" size="sm" onClick={() => setAddOpen(true)} id="add-skill-modal-btn">
                <Plus className="h-3.5 w-3.5 mr-1" /> Add Skill
              </Button>
              <Button asChild variant="outline" size="sm" id="manage-skills-btn">
                <Link href="/student/skills">
                  <ListChecks className="h-3.5 w-3.5 mr-1" /> Manage <ArrowUpRight className="h-3.5 w-3.5 ml-1" />
                </Link>
              </Button>
            </div>
          }
        />

        {skills.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border/60 py-8 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-violet/10 text-violet-bright">
              <ListChecks className="h-6 w-6" />
            </div>
            <p className="text-sm font-medium">No skills recorded yet</p>
            <p className="text-xs text-muted-foreground">Add your engineering skills to match with recruiter requirements</p>
            <Button size="sm" variant="outline" onClick={() => setAddOpen(true)}>
              <Plus className="h-3.5 w-3.5 mr-1" /> Add First Skill
            </Button>
          </div>
        ) : (
          <div className="space-y-3">
            <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-2.5">
              {displayedSkills.map((sk) => {
                const b = proficiencyBand(sk.proficiency);
                return (
                  <div
                    key={sk.id}
                    className="flex flex-col justify-between gap-2.5 rounded-xl border border-border/70 bg-card/50 p-3 transition-[border-color,background-color] duration-200 hover:border-violet/40 hover:bg-card/90"
                  >
                    <div className="flex items-start justify-between gap-2">
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-semibold text-foreground">{sk.skill}</p>
                        <span className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium mt-1 ${b.chip} ${b.text}`}>
                          <b.Icon className="h-2.5 w-2.5" />
                          {b.label}
                        </span>
                      </div>
                      <span className={`shrink-0 text-sm font-bold tabular-nums ${b.text}`}>
                        {sk.proficiency}%
                      </span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted/70">
                      <div
                        className={`h-full rounded-full transition-all duration-500 ${b.bar}`}
                        style={{ width: `${sk.proficiency}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>

            {skills.length > 9 && (
              <Button asChild variant="ghost" size="sm" className="w-full gap-1.5 text-xs text-muted-foreground mt-1">
                <Link href="/student/skills">View all {skills.length} skills <ChevronRight className="h-3.5 w-3.5" /></Link>
              </Button>
            )}
          </div>
        )}
      </Card>

      {/* Add Skill Dialog Modal */}
      <Dialog open={addOpen} onOpenChange={setAddOpen}>
        <DialogContent className="max-w-md">
          <DialogHeader>
            <DialogTitle>Add a Skill</DialogTitle>
            <DialogDescription>
              Record a new skill and your current proficiency level on your placement profile.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div>
              <Label className="text-xs text-muted-foreground">Skill Name</Label>
              <Input
                value={newSkill}
                placeholder="e.g. React, Spring Boot, Docker, System Design"
                onChange={(e) => setNewSkill(e.target.value)}
                onKeyDown={(e) => { if (e.key === "Enter") handleAdd(); }}
                className="mt-1"
                autoFocus
              />
            </div>

            <div>
              <div className="flex items-center justify-between text-xs text-muted-foreground mb-1">
                <Label className="text-xs">Proficiency Level</Label>
                <span className="font-semibold tabular-nums text-foreground">{newLevel}%</span>
              </div>
              <Input
                type="number"
                min={0}
                max={100}
                value={newLevel}
                onChange={(e) => setNewLevel(e.target.value)}
                placeholder="0 - 100"
                className="tabular-nums"
              />
            </div>

            {skillError && (
              <p className="text-xs text-risk" role="alert">
                {skillError}
              </p>
            )}
          </div>

          <DialogFooter>
            <Button variant="outline" size="sm" onClick={() => setAddOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="glow" size="sm" onClick={handleAdd} disabled={busy}>
              {busy ? "Saving..." : "Add Skill"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

// ── CERTIFICATES SECTION ──────────────────────────────────────────────────────
// Reuses the SAME listCertificates/createCertificate/updateCertificateMetadata/
// deleteCertificate/fetchCertificateFile APIs as the Certificates page.

const EMPTY_CERT_FORM: CertificateInput = {
  title: "", type: "COURSE_COMPLETION", issuingOrganization: "",
  description: "", credentialId: "", verificationUrl: "", issuerName: "", organizationWebsite: "",
};

function ProfileCertificatesSection({ certificates, loading, onReload }: {
  certificates: StudentCertificate[]; loading: boolean; onReload: () => void;
}) {
  const pushToast = useAppStore((st) => st.pushToast);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editingId, setEditingId] = useState<string | null>(null);
  const [form, setFormState] = useState<CertificateInput>(EMPTY_CERT_FORM);
  const [file, setFile] = useState<File | null>(null);
  const [existingFileName, setExistingFileName] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [formError, setFormError] = useState<string | null>(null);
  const [viewingId, setViewingId] = useState<string | null>(null);
  const fileInputRef = useRef<HTMLInputElement>(null);

  const setFormField = (key: keyof CertificateInput, value: string) => setFormState((prev) => ({ ...prev, [key]: value }));
  const openAdd = () => { setEditingId(null); setFormState(EMPTY_CERT_FORM); setFile(null); setExistingFileName(null); setFormError(null); setDialogOpen(true); };
  const openEdit = (cert: StudentCertificate) => {
    setEditingId(cert.id);
    setFormState({
      title: cert.title, type: cert.type, issuingOrganization: cert.issuingOrganization,
      description: cert.description ?? "", credentialId: cert.credentialId ?? "",
      verificationUrl: cert.verificationUrl ?? "", issuerName: cert.issuerName ?? "", organizationWebsite: cert.organizationWebsite ?? ""
    });
    setFile(null); setExistingFileName(cert.fileName); setFormError(null); setDialogOpen(true);
  };
  const onPickFile = (e: React.ChangeEvent<HTMLInputElement>) => { setFile(e.target.files?.[0] ?? null); e.target.value = ""; };

  const submit = async () => {
    if (!form.title.trim() || !form.issuingOrganization.trim()) { setFormError("Title and issuing organization are required."); return; }
    if (editingId === null && !file) { setFormError("A certificate document (PDF, PNG or JPEG) is required."); return; }
    setSaving(true); setFormError(null);
    try {
      if (editingId === null) {
        await createCertificate(form, file as File);
        pushToast("Certificate added", `${form.title} was added to your profile.`);
      } else {
        await updateCertificateMetadata(editingId, form);
        if (file) await replaceCertificateFile(editingId, file);
        pushToast("Certificate updated", `${form.title} was updated.`);
      }
      setDialogOpen(false); onReload();
    } catch (err) { setFormError(err instanceof Error ? err.message : "Could not save the certificate."); }
    finally { setSaving(false); }
  };

  const remove = async (cert: StudentCertificate) => {
    try { await deleteCertificate(cert.id); pushToast("Certificate removed", `${cert.title} was deleted.`); onReload(); }
    catch (err) { pushToast("Delete failed", err instanceof Error ? err.message : "Could not delete."); }
  };

  const viewFile = async (cert: StudentCertificate) => {
    setViewingId(cert.id);
    try {
      const blob = await fetchCertificateFile(cert.id);
      const url = URL.createObjectURL(blob);
      window.open(url, "_blank", "noopener");
      setTimeout(() => URL.revokeObjectURL(url), 60_000);
    } catch (err) { pushToast("Could not open document", err instanceof Error ? err.message : "The file could not be loaded."); }
    finally { setViewingId(null); }
  };

  return (
    <>
      <DepthCard className="mt-5 p-5">
        <SectionHeader icon={Award} title="Certificates"
          subtitle={certificates.length > 0 ? `${certificates.length} certificate${certificates.length !== 1 ? "s" : ""}` : "Showcase your credentials"}
          action={
            <div className="flex items-center gap-2">
              <Button variant="glow" size="sm" onClick={openAdd} id="add-certificate-btn"><Plus className="h-3.5 w-3.5" /> Add</Button>
              <Button asChild variant="outline" size="sm" id="manage-certificates-btn"><Link href="/student/certificates">Manage <ArrowUpRight className="h-3.5 w-3.5" /></Link></Button>
            </div>
          } />
        {loading ? (
          <div className="flex items-center gap-2 py-6 text-sm text-muted-foreground"><Loader2 className="h-4 w-4 animate-spin" /> Loading certificates...</div>
        ) : certificates.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border/60 py-8 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-violet/10 text-violet-bright"><Award className="h-6 w-6" /></div>
            <p className="text-sm font-medium">No certificates yet</p>
            <p className="text-xs text-muted-foreground">Add certificates to strengthen your profile</p>
            <Button variant="outline" size="sm" onClick={openAdd}><Plus className="h-3.5 w-3.5" /> Add Certificate</Button>
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {certificates.map((cert) => (
              <div key={cert.id} className="group relative rounded-xl border border-border/60 bg-card/30 p-4 transition-colors hover:border-violet/30">
                <div className="flex items-start gap-3">
                  <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-violet-bright"><Award className="h-4 w-4" /></div>
                  <div className="min-w-0 flex-1">
                    <p className="truncate text-sm font-semibold">{cert.title}</p>
                    <p className="flex items-center gap-1 truncate text-xs text-muted-foreground"><Building2 className="h-3 w-3 shrink-0" />{cert.issuingOrganization}</p>
                  </div>
                  <Badge variant="muted" className="shrink-0 text-[10px]">{certificateTypeLabel(cert.type)}</Badge>
                </div>
                {cert.credentialId && <p className="mt-2 truncate text-xs text-muted-foreground"><span className="text-foreground/70">ID:</span>{cert.credentialId}</p>}
                <div className="mt-3 flex flex-wrap items-center gap-1.5 border-t border-border/40 pt-3">
                  {certificateHasFile(cert) && (
                    <Button variant="outline" size="sm" className="h-7 text-xs" onClick={() => viewFile(cert)} disabled={viewingId === cert.id} id={`view-cert-${cert.id}`}>
                      {viewingId === cert.id ? <Loader2 className="h-3 w-3 animate-spin" /> : <Eye className="h-3 w-3" />}View
                    </Button>
                  )}
                  {cert.verificationUrl && (<Button asChild variant="ghost" size="sm" className="h-7 text-xs"><a href={cert.verificationUrl} target="_blank" rel="noopener noreferrer">Verify <ExternalLink className="h-3 w-3" /></a></Button>)}
                  <div className="ml-auto flex items-center gap-1">
                    <Button variant="ghost" size="sm" className="h-7 px-2 opacity-0 transition-opacity group-hover:opacity-100" onClick={() => openEdit(cert)} aria-label={`Edit ${cert.title}`}><Pencil className="h-3 w-3" /></Button>
                    <Button variant="ghost" size="sm" className="h-7 px-2 text-muted-foreground opacity-0 transition-opacity hover:text-risk group-hover:opacity-100" onClick={() => remove(cert)} aria-label={`Delete ${cert.title}`}><Trash2 className="h-3 w-3" /></Button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}
      </DepthCard>
      <Dialog open={dialogOpen} onOpenChange={(open) => !saving && setDialogOpen(open)}>
        <DialogContent className="max-h-[85vh] overflow-y-auto sm:max-w-lg">
          <DialogHeader>
            <DialogTitle>{editingId === null ? "Add Certificate" : "Edit Certificate"}</DialogTitle>
            <DialogDescription>Required: title, type, issuing organization and a document file.</DialogDescription>
          </DialogHeader>
          <div className="space-y-3">
            <CertFormField label="Certificate title" required><Input value={form.title} onChange={(e) => setFormField("title", e.target.value)} placeholder="e.g. AWS Certified Cloud Practitioner" /></CertFormField>
            <CertFormField label="Certificate type" required>
              <Select value={form.type} onValueChange={(v) => setFormField("type", v as CertificateType)}>
                <SelectTrigger><SelectValue /></SelectTrigger>
                <SelectContent>{CERTIFICATE_TYPES.map((t) => <SelectItem key={t.value} value={t.value}>{t.label}</SelectItem>)}</SelectContent>
              </Select>
            </CertFormField>
            <CertFormField label="Issuing organization" required><Input value={form.issuingOrganization} onChange={(e) => setFormField("issuingOrganization", e.target.value)} placeholder="e.g. Amazon Web Services" /></CertFormField>
            <CertFormField label="Description">
              <textarea value={form.description} onChange={(e) => setFormField("description", e.target.value)} rows={3} placeholder="Optional — what this certificate covers."
                className="flex w-full rounded-lg border border-input bg-background px-3 py-2 text-sm ring-offset-background placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2" />
            </CertFormField>
            <div className="grid gap-3 sm:grid-cols-2">
              <CertFormField label="Credential ID"><Input value={form.credentialId} onChange={(e) => setFormField("credentialId", e.target.value)} placeholder="Optional" /></CertFormField>
              <CertFormField label="Verification URL"><Input value={form.verificationUrl} onChange={(e) => setFormField("verificationUrl", e.target.value)} placeholder="https://..." /></CertFormField>
              <CertFormField label="Issuer name"><Input value={form.issuerName} onChange={(e) => setFormField("issuerName", e.target.value)} placeholder="Optional" /></CertFormField>
              <CertFormField label="Organization website"><Input value={form.organizationWebsite} onChange={(e) => setFormField("organizationWebsite", e.target.value)} placeholder="https://..." /></CertFormField>
            </div>
            <CertFormField label={editingId === null ? "Certificate document" : "Replace document (optional)"} required={editingId === null}>
              <div className="flex flex-wrap items-center gap-2">
                <input ref={fileInputRef} type="file" accept=".pdf,.png,.jpg,.jpeg,application/pdf,image/png,image/jpeg" className="hidden" onChange={onPickFile} />
                <Button type="button" variant="outline" size="sm" onClick={() => fileInputRef.current?.click()}><Upload className="h-3.5 w-3.5" /> Choose file</Button>
                <span className="min-w-0 truncate text-xs text-muted-foreground">{file?.name ?? (existingFileName ? `Current: ${existingFileName}` : "No file selected")}</span>
              </div>
              <p className="mt-1 text-[11px] text-muted-foreground">PDF, PNG or JPEG - up to 10 MB.</p>
            </CertFormField>
            {formError && <p className="text-xs text-risk" role="alert">{formError}</p>}
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setDialogOpen(false)} disabled={saving}>Cancel</Button>
            <Button variant="glow" onClick={submit} disabled={saving}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : <BadgeCheck className="h-4 w-4" />}
              {editingId === null ? "Add Certificate" : "Save Changes"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

// ── WORK EXPERIENCE SECTION ───────────────────────────────────────────────────

function WorkExperienceSection({ student: s, onEdit }: { student: Student; onEdit: () => void }) {
  return (
    <DepthCard className="mt-5 p-5">
      <SectionHeader icon={Building2} title="Work Experience"
        subtitle={s.experience.length > 0 ? `${s.experience.length} position${s.experience.length !== 1 ? "s" : ""}` : "Internships & work history"}
        action={<Button variant="outline" size="sm" onClick={onEdit} id="add-experience-btn"><Plus className="h-3.5 w-3.5" />{s.experience.length === 0 ? "Add Experience" : "Edit"}</Button>} />
      {s.experience.length === 0 ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border/60 py-8 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-violet/10 text-violet-bright"><Briefcase className="h-6 w-6" /></div>
          <p className="text-sm font-medium">No experience added yet</p>
          <p className="text-xs text-muted-foreground">Add internships or work experience to round out your profile</p>
          <Button variant="outline" size="sm" onClick={onEdit}><Plus className="h-3.5 w-3.5" /> Add Experience</Button>
        </div>
      ) : (
        <div className="space-y-3">
          {s.experience.map((e, i) => (
            <div key={`${e.company}-${i}`} className="flex gap-4 rounded-xl border border-border/60 bg-card/30 p-4">
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-violet-bright"><Building2 className="h-5 w-5" /></div>
              <div className="min-w-0 flex-1">
                <p className="font-semibold">{e.role || "—"}</p>
                <p className="text-sm text-muted-foreground">{e.company}{e.duration ? ` · ${e.duration}` : ""}</p>
                {e.summary && <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">{e.summary}</p>}
              </div>
            </div>
          ))}
        </div>
      )}
    </DepthCard>
  );
}

// ── PROJECTS SECTION ──────────────────────────────────────────────────────────
// Reuses the SAME updatePrimaryStudent API as the Projects page.

/** Free-text link -> absolute URL; empty stays undefined (field is optional). */
function normalizeProjectUrl(raw: string): string | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
}

function ProjectsSection({ student: s, onReload }: { student: Student; onReload: () => void }) {
  const pushToast = useAppStore((st) => st.pushToast);
  const [projects, setProjects] = useState<StudentProject[]>(s.projects ?? []);
  const [addOpen, setAddOpen] = useState(false);
  const [name, setName] = useState("");
  const [stack, setStack] = useState("");
  const [githubUrl, setGithubUrl] = useState("");
  const [liveUrl, setLiveUrl] = useState("");
  const [summary, setSummary] = useState("");
  const [saving, setSaving] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [projError, setProjError] = useState<string | null>(null);

  useMemo(() => setProjects(s.projects ?? []), [s.projects]);

  const persist = async (next: StudentProject[], message: string) => {
    setSaving(true); setProjError(null);
    try {
      const saved = await updatePrimaryStudent({ projects: next });
      setProjects(saved.projects ?? next);
      pushToast("Projects saved", message); onReload();
    } catch (err) { setProjError(err instanceof Error ? err.message : "Could not save your projects."); }
    finally { setSaving(false); }
  };

  const clearForm = () => {
    setName(""); setStack(""); setGithubUrl(""); setLiveUrl(""); setSummary(""); setProjError(null);
  };

  const openAdd = () => {
    setEditingIndex(null);
    clearForm();
    setAddOpen(true);
  };

  const openEdit = (index: number) => {
    const p = projects[index];
    setEditingIndex(index);
    setName(p.name);
    setStack(p.stack.join(", "));
    setGithubUrl(p.githubUrl ?? "");
    setLiveUrl(p.liveUrl ?? "");
    setSummary(p.summary ?? "");
    setProjError(null);
    setAddOpen(true);
  };

  const saveProject = async () => {
    const trimmed = name.trim();
    if (!trimmed) { setProjError("Enter a project name."); return; }
    const project: StudentProject = {
      name: trimmed,
      stack: stack.split(",").map((t) => t.trim()).filter(Boolean),
      summary: summary.trim(),
      githubUrl: normalizeProjectUrl(githubUrl),
      liveUrl: normalizeProjectUrl(liveUrl),
    };
    if (editingIndex !== null) {
      const index = editingIndex;
      await persist(projects.map((p, i) => (i === index ? project : p)), `"${projects[index].name}" updated.`);
    } else {
      await persist([...projects, project], `"${trimmed}" added.`);
    }
    setEditingIndex(null);
    clearForm(); setAddOpen(false);
  };

  const removeProject = async (index: number) => {
    const removed = projects[index];
    if (editingIndex === index) setEditingIndex(null);
    await persist(projects.filter((_, i) => i !== index), `"${removed.name}" removed.`);
  };

  return (
    <>
      <DepthCard className="mt-5 p-5">
        <SectionHeader icon={FolderGit2} title="Projects Portfolio"
          subtitle={projects.length > 0 ? `${projects.length} project${projects.length !== 1 ? "s" : ""}` : "Portfolio & Engineering Work"}
          action={
            <div className="flex items-center gap-2">
              <Button variant="glow" size="sm" onClick={openAdd} id="add-project-btn"><Plus className="h-3.5 w-3.5" /> Add Project</Button>
              <Button asChild variant="outline" size="sm" id="manage-projects-btn"><Link href="/student/projects">Manage <ArrowUpRight className="h-3.5 w-3.5" /></Link></Button>
            </div>
          } />
        {projects.length === 0 ? (
          <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border/60 py-8 text-center">
            <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-violet/10 text-violet-bright"><FolderGit2 className="h-6 w-6" /></div>
            <p className="text-sm font-medium">No projects yet</p>
            <p className="text-xs text-muted-foreground">Add projects to show recruiters what you have built</p>
            <Button variant="outline" size="sm" onClick={openAdd}><Plus className="h-3.5 w-3.5" /> Add First Project</Button>
          </div>
        ) : (
          <div className="grid gap-3 sm:grid-cols-2">
            {projects.map((p, i) => (
              <div key={`${p.name}-${i}`} className="group relative rounded-xl border border-border/60 bg-card/30 p-4 transition-colors hover:border-violet/30">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0">
                    <p className="font-semibold">{p.name}</p>
                    {p.summary && <p className="mt-1 line-clamp-2 text-xs leading-relaxed text-muted-foreground">{p.summary}</p>}
                  </div>
                  <div className="flex shrink-0 items-center gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                    <Button size="sm" variant="ghost" className="h-7 px-2 hover:text-violet-bright"
                      onClick={() => openEdit(i)} disabled={saving} aria-label={`Edit ${p.name}`}><Pencil className="h-3.5 w-3.5" /></Button>
                    <Button size="sm" variant="ghost" className="h-7 px-2 hover:text-risk"
                      onClick={() => removeProject(i)} disabled={saving} aria-label={`Remove ${p.name}`}><Trash2 className="h-3.5 w-3.5" /></Button>
                  </div>
                </div>
                {p.stack.length > 0 && (
                  <div className="mt-3 flex flex-wrap gap-1.5">
                    {p.stack.map((t) => (<Badge key={t} variant="muted" className="gap-1 text-[10px]"><span className="h-1 w-1 rounded-full bg-violet-bright" />{t}</Badge>))}
                  </div>
                )}
                {(p.githubUrl || p.liveUrl) && (
                  <div className="mt-2.5 flex flex-wrap gap-3">
                    {p.githubUrl && (
                      <a href={p.githubUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-[11px] font-medium text-violet-bright hover:underline">
                        <GitHubIcon className="h-3 w-3" /> GitHub
                      </a>
                    )}
                    {p.liveUrl && (
                      <a href={p.liveUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-[11px] font-medium text-violet-bright hover:underline">
                        <ExternalLink className="h-3 w-3" /> Live Demo
                      </a>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
        {projError && <p className="mt-3 text-xs text-risk" role="alert">{projError}</p>}
      </DepthCard>
      <Dialog open={addOpen} onOpenChange={(open) => { if (!saving) { setAddOpen(open); if (!open) setEditingIndex(null); } }}>
        <DialogContent className="sm:max-w-lg">
          <DialogHeader><DialogTitle>{editingIndex !== null ? "Edit Project" : "Add Project"}</DialogTitle><DialogDescription>{editingIndex !== null ? "Update the project on your placement profile." : "Add a project to your placement profile."}</DialogDescription></DialogHeader>
          <div className="space-y-3">
            <CertFormField label="Project name" required><Input value={name} placeholder="e.g. Campus Placement Portal" onChange={(e) => setName(e.target.value)} /></CertFormField>
            <CertFormField label="Tech stack (comma-separated)"><Input value={stack} placeholder="e.g. React, Spring Boot, PostgreSQL" onChange={(e) => setStack(e.target.value)} /></CertFormField>
            <CertFormField label="GitHub link"><Input value={githubUrl} placeholder="e.g. https://github.com/you/repo" onChange={(e) => setGithubUrl(e.target.value)} /></CertFormField>
            <CertFormField label="Live link"><Input value={liveUrl} placeholder="e.g. https://your-demo.app" onChange={(e) => setLiveUrl(e.target.value)} /></CertFormField>
            <CertFormField label="Description"><Input value={summary} placeholder="One line on what it does and your role" onChange={(e) => setSummary(e.target.value)} /></CertFormField>
            {projError && <p className="text-xs text-risk" role="alert">{projError}</p>}
          </div>
          <DialogFooter>
            <Button variant="ghost" onClick={() => setAddOpen(false)} disabled={saving}>Cancel</Button>
            <Button variant="glow" onClick={saveProject} disabled={saving}>
              {saving ? <Loader2 className="h-4 w-4 animate-spin" /> : editingIndex !== null ? <Check className="h-4 w-4" /> : <Plus className="h-4 w-4" />}{saving ? "Saving..." : editingIndex !== null ? "Save Changes" : "Add Project"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </>
  );
}

// ── CAREER INTENT SECTION ─────────────────────────────────────────────────────

function CareerIntentSection({ student: s, onEdit }: { student: Student; onEdit: () => void }) {
  const hasIntent = Boolean(s.targetRole || s.preferredRoles.length || s.preferredDomains.length || s.preferredLocations.length);
  return (
    <DepthCard className="mt-5 p-5">
      <SectionHeader icon={Target} title="Career Intent & Preferences" subtitle="Role, Industry & Location Targeting"
        action={<Button variant="outline" size="sm" onClick={onEdit} id="edit-career-intent-btn"><Pencil className="h-3.5 w-3.5" /> Edit</Button>} />
      {!hasIntent ? (
        <div className="flex flex-col items-center gap-3 rounded-xl border border-dashed border-border/60 py-8 text-center">
          <div className="flex h-12 w-12 items-center justify-center rounded-xl bg-violet/10 text-violet-bright"><Target className="h-6 w-6" /></div>
          <p className="text-sm font-medium">Career intent not set</p>
          <p className="text-xs text-muted-foreground">Set your target role to drive skill-gap analysis and matching</p>
          <Button variant="outline" size="sm" onClick={onEdit}><Plus className="h-3.5 w-3.5" /> Set Career Intent</Button>
        </div>
      ) : (
        <div className="space-y-4">
          {s.targetRole && (
            <div className="flex items-center justify-between gap-3 rounded-xl border border-violet/30 bg-violet/5 p-4">
              <div className="min-w-0">
                <p className="text-[10px] font-semibold uppercase tracking-widest text-violet-bright">Target Primary Role</p>
                <p className="mt-0.5 truncate text-lg font-bold">{s.targetRole}</p>
              </div>
              <Badge variant="default" className="shrink-0">Primary Match</Badge>
            </div>
          )}
          {s.preferredRoles.length > 0 && (
            <div>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Preferred Roles</p>
              <div className="flex flex-wrap gap-1.5">{s.preferredRoles.map((r) => <Badge key={r} variant="outline">{r}</Badge>)}</div>
            </div>
          )}
          {s.preferredDomains.length > 0 && (
            <div>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Preferred Domains</p>
              <div className="flex flex-wrap gap-1.5">{s.preferredDomains.map((r) => <Badge key={r} variant="outline">{r}</Badge>)}</div>
            </div>
          )}
          {s.preferredLocations.length > 0 && (
            <div>
              <p className="mb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Preferred Locations</p>
              <div className="flex flex-wrap gap-1.5">
                {s.preferredLocations.map((r) => (<Badge key={r} variant="muted" className="gap-1.5"><MapPin className="h-2.5 w-2.5" />{r}</Badge>))}
              </div>
            </div>
          )}
        </div>
      )}
    </DepthCard>
  );
}

// ── PROFILE INSIGHTS ──────────────────────────────────────────────────────────

function ProfileInsightsSection({ student: s, skills, eligibleCount, certificateCount }: {
  student: Student; skills: StudentSkill[]; eligibleCount: number; certificateCount: number;
}) {
  const completeness = profileCompleteness(s, skills.length, certificateCount);
  const topSkills = [...skills].sort((a, b) => b.proficiency - a.proficiency).slice(0, 3);
  return (
    <DepthCard className="mt-5 overflow-hidden p-0">
      <div className="h-px w-full bg-gradient-to-r from-transparent via-violet-bright/30 to-transparent" />
      <div className="p-5">
        <SectionHeader icon={Star} title="Profile Insights" subtitle="A snapshot of your placement readiness" />
        <div className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <StatPill label="Profile" value={`${completeness.score}%`} tone={completeness.score >= 80 ? "success" : completeness.score >= 50 ? "warning" : "risk"} />
          <StatPill label="Skills" value={skills.length} tone={skills.length >= 5 ? "success" : skills.length >= 2 ? "warning" : "risk"} />
          <StatPill label="Projects" value={s.projects.length} tone={s.projects.length >= 2 ? "success" : s.projects.length >= 1 ? "warning" : "risk"} />
          <StatPill label="Readiness" value={`${s.readiness}/100`} tone={s.readiness >= 70 ? "success" : s.readiness >= 50 ? "warning" : "risk"} />
        </div>
        {topSkills.length > 0 && (
          <div className="mt-5">
            <p className="mb-3 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">Top Skills</p>
            <div className="space-y-2">
              {topSkills.map((sk) => {
                const b = proficiencyBand(sk.proficiency);
                return (
                  <div key={sk.id} className="flex items-center gap-3">
                    <span className="w-32 truncate text-xs font-medium">{sk.skill}</span>
                    <div className="h-1.5 flex-1 overflow-hidden rounded-full bg-muted"><div className={`h-full rounded-full ${b.bar}`} style={{ width: `${sk.proficiency}%` }} /></div>
                    <span className={`text-xs font-semibold tabular-nums ${b.text}`}>{sk.proficiency}%</span>
                  </div>
                );
              })}
            </div>
          </div>
        )}
        <div className="mt-5 grid grid-cols-2 gap-3 border-t border-border/40 pt-5 sm:grid-cols-3">
          <div className="text-center">
            <p className="text-2xl font-bold tabular-nums text-violet-bright">{eligibleCount}</p>
            <p className="mt-0.5 text-[10px] text-muted-foreground">Eligible Drives</p>
          </div>
          <Link
            href="/student/resume"
            className="group block text-center rounded-lg p-1 -m-1 transition-colors hover:bg-muted/40"
            title="Click to view Resume Intelligence & ATS Score"
          >
            <p className="text-2xl font-bold tabular-nums group-hover:text-violet-bright transition-colors">
              {s.resumeScore}<span className="text-sm font-normal text-muted-foreground">/100</span>
            </p>
            <p className="mt-0.5 text-[10px] text-muted-foreground group-hover:text-violet-bright transition-colors">
              Resume Score {s.resumeScore === 0 ? "• Evaluate →" : ""}
            </p>
          </Link>
          <div className="col-span-2 text-center sm:col-span-1">
            <p className={`text-2xl font-bold tabular-nums ${s.placementProbability >= 70 ? "text-success" : s.placementProbability >= 50 ? "text-warning" : "text-risk"}`}>{s.placementProbability}%</p>
            <p className="mt-0.5 text-[10px] text-muted-foreground">Placement Probability</p>
          </div>
        </div>
      </div>
    </DepthCard>
  );
}