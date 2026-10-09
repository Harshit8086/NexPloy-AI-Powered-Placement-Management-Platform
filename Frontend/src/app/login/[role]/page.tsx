"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowLeft, ArrowRight, ShieldAlert } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/layout/Logo";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { RoleSignatureCanvas } from "@/components/three/RoleSignatureCanvas";
import { StudentTwinCompact, StudentTwinVisual } from "@/components/login/StudentTwinVisual";
import { RecruiterFlowCompact, RecruiterFlowVisual } from "@/components/login/RecruiterFlowVisual";
import { OfficerLoopCompact, OfficerLoopVisual } from "@/components/login/OfficerLoopVisual";
import { MentorJourneyCompact, MentorJourneyVisual } from "@/components/login/MentorJourneyVisual";
import { AdminInsightCompact, AdminInsightVisual } from "@/components/login/AdminInsightVisual";
import { useAppStore } from "@/hooks/useAppStore";
import { isSelfRegisterable, login } from "@/lib/api/auth";
import type { Role } from "@/types";
import {
  roleHome,
  roleIcon,
  roleLabel,
  roleDescription,
  roleTagline,
  roleDemoEmail,
  roleSignals,
  roleOrder,
} from "@/components/layout/nav-config";
import { staggerContainer, staggerItem } from "@/lib/motion-variants";

const MotionButton = motion(Button);

function isRole(value: string): value is Role {
  return (roleOrder as string[]).includes(value);
}

export default function RoleLoginPage({ params }: { params: { role: string } }) {
  if (!isRole(params.role)) {
    return <UnknownRole slug={params.role} />;
  }
  return <RoleLoginForm role={params.role} />;
}

function UnknownRole({ slug }: { slug: string }) {
  return (
    <div className="flex min-h-screen items-center justify-center p-6">
      <Card className="flex max-w-sm flex-col items-center gap-3 p-8 text-center">
        <ShieldAlert className="h-8 w-8 text-risk" />
        <p className="text-sm font-semibold">&ldquo;{slug}&rdquo; isn&apos;t a NEXPLOY role.</p>
        <p className="text-xs text-muted-foreground">Pick one of the five roles to continue.</p>
        <Button asChild variant="glow" size="sm" className="mt-2">
          <Link href="/login">
            All roles <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </Button>
      </Card>
    </div>
  );
}

