"use client";

import { useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  MessagesSquare,
  CheckCircle2,
  Users,
  CalendarClock,
  ShieldCheck,
  ClipboardList,
  Video,
} from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PageHeader } from "@/components/layout/PageHeader";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { VerificationBadge } from "@/components/skills/VerificationBadge";
import { getPrimaryStudent, listSkills, type StudentSkill } from "@/lib/api/students";
import { listInterviewsByStudent } from "@/lib/api/interviews";
import { listDrives } from "@/lib/api/drives";
import { useApiResource } from "@/hooks/useApiResource";
import type { Student, Drive, InterviewSlot, SkillGap } from "@/types";

/** Generic project prompt, used when the student has no recorded project to name. */
const PROJECT_PROMPT = "Walk through a project you owned end to end.";

/**
 * Company-agnostic behavioural prompts shown as general preparation guidance. This is static UI
 * copy (the same for every drive), not per-student business data fetched from the server;
 * buildBehaviouralPrompts prepends profile-derived prompts when the record provides them.
 */
const BEHAVIOURAL_PROMPTS = [
  "Describe a time you debugged something under pressure.",
  "How do you handle disagreement within a team?",
  "Why this role, and why this company?",
  "Tell me about a time you learned an unfamiliar technology quickly.",
];

/** en-GB short month names as produced by the interview mapper ("28 Sept 2026"). */
const MONTH_INDEX: Record<string, number> = {
  Jan: 0, Feb: 1, Mar: 2, Apr: 3, May: 4, Jun: 5,
  Jul: 6, Aug: 7, Sep: 8, Sept: 8, Oct: 9, Nov: 10, Dec: 11,
};

/**
 * Whole calendar days from today until a mapper-formatted date ("28 Sept 2026"), or null when the
 * label is unparsable or already past — the countdown only ever looks forward.
 */
function daysUntil(dateLabel: string): number | null {
  const parts = dateLabel.split(" ");
  const d = Number(parts[0]);
  const m = MONTH_INDEX[parts[1] ?? ""];
  const y = Number(parts[2]);
  if (!d || m === undefined || !y) return null;
  const today = new Date();
  today.setHours(0, 0, 0, 0);
  const diff = Math.round((new Date(y, m, d).getTime() - today.getTime()) / 86_400_000);
  return diff >= 0 ? diff : null;
}

/**
 * Prep prompts composed from the student's own record — their top project and a verified skill —
 * ahead of the company-agnostic fallbacks. Real profile data only, never invented specifics.
 */
function buildBehaviouralPrompts(student: Student | null, skills: StudentSkill[]): string[] {
  const prompts: string[] = [];
  const topProject = student?.projects[0];
  prompts.push(
    topProject
      ? `Walk through "${topProject.name}" end to end — expect follow-ups on every stack choice.`
      : PROJECT_PROMPT,
  );
  const verified = skills.find((s) => s.verificationStatus === "VERIFIED");
  if (verified) {
    prompts.push(
      `Expect depth questions on ${verified.skill} — it's verified on your profile, so interviewers will probe it.`,
    );
  }
  prompts.push(...BEHAVIOURAL_PROMPTS);
  return prompts;
}

