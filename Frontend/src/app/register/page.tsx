"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowLeft, ArrowRight, ShieldCheck } from "lucide-react";
import { Logo } from "@/components/layout/Logo";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { Card } from "@/components/ui/card";
import { roleDescription, roleIcon, roleLabel } from "@/components/layout/nav-config";
import { SELF_REGISTERABLE_ROLES } from "@/lib/api/auth";
import { fadeUp } from "@/lib/motion-variants";

/**
 * Registration hub — step 1 of sign-up. Only self-registerable roles
 * (student, recruiter) are offered; staff roles are invite-only and
 * created by an officer/admin, so they never appear here. Picking a role
 * moves to that role's dedicated sign-up form (/register/[role]).
 */
export default function RegisterHubPage() {
  const router = useRouter();

  return (
    <div className="relative min-h-screen overflow-hidden">
      <div
        className="pointer-events-none absolute inset-0 -z-10"
        style={{
          background: "radial-gradient(60% 50% at 50% 0%, hsl(var(--violet) / 0.18), transparent 70%)",
        }}
      />
      <div className="relative mx-auto flex min-h-screen w-full max-w-4xl flex-col px-6 py-8 sm:px-10">
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
              <ArrowLeft className="h-3.5 w-3.5" /> Sign in instead
            </Link>
          </div>
        </div>

        <div className="flex flex-1 flex-col items-center justify-center py-8 text-center">
          <motion.div initial="hidden" animate="visible" variants={fadeUp}>
            <p className="text-xs font-medium uppercase tracking-wider text-violet-bright">
              Create your NEXPLOY account
            </p>
            <h1 className="mt-2 text-balance text-3xl font-semibold leading-tight tracking-tight sm:text-4xl">
              How will you use NEXPLOY?
            </h1>
            <p className="mx-auto mt-3 max-w-md text-balance text-sm leading-relaxed text-muted-foreground">
              Students and recruiters can sign up here. Placement officers, mentors, and admins are
              added by your placement office.
            </p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.15 }}
            className="mt-8 grid w-full max-w-2xl gap-4 sm:grid-cols-2"
          >
            {SELF_REGISTERABLE_ROLES.map((role) => {
              const Icon = roleIcon[role];
              return (
                <button
                  key={role}
                  type="button"
                  onClick={() => router.push(`/register/${role}`)}
                  className="group flex flex-col items-start gap-3 rounded-2xl border border-white/[0.09] bg-gradient-to-b from-elevated to-card p-6 text-left shadow-card transition-all duration-300 focus-visible:-translate-y-1 focus-visible:border-violet-bright/70 focus-visible:shadow-glow-strong focus-visible:outline-none"
                >
                  <span className="flex h-11 w-11 items-center justify-center rounded-full border border-violet/30 bg-violet/10 text-violet-bright">
                    <Icon className="h-5 w-5" />
                  </span>
                  <div>
                    <h2 className="text-base font-semibold">{roleLabel[role]}</h2>
                    <p className="mt-1 text-xs text-muted-foreground">{roleDescription[role]}</p>
                  </div>
                  <span className="mt-1 inline-flex items-center gap-1 text-[11px] font-medium text-violet-bright/70 transition-colors group-hover:text-violet-bright">
                    Sign up <ArrowRight className="h-3 w-3 transition-transform group-hover:translate-x-0.5" />
                  </span>
                </button>
              );
            })}
          </motion.div>

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="mt-8 w-full max-w-2xl"
          >
            <Card className="flex items-start gap-3 border-white/[0.06] bg-elevated/50 p-4 text-left">
              <ShieldCheck className="mt-0.5 h-4 w-4 shrink-0 text-violet-bright" />
              <p className="text-xs leading-relaxed text-muted-foreground">
                Are you a placement officer, mentor, or admin? Those accounts are created for you by
                your placement office for security. Contact them to get access.
              </p>
            </Card>
          </motion.div>
        </div>

        <p className="pb-2 text-center text-[11px] text-muted-foreground/60">
          NEXPLOY Prototype — simulated data, no real authentication.
        </p>
      </div>
    </div>
  );
}
