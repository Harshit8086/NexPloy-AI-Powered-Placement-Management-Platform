import { SkillBar } from "@/components/intelligence/SkillBar";
import type { MatchBreakdown as MatchBreakdownType } from "@/types";

const LABELS: { key: keyof MatchBreakdownType; label: string }[] = [
  { key: "eligibility", label: "Eligibility" },
  { key: "skills", label: "Skills" },
  { key: "semanticMatch", label: "Semantic Match" },
  { key: "projects", label: "Projects" },
  { key: "interview", label: "Interview" },
  { key: "readiness", label: "Readiness" },
  { key: "academics", label: "Academics" },
  { key: "certificationRelevance", label: "Certifications" },
  { key: "pastInterviews", label: "Past Interviews" },
];

export function MatchBreakdownBars({ breakdown }: { breakdown: MatchBreakdownType }) {
  return (
    <div className="space-y-3">
      {LABELS.map(({ key, label }) => (
        <SkillBar key={key} label={label} value={breakdown[key]} colorClass="bg-violet" />
      ))}
    </div>
  );
}
