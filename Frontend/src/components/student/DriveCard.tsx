import Link from "next/link";
import { MapPin, IndianRupee, Calendar, Users, ShieldCheck, ShieldX } from "lucide-react";
import { Badge } from "@/components/ui/badge";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { AnimatedMetric } from "@/components/intelligence/AnimatedMetric";
import { cn } from "@/lib/utils";
import { applicationStatusLabel, applicationStatusTone, driveStatusTone } from "@/lib/status";
import type { Drive, ApplicationStatus } from "@/types";

const HIGH_MATCH_THRESHOLD = 85;

export function DriveCard({
  drive,
  matchPct,
  status,
  eligible,
}: {
  drive: Drive;
  matchPct?: number;
  status?: ApplicationStatus;
  /** Deterministic verdict from src/lib/eligibility.ts — never inferred here. */
  eligible: boolean;
}) {
  const highMatch = matchPct !== undefined && matchPct >= HIGH_MATCH_THRESHOLD;

  return (
    <Link href={`/student/drives/${drive.id}`} className="block h-full">
      <DepthCard interactive className={cn("h-full p-5", highMatch && "border-violet/40 shadow-glow")}>
        <div className="flex items-start justify-between gap-2">
          <div className="flex min-w-0 items-center gap-3">
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-violet/10 text-sm font-bold text-violet-bright">
              {drive.logoInitial}
            </div>
            <div className="min-w-0">
              <p className="truncate text-sm font-semibold">{drive.companyName}</p>
              <p className="truncate text-xs text-muted-foreground">{drive.role}</p>
            </div>
          </div>
          {matchPct !== undefined && (
            <span
              className={cn(
                "shrink-0 text-lg font-semibold tabular-nums",
                highMatch ? "text-violet-bright" : "text-foreground",
              )}
            >
              <AnimatedMetric value={matchPct} suffix="%" />
            </span>
          )}
        </div>

        <div className="mt-3 flex flex-wrap gap-1.5">
          <Badge variant={driveStatusTone[drive.status]}>{drive.status}</Badge>
          <Badge variant="outline">{drive.driveType}</Badge>
        </div>

        <div className="mt-3 flex flex-wrap gap-1.5">
          {drive.requiredSkills.slice(0, 4).map((s) => (
            <Badge key={s} variant="muted">
              {s}
            </Badge>
          ))}
        </div>

        <div className="mt-4 space-y-1.5 text-xs text-muted-foreground">
          <div className="flex items-center gap-1.5">
            <MapPin className="h-3 w-3" /> {drive.location}
          </div>
          <div className="flex items-center gap-1.5">
            <IndianRupee className="h-3 w-3" /> {drive.package}
          </div>
          <div className="flex items-center gap-1.5">
            <Calendar className="h-3 w-3" /> Apply by {drive.deadline}
          </div>
          <div className="flex items-center gap-1.5">
            <Users className="h-3 w-3" /> {drive.expectedHiring} expected hires
          </div>
        </div>

        <div className="mt-4 flex items-center justify-between gap-2">
          {status && status !== "eligible" ? (
            <Badge variant={applicationStatusTone[status]}>{applicationStatusLabel[status]}</Badge>
          ) : (
            <Badge variant={eligible ? "success" : "risk"} className="gap-1">
              {eligible ? <ShieldCheck className="h-3 w-3" /> : <ShieldX className="h-3 w-3" />}
              {eligible ? "ELIGIBLE" : "NOT ELIGIBLE"}
            </Badge>
          )}
        </div>
      </DepthCard>
    </Link>
  );
}
