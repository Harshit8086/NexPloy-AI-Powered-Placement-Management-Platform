"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { FolderGit2, Plus, Trash2, ArrowLeft, Sparkles, ExternalLink, Pencil } from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { PageHeader } from "@/components/layout/PageHeader";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { getPrimaryStudent, updatePrimaryStudent } from "@/lib/api/students";
import type { Student, StudentProject } from "@/types";

// Brand icons were removed from lucide-react — inline the GitHub mark instead.
function GitHubIcon({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" className={className} aria-hidden="true">
      <path d="M15 22v-4a4.8 4.8 0 0 0-1-3.5c3 0 6-2 6-5.5.08-1.25-.27-2.48-1-3.5.28-1.15.28-2.35 0-3.5 0 0-1 0-3 1.5-2.64-.5-5.36-.5-8 0C6 2 5 2 5 2c-.3 1.15-.3 2.35 0 3.5A5.403 5.403 0 0 0 4 9c0 3.5 3 5.5 6 5.5-.39.49-.68 1.05-.85 1.65-.17.6-.22 1.23-.15 1.85v4" />
      <path d="M9 18c-4.51 2-5-2-7-2" />
    </svg>
  );
}

function parseStack(raw: string): string[] {
  return raw
    .split(",")
    .map((t) => t.trim())
    .filter(Boolean);
}

/** Free-text link -> absolute URL; empty stays undefined (field is optional). */
function normalizeProjectUrl(raw: string): string | undefined {
  const trimmed = raw.trim();
  if (!trimmed) return undefined;
  return /^https?:\/\//i.test(trimmed) ? trimmed : `https://${trimmed}`;
}

