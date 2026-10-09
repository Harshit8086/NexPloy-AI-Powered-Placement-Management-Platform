"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import { useTheme } from "next-themes";
import {
  ArrowRight,
  Menu,
  PlayCircle,
  Fingerprint,
  Target,
  ShieldCheck,
  CalendarClock,
  GraduationCap,
  Building2,
  Sparkles,
  ArrowUpRight,
  HeartHandshake,
  Landmark,
} from "lucide-react";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { LazyShaderBackground as ShaderBackground } from "@/components/ui/lazy-shader-background";
import { Logo } from "@/components/layout/Logo";
import { DemoDataBadge } from "@/components/layout/DemoDataBadge";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { Sheet, SheetContent, SheetTrigger, SheetTitle } from "@/components/ui/sheet";
import { useAnimatedNumber } from "@/hooks/useAnimatedNumber";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { PlacementIntelligenceCore, PlacementIntelligenceCompact } from "@/components/landing/PlacementIntelligenceCore";
import { PlacementFlowMap } from "@/components/landing/PlacementFlowMap";
import { fadeUp, staggerContainer, staggerItem, showcaseEntrance } from "@/lib/motion-variants";
import { landingShowcaseMetrics } from "@/components/landing/showcase-data";
import {
  Dialog,
  DialogContent,
  DialogHeader,
  DialogTitle,
  DialogDescription,
} from "@/components/ui/dialog";

const navLinks = [
  { label: "Platform", href: "#platform" },
  { label: "Students", href: "#students" },
  { label: "Recruiters", href: "#recruiters" },
  { label: "Placement Teams", href: "#officers" },
  { label: "Placement Intelligence", href: "#intelligence" },
];

const demoStoryScript = [
  "Rahul opens his Placement Profile and Digital Twin — 78/100 readiness, refreshed live.",
  "Skill Gap flags Docker, AWS and Communication; the What-If Simulator stacks them to lift placement probability from 68% to 81%.",
  "He opens the TechNova drive — deterministic eligibility passes on every rule, and the match breakdown explains 91% line by line.",
  "Switch to Recruiter — the ranked candidate pool surfaces Rahul first; select and bulk shortlist.",
  "Switch to Placement Officer — the Command Center flags an at-risk student and a drive conflict; assign a mentor, then optimize the schedule.",
  "Switch to Mentor — activate the intervention plan and mark progress.",
  "Back as the student, accept the TechNova offer; verify the pending document as the officer.",
  "Finish as Super Admin — switch campus and watch every institutional metric change.",
];

const audienceCards = [
  {
    anchor: "students",
    icon: GraduationCap,
    title: "For Students",
    description:
      "See exactly where you stand, close the right skill gaps, and simulate how each action changes your placement probability before you commit the time.",
    cta: "Explore Student Experience",
  },
  {
    anchor: "recruiters",
    icon: Building2,
    title: "For Recruiters",
    description:
      "Rank candidates with a fully explainable match score — every point traced back to eligibility, skills, projects and readiness signals.",
    cta: "Explore Recruiter Experience",
  },
  {
    anchor: "officers",
    icon: ShieldCheck,
    title: "For Placement Officers",
    description:
      "Run the whole cycle from one command center — eligibility rules, candidate review, drive scheduling, offers, documents and analytics.",
    cta: "Explore Officer Experience",
  },
  {
    anchor: "mentors",
    icon: HeartHandshake,
    title: "For Mentors",
    description:
      "Get the at-risk students who need you most, with the issues already identified and an intervention plan ready to activate and track.",
    cta: "Explore Mentor Experience",
  },
  {
    anchor: "admin",
    icon: Landmark,
    title: "For Institutes",
    description:
      "Compare every campus on one screen — placement rate, readiness, offers and average CTC — and drill into any campus operations.",
    cta: "Explore Institute Experience",
  },
];