export default function InterviewPrepPage() {
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const student = await getPrimaryStudent();
      const [interviews, drives, skills] = await Promise.all([
        listInterviewsByStudent(student.id),
        listDrives(),
        listSkills().catch(() => [] as StudentSkill[]),
      ]);
      return { student, interviews, drives, skills };
    },
    [],
    {
      student: null as Student | null,
      interviews: [] as InterviewSlot[],
      drives: [] as Drive[],
      skills: [] as StudentSkill[],
    },
  );

  const { student, interviews, drives, skills } = data;
  const driveById = new Map(drives.map((d) => [d.id, d] as const));

  // Only drives the student actually has scheduled/completed interview rounds for.
  const driveIds = Array.from(new Set(interviews.map((i) => i.driveId)));
  const [selectedDriveId, setSelectedDriveId] = useState("");
  const activeDriveId = selectedDriveId || driveIds[0] || "";

  const driveInterviews = interviews.filter((i) => i.driveId === activeDriveId);
  const drive = driveById.get(activeDriveId);
  const companyName =
    drive?.companyName ?? driveInterviews[0]?.companyName ?? "This drive";
  const role = drive?.role ?? "";

  // Real progress: rounds cleared out of rounds still live for this drive — a cancelled round
  // doesn't count against the student.
  const liveRounds = driveInterviews.filter((i) => i.status !== "CANCELLED");
  const totalRounds = liveRounds.length;
  const clearedRounds = liveRounds.filter((i) => i.result === "CLEARED").length;
  const progress = totalRounds ? Math.round((clearedRounds / totalRounds) * 100) : 0;
  const nextInterview = driveInterviews.find((i) => i.status === "SCHEDULED");
  const countdown = nextInterview ? daysUntil(nextInterview.date) : null;
  const aptitudeNext = nextInterview?.round.toLowerCase().includes("aptitude") ?? false;

  // Technical focus derived from the drive's required skills vs the student's live strengths,
  // enriched with the student's recorded skill verification and skill-gap actions.
  const requiredSkills = drive?.requiredSkills ?? [];
  const strengths = student?.strengths ?? [];
  const strongSkills = requiredSkills.filter((s) => strengths.includes(s));
  const focusSkills = requiredSkills.filter((s) => !strengths.includes(s));
  const skillStatus = new Map(
    skills.map((k) => [k.skill.toLowerCase(), k.verificationStatus] as const),
  );
  const gapBySkill = new Map(
    (student?.skillGaps ?? []).map((g) => [g.skill.toLowerCase(), g] as const),
  );
  const focusGapActions = focusSkills
    .map((s) => gapBySkill.get(s.toLowerCase()))
    .filter((g): g is SkillGap => Boolean(g));
  const unverifiedListed = focusSkills.filter((s) => {
    const status = skillStatus.get(s.toLowerCase());
    return status !== undefined && status !== "VERIFIED";
  });

  const behaviouralPrompts = buildBehaviouralPrompts(student, skills);

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Growth"
        title="Interview Preparation"
        subtitle="A focused preparation plan for the drives you have interviews scheduled for."
        actions={
          <div className="flex flex-wrap items-center gap-2">
            {driveIds.length > 0 && (
              <Select value={activeDriveId} onValueChange={setSelectedDriveId}>
                <SelectTrigger
                  className="w-full min-w-0 sm:w-60"
                  aria-label="Select drive to prepare for"
                  title={`${companyName}${role ? ` — ${role}` : ""}`}
                >
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {driveIds.map((id) => {
                    const d = driveById.get(id);
                    const label = d
                      ? `${d.companyName}${d.role ? ` — ${d.role}` : ""}`
                      : interviews.find((i) => i.driveId === id)?.companyName ?? id;
                    return (
                      <SelectItem key={id} value={id}>
                        {label}
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
            )}
            <Button asChild variant="glow" size="sm">
              <Link href={aptitudeNext ? "/student/aptitude" : "/student/mock-interview"}>
                {aptitudeNext ? (
                  <>
                    <ClipboardList className="mr-1.5 h-4 w-4" /> Practice Aptitude
                  </>
                ) : (
                  <>
                    <Video className="mr-1.5 h-4 w-4" /> AI Mock Interview
                  </>
                )}
              </Link>
            </Button>
          </div>
        }
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={driveIds.length === 0}
        onRetry={reload}
        emptyTitle="No interviews scheduled"
        emptyMessage="Once you have an interview round scheduled for a drive, your preparation plan appears here."
      >
        <div className="grid gap-5 lg:grid-cols-[auto_1fr]">
          <Card className="flex h-full flex-col items-center justify-center gap-4 p-8">
            <p className="text-xs font-medium uppercase tracking-wider text-muted-foreground">
              Rounds Cleared
            </p>
            <ScoreRing
              value={progress}
              size={168}
              strokeWidth={12}
              label={`${clearedRounds}/${totalRounds}`}
              sublabel={companyName}
              animateKey={progress}
            />
            {nextInterview ? (
              <p className="flex items-center gap-1.5 text-center text-xs text-muted-foreground">
                <CalendarClock className="h-3.5 w-3.5 text-violet-bright" />
                {nextInterview.round} on {nextInterview.date} at {nextInterview.time}
                {countdown !== null && (
                  <span className="font-medium text-violet-bright">
                    ·{" "}
                    {countdown === 0
                      ? "today"
                      : countdown === 1
                        ? "tomorrow"
                        : `in ${countdown} days`}
                  </span>
                )}
              </p>
            ) : (
              <p className="text-center text-xs text-muted-foreground">
                No upcoming round scheduled.
              </p>
            )}
          </Card>

          <Card className="p-5">
            <CardTitle className="mb-1">Technical Focus</CardTitle>
            <p className="mb-4 text-xs text-muted-foreground">
              Based on {companyName}
              {role ? ` (${role})` : ""}&apos;s required skills, checked against the skills on your
              profile.
            </p>
            {requiredSkills.length === 0 ? (
              <p className="text-sm text-muted-foreground">
                This drive lists no required skills to prepare against.
              </p>
            ) : (
              <div className="space-y-4">
                <div>
                  <p className="mb-2 flex items-center gap-1.5 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    <ShieldCheck className="h-3.5 w-3.5 text-success" /> Already a strength
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {strongSkills.length > 0 ? (
                      strongSkills.map((s) => (
                        <span key={s} className="inline-flex items-center gap-1.5">
                          <Badge variant="success">{s}</Badge>
                          <VerificationBadge status={skillStatus.get(s.toLowerCase())} />
                        </span>
                      ))
                    ) : (
                      <span className="text-sm text-muted-foreground">
                        None of the required skills are on your profile yet.
                      </span>
                    )}
                  </div>
                </div>
                <div>
                  <p className="mb-2 text-xs font-semibold uppercase tracking-wider text-muted-foreground">
                    Focus before the interview
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {focusSkills.length > 0 ? (
                      focusSkills.map((s) => (
                        <Badge key={s} variant="risk">
                          {s}
                        </Badge>
                      ))
                    ) : (
                      <span className="text-sm text-success">
                        You already list every required skill — polish and revise.
                      </span>
                    )}
                  </div>
                  {(focusGapActions.length > 0 || unverifiedListed.length > 0) && (
                    <ul className="mt-2.5 space-y-1.5">
                      {focusGapActions.map((g) => (
                        <li key={g.skill} className="text-xs text-muted-foreground">
                          <span className="font-medium text-foreground">{g.skill}:</span>{" "}
                          {g.recommendedAction}
                        </li>
                      ))}
                      {unverifiedListed.map((s) => (
                        <li key={s} className="text-xs text-muted-foreground">
                          <span className="font-medium text-foreground">{s}</span> is on your
                          profile unverified — a skill viva makes it a stronger signal.
                        </li>
                      ))}
                    </ul>
                  )}
                </div>
              </div>
            )}
          </Card>
        </div>

        <DataReveal stagger className="mt-5 grid gap-5 md:grid-cols-2">
          <DataRevealItem>
            <DepthCard className="h-full p-5">
              <div className="mb-3 flex items-center gap-2">
                <Users className="h-4 w-4 text-violet-bright" />
                <CardTitle>Behavioural Prep</CardTitle>
              </div>
              <ul className="space-y-2">
                {behaviouralPrompts.map((b) => (
                  <li key={b} className="flex gap-2 text-sm text-muted-foreground">
                    <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-violet-bright" /> {b}
                  </li>
                ))}
              </ul>
            </DepthCard>
          </DataRevealItem>
          <DataRevealItem>
            <DepthCard className="h-full p-5">
              <div className="mb-3 flex items-center gap-2">
                <MessagesSquare className="h-4 w-4 text-violet-bright" />
                <CardTitle>Your Rounds</CardTitle>
              </div>
              <ol className="space-y-2.5">
                {driveInterviews.map((iv, i) => (
                  <motion.li
                    key={iv.id}
                    initial={{ opacity: 0, x: -6 }}
                    animate={{ opacity: 1, x: 0 }}
                    transition={{ delay: i * 0.04 }}
                    className={`flex items-center justify-between gap-2.5 text-sm ${
                      iv.status === "CANCELLED" ? "opacity-60" : ""
                    }`}
                  >
                    <span className="min-w-0">
                      <span className="font-medium">{iv.round}</span>
                      <span className="text-muted-foreground">
                        {" "}
                        · {iv.date} at {iv.time}
                      </span>
                    </span>
                    <Badge
                      variant={
                        iv.result === "CLEARED"
                          ? "success"
                          : iv.result === "NOT CLEARED"
                            ? "risk"
                            : "muted"
                      }
                      className="shrink-0"
                    >
                      {iv.status === "COMPLETED" ? iv.result : iv.status}
                    </Badge>
                  </motion.li>
                ))}
              </ol>
            </DepthCard>
          </DataRevealItem>
        </DataReveal>

        <Alert className="mt-5">
          <MessagesSquare />
          <AlertDescription>
            Your rounds and technical focus are drawn from your scheduled interviews and profile
            skills. Behavioural prompts blend your own projects and verified skills with general
            guidance.
          </AlertDescription>
        </Alert>
      </AsyncBoundary>
    </div>
  );
}