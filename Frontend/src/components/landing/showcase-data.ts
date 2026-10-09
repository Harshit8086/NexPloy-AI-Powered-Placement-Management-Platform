// ============================================================
// STATIC SHOWCASE DATA — pre-auth landing & login visuals ONLY
//
// These marketing/illustrative visuals (landing hero, flow map, sign-in
// centrepieces) are a product demo, NOT authenticated business-data views.
// Every figure below is a fixed illustrative constant and is clearly
// labelled "Demo Data" in the UI (see DemoDataBadge). Nothing here is
// fetched, and nothing is presented as a real institutional statistic.
//
// The running application's authenticated pages never use this module —
// they always read live values from the backend and show empty/error
// states when the server has no data. This file exists purely so the
// pre-auth showcase stays internally consistent without importing the
// former mock business dataset.
//
// Values mirror the previous demo student (Rahul Sharma) and institute
// demo dataset so the visuals read coherently as one story.
// ============================================================

export const showcaseStudent = {
  name: "Rahul Sharma",
  branchCode: "CSE",
  graduationYear: 2027,
  cgpa: 8.59,
  backlogs: 0,
  avatarInitials: "RS",
  readiness: 78,
  placementProbability: 68,
  riskLevel: "LOW" as const,
  riskReason: "On Track",
  targetRole: "Backend Developer",
  criticalGaps: ["Docker", "AWS", "Communication"],
  // Real profile counts used by the flow map's "Student Intelligence" card.
  projectsCount: 3,
  strengthsCount: 5,
  primaryInterest: "Fintech",
  // Headline profile counts for the student sign-in twin (illustrative).
  skillsLabel: "15+",
  projectsHeadline: 5,
  certificationsHeadline: 7,
};

export const showcaseTopMatch = {
  company: "TechNova",
  role: "Backend Developer",
  fit: 91,
  // Flow-map fit label — the former "STRONG MATCH" recommendation rendered
  // as "High FIT" (MATCH→FIT, STRONG→High).
  fitLabel: "High FIT",
};

/**
 * Rahul's placement journey state for the flow map's recruiter-connection
 * steps (assessment → interview → offer all reached in the demo story).
 */
export const showcaseJourney = {
  assessmentDone: true,
  interviewDone: true,
  offerDone: true,
};

/** Active drives Rahul is eligible for in the demo dataset (7 of 9 active). */
export const showcaseEligibleDrives = 7;

// ---- Super Admin sign-in centrepiece ("Ecosystem Insight Dashboard") ----
export const adminShowcase = {
  campuses: 3,
  students: 3614,
  // Institute-wide placed/registered rate (1,925 placed of 3,614).
  placementRate: 53,
  // Cumulative offers across all campuses, month by month (Apr–Sep).
  trend: [61, 159, 281, 441, 589, 706],
  // Risk distribution summed across campuses.
  risk: [
    { level: "LOW" as const, count: 2691 },
    { level: "MEDIUM" as const, count: 656 },
    { level: "HIGH" as const, count: 267 },
  ],
};

// ---- Landing hero metrics strip ----
export const landingShowcaseMetrics = {
  students: 3614,
  // Main-campus headline placement rate used for the marketing strip.
  placementRate: 81,
  offers: 706,
  recruiters: 121,
};
