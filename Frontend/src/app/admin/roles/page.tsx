"use client";

import { KeyRound, Check, Minus, Users } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import {
  navByRole,
  roleDescription,
  roleHome,
  roleIcon,
  roleLabel,
  roleOrder,
  roleRouteAccess,
} from "@/components/layout/nav-config";
import { getInstituteAnalytics } from "@/lib/api/analytics";
import { useApiResource } from "@/hooks/useApiResource";
import type { InstituteAnalytics, Role } from "@/types";

/** Capability matrix shown to the institute admin. Mirrors what the
 * route scoping actually permits — see roleRouteAccess. This is a governance
 * definition (UI reference), not fetched business data. */
const CAPABILITIES: { label: string; allowed: Role[] }[] = [
  { label: "View own placement profile & readiness", allowed: ["student"] },
  { label: "Apply to drives and manage offers", allowed: ["student"] },
  { label: "View ranked candidate pool", allowed: ["recruiter", "officer", "admin"] },
  { label: "Shortlist candidates", allowed: ["recruiter", "officer"] },
  { label: "Create and configure drives", allowed: ["officer", "admin"] },
  { label: "Configure eligibility criteria", allowed: ["officer", "admin"] },
  { label: "Resolve scheduling conflicts", allowed: ["officer", "admin"] },
  { label: "Verify documents", allowed: ["officer", "admin"] },
  { label: "Assign mentors", allowed: ["officer", "admin"] },
  { label: "Create intervention plans", allowed: ["mentor", "officer"] },
  { label: "Track mentee progress", allowed: ["mentor", "officer", "admin"] },
  { label: "View campus analytics", allowed: ["officer", "admin"] },
  { label: "Switch campus scope", allowed: ["officer", "admin"] },
  { label: "View cross-campus analytics", allowed: ["admin"] },
];

export default function RoleAccessPage() {
  // Live: institute totals give the real student/recruiter counts and campus (officer scope) count.
  // Mentor accounts have no aggregate endpoint, so that count is shown as unavailable rather than faked.
  const { data, loading, error, reload } = useApiResource(
    () => getInstituteAnalytics().catch(() => null),
    [],
    null as InstituteAnalytics | null,
  );

  const totals = data?.totals;
  const campusCount = data?.campuses.length ?? 0;
  const accountCounts: Record<Role, number | string> = {
    student: totals?.totalStudents ?? 0,
    recruiter: totals?.recruiters ?? 0,
    officer: campusCount,
    mentor: "—",
    admin: 1,
  };

  return (
    <div className="mx-auto max-w-6xl">
      <PageHeader
        eyebrow="Governance"
        title="Role &amp; Access Overview"
        subtitle="What each role can reach, and the routes that back it."
      />

      <Alert className="mb-5">
        <KeyRound />
        <AlertDescription>
          Route scoping shown here is enforced client-side for navigation on top of the backend&apos;s
          role-based authorization. Account counts are live institute figures; the capability matrix is
          a fixed governance definition.
        </AlertDescription>
      </Alert>

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!data}
        onRetry={reload}
        emptyTitle="No institute data"
        emptyMessage="Role counts become available once campuses and accounts are registered."
      >
        <div className="mb-5 grid grid-cols-2 gap-4 sm:grid-cols-5">
          {roleOrder.map((role) => (
            <StatCard
              key={role}
              label={roleLabel[role]}
              value={accountCounts[role]}
              icon={roleIcon[role]}
              accent={role === "admin" ? "violet" : "success"}
            />
          ))}
        </div>

        <DataReveal stagger className="mb-5 grid gap-4 md:grid-cols-2 lg:grid-cols-3">
          {roleOrder.map((role) => {
            const Icon = roleIcon[role];
            const sections = navByRole[role];
            const screenCount = sections.reduce((n, s) => n + s.items.length, 0);
            const count = accountCounts[role];
            return (
              <DataRevealItem key={role}>
                <DepthCard className="h-full p-5">
                  <div className="flex items-center gap-3">
                    <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-violet/10">
                      <Icon className="h-4 w-4 text-violet-bright" />
                    </div>
                    <div className="min-w-0">
                      <CardTitle className="truncate">{roleLabel[role]}</CardTitle>
                      <p className="truncate text-xs text-muted-foreground">
                        {roleDescription[role]}
                      </p>
                    </div>
                  </div>
                  <div className="mt-3 space-y-1.5 text-xs text-muted-foreground">
                    <p>
                      Home route:{" "}
                      <code className="rounded bg-muted px-1 py-0.5 text-foreground">
                        {roleHome[role]}
                      </code>
                    </p>
                    <p className="flex items-center gap-1.5">
                      <Users className="h-3.5 w-3.5" /> {count} account
                      {count === 1 ? "" : "s"} · {screenCount} screens
                    </p>
                  </div>
                  <p className="mb-1.5 mt-3 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground">
                    Accessible route prefixes
                  </p>
                  <div className="flex flex-wrap gap-1.5">
                    {roleRouteAccess[role].map((prefix) => (
                      <Badge key={prefix} variant="muted">
                        {prefix}
                      </Badge>
                    ))}
                  </div>
                </DepthCard>
              </DataRevealItem>
            );
          })}
        </DataReveal>

        <Card className="overflow-x-auto p-5">
          <CardTitle className="mb-3">Capability Matrix</CardTitle>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead className="min-w-[240px]">Capability</TableHead>
                {roleOrder.map((role) => (
                  <TableHead key={role} className="text-center">
                    {roleLabel[role]}
                  </TableHead>
                ))}
              </TableRow>
            </TableHeader>
            <TableBody>
              {CAPABILITIES.map((cap) => (
                <TableRow key={cap.label}>
                  <TableCell className="font-medium">{cap.label}</TableCell>
                  {roleOrder.map((role) => {
                    const allowed = cap.allowed.includes(role);
                    return (
                      <TableCell key={role} className="text-center">
                        <span className="sr-only">{allowed ? "Allowed" : "Not allowed"}</span>
                        {allowed ? (
                          <Check className="mx-auto h-4 w-4 text-success" aria-hidden />
                        ) : (
                          <Minus className="mx-auto h-4 w-4 text-muted-foreground/40" aria-hidden />
                        )}
                      </TableCell>
                    );
                  })}
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Card>
      </AsyncBoundary>
    </div>
  );
}
