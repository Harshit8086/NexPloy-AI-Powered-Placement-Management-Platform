"use client";

import { Plus, Trash2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import {
  type ResumeData,
  type ResumeExperience,
  type ResumeProject,
  type ResumeEducation,
  emptyExperience,
  emptyProject,
  emptyEducation,
} from "./resumeBuilderTypes";

const textareaClass =
  "flex min-h-[80px] w-full rounded-md border border-input bg-transparent px-3 py-2 text-sm shadow-sm placeholder:text-muted-foreground focus-visible:outline-none focus-visible:ring-1 focus-visible:ring-ring";

/**
 * The controlled résumé-authoring form. The parent owns the `ResumeData`; this
 * component emits the next value through `onChange`. No network calls — the parent
 * decides whether to persist (to localStorage) and how to export.
 */
export function ResumeBuilderForm({
  data,
  onChange,
}: {
  data: ResumeData;
  onChange: (next: ResumeData) => void;
}) {
  const setField = <K extends keyof ResumeData>(key: K, value: ResumeData[K]) =>
    onChange({ ...data, [key]: value });

  // Generic helpers for the three repeatable list sections.
  const addItem = <K extends "experience" | "projects" | "education">(
    key: K,
    blank: ResumeData[K][number],
  ) => onChange({ ...data, [key]: [...data[key], blank] });

  const removeItem = (key: "experience" | "projects" | "education", index: number) =>
    onChange({ ...data, [key]: data[key].filter((_, i) => i !== index) });

  const updateExperience = (index: number, patch: Partial<ResumeExperience>) =>
    setField("experience", data.experience.map((e, i) => (i === index ? { ...e, ...patch } : e)));
  const updateProject = (index: number, patch: Partial<ResumeProject>) =>
    setField("projects", data.projects.map((p, i) => (i === index ? { ...p, ...patch } : p)));
  const updateEducation = (index: number, patch: Partial<ResumeEducation>) =>
    setField("education", data.education.map((e, i) => (i === index ? { ...e, ...patch } : e)));

  return (
    <div className="space-y-6">
      {/* Personal info */}
      <section className="space-y-3">
        <h3 className="text-sm font-semibold text-foreground">Personal details</h3>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Full name" value={data.name} onChange={(v) => setField("name", v)} />
          <Field label="Email" value={data.email} onChange={(v) => setField("email", v)} />
          <Field label="Phone" value={data.phone} onChange={(v) => setField("phone", v)} />
          <Field label="Location" value={data.location} onChange={(v) => setField("location", v)} />
          <Field
            label="LinkedIn username"
            value={data.linkedin}
            onChange={(v) => setField("linkedin", v)}
            placeholder="rahul-sharma"
          />
          <Field
            label="GitHub username"
            value={data.github}
            onChange={(v) => setField("github", v)}
            placeholder="rahulsharma"
          />
        </div>
      </section>

      {/* Summary */}
      <section className="space-y-1.5">
        <Label htmlFor="rb-summary">Professional summary</Label>
        <textarea
          id="rb-summary"
          className={textareaClass}
          value={data.summary}
          maxLength={800}
          placeholder="Two or three lines on who you are and what you do best."
          onChange={(e) => setField("summary", e.target.value)}
        />
      </section>

      {/* Skills */}
      <section className="space-y-1.5">
        <Label htmlFor="rb-skills">Skills (comma-separated)</Label>
        <Input
          id="rb-skills"
          value={data.skills}
          placeholder="Java, Spring Boot, PostgreSQL, React, Docker"
          onChange={(e) => setField("skills", e.target.value)}
        />
      </section>

      {/* Experience */}
      <ListSection
        title="Work experience"
        onAdd={() => addItem("experience", { ...emptyExperience })}
      >
        {data.experience.map((exp, i) => (
          <ItemCard key={i} onRemove={() => removeItem("experience", i)}>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Role" value={exp.role} onChange={(v) => updateExperience(i, { role: v })} />
              <Field label="Company" value={exp.company} onChange={(v) => updateExperience(i, { company: v })} />
              <Field
                label="Duration"
                value={exp.duration}
                onChange={(v) => updateExperience(i, { duration: v })}
                placeholder="Jun 2024 – Aug 2024"
              />
            </div>
            <BulletArea
              label="Description (one bullet per line)"
              value={exp.description}
              onChange={(v) => updateExperience(i, { description: v })}
            />
          </ItemCard>
        ))}
      </ListSection>

      {/* Projects */}
      <ListSection title="Projects" onAdd={() => addItem("projects", { ...emptyProject })}>
        {data.projects.map((proj, i) => (
          <ItemCard key={i} onRemove={() => removeItem("projects", i)}>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Project name" value={proj.name} onChange={(v) => updateProject(i, { name: v })} />
              <Field
                label="Repo / link"
                value={proj.github}
                onChange={(v) => updateProject(i, { github: v })}
                placeholder="github.com/you/project"
              />
              <Field
                label="Tech stack"
                value={proj.techStack}
                onChange={(v) => updateProject(i, { techStack: v })}
                placeholder="React, Node.js, PostgreSQL"
              />
            </div>
            <BulletArea
              label="Description (one bullet per line)"
              value={proj.description}
              onChange={(v) => updateProject(i, { description: v })}
            />
          </ItemCard>
        ))}
      </ListSection>

      {/* Education */}
      <ListSection title="Education" onAdd={() => addItem("education", { ...emptyEducation })}>
        {data.education.map((edu, i) => (
          <ItemCard key={i} onRemove={() => removeItem("education", i)}>
            <div className="grid gap-3 sm:grid-cols-2">
              <Field label="Degree" value={edu.degree} onChange={(v) => updateEducation(i, { degree: v })} />
              <Field label="Branch" value={edu.branch} onChange={(v) => updateEducation(i, { branch: v })} />
              <Field label="College" value={edu.college} onChange={(v) => updateEducation(i, { college: v })} />
              <Field label="Year" value={edu.year} onChange={(v) => updateEducation(i, { year: v })} />
              <Field label="CGPA" value={edu.cgpa} onChange={(v) => updateEducation(i, { cgpa: v })} />
            </div>
          </ItemCard>
        ))}
      </ListSection>
    </div>
  );
}

function Field({
  label,
  value,
  onChange,
  placeholder,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
  placeholder?: string;
}) {
  return (
    <div className="space-y-1.5">
      <Label>{label}</Label>
      <Input value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

function BulletArea({
  label,
  value,
  onChange,
}: {
  label: string;
  value: string;
  onChange: (v: string) => void;
}) {
  return (
    <div className="mt-3 space-y-1.5">
      <Label>{label}</Label>
      <textarea
        className={textareaClass}
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder={"Built X that did Y\nReduced Z by N%"}
      />
    </div>
  );
}

function ListSection({
  title,
  onAdd,
  children,
}: {
  title: string;
  onAdd: () => void;
  children: React.ReactNode;
}) {
  return (
    <section className="space-y-3">
      <div className="flex items-center justify-between">
        <h3 className="text-sm font-semibold text-foreground">{title}</h3>
        <Button type="button" variant="outline" size="sm" onClick={onAdd}>
          <Plus className="mr-1 h-3.5 w-3.5" /> Add
        </Button>
      </div>
      <div className="space-y-3">{children}</div>
    </section>
  );
}

function ItemCard({ onRemove, children }: { onRemove: () => void; children: React.ReactNode }) {
  return (
    <div className="relative rounded-lg border border-border p-4">
      <button
        type="button"
        onClick={onRemove}
        aria-label="Remove"
        className="absolute right-3 top-3 text-muted-foreground transition-colors hover:text-risk"
      >
        <Trash2 className="h-4 w-4" />
      </button>
      {children}
    </div>
  );
}
