"use client";

import Link from "next/link";
import { ArrowUpRight, ShieldCheck, ShieldX, CheckCircle2 } from "lucide-react";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Badge } from "@/components/ui/badge";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Button } from "@/components/ui/button";
import { listMatchesForDrive } from "@/lib/api/matches";
import { getDrive } from "@/lib/api/drives";
import { listApplicationsByDrive } from "@/lib/api/applications";
import { listStudents } from "@/lib/api/students";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import { applicationStatusLabel, applicationStatusTone } from "@/lib/status";
import { useAppStore } from "@/hooks/useAppStore";
import type { Application, CandidateMatch, Drive, Student } from "@/types";

export function CandidateTable({ driveId }: { driveId: string }) {
  const shortlisted = useAppStore((s) => s.shortlisted);

  const { data: matches } = useApiResource<CandidateMatch[]>(
    () => (driveId ? listMatchesForDrive(driveId) : Promise.resolve([])),
    [driveId],
    [],
  );
  const { data: drive } = useApiResource<Drive | undefined>(
    () => (driveId ? getDrive(driveId) : Promise.resolve(undefined)),
    [driveId],
    undefined,
  );
  const { data: applications } = useApiResource<Application[]>(
    () => (driveId ? listApplicationsByDrive(driveId) : Promise.resolve([])),
    [driveId],
    [],
  );
  const { data: allStudents } = useApiResource<Student[]>(() => listStudents(), [], []);

  if (!drive) return null;

  const studentById = new Map(allStudents.map((s) => [s.id, s]));
  const appByStudent = new Map(applications.map((a) => [a.studentId, a]));

  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Rank</TableHead>
          <TableHead>Candidate</TableHead>
          <TableHead>Branch</TableHead>
          <TableHead>CGPA</TableHead>
          <TableHead>Eligibility</TableHead>
          <TableHead>Readiness</TableHead>
          <TableHead>Fit</TableHead>
          <TableHead>Status</TableHead>
          <TableHead className="text-right">Action</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {matches.map((m, i) => {
          const student = studentById.get(m.studentId);
          if (!student) return null;
          const app = appByStudent.get(student.id);
          const eligible = evaluateEligibility(student, drive).eligible;
          const isShortlisted = shortlisted.includes(student.id);
          return (
            <TableRow key={m.id}>
              <TableCell className="tabular-nums text-muted-foreground">{i + 1}</TableCell>
              <TableCell>
                <div className="flex items-center gap-2.5">
                  <Avatar className="h-7 w-7">
                    <AvatarFallback className="text-[10px]">{student.avatarInitials}</AvatarFallback>
                  </Avatar>
                  <span className="font-medium">{student.name}</span>
                </div>
              </TableCell>
              <TableCell className="text-muted-foreground">{student.branchCode}</TableCell>
              <TableCell className="tabular-nums">{student.cgpa.toFixed(2)}</TableCell>
              <TableCell>
                <Badge variant={eligible ? "success" : "risk"} className="gap-1">
                  {eligible ? <ShieldCheck className="h-3 w-3" /> : <ShieldX className="h-3 w-3" />}
                  {eligible ? "PASS" : "FAIL"}
                </Badge>
              </TableCell>
              <TableCell className="tabular-nums">{student.readiness}</TableCell>
              <TableCell>
                <span className="font-semibold tabular-nums text-violet-bright">
                  {m.overallFit}%
                </span>
              </TableCell>
              <TableCell>
                {isShortlisted ? (
                  <Badge variant="success" className="gap-1">
                    <CheckCircle2 className="h-3 w-3" /> SHORTLISTED
                  </Badge>
                ) : app ? (
                  <Badge variant={applicationStatusTone[app.status]}>
                    {applicationStatusLabel[app.status]}
                  </Badge>
                ) : (
                  <span className="text-xs text-muted-foreground">Not applied</span>
                )}
              </TableCell>
              <TableCell className="text-right">
                <Button asChild variant="ghost" size="sm">
                  <Link href={`/recruiter/candidates/${student.id}`}>
                    View <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </Button>
              </TableCell>
            </TableRow>
          );
        })}
      </TableBody>
    </Table>
  );
}
