"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { ArrowLeft, ArrowRight, CheckCircle2, KeyRound, Mail } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Logo } from "@/components/layout/Logo";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { ApiError } from "@/lib/api/client";
import { resetPassword, sendPasswordResetOtp } from "@/lib/api/auth";

type ResetStep = "email" | "reset" | "done";

export default function ForgotPasswordPage() {
  const [step, setStep] = useState<ResetStep>("email");
  const [email, setEmail] = useState("");
  const [otp, setOtp] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [confirmPassword, setConfirmPassword] = useState("");
  const [notice, setNotice] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [resendSeconds, setResendSeconds] = useState(0);

  useEffect(() => {
    if (resendSeconds <= 0) return;
    const timer = window.setTimeout(() => setResendSeconds((seconds) => seconds - 1), 1000);
    return () => window.clearTimeout(timer);
  }, [resendSeconds]);

  const handleSendCode = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setSubmitting(true);
    try {
      const response = await sendPasswordResetOtp(email);
      setNotice(`${response.message} Check your inbox and spam folder.`);
      setStep("reset");
      setResendSeconds(60);
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status < 500
        ? "Please enter a valid email address and try again."
        : "We couldn't send a reset code. Please try again later or contact your administrator.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleResetPassword = async (event: React.FormEvent<HTMLFormElement>) => {
    event.preventDefault();
    setError(null);
    setNotice(null);
    if (newPassword !== confirmPassword) {
      setError("The passwords don't match.");
      return;
    }
    setSubmitting(true);
    try {
      await resetPassword({ email, otp, newPassword });
      setStep("done");
    } catch (cause) {
      setError(cause instanceof ApiError && cause.status < 500
        ? "That code is invalid or expired. Request a new code and try again."
        : "We couldn't reset your password. Please try again later.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleResend = async () => {
    setError(null);
    setNotice(null);
    setSubmitting(true);
    try {
      const response = await sendPasswordResetOtp(email);
      setNotice(`${response.message} If a code was sent recently, use that code or wait before requesting another.`);
      setResendSeconds(60);
    } catch {
      setError("We couldn't send a new code. Please try again later.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <main className="flex min-h-screen items-center justify-center bg-background p-6">
      <div className="fixed left-6 top-6">
        <Link href="/">
          <Logo />
        </Link>
      </div>
      <div className="fixed right-6 top-6">
        <ThemeToggle />
      </div>

      <Card className="w-full max-w-md p-7 sm:p-9">
        {step === "done" ? (
          <div className="flex flex-col items-center text-center">
            <span className="mb-5 flex h-12 w-12 items-center justify-center rounded-full bg-emerald-500/10 text-emerald-600">
              <CheckCircle2 className="h-6 w-6" />
            </span>
            <h1 className="text-2xl font-semibold">Password updated</h1>
            <p className="mt-2 text-sm text-muted-foreground">
              Your password has been reset. Sign in with your new password.
            </p>
            <Button asChild variant="glow" className="mt-6 w-full">
              <Link href="/login">
                Back to sign in <ArrowRight className="h-4 w-4" />
              </Link>
            </Button>
          </div>
        ) : (
          <>
            <div className="mb-7 flex items-start gap-3">
              <span className="flex h-10 w-10 shrink-0 items-center justify-center rounded-full border border-violet/30 bg-violet/10 text-violet-bright">
                {step === "email" ? <Mail className="h-5 w-5" /> : <KeyRound className="h-5 w-5" />}
              </span>
              <div>
                <h1 className="text-xl font-semibold">
                  {step === "email" ? "Forgot your password?" : "Reset your password"}
                </h1>
                <p className="mt-1 text-sm text-muted-foreground">
                  {step === "email"
                    ? "We'll email you a one-time verification code."
                    : `Enter the 6-digit code sent to ${email} and choose a new password.`}
                </p>
              </div>
            </div>

            {step === "email" ? (
              <form onSubmit={handleSendCode} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="reset-email">Email</Label>
                  <Input
                    id="reset-email"
                    type="email"
                    autoComplete="email"
                    required
                    maxLength={320}
                    value={email}
                    onChange={(event) => setEmail(event.target.value)}
                    placeholder="you@example.com"
                  />
                </div>
                {error && <p className="text-sm text-risk" role="alert">{error}</p>}
                <Button type="submit" variant="glow" className="w-full" disabled={submitting}>
                  {submitting ? "Sending code…" : <>Send verification code <ArrowRight className="h-4 w-4" /></>}
                </Button>
              </form>
            ) : (
              <form onSubmit={handleResetPassword} className="space-y-4">
                <div className="space-y-1.5">
                  <Label htmlFor="reset-otp">Email verification code</Label>
                  <Input
                    id="reset-otp"
                    type="text"
                    inputMode="numeric"
                    autoComplete="one-time-code"
                    pattern="[0-9]{6}"
                    maxLength={6}
                    required
                    value={otp}
                    onChange={(event) => setOtp(event.target.value.replace(/\D/g, "").slice(0, 6))}
                    placeholder="6-digit code"
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="new-password">New password</Label>
                  <Input
                    id="new-password"
                    type="password"
                    autoComplete="new-password"
                    minLength={8}
                    maxLength={100}
                    required
                    value={newPassword}
                    onChange={(event) => setNewPassword(event.target.value)}
                  />
                </div>
                <div className="space-y-1.5">
                  <Label htmlFor="confirm-password">Confirm new password</Label>
                  <Input
                    id="confirm-password"
                    type="password"
                    autoComplete="new-password"
                    minLength={8}
                    maxLength={100}
                    required
                    value={confirmPassword}
                    onChange={(event) => setConfirmPassword(event.target.value)}
                  />
                </div>
                {(error || notice) && (
                  <p className={`text-sm ${error ? "text-risk" : "text-muted-foreground"}`} role={error ? "alert" : "status"}>
                    {error ?? notice}
                  </p>
                )}
                <Button type="submit" variant="glow" className="w-full" disabled={submitting}>
                  {submitting ? "Updating password…" : "Reset password"}
                </Button>
                <div className="flex items-center justify-between text-xs">
                  <button
                    type="button"
                    onClick={() => {
                      setStep("email");
                      setError(null);
                      setNotice(null);
                    }}
                    className="text-muted-foreground hover:text-foreground"
                  >
                    Use a different email
                  </button>
                  <button
                    type="button"
                    onClick={handleResend}
                    disabled={submitting || resendSeconds > 0}
                    className="font-medium text-violet-bright hover:underline disabled:cursor-not-allowed disabled:opacity-50"
                  >
                    {resendSeconds > 0 ? `Resend in ${resendSeconds}s` : "Resend code"}
                  </button>
                </div>
              </form>
            )}

            {step === "email" && notice && (
              <p className="mt-4 text-sm text-muted-foreground" role="status">{notice}</p>
            )}
            <Link
              href="/login"
              className="mt-6 inline-flex items-center gap-2 text-sm text-muted-foreground hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4" /> Back to sign in
            </Link>
          </>
        )}
      </Card>
    </main>
  );
}
