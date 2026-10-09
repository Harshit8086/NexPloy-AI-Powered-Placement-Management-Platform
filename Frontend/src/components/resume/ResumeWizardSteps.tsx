"use client";

import { useState } from "react";
import { Loader2, Plus, Sparkles, Trash2 } from "lucide-react";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  type ResumeData,
  type ResumeExperience,
  type ResumeProject,
  type ResumeEducation,
  emptyExperience,
  emptyProject,
  emptyEducation,
} from "./resumeBuilderTypes";
import { generateResumeSummary } from "@/lib/api/ai";

const textareaClass =
  "flex min-h-[120px] w-full rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground shadow-sm outline-none placeholder:text-muted-foreground focus:border-violet/50";

/** The six wizard steps, in the FresherAI order. Used for the header title/subtitle + dot count. */
export const WIZARD_STEPS = [
  { title: "Personal Information", subtitle: "Your basic contact details" },
  { title: "Professional Summary", subtitle: "A quick intro about yourself" },
  { title: "Skills", subtitle: "Your technical skills" },
  { title: "Work Experience", subtitle: "Your past jobs & internships" },
  { title: "Projects", subtitle: "Projects you have built" },
  { title: "Education", subtitle: "Your academic background" },
] as const;

export const WIZARD_STEP_COUNT = WIZARD_STEPS.length;

/**
 * Renders the fields for a single wizard step. The parent owns the `ResumeData`, the current `step`
 * index (0-5) and navigation; this component only edits the data for that step. No network calls —
 * the draft lives in the browser and is exported client-side (react-to-print).
 */
export function ResumeWizardSteps({
  step,
  data,
  onChange,
}: {
  step: number;
  data: ResumeData;
  onChange: (next: ResumeData) => void;
}) {
  const setField = <K extends keyof ResumeData>(key: K, value: ResumeData[K]) =>
    onChange({ ...data, [key]: value });

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

  // Step 1 — Personal Information
  if (step === 0) {
    return (
      <div className="space-y-4">
        <Field label="Full Name" value={data.name} onChange={(v) => setField("name", v)} placeholder="Rahul Sharma" />
        <Field label="Email" value={data.email} onChange={(v) => setField("email", v)} placeholder="rahul@email.com" />
        <Field label="Phone" value={data.phone} onChange={(v) => setField("phone", v)} placeholder="+91 9876543210" />
        <Field label="Location" value={data.location} onChange={(v) => setField("location", v)} placeholder="Jhansi, UP" />
        <Field label="LinkedIn URL" value={data.linkedin} onChange={(v) => setField("linkedin", v)} placeholder="linkedin.com/in/rahul" />
        <Field label="GitHub URL" value={data.github} onChange={(v) => setField("github", v)} placeholder="github.com/rahul" />
      </div>
    );
  }

  // Step 2 — Professional Summary
  if (step === 1) {
    return <SummaryStep data={data} setField={setField} />;
  }

  // Step 3 — Skills
  if (step === 2) {
    return (
      <div className="space-y-1.5">
        <Label htmlFor="rb-skills">Skills (comma separated)</Label>
        <textarea
          id="rb-skills"
          className={textareaClass}
          value={data.skills}
          placeholder="Java, Spring Boot, PostgreSQL, React, Docker"
          onChange={(e) => setField("skills", e.target.value)}
        />
        <p className="text-xs text-muted-foreground">Separate each skill with a comma.</p>
      </div>
    );
  }

  // Step 4 — Work Experience
  if (step === 3) {
    return (
      <div className="space-y-4">
        {data.experience.map((exp, i) => (
          <ItemCard key={i} onRemove={() => removeItem("experience", i)}>
            <Field label="Company" value={exp.company} onChange={(v) => updateExperience(i, { company: v })} placeholder="ABC Technologies" />
            <Field label="Role" value={exp.role} onChange={(v) => updateExperience(i, { role: v })} placeholder="Backend Developer" />
            <Field label="Duration" value={exp.duration} onChange={(v) => updateExperience(i, { duration: v })} placeholder="Jan 2023 – Dec 2024" />
            <BulletArea label="Description" value={exp.description} onChange={(v) => updateExperience(i, { description: v })} placeholder={"• Built REST APIs\n• Improved performance by 40%"} />
          </ItemCard>
        ))}
        <AddButton label="Add Experience" onClick={() => addItem("experience", { ...emptyExperience })} />
      </div>
    );
  }

  // Step 5 — Projects
  if (step === 4) {
    return (
      <div className="space-y-4">
        {data.projects.map((proj, i) => (
          <ItemCard key={i} onRemove={() => removeItem("projects", i)}>
            <Field label="Project Name" value={proj.name} onChange={(v) => updateProject(i, { name: v })} placeholder="InterviewIQ" />
            <Field label="Tech Stack" value={proj.techStack} onChange={(v) => updateProject(i, { techStack: v })} placeholder="React, Node.js, MongoDB" />
            <Field label="GitHub Link" value={proj.github} onChange={(v) => updateProject(i, { github: v })} placeholder="github.com/rahul/interviewiq" />
            <Field label="Live Link" value={proj.liveLink} onChange={(v) => updateProject(i, { liveLink: v })} placeholder="interviewiq.vercel.app" />
            <BulletArea label="Description" value={proj.description} onChange={(v) => updateProject(i, { description: v })} placeholder="AI-powered interview preparation platform with mock interviews and resume builder." />
          </ItemCard>
        ))}
        <AddButton label="Add Project" onClick={() => addItem("projects", { ...emptyProject })} />
      </div>
    );
  }

  // Step 6 — Education
  return (
    <div className="space-y-4">
      {data.education.map((edu, i) => (
        <ItemCard key={i} onRemove={() => removeItem("education", i)}>
          <Field label="College / University" value={edu.college} onChange={(v) => updateEducation(i, { college: v })} placeholder="SR Group of Institutions" />
          <Field label="Degree" value={edu.degree} onChange={(v) => updateEducation(i, { degree: v })} placeholder="B.Tech" />
          <Field label="Branch" value={edu.branch} onChange={(v) => updateEducation(i, { branch: v })} placeholder="Computer Science" />
          <Field label="CGPA" value={edu.cgpa} onChange={(v) => updateEducation(i, { cgpa: v })} placeholder="8.5" />
          <Field label="Year" value={edu.year} onChange={(v) => updateEducation(i, { year: v })} placeholder="2021 – 2025" />
        </ItemCard>
      ))}
      <AddButton label="Add Education" onClick={() => addItem("education", { ...emptyEducation })} />
    </div>
  );
}

