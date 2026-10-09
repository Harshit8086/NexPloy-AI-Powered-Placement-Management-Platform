"use client";

import { useMemo, useState } from "react";
import Link from "next/link";
import {
  Plus,
  Trash2,
  Check,
  Search,
  ArrowUpDown,
  ArrowLeft,
  Sparkles,
  X,
  Target,
  ShieldCheck,
  ShieldQuestion,
  Link2,
} from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PageHeader } from "@/components/layout/PageHeader";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import {
  listSkills,
  upsertSkill,
  deleteSkill,
  attachSkillEvidence,
  adjustSkillLevel,
  type StudentSkill,
} from "@/lib/api/students";
import { SkillVivaDialog } from "@/components/skills/SkillVivaDialog";
import { proficiencyBand } from "@/lib/skill-band";

/** Small badge describing a skill's verification level. */
function verificationBadge(status: string): { label: string; cls: string; Icon: typeof ShieldCheck } {
  switch (status) {
    case "VERIFIED":
      return { label: "Verified", cls: "bg-success/15 text-success", Icon: ShieldCheck };
    case "EVIDENCED":
      return { label: "Evidenced", cls: "bg-cyan-400/15 text-cyan-400", Icon: Link2 };
    default:
      return { label: "Self-declared", cls: "bg-muted text-muted-foreground", Icon: ShieldQuestion };
  }
}

function clampLevel(raw: string): number | null {
  const t = raw.trim();
  if (t === "") return null;
  const n = Number(t);
  if (!Number.isFinite(n)) return null;
  return Math.max(0, Math.min(100, Math.round(n)));
}

type SortMode = "level" | "name";

const QUICK_SUGGESTIONS = [
  "React",
  "Node.js",
  "TypeScript",
  "Python",
  "SQL",
  "System Design",
  "Docker",
  "Tailwind CSS",
  "AWS",
  "Next.js",
];