function RoleLoginForm({ role }: { role: Role }) {
  const router = useRouter();
  const setRole = useAppStore((s) => s.setRole);
  const [email, setEmail] = useState(roleDemoEmail[role]);
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const Icon = roleIcon[role];
  const signals = roleSignals[role];

  const handleSignIn = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    // Authenticate against the backend and store the JWT (client.ts then attaches it).
    setSubmitting(true);
    try {
      await login({ username: email, password });
    } catch {
      setSubmitting(false);
      setError("Sign-in failed. Check your email and password.");
      return;
    }
    setSubmitting(false);
    setRole(role);
    router.push(roleHome[role]);
  };

  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Left panel — role identity + a preview of what this role sees */}
      <div className="relative hidden overflow-hidden bg-elevated lg:flex lg:flex-col lg:justify-between lg:p-12">
        <div
          className="pointer-events-none absolute inset-0 -z-10"
          style={{
            background: "radial-gradient(70% 60% at 20% 20%, hsl(var(--violet) / 0.22), transparent 70%)",
          }}
        />
        <div className="flex items-center justify-between">
          <Link href="/">
            <Logo />
          </Link>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link
              href="/login"
              className="inline-flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> All roles
            </Link>
          </div>
        </div>

        <motion.div
          key={role}
          initial="hidden"
          animate="visible"
          variants={staggerContainer}
          className={role === "student" || role === "recruiter" || role === "officer" || role === "mentor" || role === "admin" ? "w-full max-w-xl" : "max-w-md"}
        >
          <motion.p
            variants={staggerItem}
            className="text-xs font-medium uppercase tracking-wider text-violet-bright"
          >
            {roleLabel[role]}
          </motion.p>
          <motion.h1 variants={staggerItem} className="mt-1 text-3xl font-semibold leading-tight tracking-tight">
            {roleTagline[role]}
          </motion.h1>

          {role === "student" ? (
            <>
              <motion.p variants={staggerItem} className="mt-2 text-sm text-muted-foreground">
                A complete view of you. A clearer path ahead.
              </motion.p>
              <div className="mt-6 w-full">
                <StudentTwinVisual />
              </div>
            </>
          ) : role === "recruiter" ? (
            <>
              <motion.p variants={staggerItem} className="mt-2 text-sm text-muted-foreground">
                Smarter matching. Stronger teams.
              </motion.p>
              <div className="mt-8 w-full">
                <RecruiterFlowVisual />
              </div>
            </>
          ) : role === "officer" ? (
            <>
              <motion.p variants={staggerItem} className="mt-2 text-sm text-muted-foreground">
                Plan. Execute. Monitor. Improve.
              </motion.p>
              <div className="mt-8 w-full">
                <OfficerLoopVisual />
              </div>
            </>
          ) : role === "mentor" ? (
            <>
              <motion.p variants={staggerItem} className="mt-2 text-sm text-muted-foreground">
                Guide early. Intervene intelligently. Track progress.
              </motion.p>
              <div className="mt-8 w-full">
                <MentorJourneyVisual />
              </div>
            </>
          ) : role === "admin" ? (
            <>
              <motion.p variants={staggerItem} className="mt-2 text-sm text-muted-foreground">
                Real-time insights. Better outcomes.
              </motion.p>
              <div className="mt-6 w-full">
                <AdminInsightVisual />
              </div>
            </>
          ) : (
            <motion.div variants={staggerItem} className="relative mt-4 h-[320px] w-full">
              <RoleSignatureCanvas Icon={Icon} label={roleLabel[role]} signals={signals} />
            </motion.div>
          )}
        </motion.div>

        <p className="text-xs text-muted-foreground/60">
          NEXPLOY Prototype — demo data; account access is verified by the backend.
        </p>
      </div>

      {/* Right panel — the sign-in form */}
      <div className="flex items-center justify-center p-6 sm:p-10">
        <motion.div
          key={role}
          initial="hidden"
          animate="visible"
          variants={staggerContainer}
          className="w-full max-w-sm"
        >
          <motion.div variants={staggerItem} className="mb-8 flex items-center justify-between lg:hidden">
            <Logo />
            <div className="flex items-center gap-2">
              <ThemeToggle />
              <Link href="/login" className="text-xs text-muted-foreground hover:text-foreground">
                All roles
              </Link>
            </div>
          </motion.div>

          {(role === "student" || role === "recruiter" || role === "officer" || role === "mentor" || role === "admin") && (
            <motion.div variants={staggerItem} className="mb-8 lg:hidden">
              <p className="text-xs font-medium uppercase tracking-wider text-violet-bright">{roleLabel[role]}</p>
              <h1 className="mb-4 mt-1 text-2xl font-semibold leading-tight tracking-tight">{roleTagline[role]}</h1>
              {role === "student" ? <StudentTwinCompact /> : role === "recruiter" ? <RecruiterFlowCompact /> : role === "officer" ? <OfficerLoopCompact /> : role === "mentor" ? <MentorJourneyCompact /> : <AdminInsightCompact />}
            </motion.div>
          )}

          <motion.div variants={staggerItem} className="flex items-center gap-3">
            <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-violet/30 bg-violet/10 text-violet-bright">
              <Icon className="h-5 w-5" />
            </span>
            <div>
              <h2 className="text-xl font-semibold">Sign in as {roleLabel[role]}</h2>
              <p className="text-xs text-muted-foreground">{roleDescription[role]}</p>
            </div>
          </motion.div>

          <motion.form variants={staggerItem} onSubmit={handleSignIn} className="mt-6 space-y-4">
            <div className="space-y-1.5">
              <Label htmlFor="email">Email</Label>
              <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} />
            </div>
            <div className="space-y-1.5">
              <Label htmlFor="password">Password</Label>
              <Input
                id="password"
                type="password"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
              />
            </div>
            <div className="-mt-2 flex justify-end">
              <Link
                href="/forgot-password"
                className="text-xs font-medium text-violet-bright hover:underline"
              >
                Forgot password?
              </Link>
            </div>
            {error && (
              <p className="text-xs text-risk" role="alert">
                {error}
              </p>
            )}
            <MotionButton
              type="submit"
              variant="glow"
              className="w-full"
              disabled={submitting}
              whileHover={{ scale: 1.01 }}
              whileTap={{ scale: 0.98 }}
            >
              {submitting ? "Signing in…" : <>Sign In as {roleLabel[role]} <ArrowRight className="h-4 w-4" /></>}
            </MotionButton>
          </motion.form>

          <motion.div variants={staggerItem} className="mt-6 flex items-center gap-3">
            <div className="h-px flex-1 bg-border" />
            <span className="text-[11px] uppercase tracking-wide text-muted-foreground/60">
              or continue with
            </span>
            <div className="h-px flex-1 bg-border" />
          </motion.div>
          <motion.div variants={staggerItem} className="mt-4 grid grid-cols-2 gap-2">
            <Button variant="outline" type="button" disabled className="opacity-70">
              Google
            </Button>
            <Button variant="outline" type="button" disabled className="opacity-70">
              Microsoft
            </Button>
          </motion.div>
          <motion.p variants={staggerItem} className="mt-4 text-center text-xs text-muted-foreground">
            {isRole(role) && isSelfRegisterable(role) ? (
              <>
                New to NEXPLOY?{" "}
                <Link href={`/register/${role}`} className="font-medium text-violet-bright hover:underline">
                  Create a {roleLabel[role]} account
                </Link>
              </>
            ) : (
              <>{roleLabel[role]} accounts are created by your placement office.</>
            )}
          </motion.p>
          <motion.p variants={staggerItem} className="mt-3 text-center text-[11px] text-muted-foreground/60">
            Secure sign-in powered by your campus account.
          </motion.p>
        </motion.div>
      </div>
    </div>
  );
}