// ── AI Summary Generator ──────────────────────────────────────────────────────

/**
 * Generates a professional summary from the current resume data.
 * Runs entirely in the browser — no API call required.
 */
function generateSummary(data: ResumeData): string {
  const name = data.name.trim();
  const skills = data.skills
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  const experience = data.experience.filter((e) => e.role && e.company);
  const projects = data.projects.filter((p) => p.name);
  const education = data.education.find((e) => e.branch || e.degree);

  const parts: string[] = [];

  // --- Opening line ---
  if (experience.length > 0) {
    const latest = experience[0];
    const totalExp =
      experience.length === 1 ? "" : ` with ${experience.length} internship${experience.length > 1 ? "s" : ""}`;
    parts.push(
      `${name ? name + " is a" : "A"} passionate ${latest.role}${totalExp} committed to building impactful software solutions.`,
    );
  } else if (education) {
    const branch = education.branch || education.degree || "Computer Science";
    const gradYear = education.year ? ` (${education.year})` : "";
    parts.push(
      `${name ? name + " is an" : "An"} aspiring ${branch} professional${gradYear}, eager to contribute to cutting-edge technology projects.`,
    );
  } else {
    parts.push(
      `${name ? name + " is a" : "A"} motivated technology enthusiast focused on delivering high-quality software solutions.`,
    );
  }

  // --- Skills line ---
  if (skills.length > 0) {
    const topSkills = skills.slice(0, 6).join(", ");
    const rest = skills.length > 6 ? ` and ${skills.length - 6} more` : "";
    parts.push(`Proficient in ${topSkills}${rest}.`);
  }

  // --- Experience line ---
  if (experience.length > 0) {
    const latest = experience[0];
    parts.push(
      `Has hands-on experience at ${latest.company} as a ${latest.role}${latest.duration ? " (" + latest.duration + ")" : ""
      }, delivering production-grade features.`,
    );
  }

  // --- Projects line ---
  if (projects.length > 0) {
    const projectNames = projects
      .slice(0, 3)
      .map((p) => p.name)
      .join(", ");
    parts.push(
      `Has built ${projects.length > 1 ? projects.length + " notable projects" : "a notable project"} including ${projectNames}${projects[0].techStack ? " using " + projects[0].techStack : ""
      }.`,
    );
  }

  // --- Closing line ---
  parts.push(
    "A quick learner with strong problem-solving skills, passionate about writing clean, maintainable code and growing within a collaborative team.",
  );

  return parts.join(" ");
}

