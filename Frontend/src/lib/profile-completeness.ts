import type { Student } from "@/types";

/** One checklist row in the profile-completeness nudge. */
export interface CompletenessItem {
  key: string;
  label: string;
  done: boolean;
  /** Shown when the item is incomplete — tells the student what to add. */
  hint: string;
}

export interface ProfileCompleteness {
  /** 0–100, rounded — share of checklist items completed. */
  score: number;
  completed: number;
  total: number;
  items: CompletenessItem[];
}

/**
 * Computes how complete a student's self-reported profile is. Derived from fields the student
 * can edit on the profile page (contact, career intent, certificates, projects, experience)
 * plus their skills record. Skills now live solely in My Skills (the student_skills record), so
 * the Skills check reads `skillCount` rather than the legacy `student.strengths` chips. The
 * certificates check reads `certificateCount` — the document-backed Certificates records —
 * since the legacy free-text certification tags were retired from the profile editor.
 */
export function profileCompleteness(student: Student, skillCount = 0, certificateCount = 0): ProfileCompleteness {
  const items: CompletenessItem[] = [
    {
      key: "phone",
      label: "Contact number",
      done: Boolean(student.phone?.trim()),
      hint: "Add a phone number recruiters can reach you on.",
    },
    {
      key: "targetRole",
      label: "Target role",
      done: Boolean(student.targetRole?.trim()),
      hint: "Set the one role you're aiming for — it drives matching and skill-gap.",
    },
    {
      key: "preferredRoles",
      label: "Preferred roles",
      done: student.preferredRoles.length > 0,
      hint: "List a few roles you'd consider so we can surface the right drives.",
    },
    {
      key: "skills",
      label: "Skills",
      done: skillCount > 0,
      hint: "Add your skills and proficiency in My Skills — they power matching and skill-gap.",
    },
    {
      key: "certificates",
      label: "Certificates",
      done: certificateCount > 0,
      hint: "Upload a certificate in the Certificates section to strengthen your profile evidence.",
    },
    {
      key: "projects",
      label: "Projects",
      done: student.projects.length > 0,
      hint: "Add a project or two to show recruiters what you've built.",
    },
    {
      key: "experience",
      label: "Work experience",
      done: student.experience.length > 0,
      hint: "Add internships or work experience to round out your profile.",
    },
  ];

  const completed = items.filter((i) => i.done).length;
  const total = items.length;
  const score = total === 0 ? 100 : Math.round((completed / total) * 100);
  return { score, completed, total, items };
}
