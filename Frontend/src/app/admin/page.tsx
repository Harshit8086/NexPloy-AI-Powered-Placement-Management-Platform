"use client";

import Link from "next/link";
import { motion } from "framer-motion";
import {
  Landmark,
  Users,
  ShieldCheck,
  ShieldAlert,
  Award,
  Briefcase,
  Building2,
  TrendingUp,
  ArrowUpRight,
  Network,
} from "lucide-react";
import { CardTitle } from "@/components/ui/card";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { PageHeader } from "@/components/layout/PageHeader";
import { StatCard } from "@/components/layout/StatCard";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { DataReveal, DataRevealItem } from "@/components/intelligence/DataReveal";
import { HorizontalBarChart } from "@/components/charts/HorizontalBarChart";
import { VerticalBarChart } from "@/components/charts/VerticalBarChart";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { staggerContainer, staggerItem, showcaseEntrance } from "@/lib/motion-variants";
import { INSTITUTE_NAME } from "@/lib/api/campuses";
import { getInstituteAnalytics, listAllCampusAnalytics } from "@/lib/api/analytics";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import type { CampusAnalytics, InstituteAnalytics } from "@/types";

export default function InstituteOverviewPage() {
  const activeCampusId = useAppStore((s) => s.activeCampusId);
  const setActiveCampusId = useAppStore((s) => s.setActiveCampusId);

  // Live: institute rollup (totals + per-campus comparison) + each campus's dashboard snapshot
  // (for offer counts and the active-scope card). Every figure is a server-side aggregation.
  const { data, loading, error, reload } = useApiResource(
    async () => {
      const [institute, byCampus] = await Promise.all([
        getInstituteAnalytics().catch(() => null),
        listAllCampusAnalytics().catch(() => ({} as Record<string, CampusAnalytics>)),
      ]);
      return { institute, byCampus };
    },
    [],
    { institute: null as InstituteAnalytics | null, byCampus: {} as Record<string, CampusAnalytics> },
  );

  const institute = data.institute;
  const byCampus = data.byCampus;
  const campuses = institute?.campuses ?? [];
  const totals = institute?.totals;

  const activeCampus = campuses.find((c) => c.campusId === activeCampusId) ?? campuses[0];
  const activeSnapshot = activeCampus ? byCampus[activeCampus.campusId]?.snapshot : undefined;

  const rateByCampus = campuses.map((c) => ({ label: c.campus, value: c.placementRate }));
  const offersByCampus = campuses.map((c) => ({
    campus: c.campus,
    offers: byCampus[c.campusId]?.snapshot.offersCount ?? 0,
  }));

  return (
    <motion.div
      initial="hidden"
      animate="visible"
      variants={showcaseEntrance}
      className="mx-auto max-w-7xl"
    >
      <PageHeader
        eyebrow="Super Admin"
        title="Institute Overview"
        subtitle={`${INSTITUTE_NAME} · ${campuses.length} campuses · 2026 placement cycle`}
      />

      <AsyncBoundary
        loading={loading}
        error={error}
        empty={!institute || campuses.length === 0}
        onRetry={reload}
        emptyTitle="No institute data"
        emptyMessage="No campuses or placement data have been aggregated yet."
      >
        <motion.div
          variants={staggerContainer}
          initial="hidden"
          animate="visible"
          className="grid grid-cols-2 gap-4 lg:grid-cols-4 xl:grid-cols-7"
        >
          <motion.div variants={staggerItem}>
            <StatCard label="Campuses" value={campuses.length} icon={Network} accent="violet" />
          </motion.div>
          <motion.div variants={staggerItem}>
            <StatCard label="Total Students" value={totals?.totalStudents ?? 0} icon={Users} />
          </motion.div>
          <motion.div variants={staggerItem}>
            <StatCard
              label="Placement Ready"
              value={totals?.placementReady ?? 0}
              icon={ShieldCheck}
              accent="success"
            />
          </motion.div>
          <motion.div variants={staggerItem}>
            <StatCard label="At Risk" value={totals?.atRisk ?? 0} icon={ShieldAlert} accent="risk" />
          </motion.div>
          <motion.div variants={staggerItem}>
            <StatCard label="Offers" value={totals?.offers ?? 0} icon={Award} accent="success" />
          </motion.div>
          <motion.div variants={staggerItem}>
            <StatCard label="Active Drives" value={totals?.activeDrives ?? 0} icon={Briefcase} accent="violet" />
          </motion.div>
          <motion.div variants={staggerItem}>
            <StatCard label="Recruiters" value={totals?.recruiters ?? 0} icon={Building2} />
          </motion.div>
        </motion.div>

        <DataReveal stagger className="mt-5 grid gap-5 lg:grid-cols-2">
          <DataRevealItem>
            <DepthCard className="p-5">
              <div className="mb-3 flex items-center justify-between">
                <CardTitle>Placement Rate by Campus</CardTitle>
              </div>
              <HorizontalBarChart
                data={rateByCampus}
                xKey="value"
                yKey="label"
                height={220}
                colorByValue={(v) => (v >= 80 ? "142 71% 45%" : v >= 70 ? "262 83% 58%" : "38 92% 50%")}
              />
            </DepthCard>
          </DataRevealItem>
          <DataRevealItem>
            <DepthCard className="p-5">
              <div className="mb-3 flex items-center justify-between">
                <CardTitle>Offers by Campus</CardTitle>
              </div>
              <VerticalBarChart
                data={offersByCampus}
                xKey="campus"
                yKey="offers"
                height={220}
                colorVar="--success"
              />
            </DepthCard>
          </DataRevealItem>
        </DataReveal>

        <DataReveal stagger className="mt-5 grid gap-4 md:grid-cols-3">
          {campuses.map((c) => {
            const isActive = c.campusId === activeCampusId;
            return (
              <DataRevealItem key={c.campusId}>
                <DepthCard className={isActive ? "h-full border-violet/40 p-5 shadow-glow" : "h-full p-5"}>
                  <div className="flex items-start justify-between gap-2">
                    <div className="min-w-0">
                      <CardTitle className="truncate">{c.campus}</CardTitle>
                      <p className="text-xs text-muted-foreground">{c.city}</p>
                    </div>
                    {isActive && <Badge variant="default">Active scope</Badge>}
                  </div>
                  <div className="mt-4 grid grid-cols-2 gap-3 text-xs">
                    <div>
                      <p className="text-muted-foreground">Students</p>
                      <p className="text-sm font-semibold tabular-nums">
                        {c.students.toLocaleString()}
                      </p>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Placement Rate</p>
                      <p className="text-sm font-semibold tabular-nums text-violet-bright">
                        {c.placementRate}%
                      </p>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Placed</p>
                      <p className="text-sm font-semibold tabular-nums">{c.placed.toLocaleString()}</p>
                    </div>
                    <div>
                      <p className="text-muted-foreground">Average CTC</p>
                      <p className="text-sm font-semibold tabular-nums">{c.averageCtc}</p>
                    </div>
                  </div>
                  <Button
                    variant={isActive ? "outline" : "glow"}
                    size="sm"
                    className="mt-4 w-full"
                    onClick={() => setActiveCampusId(c.campusId)}
                    disabled={isActive}
                  >
                    {isActive ? "Currently selected" : `Switch to ${c.campus}`}
                  </Button>
                </DepthCard>
              </DataRevealItem>
            );
          })}
        </DataReveal>

        {activeCampus && (
          <DepthCard className="mt-5 p-5">
            <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
              <div className="flex items-center gap-2">
                <Landmark className="h-4 w-4 text-violet-bright" />
                <CardTitle>{activeCampus.campus} — Current Scope</CardTitle>
              </div>
              <div className="flex flex-wrap gap-2">
                <Button asChild variant="outline" size="sm">
                  <Link href="/admin/analytics">
                    Cross-campus analytics <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </Button>
                <Button asChild variant="outline" size="sm">
                  <Link href="/officer">
                    Open command center <ArrowUpRight className="h-3.5 w-3.5" />
                  </Link>
                </Button>
              </div>
            </div>
            <div className="grid grid-cols-2 gap-4 sm:grid-cols-4">
              <StatCard
                label="Students"
                value={activeSnapshot?.totalStudents ?? activeCampus.students}
                icon={Users}
              />
              <StatCard
                label="Placement Rate"
                value={activeSnapshot?.placementRate ?? activeCampus.placementRate}
                suffix="%"
                icon={TrendingUp}
                accent="violet"
              />
              <StatCard
                label="Offers"
                value={activeSnapshot?.offersCount ?? 0}
                icon={Award}
                accent="success"
              />
              <StatCard label="At Risk" value={activeSnapshot?.atRisk ?? 0} icon={ShieldAlert} accent="risk" />
            </div>
            <p className="mt-3 text-xs text-muted-foreground">
              Switching campus here — or from the campus selector in the top bar — changes every
              institutional metric across the Officer and Admin workspaces.
            </p>
          </DepthCard>
        )}
      </AsyncBoundary>
    </motion.div>
  );
}
