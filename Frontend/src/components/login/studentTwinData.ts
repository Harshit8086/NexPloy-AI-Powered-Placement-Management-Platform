import { showcaseStudent, showcaseTopMatch } from "@/components/landing/showcase-data";

// Read-model for the Student sign-in visual. Illustrative showcase data
// (labelled "Demo Data" in the UI) — see components/landing/showcase-data.
export const studentTwin = {
  name: showcaseStudent.name,
  branchCode: showcaseStudent.branchCode,
  graduationYear: showcaseStudent.graduationYear,
  cgpa: showcaseStudent.cgpa,
  backlogs: showcaseStudent.backlogs,
  skills: showcaseStudent.skillsLabel,
  projects: showcaseStudent.projectsHeadline,
  certifications: showcaseStudent.certificationsHeadline,
  readiness: showcaseStudent.readiness,
  probability: showcaseStudent.placementProbability,
  topMatchRole: showcaseTopMatch.role,
  topMatchScore: showcaseTopMatch.fit,
  topMatchCompany: showcaseTopMatch.company,
  gaps: showcaseStudent.criticalGaps,
};
