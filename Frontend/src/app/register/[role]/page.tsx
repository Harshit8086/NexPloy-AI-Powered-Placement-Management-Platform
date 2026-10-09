"use client";

import { useEffect, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import { motion } from "framer-motion";
import { ArrowLeft, ArrowRight, MailCheck, ShieldAlert } from "lucide-react";
import Link from "next/link";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Logo } from "@/components/layout/Logo";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { useAppStore } from "@/hooks/useAppStore";
import type { Role } from "@/types";
import {
  roleHome,
  roleIcon,
  roleLabel,
  roleTagline,
  roleOrder,
} from "@/components/layout/nav-config";
import {
  isSelfRegisterable,
  listPublicCampuses,
  registerRecruiter,
  registerStudent,
  sendOtp,
  type PublicCampus,
} from "@/lib/api/auth";
import { ApiError } from "@/lib/api/client";
import { staggerContainer, staggerItem } from "@/lib/motion-variants";

const MotionButton = motion(Button);

function isRole(value: string): value is Role {
  return (roleOrder as string[]).includes(value);
}

export default function RoleRegisterPage({ params }: { params: { role: string } }) {
  if (!isRole(params.role)) {
    return <UnknownRole slug={params.role} />;
  }
  if (!isSelfRegisterable(params.role)) {
    return <InviteOnlyRole role={params.role} />;
  }
  return <RegisterForm role={params.role} />;
}

function Shell({ children }: { children: React.ReactNode }) {
  return <div className="flex min-h-screen items-center justify-center p-6">{children}</div>;
}

function UnknownRole({ slug }: { slug: string }) {
  return (
    <Shell>
      <Card className="flex max-w-sm flex-col items-center gap-3 p-8 text-center">
        <ShieldAlert className="h-8 w-8 text-risk" />
        <p className="text-sm font-semibold">&ldquo;{slug}&rdquo; isn&apos;t a NEXPLOY role.</p>
        <Button asChild variant="glow" size="sm" className="mt-2">
          <Link href="/register">
            Choose a role <ArrowRight className="h-3.5 w-3.5" />
          </Link>
        </Button>
      </Card>
    </Shell>
  );
}

function InviteOnlyRole({ role }: { role: Role }) {
  return (
    <Shell>
      <Card className="flex max-w-md flex-col items-center gap-3 p-8 text-center">
        <ShieldAlert className="h-8 w-8 text-violet-bright" />
        <p className="text-sm font-semibold">{roleLabel[role]} accounts are invite-only.</p>
        <p className="text-xs text-muted-foreground">
          For security, {roleLabel[role].toLowerCase()} accounts are created by your placement
          office rather than self-registered. Contact them to get access.
        </p>
        <div className="mt-2 flex gap-2">
          <Button asChild variant="outline" size="sm">
            <Link href="/register">Other roles</Link>
          </Button>
          <Button asChild variant="glow" size="sm">
            <Link href={`/login/${role}`}>Sign in</Link>
          </Button>
        </div>
      </Card>
    </Shell>
  );
}

/** Small labelled field wrapper. */
function Field({
  id,
  label,
  required,
  hint,
  children,
}: {
  id: string;
  label: string;
  required?: boolean;
  hint?: string;
  children: React.ReactNode;
}) {
  return (
    <div className="space-y-1.5">
      <Label htmlFor={id}>
        {label}
        {required && <span className="ml-0.5 text-violet-bright">*</span>}
      </Label>
      {children}
      {hint && <p className="text-[11px] text-muted-foreground/70">{hint}</p>}
    </div>
  );
}

/**
 * Six-box OTP entry. A visually-hidden input captures typing/paste/backspace;
 * the boxes are pure display, so native editing behaviour stays correct.
 */
function OtpBoxes({
  value,
  onChange,
  disabled,
}: {
  value: string;
  onChange: (next: string) => void;
  disabled?: boolean;
}) {
  const inputRef = useRef<HTMLInputElement>(null);
  const digits = Array.from({ length: 6 }, (_, i) => value[i] ?? "");
  return (
    <div
      className="flex cursor-text justify-center gap-2"
      onClick={() => inputRef.current?.focus()}
    >
      <input
        ref={inputRef}
        className="sr-only"
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, "").slice(0, 6))}
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        disabled={disabled}
        aria-label="6-digit verification code"
      />
      {digits.map((digit, i) => (
        <div
          key={i}
          className={`flex h-12 w-10 items-center justify-center rounded-md border text-lg font-semibold transition-colors ${
            digit
              ? "border-violet/50 bg-violet/10 text-foreground"
              : i === value.length
                ? "border-violet/40 bg-background"
                : "border-border bg-background"
          }`}
        >
          {digit}
        </div>
      ))}
    </div>
  );
}

