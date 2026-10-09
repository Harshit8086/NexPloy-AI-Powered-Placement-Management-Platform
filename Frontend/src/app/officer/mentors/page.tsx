"use client";

import { useMemo, useState } from "react";
import { HeartHandshake, Users, Search, UserPlus, Plus, ShieldCheck } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { listStudentsByCampus } from "@/lib/api/students";
import { listRiskEntriesByCampus } from "@/lib/api/risk";
import {
  createAssignment,
  listAssignmentsForMentor,
  listMentors,
  createMentorAccount,
  type MentorAccount,
} from "@/lib/api/mentors";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { ApiError } from "@/lib/api/client";
import { riskTone } from "@/lib/status";
import type { MentorAssignment, Student, RiskEntry } from "@/types";

export default function OfficerMentorsPage() {
  const campusId = useAppStore((s) => s.activeCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  const [mentorUsername, setMentorUsername] = useState("");
  const [studentId, setStudentId] = useState("");
  const [queried, setQueried] = useState("");
  const [busy, setBusy] = useState(false);

  // Create Mentor Dialog state
  const [createOpen, setCreateOpen] = useState(false);
  const [newEmail, setNewEmail] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [createBusy, setCreateBusy] = useState(false);
  const [createError, setCreateError] = useState<string | null>(null);

  // Campus students — the pool the officer can assign to a mentor.
  const {
    data: students,
    loading: studentsLoading,
    error: studentsError,
    reload: reloadStudents,
  } = useApiResource<Student[]>(() => listStudentsByCampus(campusId), [campusId], []);

  // Authoritative ML Risk entries for campus students
  const { data: campusRisk } = useApiResource<RiskEntry[]>(
    () => (campusId ? listRiskEntriesByCampus(campusId) : Promise.resolve([])),
    [campusId],
    [],
  );

  const riskByStudent = useMemo(() => {
    const map = new Map<string, string>();
    campusRisk.forEach((r) => map.set(r.studentId, r.level));
    return map;
  }, [campusRisk]);

  const getEffectiveRisk = (s: Student) => {
    return (riskByStudent.get(s.id) ?? s.riskLevel) as Student["riskLevel"];
  };

  // Registered mentors on this campus
  const {
    data: mentors,
    reload: reloadMentors,
  } = useApiResource<MentorAccount[]>(() => listMentors(campusId), [campusId], []);

  // A specific mentor's current coverage, fetched on demand when the officer looks one up.
  const {
    data: assignments,
    loading: assignmentsLoading,
    error: assignmentsError,
    reload: reloadAssignments,
  } = useApiResource<MentorAssignment[]>(
    () => (queried ? listAssignmentsForMentor(queried) : Promise.resolve([])),
    [queried],
    [],
  );

  const studentById = useMemo(() => {
    const map = new Map<string, Student>();
    students.forEach((s) => map.set(s.id, s));
    return map;
  }, [students]);

  const lookUp = (usernameToLookup?: string) => {
    const target = (usernameToLookup ?? mentorUsername).trim();
    if (!target) return;
    setQueried(target);
    if (usernameToLookup) {
      setMentorUsername(target);
    }
  };

  const handleCreateMentor = async () => {
    const email = newEmail.trim();
    const password = newPassword.trim();
    if (!email) {
      setCreateError("Please enter a valid email address.");
      return;
    }
    if (!password || password.length < 6) {
      setCreateError("Password must be at least 6 characters.");
      return;
    }

    setCreateBusy(true);
    setCreateError(null);
    try {
      const created = await createMentorAccount({
        email,
        password,
        campusId,
      });
      pushToast("Mentor Account Created", `Account created for ${created.username}. They can now sign in at /login/mentor.`);
      setNewEmail("");
      setNewPassword("");
      setCreateOpen(false);
      setMentorUsername(created.username);
      reloadMentors();
    } catch (err) {
      const message = err instanceof ApiError ? err.message : "Could not create mentor account.";
      setCreateError(message);
    } finally {
      setCreateBusy(false);
    }
  };

  const assign = async () => {
    const mentor = mentorUsername.trim();
    if (!mentor || !studentId || busy) return;
    setBusy(true);
    try {
      const created = await createAssignment(mentor, studentId);
      const student = studentById.get(studentId);
      pushToast(
        "Mentor assigned",
        `${student?.name ?? studentId} is now assigned to ${created.mentorUsername}.`,
      );
      setStudentId("");
      reloadMentors();
      if (queried === mentor) reloadAssignments();
      else setQueried(mentor);
    } catch (err) {
      const message =
        err instanceof ApiError
          ? err.message
          : "Couldn't assign the mentor. Check the username and try again.";
      pushToast("Assignment failed", message);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl space-y-5 pb-12">
      <PageHeader
        eyebrow="Support & Mentorship"
        title="Mentor Management & Assignment"
        subtitle="Create faculty mentor credentials, assign at-risk students, and track active mentorship caseloads."
        actions={
          <Button
            variant="glow"
            size="sm"
            onClick={() => {
              setCreateError(null);
              setCreateOpen(true);
            }}
          >
            <Plus className="h-4 w-4 mr-1.5" /> Create Mentor
          </Button>
        }
      />

      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <StatCard label="Registered Mentors" value={mentors.length} icon={ShieldCheck} accent="violet" />
        <StatCard label="Students on Campus" value={students.length} icon={Users} accent="violet" />
        <StatCard
          label={queried ? `${queried} · caseload` : "Active Caseload"}
          value={assignments.length}
          icon={HeartHandshake}
          accent="success"
        />
      </div>

      {/* Registered Mentors Quick Select Bar */}
      {mentors.length > 0 && (
        <DepthCard className="p-4">
          <div className="flex flex-wrap items-center justify-between gap-2 mb-2.5">
            <span className="text-xs font-semibold text-foreground">Registered Campus Mentors ({mentors.length}):</span>
            <span className="text-[11px] text-muted-foreground">Click any mentor to view caseload</span>
          </div>
          <div className="flex flex-wrap gap-2">
            {mentors.map((m) => (
              <button
                key={m.username}
                type="button"
                onClick={() => {
                  setMentorUsername(m.username);
                  lookUp(m.username);
                }}
                className={`inline-flex items-center gap-2 rounded-lg border px-3 py-1.5 text-xs transition-colors ${
                  mentorUsername === m.username
                    ? "border-violet bg-violet/10 text-violet-bright font-medium"
                    : "border-border/80 bg-muted/20 text-muted-foreground hover:text-foreground hover:bg-muted/40"
                }`}
              >
                <Avatar className="h-5 w-5">
                  <AvatarFallback className="text-[9px] bg-violet/20 text-violet-bright">
                    {m.username.substring(0, 2).toUpperCase()}
                  </AvatarFallback>
                </Avatar>
                <span>{m.username}</span>
                <Badge variant="muted" className="text-[10px] px-1.5 py-0 h-4 tabular-nums">
                  {m.activeAssignmentsCount} assigned
                </Badge>
              </button>
            ))}
          </div>
        </DepthCard>
      )}

      {/* Assign Mentor Card */}
      <DepthCard className="p-5">
        <div className="mb-3 flex items-center justify-between">
          <div className="flex items-center gap-2">
            <UserPlus className="h-4 w-4 text-violet-bright" />
            <CardTitle>Assign Student to Mentor</CardTitle>
          </div>
          <Button
            variant="ghost"
            size="sm"
            className="text-xs text-violet-bright hover:text-white h-auto p-0"
            onClick={() => {
              setCreateError(null);
              setCreateOpen(true);
            }}
          >
            + Need a new mentor account?
          </Button>
        </div>

        <AsyncBoundary
          loading={studentsLoading}
          error={studentsError}
          empty={students.length === 0}
          onRetry={reloadStudents}
          emptyTitle="No students"
          emptyMessage="No students on this campus yet, so there's nobody to assign."
        >
          <div className="grid gap-3 sm:grid-cols-[1fr_1fr_auto] sm:items-end">
            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">
                Mentor Username / Email
              </label>
              {mentors.length > 0 ? (
                <div className="flex gap-2">
                  <Select
                    value={mentors.some((m) => m.username === mentorUsername) ? mentorUsername : "custom"}
                    onValueChange={(val) => {
                      if (val !== "custom") {
                        setMentorUsername(val);
                        lookUp(val);
                      }
                    }}
                  >
                    <SelectTrigger className="w-full text-xs">
                      <SelectValue placeholder="Pick a mentor" />
                    </SelectTrigger>
                    <SelectContent>
                      {mentors.map((m) => (
                        <SelectItem key={m.username} value={m.username}>
                          {m.username} ({m.activeAssignmentsCount} assigned)
                        </SelectItem>
                      ))}
                      <SelectItem value="custom">Other / Type manually...</SelectItem>
                    </SelectContent>
                  </Select>
                </div>
              ) : (
                <Input
                  value={mentorUsername}
                  onChange={(e) => setMentorUsername(e.target.value)}
                  placeholder="e.g. mentor.anita@college.edu"
                  className="text-xs"
                />
              )}
              {mentors.length > 0 && !mentors.some((m) => m.username === mentorUsername) && (
                <Input
                  value={mentorUsername}
                  onChange={(e) => setMentorUsername(e.target.value)}
                  placeholder="Type mentor username..."
                  className="mt-2 text-xs"
                />
              )}
            </div>

            <div>
              <label className="mb-1 block text-xs font-medium text-muted-foreground">Student to Support</label>
              <Select value={studentId} onValueChange={setStudentId}>
                <SelectTrigger aria-label="Select student to assign" className="text-xs">
                  <SelectValue placeholder="Select student" />
                </SelectTrigger>
                <SelectContent>
                  {students.map((s) => {
                    const isHigh = getEffectiveRisk(s) === "HIGH";
                    return (
                      <SelectItem key={s.id} value={s.id}>
                        {s.name} · {s.rollNumber} {isHigh ? "⚠️ (High Risk)" : ""}
                      </SelectItem>
                    );
                  })}
                </SelectContent>
              </Select>
            </div>

            <Button
              variant="glow"
              onClick={assign}
              disabled={busy || !mentorUsername.trim() || !studentId}
            >
              <UserPlus className="h-4 w-4 mr-1.5" /> Assign Mentee
            </Button>
          </div>
          <p className="mt-2 text-[11px] text-muted-foreground">
            The mentor will immediately receive access to the student&apos;s academic profile, skill gaps, and intervention action plans upon login.
          </p>
        </AsyncBoundary>
      </DepthCard>

      {/* Caseload Review Card */}
      <Card className="p-5 border border-border/80 bg-card shadow-card">
        <div className="mb-4 flex flex-wrap items-end justify-between gap-3">
          <div>
            <h3 className="text-sm font-semibold text-foreground">Mentor Caseload &amp; Coverage</h3>
            <p className="text-xs text-muted-foreground">Review students assigned to a specific mentor.</p>
          </div>

          <div className="flex items-center gap-2">
            <Input
              value={mentorUsername}
              onChange={(e) => setMentorUsername(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && lookUp()}
              placeholder="Mentor username to inspect..."
              className="h-8.5 w-60 text-xs"
            />
            <Button variant="outline" size="sm" onClick={() => lookUp()} disabled={!mentorUsername.trim()} className="h-8.5 text-xs">
              <Search className="h-3.5 w-3.5 mr-1" /> Inspect
            </Button>
          </div>
        </div>

        {queried ? (
          <AsyncBoundary
            loading={assignmentsLoading}
            error={assignmentsError}
            empty={assignments.length === 0}
            onRetry={reloadAssignments}
            emptyTitle="No assigned students"
            emptyMessage={`${queried} has no students assigned on this campus yet.`}
          >
            <div className="overflow-x-auto">
              <Table>
                <TableHeader>
                  <TableRow>
                    <TableHead>Student</TableHead>
                    <TableHead>Readiness</TableHead>
                    <TableHead>Risk Level</TableHead>
                    <TableHead>Assigned Date</TableHead>
                  </TableRow>
                </TableHeader>
                <TableBody>
                  {assignments.map((a) => {
                    const student = studentById.get(a.studentId);
                    return (
                      <TableRow key={a.id}>
                        <TableCell>
                          <div className="flex items-center gap-2.5">
                            <Avatar className="h-7 w-7">
                              <AvatarFallback className="text-[10px] bg-violet/20 text-violet-bright">
                                {student?.avatarInitials ?? "?"}
                              </AvatarFallback>
                            </Avatar>
                            <div>
                              <span className="font-medium text-foreground block">{student?.name ?? a.studentId}</span>
                              <span className="text-[11px] text-muted-foreground">{student?.branch} · {student?.rollNumber}</span>
                            </div>
                          </div>
                        </TableCell>
                        <TableCell className="tabular-nums font-semibold">{student?.readiness ?? "—"}/100</TableCell>
                        <TableCell>
                          {student ? (
                            <Badge variant={riskTone[getEffectiveRisk(student)]}>
                              {getEffectiveRisk(student)}
                            </Badge>
                          ) : (
                            <span className="text-xs text-muted-foreground">—</span>
                          )}
                        </TableCell>
                        <TableCell className="text-muted-foreground text-xs">{a.assignedAt}</TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </div>
          </AsyncBoundary>
        ) : (
          <div className="rounded-xl border border-dashed border-border/80 p-8 text-center text-xs text-muted-foreground">
            Select a registered mentor above or enter a mentor username to review their current student caseload.
          </div>
        )}
      </Card>

      {/* Create Mentor Account Dialog Modal */}
      <Dialog open={createOpen} onOpenChange={setCreateOpen}>
        <DialogContent className="sm:max-w-md">
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <Plus className="h-4 w-4 text-violet-bright" />
              Create Faculty Mentor Account
            </DialogTitle>
            <DialogDescription className="text-xs">
              Provision login credentials for a mentor. They can log in directly at <code className="text-violet-bright">/login/mentor</code> to guide their assigned students.
            </DialogDescription>
          </DialogHeader>

          <div className="space-y-4 py-2">
            <div className="space-y-1.5">
              <Label className="text-xs text-muted-foreground">Mentor Email / Username</Label>
              <Input
                type="email"
                value={newEmail}
                onChange={(e) => setNewEmail(e.target.value)}
                placeholder="e.g. anita.mishra@college.edu"
                className="text-sm"
              />
            </div>

            <div className="space-y-1.5">
              <div className="flex items-center justify-between">
                <Label className="text-xs text-muted-foreground">Password</Label>
                <button
                  type="button"
                  onClick={() => setNewPassword("Mentor@" + Math.floor(100 + Math.random() * 900))}
                  className="text-[11px] text-violet-bright hover:underline"
                >
                  Generate sample
                </button>
              </div>
              <Input
                type="text"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                placeholder="Enter password (min 6 characters)"
                className="text-sm font-mono"
              />
            </div>

            <div className="rounded-lg border border-border/60 bg-muted/20 px-3 py-2 text-xs flex items-center justify-between">
              <span className="text-muted-foreground">Role Assigned:</span>
              <Badge variant="outline" className="border-violet/40 bg-violet/10 text-violet-bright">
                MENTOR
              </Badge>
            </div>

            {createError && (
              <p className="text-xs text-risk font-medium" role="alert">
                {createError}
              </p>
            )}
          </div>

          <DialogFooter className="gap-2 sm:gap-0">
            <Button variant="ghost" size="sm" onClick={() => setCreateOpen(false)} disabled={createBusy}>
              Cancel
            </Button>
            <Button variant="glow" size="sm" onClick={handleCreateMentor} disabled={createBusy}>
              {createBusy ? "Creating Account…" : "Create Mentor"}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