export default function StudentProjectsPage() {
  const { data: student } = useApiResource<Student | null>(() => getPrimaryStudent(), [], null);

  const [projects, setProjects] = useState<StudentProject[]>([]);
  const [seeded, setSeeded] = useState(false);
  const [name, setName] = useState("");
  const [stack, setStack] = useState("");
  const [githubUrl, setGithubUrl] = useState("");
  const [liveUrl, setLiveUrl] = useState("");
  const [summary, setSummary] = useState("");
  const [saving, setSaving] = useState(false);
  const [editingIndex, setEditingIndex] = useState<number | null>(null);
  const [error, setError] = useState<string | null>(null);

  const pushToast = useAppStore((s) => s.pushToast);

  // Seed the editable list once the profile loads (the record holds the projects array).
  useEffect(() => {
    if (student && !seeded) {
      setProjects(student.projects ?? []);
      setSeeded(true);
    }
  }, [student, seeded]);

  const persist = async (next: StudentProject[], message: string) => {
    setSaving(true);
    setError(null);
    try {
      const saved = await updatePrimaryStudent({ projects: next });
      setProjects(saved.projects ?? next);
      pushToast("Projects saved", message);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Could not save your projects. Please try again.");
    } finally {
      setSaving(false);
    }
  };

  const clearForm = () => {
    setName("");
    setStack("");
    setGithubUrl("");
    setLiveUrl("");
    setSummary("");
  };

  const startEdit = (index: number) => {
    const p = projects[index];
    setEditingIndex(index);
    setName(p.name);
    setStack(p.stack.join(", "));
    setGithubUrl(p.githubUrl ?? "");
    setLiveUrl(p.liveUrl ?? "");
    setSummary(p.summary ?? "");
    setError(null);
  };

  const cancelEdit = () => {
    setEditingIndex(null);
    clearForm();
    setError(null);
  };

  const saveProject = async () => {
    const trimmed = name.trim();
    if (!trimmed) {
      setError("Enter a project name.");
      return;
    }
    const project: StudentProject = {
      name: trimmed,
      stack: parseStack(stack),
      summary: summary.trim(),
      githubUrl: normalizeProjectUrl(githubUrl),
      liveUrl: normalizeProjectUrl(liveUrl),
    };
    if (editingIndex !== null) {
      const index = editingIndex;
      await persist(projects.map((p, i) => (i === index ? project : p)), `"${projects[index].name}" updated.`);
      setEditingIndex(null);
    } else {
      await persist([...projects, project], `"${trimmed}" added.`);
    }
    clearForm();
  };

  const removeProject = async (index: number) => {
    const removed = projects[index];
    if (editingIndex === index) cancelEdit();
    await persist(projects.filter((_, i) => i !== index), `"${removed.name}" removed.`);
  };

  return (
    <div className="mx-auto max-w-3xl">
      <PageHeader
        eyebrow="Profile"
        title="My Projects"
        subtitle="Projects on your placement profile — recruiters and matching read these to gauge applied skill."
        actions={
          <Button asChild variant="outline" size="sm">
            <Link href="/student/profile">
              <ArrowLeft className="h-4 w-4 mr-1.5" /> Back to Profile
            </Link>
          </Button>
        }
      />

      <div className="mb-5 flex items-center justify-between gap-3 rounded-lg border border-violet/20 bg-violet/5 px-4 py-3 text-xs text-muted-foreground">
        <div className="flex items-center gap-2">
          <Sparkles className="h-4 w-4 shrink-0 text-violet-bright" />
          <span>Projects added here automatically sync with your <strong>Placement Profile</strong> and <strong>Resume Builder</strong>.</span>
        </div>
        <Button asChild variant="ghost" size="sm" className="h-auto p-0 text-xs text-violet-bright hover:text-white shrink-0">
          <Link href="/student/resume/builder">Resume Builder →</Link>
        </Button>
      </div>

      <DepthCard className="mb-5 p-5">
        <div className="mb-3 flex items-center gap-2">
          {editingIndex !== null ? <Pencil className="h-4 w-4 text-violet-bright" /> : <Plus className="h-4 w-4 text-violet-bright" />}
          <CardTitle>{editingIndex !== null ? "Edit project" : "Add a project"}</CardTitle>
        </div>
        <div className="space-y-3">
          <div>
            <Label className="text-xs text-muted-foreground">Project name</Label>
            <Input
              value={name}
              placeholder="e.g. Campus Placement Portal"
              onChange={(e) => setName(e.target.value)}
              className="mt-1"
            />
          </div>
          <div>
            <Label className="text-xs text-muted-foreground">Tech stack (comma-separated)</Label>
            <Input
              value={stack}
              placeholder="e.g. React, Spring Boot, PostgreSQL"
              onChange={(e) => setStack(e.target.value)}
              className="mt-1"
            />
          </div>
          <div>
            <Label className="text-xs text-muted-foreground">GitHub link</Label>
            <Input
              value={githubUrl}
              placeholder="e.g. https://github.com/you/repo"
              onChange={(e) => setGithubUrl(e.target.value)}
              className="mt-1"
            />
          </div>
          <div>
            <Label className="text-xs text-muted-foreground">Live link</Label>
            <Input
              value={liveUrl}
              placeholder="e.g. https://your-demo.app"
              onChange={(e) => setLiveUrl(e.target.value)}
              className="mt-1"
            />
          </div>
          <div>
            <Label className="text-xs text-muted-foreground">Description</Label>
            <Input
              value={summary}
              placeholder="One line on what it does and your role"
              onChange={(e) => setSummary(e.target.value)}
              className="mt-1"
            />
          </div>
          <div className="flex items-center gap-2">
            <Button variant="glow" onClick={saveProject} disabled={saving}>
              {editingIndex !== null ? <Pencil className="h-3.5 w-3.5" /> : <Plus className="h-3.5 w-3.5" />}{" "}
              {saving ? "Saving…" : editingIndex !== null ? "Save changes" : "Add project"}
            </Button>
            {editingIndex !== null && (
              <Button variant="outline" onClick={cancelEdit} disabled={saving}>
                Cancel
              </Button>
            )}
          </div>
        </div>
        {error && (
          <p className="mt-3 text-xs text-risk" role="alert">
            {error}
          </p>
        )}
      </DepthCard>

      <DepthCard className="p-5">
        <div className="mb-4 flex items-center gap-2">
          <FolderGit2 className="h-4 w-4 text-violet-bright" />
          <CardTitle>Your projects</CardTitle>
        </div>

        {projects.length === 0 ? (
          <p className="py-8 text-center text-sm text-muted-foreground">
            {student ? "No projects yet. Add your first project above." : "Loading projects…"}
          </p>
        ) : (
          <div className="space-y-3">
            {projects.map((p, i) => (
              <div key={`${p.name}-${i}`} className={`rounded-lg border p-3 ${editingIndex === i ? "border-violet/50 bg-violet/5" : "border-border"}`}>
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <p className="text-sm font-medium">{p.name}</p>
                    {p.summary && <p className="mt-0.5 text-xs text-muted-foreground">{p.summary}</p>}
                  </div>
                  <div className="flex shrink-0 items-center gap-1.5">
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => startEdit(i)}
                      disabled={saving}
                      aria-label={`Edit ${p.name}`}
                    >
                      <Pencil className="h-3.5 w-3.5" />
                    </Button>
                    <Button
                      size="sm"
                      variant="outline"
                      onClick={() => removeProject(i)}
                      disabled={saving}
                      aria-label={`Remove ${p.name}`}
                    >
                      <Trash2 className="h-3.5 w-3.5" />
                    </Button>
                  </div>
                </div>
                {p.stack.length > 0 && (
                  <div className="mt-2 flex flex-wrap gap-1.5">
                    {p.stack.map((t) => (
                      <Badge key={t} variant="muted">
                        {t}
                      </Badge>
                    ))}
                  </div>
                )}
                {(p.githubUrl || p.liveUrl) && (
                  <div className="mt-2 flex flex-wrap gap-3">
                    {p.githubUrl && (
                      <a href={p.githubUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-xs text-violet-bright hover:underline">
                        <GitHubIcon className="h-3 w-3" /> GitHub
                      </a>
                    )}
                    {p.liveUrl && (
                      <a href={p.liveUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-xs text-violet-bright hover:underline">
                        <ExternalLink className="h-3 w-3" /> Live Demo
                      </a>
                    )}
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </DepthCard>
    </div>
  );
}
