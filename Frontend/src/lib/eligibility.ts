// ============================================================
// DETERMINISTIC ELIGIBILITY ENGINE
//
// Eligibility is a RULE CHECK. Match Score is an INTELLIGENCE ESTIMATE.
// They are deliberately computed in different places and must never be
// conflated in the UI: a student can be 91% matched and still NOT
// ELIGIBLE, and vice-versa.
//
// Every check here is a pure comparison against Drive.criteria, so the
// same student + drive pair always produces identical output and an
// explanation string a human can verify by eye.
// ============================================================

import type { Drive, EligibilityCheck, EligibilityResult, Student } from "@/types";

export function evaluateEligibility(student: Student, drive: Drive): EligibilityResult {
  const c = drive.criteria;
  const checks: EligibilityCheck[] = [];

  const cgpaPassed = student.cgpa >= c.minCgpa;
  checks.push({
    label: "CGPA",
    passed: cgpaPassed,
    detail: `${student.cgpa.toFixed(2)} ${cgpaPassed ? ">=" : "<"} ${c.minCgpa.toFixed(1)} required`,
  });

  const branchPassed = c.allowedBranches.includes(student.branchCode);
  checks.push({
    label: "Branch",
    passed: branchPassed,
    detail: branchPassed
      ? `${student.branchCode} is in allowed list (${c.allowedBranches.join(", ")})`
      : `${student.branchCode} not in allowed list (${c.allowedBranches.join(", ")})`,
  });

  const backlogsPassed = student.backlogs <= c.maxBacklogs;
  checks.push({
    label: "Active Backlogs",
    passed: backlogsPassed,
    detail: `${student.backlogs} ${backlogsPassed ? "<=" : ">"} maximum ${c.maxBacklogs}`,
  });

  const yearPassed = student.graduationYear === c.graduationYear;
  checks.push({
    label: "Graduation Year",
    passed: yearPassed,
    detail: yearPassed
      ? `${student.graduationYear} matches required batch`
      : `${student.graduationYear} does not match required ${c.graduationYear}`,
  });

  if (c.minTenthPercentage !== undefined) {
    const passed = student.tenthPercentage >= c.minTenthPercentage;
    checks.push({
      label: "10th Percentage",
      passed,
      detail: `${student.tenthPercentage}% ${passed ? ">=" : "<"} ${c.minTenthPercentage}% required`,
    });
  }

  if (c.minTwelfthPercentage !== undefined) {
    const passed = student.twelfthPercentage >= c.minTwelfthPercentage;
    checks.push({
      label: "12th Percentage",
      passed,
      detail: `${student.twelfthPercentage}% ${passed ? ">=" : "<"} ${c.minTwelfthPercentage}% required`,
    });
  }

  const failureReasons = checks.filter((k) => !k.passed).map((k) => `${k.label}: ${k.detail}`);

  return { eligible: failureReasons.length === 0, checks, failureReasons };
}

/** Convenience for tables/badges that only need the verdict string. */
export function eligibilityLabel(student: Student, drive: Drive): "ELIGIBLE" | "NOT ELIGIBLE" {
  return evaluateEligibility(student, drive).eligible ? "ELIGIBLE" : "NOT ELIGIBLE";
}

export function getEligibleStudents(students: Student[], drive: Drive): Student[] {
  return students.filter((s) => evaluateEligibility(s, drive).eligible);
}

/** Short, single-line reason used in the eligibility matrix. */
export function primaryFailureReason(student: Student, drive: Drive): string {
  const result = evaluateEligibility(student, drive);
  if (result.eligible) return "All criteria met";
  return result.failureReasons[0];
}

/** Human-readable summary of a drive's criteria, used on drive cards. */
export function describeCriteria(drive: Drive): string {
  const c = drive.criteria;
  return `CGPA ${c.minCgpa}+ · ${c.allowedBranches.join("/")} · max ${c.maxBacklogs} backlog${
    c.maxBacklogs === 1 ? "" : "s"
  } · ${c.graduationYear} batch`;
}
