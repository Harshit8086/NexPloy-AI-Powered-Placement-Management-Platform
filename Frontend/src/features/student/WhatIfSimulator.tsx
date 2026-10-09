"use client";

import { useEffect, useState } from "react";
import { motion } from "framer-motion";
import { FlaskConical, Info, Sparkles, GraduationCap, ListChecks, Gauge, TrendingUp, Target, Check, Loader2 } from "lucide-react";
import { Card, CardTitle } from "@/components/ui/card";
import { Switch } from "@/components/ui/switch";
import { Label } from "@/components/ui/label";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { PageHeader } from "@/components/layout/PageHeader";
import { ScoreRing } from "@/components/intelligence/ScoreRing";
import { IntelligencePulse } from "@/components/intelligence/IntelligencePulse";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { GlowLine } from "@/components/intelligence/GlowLine";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";
import {
  applyRoadmap as applyRoadmapApi,
  getMyGoal,
  getWhatIfFactors,
  runWhatIfScenario,
  type WhatIfFactorItem,
  type WhatIfResult,
} from "@/lib/api/students";

const DEFAULT_FACTORS: WhatIfFactorItem[] = [
  { id: "core_skill_1", key: "core_skill_1", label: "Master Core Domain Skills", category: "ROADMAP_SKILL", impactWeight: 4, detail: "High-priority technical subjects aligned to your branch & target role", isCurrentGap: true },
  { id: "core_skill_2", key: "core_skill_2", label: "Advanced Tools & Simulation", category: "ROADMAP_SKILL", impactWeight: 3, detail: "Industry-standard software, design tools, or frameworks", isCurrentGap: true },
  { id: "technicalPractice", key: "technicalPractice", label: "Core Technical Aptitude (+30 Questions)", category: "PRACTICE", impactWeight: 4, detail: "Branch-specific problem solving & screening test patterns", isCurrentGap: false },
  { id: "mockInterviews", key: "mockInterviews", label: "3 Mock Interviews", category: "INTERVIEW", impactWeight: 5, detail: "Practice technical and behavioral rounds with feedback", isCurrentGap: false },
  { id: "domainProject", key: "domainProject", label: "Build 1 Domain Project", category: "PROJECT", impactWeight: 4, detail: "Practical capstone or portfolio demonstration in your field", isCurrentGap: false },
  { id: "communication", key: "communication", label: "Improve Communication", category: "PRACTICE", impactWeight: 3, detail: "Articulation, STAR responses, and interview confidence", isCurrentGap: false },
];

/** Stable signature for a factor selection, order-independent, so we can compare saved vs current. */
const signature = (factors: string[]) => [...factors].sort().join(",");

function ScenarioFlow({
  selectedCount,
  readinessImpact,
  delta,
  active,
}: {
  selectedCount: number;
  readinessImpact: number;
  delta: number;
  active: boolean;
}) {
  const stages = [
    { key: "student", label: "Current Student", icon: GraduationCap, value: null as string | null, live: true },
    { key: "improvements", label: "Selected Actions", icon: ListChecks, value: `${selectedCount}`, live: selectedCount > 0 },
    { key: "readiness", label: "Readiness Change", icon: Gauge, value: `+${readinessImpact}`, live: readinessImpact > 0 },
    { key: "probability", label: "Probability Change", icon: TrendingUp, value: `${delta >= 0 ? "+" : ""}${delta}%`, live: delta !== 0 },
    { key: "outcome", label: "Recommended Outcome", icon: Target, value: null, live: selectedCount > 0 },
  ];

  return (
    <div className="flex items-center gap-1 overflow-x-auto pb-1 sm:gap-2">
      {stages.map((stage, i) => {
        const Icon = stage.icon;
        return (
          <div key={stage.key} className="flex shrink-0 items-center gap-1 sm:gap-2">
            <div
              className={cn(
                "flex min-w-[92px] flex-col items-center gap-1.5 rounded-xl border px-3 py-2.5 text-center transition-colors duration-300",
                stage.live ? "border-violet/40 bg-violet/10 shadow-glow" : "border-border bg-card",
              )}
            >
              <Icon className={cn("h-4 w-4", stage.live ? "text-violet-bright" : "text-muted-foreground")} />
              <span className="text-[9.5px] leading-tight text-muted-foreground">{stage.label}</span>
              {stage.value !== null && (
                <span className={cn("text-xs font-semibold tabular-nums", stage.live ? "text-violet-bright" : "text-muted-foreground")}>
                  {stage.value}
                </span>
              )}
            </div>
            {i < stages.length - 1 && <GlowLine active={active} className="w-5 sm:w-8" />}
          </div>
        );
      })}
    </div>
  );
}

