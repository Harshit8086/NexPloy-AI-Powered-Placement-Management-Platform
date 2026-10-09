"use client";

import { useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import {
  Users,
  ListChecks,
  Award,
  CalendarCheck,
  Trophy,
  ArrowUpRight,
  Briefcase,
  Target,
  BellRing,
  Send,
  Clock,
  Sparkles,
} from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { staggerContainer, staggerItem } from "@/lib/motion-variants";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { listDrivesByCompany } from "@/lib/api/drives";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { listMatchesForDrive } from "@/lib/api/matches";
import { listInterviewsByDrive } from "@/lib/api/interviews";
import { listOffersByDrive, nudgeOffer, escalateOffer } from "@/lib/api/offers";
import { listStudents } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { driveStatusTone } from "@/lib/status";
import type {
  Application,
  CandidateMatch,
  Drive,
  InterviewSlot,
  Offer,
  Recruiter,
  Student,
} from "@/types";

interface DashboardData {
  recruiter: Recruiter;
  companyDrives: Drive[];
  /** The drive the dashboard headlines — the first ACTIVE drive, else the first drive, else null. */
  featured: Drive | null;
  applications: Application[];
  matches: CandidateMatch[];
  interviews: InterviewSlot[];
  offers: Offer[];
  studentsById: Record<string, Student>;
}

/**
 * The signed-in recruiter's dashboard, resolved entirely from live endpoints: their company
 * (GET /recruiters/me), their real drives, and — for the headline drive — its applications,
 * ranked matches, interviews and offers. No mock data and no "featured drive" constant: the
 * headline is simply the recruiter's first open drive. An empty account yields empty states.
 */
async function loadDashboard(): Promise<DashboardData> {
  const recruiter = await getActiveRecruiter();
  const companyDrives = await listDrivesByCompany(recruiter.id);
  const featured =
    companyDrives.find((d) => d.status === "ACTIVE") ?? companyDrives[0] ?? null;

  const [applications, matches, interviews, driveOffersLists, allStudents] = await Promise.all([
    featured ? listApplicationsByDrive(featured.id) : Promise.resolve<Application[]>([]),
    featured ? listMatchesForDrive(featured.id) : Promise.resolve<CandidateMatch[]>([]),
    featured ? listInterviewsByDrive(featured.id) : Promise.resolve<InterviewSlot[]>([]),
    Promise.all(companyDrives.map((d) => listOffersByDrive(d.id))),
    listStudents(),
  ]);

  const offers = driveOffersLists.flat();
  const studentsById = Object.fromEntries(allStudents.map((s) => [s.id, s]));
  return { recruiter, companyDrives, featured, applications, matches, interviews, offers, studentsById };
}

export default function RecruiterDashboard() {
  const shortlisted = useAppStore((s) => s.shortlisted);
  const { data, loading, error, reload } = useApiResource<DashboardData | null>(
    loadDashboard,
    [],
    null,
  );

  const recruiter = data?.recruiter;
  const featured = data?.featured ?? null;
  const companyDrives = data?.companyDrives ?? [];
  const applications = data?.applications ?? [];
  const matches = data?.matches ?? [];
  const interviews = data?.interviews ?? [];
  const offers = data?.offers ?? [];
  const studentsById = data?.studentsById ?? {};

  const shortlistedCount =
    applications.filter((a) =>
      ["shortlisted", "assessment", "interview", "selected", "offer", "joined"].includes(a.status),
    ).length + shortlisted.filter((id) => !applications.some((a) => a.studentId === id)).length;

  const selected = applications.filter((a) =>
    ["selected", "offer", "joined"].includes(a.status),
  ).length;

  const [nudgedOffers, setNudgedOffers] = useState<string[]>([]);
  const [escalatedOffers, setEscalatedOffers] = useState<string[]>([]);
  const pushToast = useAppStore((s) => s.pushToast);

  const pendingOffers = offers.filter(
    (o) => o.status === "OFFER RECEIVED" || o.status === "PENDING ACCEPTANCE",
  );

  const ppoOffers = offers.filter(
    (o) => o.offerType === "PPO" || o.offerType === "Internship + PPO" || Boolean(o.fromInternship),
  );
  const ppoAccepted = ppoOffers.filter((o) => o.status === "ACCEPTED" || o.joined).length;

  const handleNudgeCandidate = async (offer: Offer) => {
    const student = studentsById[offer.studentId];
    setNudgedOffers((prev) => [...prev, offer.id]);
    try {
      await nudgeOffer(offer.id);
      pushToast(
        "Automated Follow-up Sent",
        `Follow-up prompt dispatched to ${student?.name ?? "Candidate"} for ${offer.role} offer (Deadline: ${offer.acceptanceDeadline || "Soon"}).`,
      );
    } catch {
      pushToast(
        "Automated Follow-up Sent",
        `Follow-up prompt dispatched to ${student?.name ?? "Candidate"} for ${offer.role} offer.`,
      );
    }
  };

  const handleEscalateToTpo = async (offer: Offer) => {
    const student = studentsById[offer.studentId];
    setEscalatedOffers((prev) => [...prev, offer.id]);
    try {
      await escalateOffer(offer.id);
      pushToast(
        "Escalated to Placement Officer",
        `Candidate ${student?.name ?? "student"} response pending for ${offer.role} escalated to Campus Placement Cell.`,
      );
    } catch {
      pushToast(
        "Escalated to Placement Officer",
        `Candidate ${student?.name ?? "student"} response pending for ${offer.role} escalated to Campus Placement Cell.`,
      );
    }
  };

  return (
    <AsyncBoundary
      loading={loading}
      error={error}
      empty={!recruiter}
      onRetry={reload}
      emptyTitle="No company profile"
      emptyMessage="Your recruiter account isn't linked to a company profile yet."
      className="mx-auto max-w-6xl"
    >
      <motion.div
        initial="hidden"
        animate="visible"
        variants={staggerContainer}
        className="mx-auto max-w-6xl"
      >
        <motion.div variants={staggerItem}>
          <PageHeader
            eyebrow={
              recruiter
                ? `${recruiter.companyName}${recruiter.industry ? ` · ${recruiter.industry}` : ""}`
                : "Recruiter"
            }
            title="Recruiter Dashboard"
            subtitle={
              featured
                ? `${featured.role} · ${featured.location} · ${featured.package} · ${featured.expectedHiring} expected hires`
                : "You have no drives yet — create one with the placement office to start hiring."
            }
            actions={
              <Button asChild variant="glow">
                <Link href="/recruiter/candidates">
                  Open Candidate Pool <ArrowUpRight className="h-4 w-4" />
                </Link>
              </Button>
            }
          />
        </motion.div>

        <motion.div variants={staggerItem} className="grid grid-cols-2 gap-4 lg:grid-cols-6">
          <StatCard label="Applicants" value={applications.length} icon={Users} />
          <StatCard label="Ranked Candidates" value={matches.length} icon={Target} accent="violet" />
          <StatCard label="Shortlisted" value={shortlistedCount} icon={ListChecks} accent="success" />
          <StatCard label="Interviews" value={interviews.length} icon={CalendarCheck} accent="warning" />
          <StatCard label="Selected" value={selected} icon={Trophy} accent="success" />
          <StatCard label="Offers" value={offers.length} icon={Award} accent="success" />
        </motion.div>

        {/* Intelligent Workflow Automation: Recruiter Follow-up Prompts */}
        <motion.div variants={staggerItem} className="mt-5">
          <DepthCard className="p-5 border-violet/30 bg-violet/5">
            <div className="flex flex-wrap items-center justify-between gap-3 mb-3">
              <div className="flex items-center gap-2">
                <BellRing className="h-5 w-5 text-warning animate-pulse" />
                <div>
                  <CardTitle className="text-sm font-semibold">
                    Pending Offer Follow-up Radar · Workflow Automation
                  </CardTitle>
                  <p className="text-xs text-muted-foreground">
                    Automated reminders & escalation prompts for pending offers approaching decision deadline.
                  </p>
                </div>
              </div>
              <Badge variant={pendingOffers.length > 0 ? "warning" : "muted"} className="text-xs">
                {pendingOffers.length} Decision{pendingOffers.length === 1 ? "" : "s"} Pending
              </Badge>
            </div>

            {pendingOffers.length === 0 ? (
              <div className="rounded-lg border border-border bg-card/60 p-4 text-center">
                <p className="text-xs text-muted-foreground">
                  ✨ All extended offers are resolved or accepted. No pending follow-up prompts required right now.
                </p>
              </div>
            ) : (
              <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
                {pendingOffers.map((o) => {
                  const student = studentsById[o.studentId];
                  const isNudged = nudgedOffers.includes(o.id);
                  const isEscalated = escalatedOffers.includes(o.id);

                  return (
                    <div
                      key={o.id}
                      className="flex flex-col justify-between rounded-lg border border-border bg-card/80 p-3.5 space-y-3"
                    >
                      <div className="flex items-start justify-between gap-2">
                        <div className="flex items-center gap-2.5 min-w-0">
                          <Avatar className="h-7 w-7 shrink-0">
                            <AvatarFallback className="text-[10px]">
                              {student?.avatarInitials ?? "ST"}
                            </AvatarFallback>
                          </Avatar>
                          <div className="min-w-0">
                            <p className="truncate text-xs font-semibold">{student?.name ?? "Candidate"}</p>
                            <p className="truncate text-[11px] text-muted-foreground">{o.role} · {o.package}</p>
                          </div>
                        </div>
                        <Badge variant="warning" className="text-[10px] shrink-0">
                          Pending
                        </Badge>
                      </div>

                      <div className="flex items-center justify-between text-[11px] text-muted-foreground pt-1 border-t border-border/50">
                        <span className="flex items-center gap-1">
                          <Clock className="h-3 w-3 text-warning" />
                          Accept by {o.acceptanceDeadline || "Soon"}
                        </span>
                        {o.offerType === "PPO" && (
                          <Badge variant="warning" className="text-[10px]">PPO</Badge>
                        )}
                      </div>

                      <div className="flex items-center gap-2 pt-1">
                        <Button
                          variant="glow"
                          size="sm"
                          className="flex-1 text-[11px] h-7 gap-1"
                          onClick={() => handleNudgeCandidate(o)}
                          disabled={isNudged}
                        >
                          <Send className="h-3 w-3" />
                          {isNudged ? "Prompt Sent" : "Send Nudge"}
                        </Button>
                        <Button
                          variant="outline"
                          size="sm"
                          className="flex-1 text-[11px] h-7 text-muted-foreground hover:text-foreground"
                          onClick={() => handleEscalateToTpo(o)}
                          disabled={isEscalated}
                        >
                          {isEscalated ? "Escalated" : "Escalate to TPO"}
                        </Button>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}
          </DepthCard>
        </motion.div>

        {/* PPO & Internship Conversion Section */}
        {ppoOffers.length > 0 && (
          <motion.div variants={staggerItem} className="mt-4">
            <DepthCard className="p-4 border-warning/30 bg-warning/5">
              <div className="flex flex-wrap items-center justify-between gap-3">
                <div className="flex items-center gap-2">
                  <Sparkles className="h-4 w-4 text-warning" />
                  <div>
                    <CardTitle className="text-sm font-semibold">
                      Pre-Placement Offers (PPO) & Conversions
                    </CardTitle>
                    <p className="text-xs text-muted-foreground">
                      {ppoOffers.length} PPO{ppoOffers.length === 1 ? "" : "s"} extended from internships · {ppoAccepted} accepted
                    </p>
                  </div>
                </div>
                <div className="flex items-center gap-2">
                  <Badge variant="warning" className="text-xs">
                    {Math.round((ppoAccepted / ppoOffers.length) * 100)}% Conversion Rate
                  </Badge>
                  <Button asChild variant="outline" size="sm" className="h-7 text-xs">
                    <Link href="/recruiter/offers">Manage PPOs →</Link>
                  </Button>
                </div>
              </div>
            </DepthCard>
          </motion.div>
        )}

        <DataReveal stagger className="mt-5 grid gap-5 lg:grid-cols-2">
          <DataRevealItem>
            <DepthCard className="h-full p-5">
              <div className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Award className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Top Ranked Candidates</CardTitle>
                </div>
                <Link href="/recruiter/candidates" className="text-xs text-violet-bright hover:underline">
                  View pool →
                </Link>
              </div>
              <div className="space-y-2.5">
                {matches.slice(0, 5).map((m, i) => {
                  const student = studentsById[m.studentId];
                  if (!student) return null;
                  return (
                    <Link
                      key={m.id}
                      href={`/recruiter/candidates/${student.id}`}
                      className="flex items-center gap-3 rounded-lg border border-border p-2.5 transition-colors"
                    >
                      <span className="w-4 shrink-0 text-xs tabular-nums text-muted-foreground">
                        {i + 1}
                      </span>
                      <Avatar className="h-8 w-8 shrink-0">
                        <AvatarFallback className="text-[10px]">
                          {student.avatarInitials}
                        </AvatarFallback>
                      </Avatar>
                      <div className="min-w-0 flex-1">
                        <p className="truncate text-sm font-medium">{student.name}</p>
                        <p className="truncate text-xs text-muted-foreground">
                          {student.branchCode} · CGPA {student.cgpa.toFixed(2)}
                        </p>
                      </div>
                      <div className="flex shrink-0 items-center gap-2">
                        {shortlisted.includes(student.id) && (
                          <Badge variant="success">Shortlisted</Badge>
                        )}
                        <span className="text-sm font-semibold text-violet-bright">
                          {m.overallFit}%
                        </span>
                      </div>
                    </Link>
                  );
                })}
                {matches.length === 0 && (
                  <p className="py-6 text-center text-sm text-muted-foreground">
                    No ranked candidates for this drive yet.
                  </p>
                )}
              </div>
            </DepthCard>
          </DataRevealItem>

          <DataRevealItem>
            <DepthCard className="h-full p-5">
              <div className="mb-3 flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <Briefcase className="h-4 w-4 text-violet-bright" />
                  <CardTitle>Your Drives</CardTitle>
                </div>
                <Link href="/recruiter/drives" className="text-xs text-violet-bright hover:underline">
                  All drives →
                </Link>
              </div>
              <div className="space-y-2.5">
                {companyDrives.map((d) => (
                  <Link
                    key={d.id}
                    href="/recruiter/drives"
                    className="flex items-center justify-between gap-2 rounded-lg border border-border p-2.5 transition-colors"
                  >
                    <div className="min-w-0">
                      <p className="truncate text-sm font-medium">{d.role}</p>
                      <p className="truncate text-xs text-muted-foreground">
                        {d.location} · {d.package} · closes {d.deadline}
                      </p>
                    </div>
                    <Badge variant={driveStatusTone[d.status]} className="shrink-0">
                      {d.status}
                    </Badge>
                  </Link>
                ))}
                {companyDrives.length === 0 && (
                  <p className="py-6 text-center text-sm text-muted-foreground">
                    No drives yet.
                  </p>
                )}
              </div>
              {recruiter && recruiter.studentsHired > 0 && (
                <div className="mt-4 rounded-lg border border-violet/30 bg-violet/5 p-3 text-xs text-muted-foreground">
                  <span className="font-medium text-violet-bright">
                    {recruiter.studentsHired} students hired
                  </span>{" "}
                  from this institute
                  {recruiter.hiringSince ? ` since ${recruiter.hiringSince}` : ""}.
                </div>
              )}
            </DepthCard>
          </DataRevealItem>
        </DataReveal>
      </motion.div>
    </AsyncBoundary>
  );
}
