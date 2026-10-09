// ============================================================
// RESUME BUILDER TYPES — client-side résumé authoring model. This is the
// authoring/export counterpart to CampusLink's resume *analysis* (which reads an
// uploaded PDF). Drafts live in the browser (localStorage) only; nothing is sent
// to the backend, so there is no new endpoint or stored PII on the server.
// ============================================================

export interface ResumeExperience {
  role: string;
  company: string;
  duration: string;
  description: string; // newline-separated bullet points
}

export interface ResumeProject {
  name: string;
  github: string;
  liveLink: string; // deployed / demo URL
  techStack: string;
  description: string; // newline-separated bullet points
}

export interface ResumeEducation {
  degree: string;
  branch: string;
  college: string;
  year: string;
  cgpa: string;
}

export interface ResumeData {
  name: string;
  email: string;
  phone: string;
  location: string;
  linkedin: string;
  github: string;
  summary: string;
  skills: string; // comma-separated
  experience: ResumeExperience[];
  projects: ResumeProject[];
  education: ResumeEducation[];
}

export const emptyResume: ResumeData = {
  name: "",
  email: "",
  phone: "",
  location: "",
  linkedin: "",
  github: "",
  summary: "",
  skills: "",
  experience: [],
  projects: [],
  education: [],
};

export const emptyExperience: ResumeExperience = { role: "", company: "", duration: "", description: "" };
export const emptyProject: ResumeProject = { name: "", github: "", liveLink: "", techStack: "", description: "" };
export const emptyEducation: ResumeEducation = { degree: "", branch: "", college: "", year: "", cgpa: "" };

/**
 * Case-insensitive union of a comma-separated skills string with extra skill names. Existing entries
 * (and the user's own order) are preserved; only names not already present are appended. Used to merge
 * the student's recorded skills into an existing résumé draft without clobbering their manual edits.
 */
export function mergeSkillsCsv(existingCsv: string, skillNames: string[]): string {
  const existing = existingCsv
    .split(",")
    .map((s) => s.trim())
    .filter(Boolean);
  const seen = new Set(existing.map((s) => s.toLowerCase()));
  for (const name of skillNames) {
    const trimmed = name.trim();
    if (trimmed && !seen.has(trimmed.toLowerCase())) {
      existing.push(trimmed);
      seen.add(trimmed.toLowerCase());
    }
  }
  return existing.join(", ");
}
/**
 * Serializes the authored résumé into a readable plain-text document, suitable for the Resume
 * Intelligence analyzer (which extracts text from an uploaded .txt/PDF file). Empty sections are
 * omitted so the analyzer only sees real content.
 */
export function resumeDataToText(data: ResumeData): string {
  const lines: string[] = [];
  const push = (s = "") => lines.push(s);

  if (data.name.trim()) push(data.name.trim());
  const contact = [data.email, data.phone, data.location, data.linkedin, data.github]
    .map((s) => s.trim())
    .filter(Boolean);
  if (contact.length) push(contact.join(" | "));

  if (data.summary.trim()) {
    push();
    push("SUMMARY");
    push(data.summary.trim());
  }

  const skills = data.skills.split(",").map((s) => s.trim()).filter(Boolean);
  if (skills.length) {
    push();
    push("SKILLS");
    push(skills.join(", "));
  }

  const bullets = (text: string) =>
    text.split("\n").map((b) => b.trim()).filter(Boolean).forEach((b) => push(`- ${b}`));

  const exp = data.experience.filter((e) => e.role || e.company || e.description);
  if (exp.length) {
    push();
    push("EXPERIENCE");
    for (const e of exp) {
      const header = [e.role, e.company, e.duration].map((s) => s.trim()).filter(Boolean).join(" — ");
      if (header) push(header);
      bullets(e.description);
    }
  }
  const projects = data.projects.filter((p) => p.name || p.description || p.techStack);
  if (projects.length) {
    push();
    push("PROJECTS");
    for (const p of projects) {
      const header = [p.name, p.techStack].map((s) => s.trim()).filter(Boolean).join(" — ");
      if (header) push(header);
      const links = [p.github, p.liveLink].map((s) => s.trim()).filter(Boolean);
      if (links.length) push(links.join(" | "));
      bullets(p.description);
    }
  }

  const education = data.education.filter((e) => e.degree || e.branch || e.college || e.year || e.cgpa);
  if (education.length) {
    push();
    push("EDUCATION");
    for (const e of education) {
      const parts = [
        [e.degree, e.branch].map((s) => s.trim()).filter(Boolean).join(" in "),
        e.college.trim(),
        e.year.trim(),
        e.cgpa.trim() ? `CGPA: ${e.cgpa.trim()}` : "",
      ]
        .map((s) => s.trim())
        .filter(Boolean);
      if (parts.length) push(parts.join(", "));
    }
  }

  return lines.join("\n").trim();
}