function SummaryStep({
  data,
  setField,
}: {
  data: ResumeData;
  setField: <K extends keyof ResumeData>(key: K, value: ResumeData[K]) => void;
}) {
  const [generating, setGenerating] = useState(false);
  const [generated, setGenerated] = useState(false);
  const [source, setSource] = useState<string>("Spring AI");

  const handleGenerate = async () => {
    setGenerating(true);
    setGenerated(false);

    try {
      // Call Spring AI backend (powered by Groq ChatClient)
      const res = await generateResumeSummary({
        name: data.name,
        targetRole: data.experience?.[0]?.role || "",
        skills: data.skills,
        experience: data.experience,
        projects: data.projects,
        education: data.education,
      });

      if (res && res.summary) {
        setField("summary", res.summary);
        setSource(res.provider === "spring-ai-groq" ? "Spring AI" : "AI");
        setGenerated(true);
        setGenerating(false);
        return;
      }
    } catch (err) {
      console.warn("Backend Spring AI call failed or backend offline, using fallback generator:", err);
    }

    // Fallback: smart client-side generation if backend is uncontactable
    const summary = generateSummary(data);
    setField("summary", summary);
    setSource("AI");
    setGenerated(true);
    setGenerating(false);
  };

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between">
        <Label htmlFor="rb-summary">Professional Summary</Label>
        <button
          type="button"
          onClick={handleGenerate}
          disabled={generating}
          className="inline-flex items-center gap-1.5 rounded-lg border border-violet/40 bg-violet/10 px-3 py-1.5 text-xs font-semibold text-violet-bright transition-all hover:bg-violet/20 hover:border-violet/70 disabled:opacity-60 disabled:cursor-not-allowed"
        >
          {generating ? (
            <>
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              Generating with Spring AI…
            </>
          ) : (
            <>
              <Sparkles className="h-3.5 w-3.5" />
              {generated ? "Regenerate" : "Generate with AI"}
            </>
          )}
        </button>
      </div>

      <div className="relative">
        <textarea
          id="rb-summary"
          className={`${textareaClass} transition-all duration-300 ${generating ? "opacity-50" : "opacity-100"
            }`}
          value={data.summary}
          maxLength={800}
          placeholder="Click ✨ Generate with AI above to generate a summary via Spring AI…"
          onChange={(e) => setField("summary", e.target.value)}
          disabled={generating}
        />
      </div>

      <p className="text-xs text-muted-foreground">
        {generated
          ? `✅ Summary generated using ${source}! Edit freely to personalise it.`
          : "Generate with AI — Create a tailored professional summary instantly."}
      </p>
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
      <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</Label>
      <Input value={value} placeholder={placeholder} onChange={(e) => onChange(e.target.value)} />
    </div>
  );
}

function BulletArea({
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
      <Label className="text-xs font-semibold uppercase tracking-wide text-muted-foreground">{label}</Label>
      <textarea className={textareaClass} value={value} onChange={(e) => onChange(e.target.value)} placeholder={placeholder} />
    </div>
  );
}

function ItemCard({ onRemove, children }: { onRemove: () => void; children: React.ReactNode }) {
  return (
    <div className="relative space-y-3 rounded-xl border border-border p-4">
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

function AddButton({ label, onClick }: { label: string; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex w-full items-center justify-center gap-1.5 rounded-xl border border-dashed border-border py-3 text-sm font-medium text-muted-foreground transition-colors hover:border-violet/40 hover:text-violet-bright"
    >
      <Plus className="h-4 w-4" /> {label}
    </button>
  );
}