function RegisterForm({ role }: { role: Role }) {
  const router = useRouter();
  const setRole = useAppStore((s) => s.setRole);
  const Icon = roleIcon[role];

  // Shared fields. V60: students no longer self-report their name or academic
  // record — the campus directory supplies those once the email is verified.
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [password, setPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [campusId, setCampusId] = useState("");
  const [campuses, setCampuses] = useState<PublicCampus[]>([]);

  // Recruiter-only
  const [companyName, setCompanyName] = useState("");
  const [companyWebsite, setCompanyWebsite] = useState("");
  const [companyIndustry, setCompanyIndustry] = useState("");
  const [companyLocation, setCompanyLocation] = useState("");
  const [designation, setDesignation] = useState("");

  // Student OTP step (V60)
  const [step, setStep] = useState<"form" | "otp">("form");
  const [otp, setOtp] = useState("");
  const [resendIn, setResendIn] = useState(0);

  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    listPublicCampuses()
      .then((list) => {
        if (!active) return;
        setCampuses(list);
        if (list.length > 0) setCampusId((current) => current || list[0].id);
      })
      .catch(() => {
        if (active) setError("Couldn't load campuses. Is the backend running?");
      });
    return () => {
      active = false;
    };
  }, []);

  // Resend cooldown ticker — the backend allows one OTP send per 60 seconds.
  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn((s) => s - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  const passwordTooShort = password.length > 0 && password.length < 8;
  const passwordMismatch = confirmPassword.length > 0 && confirmPassword !== password;

  const formValid =
    role === "student"
      ? email.trim() !== "" &&
        password.length >= 8 &&
        confirmPassword === password &&
        campusId !== ""
      : fullName.trim() !== "" &&
        email.trim() !== "" &&
        phone.trim() !== "" &&
        password.length >= 8 &&
        confirmPassword === password &&
        campusId !== "" &&
        companyName.trim() !== "" &&
        companyIndustry.trim() !== "" &&
        companyLocation.trim() !== "" &&
        designation.trim() !== "";

  const canSubmit = step === "form" && formValid && !submitting;
  const canVerify = step === "otp" && otp.length === 6 && !submitting;

  const friendlyError = (err: unknown, fallback: string): string => {
    if (err instanceof ApiError) {
      return err.status === 409
        ? "An account with that email already exists. Try signing in."
        : err.message || fallback;
    }
    return fallback;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      if (role === "student") {
        // The directory pre-checks (in the directory? active record? no account yet?)
        // run here, BEFORE any code is sent, so a blocked student sees the reason now.
        const { otpRequired } = await sendOtp(campusId, email.trim());
        if (otpRequired) {
          setStep("otp");
          setOtp("");
          setResendIn(60);
          setSubmitting(false);
          return;
        }
        // OTP disabled on this deployment (no SMTP credentials) — register directly.
        await registerStudent({
          email: email.trim(),
          password,
          phone: phone.trim() || undefined,
          campusId,
        });
      } else {
        await registerRecruiter({
          fullName: fullName.trim(),
          email: email.trim(),
          phone: phone.trim(),
          password,
          campusId,
          companyName: companyName.trim(),
          companyWebsite: companyWebsite.trim() || undefined,
          companyIndustry: companyIndustry.trim(),
          companyLocation: companyLocation.trim(),
          designation: designation.trim(),
        });
      }
      setRole(role);
      router.push(roleHome[role]);
    } catch (err) {
      setError(friendlyError(err, "Registration failed. Please try again."));
      setSubmitting(false);
    }
  };

  const handleVerify = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canVerify) return;
    setSubmitting(true);
    setError(null);
    try {
      await registerStudent({
        email: email.trim(),
        password,
        phone: phone.trim() || undefined,
        campusId,
        otp,
      });
      setRole(role);
      router.push(roleHome[role]);
    } catch (err) {
      setError(friendlyError(err, "Verification failed. Please try again."));
      setSubmitting(false);
    }
  };

  const handleResend = async () => {
    if (resendIn > 0 || submitting) return;
    setError(null);
    try {
      await sendOtp(campusId, email.trim());
      setOtp("");
      setResendIn(60);
    } catch (err) {
      setError(friendlyError(err, "Could not resend the code. Please try again."));
    }
  };

  const campusLabel = role === "student" ? "College / University" : "Campus";

  return (
    <div className="flex min-h-screen items-center justify-center p-6 sm:p-10">
      <motion.div
        initial="hidden"
        animate="visible"
        variants={staggerContainer}
        className="w-full max-w-lg"
      >
        <motion.div variants={staggerItem} className="mb-8 flex items-center justify-between">
          <Logo />
          <div className="flex items-center gap-2">
            <ThemeToggle />
            <Link
              href="/register"
              className="inline-flex items-center gap-1.5 text-xs text-muted-foreground hover:text-foreground"
            >
              <ArrowLeft className="h-3.5 w-3.5" /> Roles
            </Link>
          </div>
        </motion.div>

        <motion.div variants={staggerItem} className="flex items-center gap-3">
          <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-violet/30 bg-violet/10 text-violet-bright">
            {step === "otp" ? <MailCheck className="h-5 w-5" /> : <Icon className="h-5 w-5" />}
          </span>
          <div>
            <h2 className="text-xl font-semibold">
              {step === "otp" ? "Verify your email" : `Sign up as ${roleLabel[role]}`}
            </h2>
            <p className="text-xs text-muted-foreground">{roleTagline[role]}</p>
          </div>
        </motion.div>

        {step === "otp" ? (
          <motion.form variants={staggerItem} onSubmit={handleVerify} className="mt-6 space-y-5">
            <p className="rounded-md border border-violet/20 bg-violet/5 px-3 py-2 text-xs text-muted-foreground">
              We sent a 6-digit verification code to{" "}
              <span className="font-medium text-foreground">{email.trim()}</span>. It expires in 5
              minutes. Your name and academic record come from your college&apos;s directory — you
              don&apos;t enter them here.
            </p>
            <OtpBoxes value={otp} onChange={setOtp} disabled={submitting} />
            {error && (
              <p className="rounded-md border border-risk/30 bg-risk/10 px-3 py-2 text-xs text-risk">
                {error}
              </p>
            )}
            <MotionButton
              type="submit"
              variant="glow"
              className="w-full"
              disabled={!canVerify}
              whileHover={canVerify ? { scale: 1.01 } : undefined}
              whileTap={canVerify ? { scale: 0.98 } : undefined}
            >
              {submitting ? "Verifying…" : "Verify & create account"}
              {!submitting && <ArrowRight className="h-4 w-4" />}
            </MotionButton>
            <div className="flex items-center justify-between text-xs">
              <button
                type="button"
                onClick={() => {
                  setStep("form");
                  setError(null);
                }}
                className="inline-flex items-center gap-1 text-muted-foreground hover:text-foreground"
              >
                <ArrowLeft className="h-3 w-3" /> Change details
              </button>
              <button
                type="button"
                onClick={handleResend}
                disabled={resendIn > 0}
                className="text-violet-bright hover:underline disabled:cursor-not-allowed disabled:text-muted-foreground disabled:no-underline"
              >
                {resendIn > 0 ? `Resend code in ${resendIn}s` : "Resend code"}
              </button>
            </div>
          </motion.form>
        ) : (
          <motion.form variants={staggerItem} onSubmit={handleSubmit} className="mt-6 space-y-5">
            {/* Identity */}
            <div className="grid gap-4 sm:grid-cols-2">
              {role === "recruiter" && (
                <Field id="fullName" label="Recruiter Full Name" required>
                  <Input id="fullName" value={fullName} onChange={(e) => setFullName(e.target.value)} autoComplete="name" />
                </Field>
              )}
              <Field
                id="email"
                label={role === "student" ? "College Email" : "Work Email"}
                required
                hint={role === "student" ? "Must match your campus directory entry." : undefined}
              >
                <Input id="email" type="email" value={email} onChange={(e) => setEmail(e.target.value)} autoComplete="email" />
              </Field>
              <Field
                id="phone"
                label="Phone Number"
                required={role === "recruiter"}
                hint={role === "student" ? "Optional — your college may already have it on file." : undefined}
              >
                <Input id="phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} autoComplete="tel" />
              </Field>
              <Field id="campus" label={campusLabel} required>
                <Select value={campusId} onValueChange={setCampusId}>
                  <SelectTrigger id="campus">
                    <SelectValue placeholder="Select" />
                  </SelectTrigger>
                  <SelectContent>
                    {campuses.map((c) => (
                      <SelectItem key={c.id} value={c.id}>
                        {c.name}
                        {c.city ? ` — ${c.city}` : ""}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              </Field>
            </div>

            {/* Passwords */}
            <div className="grid gap-4 sm:grid-cols-2">
              <Field id="password" label="Password" required>
                <Input
                  id="password"
                  type="password"
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  autoComplete="new-password"
                  placeholder="Min 8 characters"
                />
                {passwordTooShort && <p className="text-[11px] text-risk">At least 8 characters.</p>}
              </Field>
              <Field id="confirmPassword" label="Confirm Password" required>
                <Input
                  id="confirmPassword"
                  type="password"
                  value={confirmPassword}
                  onChange={(e) => setConfirmPassword(e.target.value)}
                  autoComplete="new-password"
                />
                {passwordMismatch && <p className="text-[11px] text-risk">Passwords don&apos;t match.</p>}
              </Field>
            </div>

            {/* Role-specific */}
            {role === "student" ? (
              <p className="rounded-md border border-violet/20 bg-violet/5 px-3 py-2 text-[11px] text-muted-foreground">
                Your name, branch and academic record come from your college&apos;s official student
                directory — you won&apos;t enter them here, and they stay officer-verified.
              </p>
            ) : (
              <div className="space-y-4">
                <p className="text-xs font-medium uppercase tracking-wider text-violet-bright">Company</p>
                <div className="grid gap-4 sm:grid-cols-2">
                  <Field id="companyName" label="Company Name" required>
                    <Input id="companyName" value={companyName} onChange={(e) => setCompanyName(e.target.value)} />
                  </Field>
                  <Field id="companyWebsite" label="Company Website" hint="Optional">
                    <Input id="companyWebsite" type="url" value={companyWebsite} onChange={(e) => setCompanyWebsite(e.target.value)} placeholder="https://" />
                  </Field>
                  <Field id="companyIndustry" label="Company Industry" required>
                    <Input id="companyIndustry" value={companyIndustry} onChange={(e) => setCompanyIndustry(e.target.value)} placeholder="e.g. Software" />
                  </Field>
                  <Field id="companyLocation" label="Company Location" required>
                    <Input id="companyLocation" value={companyLocation} onChange={(e) => setCompanyLocation(e.target.value)} placeholder="City, Country" />
                  </Field>
                  <Field id="designation" label="Designation / Job Title" required>
                    <Input id="designation" value={designation} onChange={(e) => setDesignation(e.target.value)} placeholder="e.g. Talent Acquisition Lead" />
                  </Field>
                </div>
              </div>
            )}

            {error && (
              <p className="rounded-md border border-risk/30 bg-risk/10 px-3 py-2 text-xs text-risk">{error}</p>
            )}

            <MotionButton
              type="submit"
              variant="glow"
              className="w-full"
              disabled={!canSubmit}
              whileHover={canSubmit ? { scale: 1.01 } : undefined}
              whileTap={canSubmit ? { scale: 0.98 } : undefined}
            >
              {submitting
                ? role === "student"
                  ? "Checking directory…"
                  : "Creating account…"
                : role === "student"
                  ? "Continue — verify email"
                  : `Create ${roleLabel[role]} account`}
              {!submitting && <ArrowRight className="h-4 w-4" />}
            </MotionButton>
          </motion.form>
        )}

        <motion.p variants={staggerItem} className="mt-4 text-center text-xs text-muted-foreground">
          Already have an account?{" "}
          <Link href={`/login/${role}`} className="text-violet-bright hover:underline">
            Sign in
          </Link>
        </motion.p>
        <motion.p variants={staggerItem} className="mt-3 text-center text-[11px] text-muted-foreground/60">
          {role === "student"
            ? "Student sign-ups are verified against your campus directory. Not listed? Contact your Placement Officer."
            : "NEXPLOY Prototype."}
        </motion.p>
      </motion.div>
    </div>
  );
}