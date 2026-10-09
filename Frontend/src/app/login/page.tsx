"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { motion } from "framer-motion";
import { ArrowLeft } from "lucide-react";
import { useTheme } from "next-themes";
import { Logo } from "@/components/layout/Logo";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { LazyShaderBackground as ShaderBackground } from "@/components/ui/lazy-shader-background";
import { RoleSelector } from "@/components/login/RoleSelector";
import { fadeUp } from "@/lib/motion-variants";

/**
 * Role-selection hub — step 1 of sign-in. Each role gets its own avatar;
 * picking one moves to that role's dedicated, themed sign-in page
 * (/login/[role]) instead of a single shared form with a role switch.
 */
export default function LoginHubPage() {
  const { resolvedTheme } = useTheme();
  const [mounted, setMounted] = useState(false);
  useEffect(() => setMounted(true), []);
  const shaderVariant = mounted && resolvedTheme === "light" ? "light" : "dark";

  return (
    <div className="relative min-h-screen overflow-hidden">
      <ShaderBackground className="absolute inset-0" variant={shaderVariant} />
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-b from-background/40 via-background/70 to-background" />

      <div className="relative mx-auto flex min-h-screen w-full max-w-6xl flex-col px-6 py-8 sm:px-10">
        <div className="flex items-center justify-between">
          <Link href="/">
            <Logo />
          </Link>
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link
              href="/"
              className="inline-flex items-center gap-1.5 text-xs text-muted-foreground transition-colors hover:text-foreground"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Back to home
            </Link>
          </div>
        </div>

        <div className="flex flex-1 flex-col items-center justify-center py-8 text-center">
          <motion.div initial="hidden" animate="visible" variants={fadeUp}>
            <p className="text-xs font-medium uppercase tracking-wider text-violet-bright">
              Sign in to NEXPLOY
            </p>
            <h1 className="mt-2 text-balance text-3xl font-semibold leading-tight tracking-tight sm:text-4xl">
              Who&apos;s signing in?
            </h1>
            <p className="mx-auto mt-3 max-w-md text-balance text-sm leading-relaxed text-muted-foreground">
              Every role sees a different side of NEXPLOY. Choose yours to continue.
            </p>
          </motion.div>

          <motion.div
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.15 }}
            className="mt-8 w-full"
          >
            <RoleSelector />
          </motion.div>

          <motion.p
            initial={{ opacity: 0 }}
            animate={{ opacity: 1 }}
            transition={{ duration: 0.6, delay: 0.3 }}
            className="mt-8 text-sm text-muted-foreground"
          >
            New here?{" "}
            <Link href="/register" className="font-medium text-violet-bright hover:underline">
              Create an account
            </Link>
          </motion.p>
        </div>

        <p className="pb-2 text-center text-[11px] text-muted-foreground/60">
          NEXPLOY Prototype — simulated data, no real authentication.
        </p>
      </div>
    </div>
  );
}
