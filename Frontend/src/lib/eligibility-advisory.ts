// ============================================================
// ELIGIBILITY ADVISORY — SOFT, EXPLAINABLE CONTEXT (NOT A RULE CHECK)
//
// The deterministic eligibility verdict (src/lib/eligibility.ts) is a hard
// boolean gate and is never touched here. This builds the *advisory* context
// the problem statement's Section D asks for — "CGPA meets criteria, but the
// skill set shows a gap in cloud tech and the mock-interview is below
// benchmark" — from signals that already reach the UI: the drive skill-fit
// analysis and the student's mock-interview score. It is presented in a
// clearly separated block and must never change the eligibility badge.
// ============================================================

import type { MatchBreakdown, Student } from "@/types";
import type { RoleSkillGap } from "@/lib/api/skillgap";

/**
 * Mock-interview scores below this are flagged as "below benchmark". There is no per-recruiter
 * benchmark field in the model yet, so this is a single labeled constant rather than a fabricated
 * per-drive threshold — swap for `Drive.minimumMockInterviewScore` when that field exists.
 */
export const MOCK_BENCHMARK = 60;

export interface EligibilityAdvisory {
  /** One-line Section-D style summary. */
  headline: string;
  /** Grounded supporting notes; every entry traces to real, fetched data. */
  notes: string[];
  /** True when at least one note is a concern (gap / below benchmark) — drives tone in the UI. */
  hasConcern: boolean;
}

/**
 * Builds advisory context for one student against the selected drive. Returns null when no
 * advisory signal is available (no role catalog entry and no mock score), so the drawer can omit
 * the block entirely rather than show an empty one. Nothing here is fabricated: the skill note is
 * emitted only when the drive's role has a requirements catalog entry
 * (`roleRequirementsAvailable`), and the mock note only when a score is on record (> 0).
 */
export function buildAdvisory(input: {
  student: Student;
  driveSkillFit?: RoleSkillGap;
  matchBreakdown?: MatchBreakdown;
}): EligibilityAdvisory | null {
  const { student, driveSkillFit, matchBreakdown } = input;
  const notes: string[] = [];
  const concerns: string[] = [];

  // Skill-gap signal — only when the role has a requirements catalog entry.
  if (driveSkillFit?.roleRequirementsAvailable) {
    const priorityGaps = driveSkillFit.gaps
      .filter((gap) => gap.priority === "HIGH" || gap.priority === "MEDIUM")
      .slice(0, 3);
    if (priorityGaps.length > 0) {
      const skills = priorityGaps.map((gap) => gap.skill).join(", ");
      concerns.push(`skill set shows a gap in ${skills} (role match ${driveSkillFit.overallMatch}%)`);
      notes.push(`Skill gap in ${skills}. ${priorityGaps[0].whyItMatters}`);
    } else {
      notes.push(`Skills meet the role profile (role match ${driveSkillFit.overallMatch}%).`);
    }
  }

  // Mock-interview signal — only when a score is on record.
  const mock = student.assessments?.mockInterview ?? 0;
  if (mock > 0) {
    if (mock < MOCK_BENCHMARK) {
      concerns.push(`mock-interview score (${mock}) is below the ${MOCK_BENCHMARK} benchmark`);
      notes.push(`Mock-interview score ${mock} is below the ${MOCK_BENCHMARK} benchmark.`);
    } else {
      notes.push(`Mock-interview score ${mock} meets the ${MOCK_BENCHMARK} benchmark.`);
    }
  }

  // Optional fit-breakdown context, when a match has been computed for this pair.
  if (matchBreakdown) {
    notes.push(
      `Interview fit ${matchBreakdown.interview}% · readiness ${matchBreakdown.readiness}%.`,
    );
  }

  if (notes.length === 0) return null;

  const headline =
    concerns.length > 0
      ? `Below threshold: ${concerns.join("; ")}.`
      : "Strong candidate on the advisory signals available.";

  return { headline, notes, hasConcern: concerns.length > 0 };
}