export function WhatIfSimulator() {
  const [factors, setFactors] = useState<WhatIfFactorItem[]>([]);
  const [factorsLoading, setFactorsLoading] = useState(true);
  const [selected, setSelected] = useState<string[]>([]);
  const [pulsing, setPulsing] = useState(false);
  const [applying, setApplying] = useState(false);
  // Signature of the roadmap currently persisted for this student, or null if none applied yet.
  const [savedSignature, setSavedSignature] = useState<string | null>(null);
  // Authoritative, server-computed projection for the current selection (null until first compute).
  const [result, setResult] = useState<WhatIfResult | null>(null);
  const [computing, setComputing] = useState(true);
  const [computeError, setComputeError] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);
  const pushToast = useAppStore((s) => s.pushToast);

  // Load dynamic factors derived from the student's Career Roadmap and campus drives.
  useEffect(() => {
    let active = true;
    setFactorsLoading(true);
    getWhatIfFactors()
      .then((data) => {
        if (!active) return;
        setFactors(data && data.length > 0 ? data : DEFAULT_FACTORS);
      })
      .catch(() => {
        if (!active) return;
        setFactors(DEFAULT_FACTORS);
      })
      .finally(() => {
        if (active) setFactorsLoading(false);
      });
    return () => {
      active = false;
    };
  }, []);

  // Load any previously applied roadmap so the selection and success state survive refresh/login.
  useEffect(() => {
    let active = true;
    getMyGoal()
      .then((goal) => {
        if (!active || !goal) return;
        setSelected(goal.factors);
        setSavedSignature(signature(goal.factors));
      })
      .catch(() => {
        /* No applied roadmap yet, or fetch failed — leave the simulator at its default. */
      });
    return () => {
      active = false;
    };
  }, []);

  // Compute the authoritative projection whenever the selection changes. The baseline is the
  // student's own stored probability, returned by the server — never a client-side constant.
  useEffect(() => {
    let active = true;
    setComputing(true);
    runWhatIfScenario(selected)
      .then((res) => {
        if (!active) return;
        setResult(res);
        setComputeError(false);
      })
      .catch(() => {
        if (!active) return;
        setComputeError(true);
      })
      .finally(() => {
        if (active) setComputing(false);
      });
    return () => {
      active = false;
    };
  }, [selected, reloadKey]);

  const baseline = result?.baselineProbability ?? null;
  const delta = result && baseline !== null ? result.probability - baseline : 0;
  const isSaved = selected.length > 0 && savedSignature === signature(selected);

  const toggle = (factorKey: string) => {
    setSelected((prev) =>
      prev.includes(factorKey) ? prev.filter((f) => f !== factorKey) : [...prev, factorKey],
    );
    setPulsing(true);
    window.setTimeout(() => setPulsing(false), 700);
  };

  const applyRoadmap = async () => {
    if (selected.length === 0 || applying || !result || baseline === null) return;
    setApplying(true);
    try {
      await applyRoadmapApi({
        factors: selected,
        projectedProbability: result.probability,
        baselineProbability: baseline,
        readinessImpact: result.readinessImpact,
        recommendedPath: result.recommendedPath,
      });
      setSavedSignature(signature(selected));
      pushToast("Roadmap applied", "Your growth roadmap has been saved to your profile.");
    } catch {
      pushToast("Could not apply roadmap", "Something went wrong saving your roadmap. Please try again.");
    } finally {
      setApplying(false);
    }
  };

  return (
    <div className="mx-auto max-w-5xl">
      <PageHeader
        eyebrow="Growth"
        title="What-If Simulator"
        subtitle={
          baseline !== null
            ? `See how specific actions change your placement probability before you commit the time. Current baseline: ${baseline}%.`
            : "See how specific actions change your placement probability before you commit the time."
        }
      />

      <DepthCard className="mb-5 p-4">
        <ScenarioFlow
          selectedCount={selected.length}
          readinessImpact={result?.readinessImpact ?? 0}
          delta={delta}
          active={pulsing}
        />
      </DepthCard>

      <div className="grid gap-5 lg:grid-cols-[1fr_1.1fr]">
        <IntelligencePulse active={pulsing}>
          <Card className="flex flex-col items-center justify-center gap-4 p-8">
            <div className="flex items-center gap-1.5 text-xs font-medium uppercase tracking-wider text-muted-foreground">
              <FlaskConical className="h-3.5 w-3.5 text-violet-bright" /> Simulated Placement Probability
            </div>
            {computeError ? (
              <div className="flex flex-col items-center gap-3 py-8 text-center">
                <p className="text-sm text-muted-foreground">
                  Couldn&apos;t compute your scenario. Your projection comes from the server — please try again.
                </p>
                <Button variant="outline" size="sm" onClick={() => setReloadKey((k) => k + 1)}>
                  Retry
                </Button>
              </div>
            ) : !result || baseline === null ? (
              <div className="flex flex-col items-center gap-3 py-12">
                <Loader2 className="h-6 w-6 animate-spin text-violet-bright" />
                <p className="text-xs text-muted-foreground">Computing projection…</p>
              </div>
            ) : (
              <>
                <ScoreRing
                  value={result.probability}
                  size={180}
                  strokeWidth={13}
                  label="Probability"
                  sublabel={`baseline ${baseline}%`}
                  animateKey={result.probability}
                />
                <motion.div
                  key={delta}
                  initial={{ opacity: 0, y: 6 }}
                  animate={{ opacity: 1, y: 0 }}
                  className="flex items-center gap-2 rounded-full border border-border bg-elevated px-3 py-1.5 text-sm"
                >
                  <span className="text-muted-foreground">Change:</span>
                  <span className={delta >= 0 ? "font-semibold text-success" : "font-semibold text-risk"}>
                    {delta >= 0 ? "+" : ""}
                    {delta}%
                  </span>
                  <span className="text-muted-foreground">· Readiness impact:</span>
                  <span className="font-semibold text-violet-bright">+{result.readinessImpact}</span>
                </motion.div>

                <div className="w-full rounded-lg border border-violet/30 bg-violet/5 p-3 text-center">
                  <p className="flex items-center justify-center gap-1.5 text-xs font-medium text-violet-bright">
                    <Sparkles className="h-3 w-3" /> Recommended Path
                  </p>
                  <p className="mt-1 text-xs text-muted-foreground">{result.recommendedPath}</p>
                </div>

                <Button
                  variant="glow"
                  className="w-full"
                  onClick={applyRoadmap}
                  disabled={selected.length === 0 || applying || computing}
                >
                  {applying ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" /> Applying…
                    </>
                  ) : isSaved ? (
                    <>
                      <Check className="h-4 w-4" /> Roadmap Applied
                    </>
                  ) : (
                    "Apply This Roadmap"
                  )}
                </Button>

                {isSaved && (
                  <p className="flex items-center justify-center gap-1.5 text-xs font-medium text-success">
                    <Check className="h-3 w-3" /> Saved to your profile — retrievable after you log back in.
                  </p>
                )}
              </>
            )}
          </Card>
        </IntelligencePulse>

        <Card className="p-5">
          <div className="mb-4 flex items-center justify-between">
            <div>
              <CardTitle className="text-base font-semibold">Scenario Factors</CardTitle>
              <p className="mt-0.5 text-xs text-muted-foreground">
                Grounded in your Career Roadmap and campus drive requirements
              </p>
            </div>
            {factorsLoading && <Loader2 className="h-4 w-4 animate-spin text-muted-foreground" />}
          </div>

          <div className="space-y-4">
            {/* Category: Roadmap Skills & Target Gaps */}
            {factors.filter((f) => f.category === "ROADMAP_SKILL").length > 0 && (
              <div className="space-y-2">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Target Roadmap Skills & Gaps
                </span>
                <div className="space-y-1.5">
                  {factors
                    .filter((f) => f.category === "ROADMAP_SKILL")
                    .map((factor) => {
                      const isChecked = selected.includes(factor.key) || selected.includes(factor.id);
                      return (
                        <div
                          key={factor.id}
                          className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-card/60 p-2.5 transition-colors hover:bg-accent/40"
                        >
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <Label htmlFor={factor.id} className="cursor-pointer text-sm font-medium">
                                {factor.label}
                              </Label>
                              {factor.isCurrentGap && (
                                <Badge variant="outline" className="border-warning/50 bg-warning/10 text-warning text-[10px]">
                                  Current Gap
                                </Badge>
                              )}
                              <Badge variant="outline" className="border-violet/40 bg-violet/10 text-violet-bright text-[10px] font-semibold">
                                +{factor.impactWeight}% Lift
                              </Badge>
                            </div>
                            {factor.detail && (
                              <p className="mt-0.5 text-xs text-muted-foreground line-clamp-1">
                                {factor.detail}
                              </p>
                            )}
                          </div>
                          <Switch
                            id={factor.id}
                            checked={isChecked}
                            onCheckedChange={() => toggle(factor.key || factor.id)}
                          />
                        </div>
                      );
                    })}
                </div>
              </div>
            )}

            {/* Category: Practice & Interview Levers */}
            {factors.filter((f) => f.category !== "ROADMAP_SKILL").length > 0 && (
              <div className="space-y-2">
                <span className="text-[11px] font-semibold uppercase tracking-wider text-muted-foreground">
                  Preparation & Practice Levers
                </span>
                <div className="space-y-1.5">
                  {factors
                    .filter((f) => f.category !== "ROADMAP_SKILL")
                    .map((factor) => {
                      const isChecked = selected.includes(factor.key) || selected.includes(factor.id);
                      return (
                        <div
                          key={factor.id}
                          className="flex items-center justify-between gap-3 rounded-lg border border-border/60 bg-card/60 p-2.5 transition-colors hover:bg-accent/40"
                        >
                          <div className="min-w-0 flex-1">
                            <div className="flex items-center gap-2">
                              <Label htmlFor={factor.id} className="cursor-pointer text-sm font-medium">
                                {factor.label}
                              </Label>
                              <Badge variant="outline" className="border-violet/40 bg-violet/10 text-violet-bright text-[10px] font-semibold">
                                +{factor.impactWeight}% Lift
                              </Badge>
                            </div>
                            {factor.detail && (
                              <p className="mt-0.5 text-xs text-muted-foreground line-clamp-1">
                                {factor.detail}
                              </p>
                            )}
                          </div>
                          <Switch
                            id={factor.id}
                            checked={isChecked}
                            onCheckedChange={() => toggle(factor.key || factor.id)}
                          />
                        </div>
                      );
                    })}
                </div>
              </div>
            )}
          </div>
        </Card>
      </div>

      <Alert className="mt-5">
        <Info />
        <AlertDescription>
          Projected scenario estimate — not a guaranteed placement probability, and not a real ML
          prediction.
        </AlertDescription>
      </Alert>
    </div>
  );
}
