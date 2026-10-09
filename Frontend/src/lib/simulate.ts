// ============================================================
// UI PROGRESS-STEP DEFINITIONS
//
// This module holds ONLY presentation-layer constants: the staged
// "runner" step labels shown while a real backend request is in flight,
// plus the What-If factor labels/types used by the What-If simulator UI.
//
// It no longer computes or fabricates any business data. All intelligence
// (digital twin, what-if, candidate analysis, resume intelligence) now comes
// from the backend via the api layer. Nothing here imports @/data/mock.
// ============================================================

export interface StagedStep {
  label: string;
  durationMs: number;
}

export const digitalTwinSteps: StagedStep[] = [
  { label: "Analyzing profile...", durationMs: 280 },
  { label: "Skills connected", durationMs: 260 },
  { label: "Academics connected", durationMs: 260 },
  { label: "Assessments connected", durationMs: 260 },
  { label: "Placement activity connected", durationMs: 260 },
];

export interface DigitalTwinResult {
  finalScore: number;
  label: string;
}

// ---- What-If Simulator (UI labels/types only) ------------------------

export type WhatIfFactor =
  | "aws"
  | "docker"
  | "dsa"
  | "mockInterviews"
  | "newProject"
  | "communication";

export const whatIfFactorLabels: Record<WhatIfFactor, string> = {
  aws: "Learn AWS",
  docker: "Complete Docker",
  dsa: "Improve DSA",
  mockInterviews: "3 Mock Interviews",
  newProject: "Build 1 New Project",
  communication: "Improve Communication",
};

// ---- Candidate Analysis (recruiter side) -----------------------------

export const candidateAnalysisSteps: StagedStep[] = [
  { label: "Checking eligibility...", durationMs: 260 },
  { label: "Comparing skills...", durationMs: 260 },
  { label: "Analyzing resume relevance...", durationMs: 260 },
  { label: "Evaluating readiness...", durationMs: 260 },
  { label: "Generating match intelligence...", durationMs: 300 },
];

export const shortlistingSteps: StagedStep[] = [
  { label: "Re-checking eligibility...", durationMs: 240 },
  { label: "Confirming match thresholds...", durationMs: 240 },
  { label: "Updating candidate pipeline...", durationMs: 260 },
];

// ---- Eligibility & document checks -----------------------------------

export const eligibilityCheckSteps: StagedStep[] = [
  { label: "Reading drive criteria...", durationMs: 240 },
  { label: "Checking CGPA and branch...", durationMs: 240 },
  { label: "Checking backlogs and batch...", durationMs: 240 },
  { label: "Producing eligibility verdict...", durationMs: 260 },
];

export const documentVerificationSteps: StagedStep[] = [
  { label: "Opening submitted document...", durationMs: 240 },
  { label: "Matching against student record...", durationMs: 260 },
  { label: "Recording verification...", durationMs: 240 },
];

// ---- Schedule Optimization (officer side) -----------------------------

export const scheduleOptimizationSteps: StagedStep[] = [
  { label: "Analyzing student overlap...", durationMs: 280 },
  { label: "Checking venue availability...", durationMs: 260 },
  { label: "Checking panel availability...", durationMs: 260 },
  { label: "Evaluating alternative slots...", durationMs: 280 },
];

// ---- Resume intelligence ---------------------------------------------

export const resumeAnalysisSteps: StagedStep[] = [
  { label: "Reading resume sections...", durationMs: 260 },
  { label: "Matching against target role...", durationMs: 260 },
  { label: "Scoring impact statements...", durationMs: 260 },
  { label: "Generating recommendations...", durationMs: 280 },
];

export interface ResumeIntelligence {
  score: number;
  strengths: string[];
  weaknesses: string[];
  missingKeywords: string[];
  projectSuggestions: string[];
  recommendation: string;
}