const platformPillars = [
  {
    icon: Fingerprint,
    title: "Placement Digital Twin",
    description:
      "A continuously updated digital representation of every student's placement readiness and trajectory.",
  },
  {
    icon: Target,
    title: "Explainable Matching",
    description:
      "Every candidate ranking comes with a transparent, component-level breakdown — never a black box score.",
  },
  {
    icon: CalendarClock,
    title: "Drive Orchestrator",
    description: "Detects scheduling conflicts across drives and recommends optimal resolutions instantly.",
  },
  {
    icon: ShieldCheck,
    title: "Risk Radar",
    description: "Surfaces at-risk students early with clear factors and recommended interventions.",
  },
];

function CountUp({ value, suffix = "" }: { value: number; suffix?: string }) {
  const v = useAnimatedNumber(value, 1000);
  return (
    <>
      {Math.round(v).toLocaleString("en-US")}
      {suffix}
    </>
  );
}

export function LandingPage() {
  const [watchOpen, setWatchOpen] = useState(false);
  const [mobileMenuOpen, setMobileMenuOpen] = useState(false);
  const { resolvedTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const shaderVariant = mounted && resolvedTheme === "light" ? "light" : "dark";

  return (
    <div className="min-h-screen bg-background">
      {/* Nav */}
      <header className="sticky top-0 z-40 border-b border-border/60 bg-background/80 backdrop-blur-md">
        <div className="container flex h-16 items-center justify-between">
          <Logo />
          <nav className="hidden items-center gap-7 md:flex">
            {navLinks.map((l) => (
              <a key={l.href} href={l.href} className="text-sm text-muted-foreground transition-colors hover:text-foreground">
                {l.label}
              </a>
            ))}
          </nav>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Button asChild variant="ghost" size="sm" className="hidden sm:inline-flex">
              <Link href="/login">Login</Link>
            </Button>
            <Button asChild size="sm" variant="glow" className="hidden sm:inline-flex">
              <Link href="/login">
                Explore Platform <ArrowRight className="h-3.5 w-3.5" />
              </Link>
            </Button>

            {/* Mobile menu — on phones the section links (hidden md:flex) and the auth CTAs
                (hidden sm:inline-flex) have no room inline, so they live here. Closes on select. */}
            <Sheet open={mobileMenuOpen} onOpenChange={setMobileMenuOpen}>
              <SheetTrigger asChild>
                <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open menu">
                  <Menu className="h-5 w-5" />
                </Button>
              </SheetTrigger>
              <SheetContent side="right" className="flex w-72 flex-col p-0">
                <SheetTitle className="sr-only">Site navigation</SheetTitle>
                <div className="flex h-16 shrink-0 items-center border-b border-border/60 px-5">
                  <Logo />
                </div>
                <nav className="flex flex-col px-3 py-4">
                  {navLinks.map((l) => (
                    <a
                      key={l.href}
                      href={l.href}
                      onClick={() => setMobileMenuOpen(false)}
                      className="rounded-lg px-3 py-2.5 text-sm font-medium text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
                    >
                      {l.label}
                    </a>
                  ))}
                </nav>
                <div className="mt-auto flex flex-col gap-2 border-t border-border/60 p-4">
                  <Button asChild variant="outline" onClick={() => setMobileMenuOpen(false)}>
                    <Link href="/login">Login</Link>
                  </Button>
                  <Button asChild variant="glow" onClick={() => setMobileMenuOpen(false)}>
                    <Link href="/login">
                      Explore Platform <ArrowRight className="h-4 w-4" />
                    </Link>
                  </Button>
                </div>
              </SheetContent>
            </Sheet>
          </div>
        </div>
      </header>

      {/* Hero */}
      <section id="platform" className="relative overflow-hidden">
        <div className="relative min-h-[640px] overflow-hidden">
          <ShaderBackground className="absolute inset-0" variant={shaderVariant} />
          <div className="relative z-10">
            <div className="container grid gap-12 py-16 lg:grid-cols-[0.85fr_1.15fr] lg:items-center lg:py-20">
              <motion.div initial="hidden" animate="visible" variants={staggerContainer}>
                <motion.div variants={staggerItem}>
                  <Badge className="mb-5 border border-violet/30 bg-violet/10 text-violet-bright" variant="default">
                    <Sparkles className="mr-1 h-3 w-3" /> INTELLIGENCE BEFORE OPPORTUNITY
                  </Badge>
                </motion.div>
                <motion.h1 variants={staggerItem} className="text-balance text-4xl font-semibold leading-tight tracking-tight sm:text-5xl">
                  From Potential to <span className="bg-gradient-to-r from-violet-bright to-[#C4A6FF] bg-clip-text text-transparent">Placement.</span>
                </motion.h1>
                <motion.p variants={staggerItem} className="mt-5 max-w-xl text-balance text-base leading-relaxed text-muted-foreground">
                  NEXPLOY is an AI-powered placement intelligence platform that helps students understand and
                  improve their placement readiness, intelligently connects candidates with opportunities, and
                  enables placement teams to optimize the complete campus placement lifecycle.
                </motion.p>
                <motion.div variants={staggerItem} className="mt-8 flex flex-wrap items-center gap-3">
                  <Button asChild size="lg" variant="glow">
                    <Link href="/login">
                      Explore NEXPLOY <ArrowRight className="h-4 w-4" />
                    </Link>
                  </Button>
                  <Button size="lg" variant="outline" onClick={() => setWatchOpen(true)}>
                    <PlayCircle className="h-4 w-4" /> Watch Demo
                  </Button>
                </motion.div>
              </motion.div>

              {/* Placement Intelligence Core */}
              <motion.div initial="hidden" animate="visible" variants={showcaseEntrance} className="hidden sm:block">
                <PlacementIntelligenceCore />
              </motion.div>
              <motion.div initial="hidden" animate="visible" variants={showcaseEntrance} className="sm:hidden">
                <PlacementIntelligenceCompact />
              </motion.div>
            </div>
          </div>
        </div>

        {/* Metrics strip */}
        <div className="border-y border-border/60 bg-elevated/40">
          <div className="container grid grid-cols-2 gap-6 py-8 sm:grid-cols-4">
            {[
              { label: "Students", value: landingShowcaseMetrics.students, suffix: "" },
              { label: "Placement Rate", value: landingShowcaseMetrics.placementRate, suffix: "%" },
              { label: "Offers", value: landingShowcaseMetrics.offers, suffix: "" },
              { label: "Recruiters", value: landingShowcaseMetrics.recruiters, suffix: "+" },
            ].map((m) => (
              <div key={m.label} className="text-center">
                <p className="text-2xl font-semibold tabular-nums text-foreground sm:text-3xl">
                  <CountUp value={m.value} suffix={m.suffix} />
                </p>
                <p className="mt-1 text-xs text-muted-foreground">{m.label}</p>
              </div>
            ))}
          </div>
          <div className="container pb-4 text-center">
            <DemoDataBadge />
          </div>
        </div>
      </section>

      {/* Placement Intelligence Flow Map */}
      <section id="intelligence" className="relative overflow-hidden border-y border-border/60 bg-elevated/30">
        <div className="container py-16">
          <motion.div
            initial="hidden"
            whileInView="visible"
            viewport={{ once: true }}
            variants={fadeUp}
            className="mx-auto max-w-2xl text-center"
          >
            <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-violet-bright">
              Placement Intelligence Network
            </p>
            <h2 className="text-3xl font-semibold tracking-tight">Every placement is a connected signal</h2>
            <p className="mt-3 text-muted-foreground">
              From student potential to recruiter intent, NEXPLOY connects every signal that shapes a placement
              outcome.
            </p>
          </motion.div>

          <div className="mt-12">
            <PlacementFlowMap />
          </div>
        </div>
      </section>

      {/* Platform pillars */}
      <section className="container py-20">
        <motion.div initial="hidden" whileInView="visible" viewport={{ once: true }} variants={fadeUp} className="mx-auto max-w-2xl text-center">
          <h2 className="text-3xl font-semibold tracking-tight">Placement Intelligence, end to end</h2>
          <p className="mt-3 text-muted-foreground">
            Four systems working together — readiness, matching, scheduling, and risk — so nothing about a
            placement cycle is a surprise.
          </p>
        </motion.div>
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          variants={staggerContainer}
          className="mt-12 grid gap-5 sm:grid-cols-2 lg:grid-cols-4"
        >
          {platformPillars.map((p) => {
            const Icon = p.icon;
            return (
              <motion.div key={p.title} variants={staggerItem}>
                <DepthCard className="h-full p-5">
                  <div className="mb-3 flex h-9 w-9 items-center justify-center rounded-lg bg-violet/10">
                    <Icon className="h-4 w-4 text-violet-bright" />
                  </div>
                  <h3 className="text-sm font-semibold">{p.title}</h3>
                  <p className="mt-1.5 text-xs leading-relaxed text-muted-foreground">{p.description}</p>
                </DepthCard>
              </motion.div>
            );
          })}
        </motion.div>
      </section>

      {/* Who NEXPLOY is for */}
      <section className="container pb-20">
        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          variants={fadeUp}
          className="mx-auto mb-10 max-w-2xl text-center"
        >
          <p className="mb-2 text-[11px] font-semibold uppercase tracking-wider text-violet-bright">
            One platform, five workspaces
          </p>
          <h2 className="text-3xl font-semibold tracking-tight">Built for everyone in the cycle</h2>
        </motion.div>

        <motion.div
          initial="hidden"
          whileInView="visible"
          viewport={{ once: true }}
          variants={staggerContainer}
          className="grid gap-5 md:grid-cols-2 lg:grid-cols-3"
        >
          {audienceCards.map((card) => {
            const Icon = card.icon;
            return (
              <motion.div key={card.title} variants={staggerItem} id={card.anchor}>
                <DepthCard className="h-full p-7">
                  <Icon className="h-6 w-6 text-violet-bright" />
                  <h3 className="mt-3 text-lg font-semibold">{card.title}</h3>
                  <p className="mt-1.5 text-sm leading-relaxed text-muted-foreground">
                    {card.description}
                  </p>
                  <Button asChild variant="link" className="mt-3 px-0">
                    <Link href="/login">
                      {card.cta} <ArrowUpRight className="h-3.5 w-3.5" />
                    </Link>
                  </Button>
                </DepthCard>
              </motion.div>
            );
          })}
        </motion.div>
      </section>

      <footer className="border-t border-border/60 py-8">
        <div className="container flex flex-col items-center justify-between gap-3 sm:flex-row">
          <Logo />
          <p className="text-xs text-muted-foreground">
            NEXPLOY Prototype — interactive product demo. Simulated data throughout.
          </p>
        </div>
      </footer>

      <Dialog open={watchOpen} onOpenChange={setWatchOpen}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle className="flex items-center gap-2">
              <PlayCircle className="h-5 w-5 text-violet-bright" /> Demo Walkthrough
            </DialogTitle>
            <DialogDescription>The interactive story this prototype is built to walk through.</DialogDescription>
          </DialogHeader>
          <ol className="space-y-3">
            {demoStoryScript.map((step, i) => (
              <li key={i} className="flex gap-3 text-sm">
                <span className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-violet/15 text-xs font-semibold text-violet-bright">
                  {i + 1}
                </span>
                <span className="leading-relaxed text-foreground">{step}</span>
              </li>
            ))}
          </ol>
          <Button asChild variant="glow" className="mt-2">
            <Link href="/login">
              Start the walkthrough <ArrowRight className="h-4 w-4" />
            </Link>
          </Button>
        </DialogContent>
      </Dialog>
    </div>
  );
}
