"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Loader2, ShieldCheck, AlertTriangle, Clock, CheckCircle2, XCircle } from "lucide-react";
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import {
  startSkillViva,
  submitSkillViva,
  type StartVivaResponse,
  type SubmitVivaResponse,
} from "@/lib/api/skillVivas";

type Phase = "loading" | "ready" | "answering" | "submitting" | "result" | "error";

const MAX_TAB_SWITCHES = 3;
const OPTION_LETTERS = ["A", "B", "C", "D"];

function fmt(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return `${m}:${s.toString().padStart(2, "0")}`;
}

/**
 * The AI Skill Viva flow in a modal: loads skill-scoped MCQs (options arrive shuffled), shows them
 * one at a time with a per-question timer, flags tab-switching (and auto-submits after too many),
 * then shows the measured level with per-question feedback — correct options disclosed after
 * submit. The measured level is written back server-side and the skill becomes VERIFIED; the
 * parent refetches via onVerified.
 */
export function SkillVivaDialog({
  skillId,
  skillName,
  open,
  onClose,
  onVerified,
}: {
  skillId: string | null;
  skillName: string | null;
  open: boolean;
  onClose: () => void;
  onVerified: () => void;
}) {
  const [phase, setPhase] = useState<Phase>("loading");
  const [viva, setViva] = useState<StartVivaResponse | null>(null);
  const [index, setIndex] = useState(0);
  const [picks, setPicks] = useState<(number | null)[]>([]);
  const [remaining, setRemaining] = useState(0);
  const [switches, setSwitches] = useState(0);
  const [result, setResult] = useState<SubmitVivaResponse | null>(null);
  const [errorMsg, setErrorMsg] = useState<string | null>(null);
  const submittingRef = useRef(false);

  // Fullscreen helpers. Requesting fullscreen needs a user gesture, so it fires from the "Start"
  // button (beginAnswering), not an effect. Exiting fullscreen while answering is treated as leaving.
  const enterFullscreen = useCallback(() => {
    try {
      const p = document.documentElement.requestFullscreen?.();
      if (p && typeof p.catch === "function") p.catch(() => {});
    } catch {
      /* fullscreen unavailable/denied — the viva still runs with focus/visibility guards */
    }
  }, []);

  const exitFullscreen = useCallback(() => {
    try {
      if (document.fullscreenElement) {
        const p = document.exitFullscreen?.();
        if (p && typeof p.catch === "function") p.catch(() => {});
      }
    } catch {
      /* ignore */
    }
  }, []);

  const beginAnswering = useCallback(() => {
    enterFullscreen();
    setPhase("answering");
  }, [enterFullscreen]);

  // Load the viva when the dialog opens for a skill.
  useEffect(() => {
    if (!open || !skillId) return;
    let cancelled = false;
    setPhase("loading");
    setResult(null);
    setIndex(0);
    setPicks([]);
    setSwitches(0);
    setErrorMsg(null);
    submittingRef.current = false;
    startSkillViva(skillId)
      .then((v) => {
        if (cancelled) return;
        setViva(v);
        setPicks(new Array<number | null>(v.questions.length).fill(null));
        if (v.questions.length === 0) {
          setErrorMsg("No questions were generated. Please try again.");
          setPhase("error");
          return;
        }
        setRemaining(v.questions[0].timerSeconds);
        setPhase("ready");
      })
      .catch((e) => {
        if (cancelled) return;
        setErrorMsg(e instanceof Error ? e.message : "Could not start the viva.");
        setPhase("error");
      });
    return () => {
      cancelled = true;
    };
  }, [open, skillId]);

  const doSubmit = useCallback(
    async (finalPicks: (number | null)[]) => {
      if (!skillId || !viva || submittingRef.current) return;
      submittingRef.current = true;
      exitFullscreen();
      setPhase("submitting");
      try {
        // Submit the vivaId plus the picked option TEXT, not its position: the options arrived
        // shuffled, and the server scores by text against its stored copy of this exact set, so the
        // shuffle can't leak or confuse anything. Unanswered = "".
        const payload = viva.questions.map((q, i) => {
          const pick = finalPicks[i];
          const option = pick != null && pick >= 0 && pick < q.options.length ? q.options[pick] : "";
          return { question: q.prompt, option };
        });
        const res = await submitSkillViva(skillId, viva.vivaId, payload);
        setResult(res);
        setPhase("result");
        onVerified(); // the skill's level + status changed either way — refresh the list
      } catch (e) {
        setErrorMsg(e instanceof Error ? e.message : "Could not submit the viva.");
        setPhase("error");
      }
    },
    [skillId, viva, onVerified, exitFullscreen],
  );

  const pickOption = useCallback(
    (optionIndex: number) => {
      setPicks((p) => {
        const next = [...p];
        next[index] = optionIndex;
        return next;
      });
    },
    [index],
  );

  const advance = useCallback(() => {
    if (!viva) return;
    if (index + 1 < viva.questions.length) {
      const next = index + 1;
      setIndex(next);
      setRemaining(viva.questions[next].timerSeconds);
    } else {
      void doSubmit(picks);
    }
  }, [viva, index, picks, doSubmit]);

  // Per-question countdown; expiry auto-advances (the pick so far is kept).
  useEffect(() => {
    if (phase !== "answering") return;
    if (remaining <= 0) {
      advance();
      return;
    }
    const t = setTimeout(() => setRemaining((r) => r - 1), 1000);
    return () => clearTimeout(t);
  }, [phase, remaining, advance]);

  // Anti-cheat: count any "leaving the viva" signal while answering — the tab being hidden, the
  // window losing focus (e.g. clicking a second browser in split-screen), or exiting fullscreen.
  useEffect(() => {
    if (phase !== "answering") return;
    const bump = () => setSwitches((s) => s + 1);
    const onVisibility = () => {
      if (document.visibilityState === "hidden") bump();
    };
    const onBlur = () => bump();
    const onFsChange = () => {
      if (!document.fullscreenElement) bump();
    };
    document.addEventListener("visibilitychange", onVisibility);
    window.addEventListener("blur", onBlur);
    document.addEventListener("fullscreenchange", onFsChange);
    return () => {
      document.removeEventListener("visibilitychange", onVisibility);
      window.removeEventListener("blur", onBlur);
      document.removeEventListener("fullscreenchange", onFsChange);
    };
  }, [phase]);

  // Too many switches -> end the viva early (submit what we have).
  useEffect(() => {
    if (phase === "answering" && switches >= MAX_TAB_SWITCHES) {
      void doSubmit(picks);
    }
  }, [switches, phase, picks, doSubmit]);

  const question = viva?.questions[index];
  const total = viva?.questions.length ?? 0;

  return (
    <Dialog open={open} onOpenChange={(o) => { if (!o) { exitFullscreen(); onClose(); } }}>
      <DialogContent className="sm:max-w-lg">
        <DialogHeader>
          <DialogTitle className="flex items-center gap-2">
            <ShieldCheck className="h-4 w-4 text-violet-bright" /> Verify {skillName ?? "Skill"}
          </DialogTitle>
          <DialogDescription className="text-xs">
            Pick the best option for each timed question. The viva runs in fullscreen and is timed;
            leaving the window or fullscreen is detected — that&apos;s how we confirm you actually
            know the skill, not just claimed it.
          </DialogDescription>
        </DialogHeader>

        {phase === "loading" && (
          <div className="flex flex-col items-center gap-3 py-10 text-sm text-muted-foreground">
            <Loader2 className="h-6 w-6 animate-spin text-violet-bright" /> Preparing your viva…
          </div>
        )}

        {phase === "ready" && viva && (
          <div className="space-y-3 py-3">
            <p className="text-sm text-foreground">
              You&apos;ll answer <strong>{total}</strong> timed multiple-choice questions on{" "}
              <strong>{skillName}</strong>, pitched at your current level (
              <strong>{viva.targetLevel}%</strong>). Your score will set your verified level.
            </p>
            <ul className="space-y-1.5 text-xs text-muted-foreground">
              <li>• Runs in fullscreen. Leaving fullscreen, switching tabs or clicking another window is detected.</li>
              <li>• Options are shuffled — pick the best one; correct answers are revealed after you submit.</li>
              <li>• After {MAX_TAB_SWITCHES} leaves, the viva ends automatically.</li>
            </ul>
          </div>
        )}

        {phase === "answering" && question && (
          <div className="space-y-3 py-1">
            <div className="flex items-center justify-between text-xs">
              <span className="text-muted-foreground">Question {index + 1} of {total}</span>
              <span className={`inline-flex items-center gap-1 font-mono ${remaining <= 15 ? "text-risk" : "text-muted-foreground"}`}>
                <Clock className="h-3.5 w-3.5" /> {fmt(remaining)}
              </span>
            </div>
            {switches > 0 && (
              <div className="flex items-center gap-1.5 rounded-lg border border-risk/40 bg-risk/5 px-3 py-1.5 text-xs text-risk">
                <AlertTriangle className="h-3.5 w-3.5 shrink-0" />
                You left the viva ({switches}/{MAX_TAB_SWITCHES}) — switching window, tab or fullscreen
                is detected; it ends automatically if you keep leaving.
              </div>
            )}
            <p className="text-sm font-medium text-foreground">{question.prompt}</p>
            <div className="space-y-2">
              {question.options.map((option, optionIndex) => {
                const selected = picks[index] === optionIndex;
                return (
                  <button
                    key={optionIndex}
                    type="button"
                    onClick={() => pickOption(optionIndex)}
                    className={`flex w-full items-start gap-2.5 rounded-lg border p-3 text-left text-sm transition-colors ${
                      selected
                        ? "border-violet/60 bg-violet/10 text-foreground"
                        : "border-border bg-elevated/60 text-muted-foreground hover:border-violet/30 hover:text-foreground"
                    }`}
                  >
                    <span
                      className={`mt-0.5 flex h-5 w-5 shrink-0 items-center justify-center rounded-full text-[11px] font-semibold ${
                        selected ? "bg-violet-bright text-white" : "bg-muted text-muted-foreground"
                      }`}
                    >
                      {OPTION_LETTERS[optionIndex] ?? optionIndex + 1}
                    </span>
                    <span>{option}</span>
                  </button>
                );
              })}
            </div>
          </div>
        )}

        {phase === "submitting" && (
          <div className="flex flex-col items-center gap-3 py-10 text-sm text-muted-foreground">
            <Loader2 className="h-6 w-6 animate-spin text-violet-bright" /> Scoring your answers…
          </div>
        )}

        {phase === "result" && result && (
          <div className="space-y-3 py-2">
            <div className="flex flex-col items-center gap-2 text-center">
              <ShieldCheck className="h-10 w-10 text-success" />
              <p className="text-lg font-semibold text-foreground">Verified at {result.achievedLevel}%</p>
              <p className="text-sm text-muted-foreground">
                {result.achievedLevel > result.claimedLevel
                  ? `Up from your claimed ${result.claimedLevel}% — nice work!`
                  : result.achievedLevel < result.claimedLevel
                    ? `You claimed ${result.claimedLevel}% — your real level looks closer to ${result.achievedLevel}%.`
                    : `Matches your claimed ${result.claimedLevel}%.`}
              </p>
              <p className="text-xs text-muted-foreground">
                Your skill is now set to this verified level. Work on it and re-take the viva to raise
                it — next time it starts from {result.achievedLevel}%.
              </p>
            </div>
            {result.feedback.length > 0 && (
              <div className="max-h-48 space-y-1.5 overflow-y-auto rounded-lg border border-border bg-elevated/40 p-2.5">
                {result.feedback.map((f, i) => (
                  <div key={i} className="flex items-start gap-2 text-xs">
                    {f.score >= 100 ? (
                      <CheckCircle2 className="mt-0.5 h-3.5 w-3.5 shrink-0 text-success" />
                    ) : (
                      <XCircle className="mt-0.5 h-3.5 w-3.5 shrink-0 text-risk" />
                    )}
                    <div className="min-w-0">
                      <p className="text-foreground">{f.question}</p>
                      {f.score < 100 && f.correctOption && (
                        <p className="text-muted-foreground">Correct: {f.correctOption}</p>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}

        {phase === "error" && (
          <div className="flex items-start gap-2 rounded-lg border border-risk/40 bg-risk/5 p-3 py-6 text-sm text-risk">
            <AlertTriangle className="mt-0.5 h-4 w-4 shrink-0" /> {errorMsg ?? "Something went wrong."}
          </div>
        )}

        <DialogFooter>
          {phase === "ready" && (
            <Button variant="glow" size="sm" onClick={beginAnswering}>
              Start viva (fullscreen)
            </Button>
          )}
          {phase === "answering" && (
            <Button variant="glow" size="sm" onClick={advance}>
              {index + 1 < total ? "Next question" : "Submit viva"}
            </Button>
          )}
          {(phase === "result" || phase === "error") && (
            <Button variant="outline" size="sm" onClick={() => { exitFullscreen(); onClose(); }}>
              Close
            </Button>
          )}
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}