"use client";

import { useMemo, useState } from "react";
import { Search } from "lucide-react";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { PageHeader } from "@/components/layout/PageHeader";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { DriveCard } from "@/components/student/DriveCard";
import { getPrimaryStudent } from "@/lib/api/students";
import { listActiveDrives } from "@/lib/api/drives";
import { listApplicationsByStudent } from "@/lib/api/applications";
import { useApiResource } from "@/hooks/useApiResource";
import { evaluateEligibility } from "@/lib/eligibility";
import type { Application, Drive, Student } from "@/types";

const tabs = [
  { value: "all", label: "All Drives" },
  { value: "eligible", label: "Eligible" },
  { value: "applied", label: "Applied" },
  { value: "shortlisted", label: "Shortlisted" },
  { value: "not-eligible", label: "Not Eligible" },
];

export default function DrivesPage() {
  const {
    data: student,
    loading: studentLoading,
    error: studentError,
    reload: reloadStudent,
  } = useApiResource<Student | null>(() => getPrimaryStudent(), [], null);

  const {
    data: drives,
    loading: drivesLoading,
    error: drivesError,
    reload: reloadDrives,
  } = useApiResource<Drive[]>(() => listActiveDrives(), [], []);

  // Applications are the signed-in student's; they resolve once we know the student id.
  const {
    data: applications,
    loading: appsLoading,
    error: appsError,
    reload: reloadApps,
  } = useApiResource<Application[]>(
    () => (student ? listApplicationsByStudent(student.id) : Promise.resolve<Application[]>([])),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [student?.id],
    [],
  );

  const [location, setLocation] = useState("all");
  const [query, setQuery] = useState("");

  const locations = useMemo(
    () => Array.from(new Set(drives.map((d) => d.location).filter(Boolean))),
    [drives],
  );

  const rows = useMemo(
    () =>
      !student
        ? []
        : drives
            // The API already returns ACTIVE drives; eligibility is computed against the
            // signed-in student's real profile (no mock student, no fabricated match score).
            .map((drive) => ({
              drive,
              status: applications.find((a) => a.driveId === drive.id)?.status,
              eligible: evaluateEligibility(student, drive).eligible,
            }))
            .filter((r) => (location === "all" ? true : r.drive.location === location))
            .filter((r) =>
              query
                ? `${r.drive.companyName} ${r.drive.role}`.toLowerCase().includes(query.toLowerCase())
                : true,
            ),
    [applications, drives, location, query, student],
  );

  const byTab = (tab: string) => {
    if (tab === "eligible") return rows.filter((r) => r.eligible);
    if (tab === "not-eligible") return rows.filter((r) => !r.eligible);
    if (tab === "applied") return rows.filter((r) => r.status && r.status !== "eligible");
    if (tab === "shortlisted")
      return rows.filter(
        (r) =>
          r.status &&
          ["shortlisted", "assessment", "interview", "selected", "offer"].includes(r.status),
      );
    return rows;
  };

  const loading = studentLoading || drivesLoading || appsLoading;
  const error = studentError ?? drivesError ?? appsError;
  const reload = () => {
    reloadStudent();
    reloadDrives();
    reloadApps();
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Opportunities"
        title="Placement Drives"
        subtitle="Every drive checked against your eligibility from your live placement profile."
      />

      <div className="mb-5 flex flex-wrap items-center gap-3">
        <div className="relative min-w-[200px] flex-1">
          <Search className="absolute left-2.5 top-1/2 h-3.5 w-3.5 -translate-y-1/2 text-muted-foreground" />
          <Input
            placeholder="Search company or role..."
            className="pl-8"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            aria-label="Search drives"
          />
        </div>
        <Select value={location} onValueChange={setLocation}>
          <SelectTrigger className="w-40" aria-label="Filter by location">
            <SelectValue placeholder="Location" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="all">All Locations</SelectItem>
            {locations.map((l) => (
              <SelectItem key={l} value={l}>
                {l}
              </SelectItem>
            ))}
          </SelectContent>
        </Select>
      </div>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={drives.length === 0}
        onRetry={reload}
        emptyTitle="No open drives"
        emptyMessage="There are no active placement drives right now. Check back once recruiters post new opportunities."
      >
        <Tabs defaultValue="all">
          <TabsList>
            {tabs.map((t) => (
              <TabsTrigger key={t.value} value={t.value}>
                {t.label}
              </TabsTrigger>
            ))}
          </TabsList>
          {tabs.map((t) => (
            <TabsContent key={t.value} value={t.value}>
              <div className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
                {byTab(t.value).map((r) => (
                  <DriveCard
                    key={r.drive.id}
                    drive={r.drive}
                    status={r.status}
                    eligible={r.eligible}
                  />
                ))}
                {byTab(t.value).length === 0 && (
                  <p className="col-span-full py-10 text-center text-sm text-muted-foreground">
                    No drives match this filter.
                  </p>
                )}
              </div>
            </TabsContent>
          ))}
        </Tabs>
      </AsyncBoundary>
    </div>
  );
}