export default function StudentSkillsPage() {
  const [version, setVersion] = useState(0);
  const { data: skills, loading } = useApiResource<StudentSkill[]>(
    () => listSkills(),
    [version],
    [],
  );
  const pushToast = useAppStore((s) => s.pushToast);

  // Add Skill Modal State
  const [dialogOpen, setDialogOpen] = useState(false);
  const [newSkill, setNewSkill] = useState("");
  const [newLevel, setNewLevel] = useState("65");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Per-card proficiency drafts, keyed by skill id
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  // View controls: search filter + sort toggle
  const [query, setQuery] = useState("");
  const [sort, setSort] = useState<SortMode>("level");

  // Skill verification: AI viva dialog + evidence (proof link) dialog.
  const [vivaSkill, setVivaSkill] = useState<StudentSkill | null>(null);
  const [evidenceSkill, setEvidenceSkill] = useState<StudentSkill | null>(null);
  const [evidenceUrl, setEvidenceUrl] = useState("");
  const [evidenceNote, setEvidenceNote] = useState("");

  const refetch = () => setVersion((v) => v + 1);

  const openEvidence = (s: StudentSkill) => {
    setEvidenceSkill(s);
    setEvidenceUrl(s.evidenceUrl ?? "");
    setEvidenceNote(s.evidenceNote ?? "");
    setError(null);
  };

  const handleSaveEvidence = async () => {
    if (!evidenceSkill) return;
    const url = evidenceUrl.trim();
    if (!/^https:\/\//i.test(url)) {
      setError("Proof link must start with https://");
      return;
    }
    setBusy(true);
    try {
      await attachSkillEvidence(evidenceSkill.id, url, evidenceNote.trim() || undefined);
      pushToast("Proof added", `${evidenceSkill.skill} marked as evidenced.`);
      setEvidenceSkill(null);
      refetch();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the proof link.");
    } finally {
      setBusy(false);
    }
  };

  // Statistics calculation for compact insight pills
  const stats = useMemo(() => {
    let expert = 0;
    let advanced = 0;
    let developing = 0;
    for (const s of skills) {
      if (s.proficiency >= 75) expert++;
      else if (s.proficiency >= 60) advanced++;
      else developing++;
    }
    return { expert, advanced, developing, total: skills.length };
  }, [skills]);

  const visible = useMemo(() => {
    const q = query.trim().toLowerCase();
    const filtered = q ? skills.filter((s) => s.skill.toLowerCase().includes(q)) : skills;
    return [...filtered].sort((a, b) =>
      sort === "level" ? b.proficiency - a.proficiency : a.skill.localeCompare(b.skill),
    );
  }, [skills, query, sort]);

  const handleAdd = async () => {
    const skill = newSkill.trim();
    const level = clampLevel(newLevel);
    if (!skill) {
      setError("Please enter a skill name.");
      return;
    }
    if (level === null) {
      setError("Proficiency must be a number between 0 and 100.");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await upsertSkill({ skill, proficiency: level });
      setNewSkill("");
      setNewLevel("65");
      pushToast("Skill added", `${skill} set to ${level}%.`);
      setDialogOpen(false);
      refetch();
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save the skill. Please try again.");
    } finally {
      setBusy(false);
    }
  };

  const handleUpdateLevel = async (s: StudentSkill) => {
    const level = clampLevel(drafts[s.id] ?? String(s.proficiency));
    if (level === null) {
      setError("Proficiency must be a number between 0 and 100.");
      return;
    }
    setBusy(true);
    try {
      // Verified skills keep their badge: small corrections go through the level-nudge endpoint
      // (the server refuses jumps larger than ±15 without a re-viva); self-declared/evidenced
      // levels are self-reported anyway and upsert freely.
      if (s.verificationStatus === "VERIFIED") {
        await adjustSkillLevel(s.id, level);
      } else {
        await upsertSkill({ skill: s.skill, proficiency: level, source: s.source });
      }
      setDrafts((d) => {
        const next = { ...d };
        delete next[s.id];
        return next;
      });
      pushToast("Skill updated", `${s.skill} set to ${level}%.`);
      refetch();
    } catch (err) {
      pushToast("Update failed", err instanceof Error ? err.message : "Could not update skill.");
    } finally {
      setBusy(false);
    }
  };

  const handleDelete = async (s: StudentSkill) => {
    setBusy(true);
    try {
      await deleteSkill(s.id);
      pushToast("Skill removed", `${s.skill} was removed from your profile.`);
      refetch();
    } catch (err) {
      pushToast("Remove failed", err instanceof Error ? err.message : "Could not remove skill.");
    } finally {
      setBusy(false);
    }
  };

  const modalPreviewBand = proficiencyBand(clampLevel(newLevel) ?? 60);

  return (
    <div className="mx-auto max-w-5xl space-y-5 pb-12">
      {/* Page Header */}
      <PageHeader
        eyebrow="Intelligence"
        title="My Skills"
        subtitle="Manage your skill inventory and proficiency levels for placement matching and ATS scoring."
        actions={
          <div className="flex items-center gap-2">
            <Button asChild variant="outline" size="sm">
              <Link href="/student/profile">
                <ArrowLeft className="h-4 w-4 mr-1.5" /> Back to Profile
              </Link>
            </Button>
            <Button
              variant="glow"
              size="sm"
              onClick={() => {
                setError(null);
                setDialogOpen(true);
              }}
            >
              <Plus className="h-4 w-4 mr-1.5" /> Add Skill
            </Button>
          </div>
        }
      />

      {/* Info & Sync Banner */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-lg border border-violet/20 bg-violet/5 px-4 py-2.5 text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 shrink-0 text-violet-bright" />
          <span>
            Skills recorded here power your <strong>Placement Profile</strong>, ATS resume parsing, and <strong>Skill Gap Analysis</strong>.
          </span>
        </div>
        <Button asChild variant="ghost" size="sm" className="h-auto p-0 text-xs text-violet-bright hover:text-white shrink-0">
          <Link href="/student/skill-gap">Skill Gap Analysis →</Link>
        </Button>
      </div>

      {/* Compact Controls & Stats Bar */}
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-xl border border-border/80 bg-card/60 px-4 py-3 backdrop-blur-sm">
        {/* Left: Quick Counts */}
        <div className="flex flex-wrap items-center gap-2 text-xs">
          <Badge variant="muted" className="tabular-nums font-medium">
            {stats.total} total skills
          </Badge>
          <div className="hidden sm:flex items-center gap-2 text-muted-foreground">
            <span className="inline-flex items-center gap-1 text-success">
              <span className="h-1.5 w-1.5 rounded-full bg-success" />
              {stats.expert} Expert
            </span>
            <span className="text-border">•</span>
            <span className="inline-flex items-center gap-1 text-cyan-400">
              <span className="h-1.5 w-1.5 rounded-full bg-cyan-400" />
              {stats.advanced} Advanced
            </span>
            <span className="text-border">•</span>
            <span className="inline-flex items-center gap-1 text-warning">
              <span className="h-1.5 w-1.5 rounded-full bg-warning" />
              {stats.developing} Developing
            </span>
          </div>
        </div>

        {/* Right: Search & Sort Controls */}
        <div className="flex items-center gap-2">
          <div className="relative">
            <Search className="pointer-events-none absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
            <Input
              value={query}
              placeholder="Search skills…"
              onChange={(e) => setQuery(e.target.value)}
              className="h-8.5 w-36 sm:w-48 pl-8 pr-7 text-xs"
              aria-label="Filter skills by name"
            />
            {query && (
              <button
                type="button"
                onClick={() => setQuery("")}
                className="absolute right-2 top-1/2 -translate-y-1/2 text-muted-foreground hover:text-foreground"
              >
                <X className="h-3 w-3" />
              </button>
            )}
          </div>
          <Button
            variant="outline"
            size="sm"
            className="h-8.5 text-xs px-2.5"
            onClick={() => setSort((m) => (m === "level" ? "name" : "level"))}
            title={sort === "level" ? "Sorted by proficiency — click for A–Z" : "Sorted A–Z — click for proficiency"}
          >
            <ArrowUpDown className="h-3 w-3 mr-1" />
            {sort === "level" ? "Level" : "A–Z"}
          </Button>
        </div>
      </div>

      {/* Design 1: 3-Column Compact Grid */}
      {loading ? (
        <div className="rounded-xl border border-border/70 p-12 text-center">
          <Sparkles className="mx-auto h-7 w-7 text-violet-bright animate-pulse mb-3" />
          <p className="text-sm font-medium text-foreground">Loading your skills…</p>
          <p className="text-xs text-muted-foreground mt-1">Retrieving proficiency records and assessment bands.</p>
        </div>
      ) : skills.length === 0 ? (
        <div className="rounded-xl border border-dashed border-border/80 p-12 text-center">
          <Target className="mx-auto h-8 w-8 text-muted-foreground/60 mb-3" />
          <p className="text-sm font-medium text-foreground">No skills recorded yet</p>
          <p className="text-xs text-muted-foreground mt-1 max-w-sm mx-auto">
            Add your key programming languages, tools, and frameworks to boost your placement readiness score.
          </p>
          <Button
            variant="glow"
            size="sm"
            className="mt-4"
            onClick={() => {
              setError(null);
              setDialogOpen(true);
            }}
          >
            <Plus className="h-4 w-4 mr-1.5" /> Add Your First Skill
          </Button>
        </div>
      ) : visible.length === 0 ? (
        <div className="rounded-xl border border-border/70 p-8 text-center">
          <p className="text-xs text-muted-foreground">
            No skills match <span className="font-semibold text-foreground">“{query}”</span>.
          </p>
          <Button variant="ghost" size="sm" onClick={() => setQuery("")} className="mt-2 text-xs text-violet-bright">
            Clear search filter
          </Button>
        </div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
          {visible.map((s) => {
            const draft = drafts[s.id];
            const changed = draft !== undefined && clampLevel(draft) !== s.proficiency;
            const b = proficiencyBand(s.proficiency);
            const vb = verificationBadge(s.verificationStatus);

            return (
              <div
                key={s.id}
                className="group relative rounded-xl border border-border/80 bg-card/70 p-3.5 shadow-sm transition-[border-color,box-shadow] duration-200 hover:border-violet/40 hover:shadow-glow flex flex-col justify-between"
              >
                {/* Card Top: Skill Name & Band Badge */}
                <div>
                  <div className="flex items-start justify-between gap-2 mb-2">
                    <span className="font-semibold text-sm text-foreground truncate" title={s.skill}>
                      {s.skill}
                    </span>
                    <span
                      className={`inline-flex shrink-0 items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${b.chip} ${b.text}`}
                    >
                      <b.Icon className="h-2.5 w-2.5" /> {b.label}
                    </span>
                  </div>

                  {/* Progress Bar & Percentage */}
                  <div className="space-y-1 mb-3">
                    <div className="flex items-center justify-between text-[11px]">
                      <span className="text-muted-foreground">Proficiency</span>
                      <span className={`font-mono font-semibold ${b.text}`}>{s.proficiency}%</span>
                    </div>
                    <div className="h-1.5 w-full overflow-hidden rounded-full bg-muted/60">
                      <div
                        className={`h-full rounded-full transition-all duration-300 ${b.bar}`}
                        style={{ width: `${s.proficiency}%` }}
                      />
                    </div>
                  </div>
                </div>

                {/* Verification badge + verify / proof actions */}
                <div className="mb-1 flex items-center justify-between gap-2">
                  <span
                    className={`inline-flex items-center gap-1 rounded-full px-2 py-0.5 text-[10px] font-medium ${vb.cls}`}
                    title={s.evidenceUrl ?? undefined}
                  >
                    <vb.Icon className="h-2.5 w-2.5" /> {vb.label}
                  </span>
                  <div className="flex items-center gap-2">
                    {s.verificationStatus !== "VERIFIED" && (
                      <button
                        type="button"
                        onClick={() => setVivaSkill(s)}
                        className="text-[10px] font-medium text-violet-bright hover:underline"
                      >
                        Verify
                      </button>
                    )}
                    <button
                      type="button"
                      onClick={() => openEvidence(s)}
                      className="text-[10px] text-muted-foreground hover:text-foreground hover:underline"
                    >
                      {s.evidenceUrl ? "Edit proof" : "Add proof"}
                    </button>
                  </div>
                </div>

                {/* Card Bottom: Inline Quick Adjustment & Delete */}
                <div className="flex items-center justify-between gap-2 pt-2 border-t border-border/40">
                  <div className="flex items-center gap-1.5">
                    <span className="text-[11px] text-muted-foreground">Level:</span>
                    <Input
                      type="number"
                      min={0}
                      max={100}
                      aria-label={`${s.skill} proficiency`}
                      value={draft ?? String(s.proficiency)}
                      onChange={(e) => setDrafts((d) => ({ ...d, [s.id]: e.target.value }))}
                      className="h-6.5 w-14 rounded px-1.5 text-center text-xs font-mono tabular-nums bg-elevated/80 border-border/70"
                    />
                    <span className="text-[11px] text-muted-foreground">%</span>
                    {changed && (
                      <Button
                        size="sm"
                        variant="glow"
                        onClick={() => handleUpdateLevel(s)}
                        disabled={busy}
                        className="h-6.5 px-2 text-xs"
                        aria-label={`Save ${s.skill}`}
                        title="Save changed proficiency"
                      >
                        <Check className="h-3 w-3" />
                      </Button>
                    )}
                  </div>

                  <Button
                    size="sm"
                    variant="ghost"
                    onClick={() => handleDelete(s)}
                    disabled={busy}
                    className="h-6.5 w-6.5 p-0 text-muted-foreground/60 hover:text-risk hover:bg-risk/10 transition-colors"
                    aria-label={`Remove ${s.skill}`}
                    title="Remove skill"
                  >
                    <Trash2 className="h-3.5 w-3.5" />
                  </Button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* Add Skill Dialog Modal */}
      <Dialog open={dialogOpen} onOpenChange={setDialogOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Plus className="h-4 w-4 text-violet-bright" />
              Add Skill to Profile
            </DialogTitle>
            <DialogDescription className="text-xs">
              Record a technical or soft skill with your estimated proficiency. This synchronizes with your Placement Profile.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            {/* Skill Name Field */}
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">Skill Name</Label>
              <Input
                value={newSkill}
                placeholder="e.g. Next.js, Kubernetes, System Design"
                onChange={(e) => setNewSkill(e.target.value)}
                onKeyDown={(e) => {
                  if (e.key === "Enter") handleAdd();
                }}
                className="text-sm"
              />
            </div>

            {/* Quick Suggestions */}
            <div>
              <span className="text-[11px] text-muted-foreground block mb-1.5">Quick picks:</span>
              <div className="flex flex-wrap gap-1.5">
                {QUICK_SUGGESTIONS.map((tag) => (
                  <button
                    key={tag}
                    type="button"
                    onClick={() => setNewSkill(tag)}
                    className="text-[11px] px-2 py-0.5 rounded-full border border-border/80 bg-muted/40 hover:bg-violet/10 hover:border-violet/40 text-muted-foreground hover:text-violet-bright transition-colors"
                  >
                    + {tag}
                  </button>
                ))}
              </div>
            </div>

            {/* Proficiency Slider & Numeric Input */}
            <div className="space-y-2 pt-2 border-t border-border/40">
              <div className="flex items-center justify-between text-xs">
                <Label className="text-muted-foreground">Proficiency Level</Label>
                <div className="flex items-center gap-1.5 font-mono">
                  <Input
                    type="number"
                    min={0}
                    max={100}
                    value={newLevel}
                    onChange={(e) => setNewLevel(e.target.value)}
                    className="h-7 w-16 text-center text-xs tabular-nums"
                  />
                  <span className="text-muted-foreground text-xs">%</span>
                </div>
              </div>

              {/* Slider Input */}
              <input
                type="range"
                min={0}
                max={100}
                step={5}
                value={newLevel || 0}
                onChange={(e) => setNewLevel(e.target.value)}
                className="w-full accent-violet cursor-pointer h-1.5 bg-muted rounded-lg"
              />

              {/* Live Preview Band */}
              <div className="flex items-center justify-between rounded-lg border border-border/60 bg-muted/20 px-3 py-2 text-xs">
                <span className="text-muted-foreground">Assessment Band:</span>
                <span
                  className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-xs font-medium ${modalPreviewBand.chip} ${modalPreviewBand.text}`}
                >
                  <modalPreviewBand.Icon className="h-3 w-3" />
                  {modalPreviewBand.label} ({newLevel}%)
                </span>
              </div>
            </div>

            {error && (
              <p className="text-xs text-risk font-medium" role="alert">
                {error}
              </p>
            )}
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="ghost" size="sm" onClick={() => setDialogOpen(false)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="glow" size="sm" onClick={handleAdd} disabled={busy}>
              {busy ? "Saving…" : "Save Skill"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* AI Skill Viva — verifies a skill when passed */}
      <SkillVivaDialog
        skillId={vivaSkill?.id ?? null}
        skillName={vivaSkill?.skill ?? null}
        open={vivaSkill !== null}
        onClose={() => setVivaSkill(null)}
        onVerified={refetch}
      />

      {/* Attach a proof link — marks the skill Evidenced */}
      <Dialog open={evidenceSkill !== null} onOpenChange={(o) => { if (!o) setEvidenceSkill(null); }}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Link2 className="h-4 w-4 text-violet-bright" /> Add Proof for {evidenceSkill?.skill}
            </DialogTitle>
            <DialogDescription className="text-xs">
              Link a project, GitHub repo or certificate (https). This marks the skill Evidenced — a
              stronger signal than self-declared, though a passed viva (Verified) counts for the most.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-3 py-2">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">Proof link (https)</Label>
              <Input
                value={evidenceUrl}
                placeholder="https://github.com/you/project"
                onChange={(e) => setEvidenceUrl(e.target.value)}
                className="text-sm"
              />
            </div>
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">Note (optional)</Label>
              <Input
                value={evidenceNote}
                placeholder="e.g. Built the authentication module"
                onChange={(e) => setEvidenceNote(e.target.value)}
                className="text-sm"
              />
            </div>
            {error && (
              <p className="text-xs text-risk font-medium" role="alert">
                {error}
              </p>
            )}
          </div>
          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="ghost" size="sm" onClick={() => setEvidenceSkill(null)} disabled={busy}>
              Cancel
            </Button>
            <Button variant="glow" size="sm" onClick={handleSaveEvidence} disabled={busy}>
              {busy ? "Saving…" : "Save proof"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
