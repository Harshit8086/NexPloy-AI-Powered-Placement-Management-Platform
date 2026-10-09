"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import Link from "next/link";
import {
  Loader2,
  Send,
  CheckCircle2,
  Mic,
  MicOff,
  Camera,
  CameraOff,
  Volume2,
  Code2,
  Clock,
  Video,
  X,
} from "lucide-react";
import { AsyncBoundary } from "@/components/common/AsyncBoundary";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { Button } from "@/components/ui/button";
import { Badge } from "@/components/ui/badge";
import { Progress } from "@/components/ui/progress";
import { Dialog, DialogContent } from "@/components/ui/dialog";
import { CircularTimer } from "@/components/interview/CircularTimer";
import { CodeEditor } from "@/components/interview/CodeEditor";
import { useApiResource } from "@/hooks/useApiResource";
import { useAppStore } from "@/hooks/useAppStore";
import { getPrimaryStudent } from "@/lib/api/students";
import {
  createSpeechRecognition,
  speechSynthesisAvailable,
  type SpeechRecognitionLike,
} from "@/lib/speech";
import {
  getMockInterview,
  submitMockInterviewAnswer,
  type MockInterview,
  type AnswerFeedback,
} from "@/lib/api/mockInterviews";

// The two prerecorded interviewer clips shipped in /public/interview. Which one plays follows the
// chosen synthesized voice's gender so the face on screen matches the voice the student hears.
const AVATAR_SRC = { female: "/interview/female-ai.mp4", male: "/interview/male-ai.mp4" } as const;
type VoiceGender = keyof typeof AVATAR_SRC;

export default function MockInterviewRunPage() {
  const params = useParams<{ id: string }>();
  const id = params.id;
  const router = useRouter();
  const pushToast = useAppStore((st) => st.pushToast);

  const { data, loading, error, reload } = useApiResource<MockInterview | null>(
    () => getMockInterview(id),
    [id],
    null,
  );

  // ── Answer + submission state ──
  const [answer, setAnswer] = useState("");
  const [language, setLanguage] = useState("javascript");
  const [submitting, setSubmitting] = useState(false);
  const [lastFeedback, setLastFeedback] = useState<AnswerFeedback | null>(null);

  // ── Code-editor modal ── the coding portion opens a full-screen editor; "Add to answer" appends
  // the drafted code (fenced) into the answer that gets submitted. Code is scored as text, never run.
  const [codeOpen, setCodeOpen] = useState(false);
  const [codeDraft, setCodeDraft] = useState("");

  // ── Video-call UX state ──
  const [micOn, setMicOn] = useState(false);
  const [cameraOn, setCameraOn] = useState(false);
  const [isAISpeaking, setIsAISpeaking] = useState(false);
  const [subtitle, setSubtitle] = useState("");
  const [voiceGender, setVoiceGender] = useState<VoiceGender>("female");
  const [voiceReady, setVoiceReady] = useState(false);
  const [firstName, setFirstName] = useState<string>("");

  // ── Refs (stable across renders; used so async callbacks avoid stale closures) ──
  const aiVideoRef = useRef<HTMLVideoElement>(null);
  const userVideoRef = useRef<HTMLVideoElement>(null);
  const recognitionRef = useRef<SpeechRecognitionLike | null>(null);
  const streamRef = useRef<MediaStream | null>(null);
  const selectedVoiceRef = useRef<SpeechSynthesisVoice | null>(null);
  const micOnRef = useRef(false);
  const isAISpeakingRef = useRef(false);
  const isRecognizingRef = useRef(false);
  const introSpokenRef = useRef(false);
  const spokenQuestionIdRef = useRef<string | null>(null);
  const answerRef = useRef("");

  // The current question is the one at data.currentQuestion (questions are ordinal-ordered).
  const current = useMemo(() => {
    if (!data) return null;
    return data.questions[data.currentQuestion] ?? null;
  }, [data]);

  const total = data?.questions.length ?? 0;
  const position = data ? Math.min(data.currentQuestion + 1, total) : 0;

  useEffect(() => {
    answerRef.current = answer;
  }, [answer]);
  useEffect(() => {
    micOnRef.current = micOn;
  }, [micOn]);

  // ── Mic (speech-to-text) helpers ── guard against start() while already running.
  const startMic = useCallback(() => {
    const rec = recognitionRef.current;
    if (rec && !isRecognizingRef.current) {
      try {
        rec.start();
      } catch {
        /* already starting */
      }
    }
  }, []);
  const stopMic = useCallback(() => {
    const rec = recognitionRef.current;
    if (rec && isRecognizingRef.current) {
      try {
        rec.stop();
      } catch {
        /* already stopped */
      }
    }
  }, []);

  // Initialise speech recognition once. Each final transcript is appended to the answer draft;
  // recognition auto-restarts after a silence timeout as long as the mic is on and the AI is silent.
  useEffect(() => {
    const rec = createSpeechRecognition();
    if (!rec) return;
    rec.lang = "en-US";
    rec.continuous = true;
    rec.interimResults = false;
    rec.onresult = (e) => {
      const results = e.results;
      const last = results[results.length - 1];
      const transcript = last?.[0]?.transcript ?? "";
      if (transcript) setAnswer((prev) => (prev ? `${prev} ${transcript}` : transcript));
    };
    rec.onstart = () => {
      isRecognizingRef.current = true;
    };
    rec.onend = () => {
      isRecognizingRef.current = false;
      if (micOnRef.current && !isAISpeakingRef.current) {
        try {
          rec.start();
        } catch {
          /* noop */
        }
      }
    };
    rec.onerror = (ev) => {
      if (ev.error !== "aborted") console.warn("SpeechRecognition error:", ev.error);
    };
    recognitionRef.current = rec;
  }, []);

  // Speak `text` with the selected voice; resolves when finished. While speaking, the avatar video
  // plays, the mic is paused (so the interviewer isn't transcribed), and a subtitle is shown.
  const speakText = useCallback(
    (text: string) =>
      new Promise<void>((resolve) => {
        const voice = selectedVoiceRef.current;
        if (!speechSynthesisAvailable() || !voice || !text.trim()) {
          resolve();
          return;
        }
        window.speechSynthesis.cancel();
        // Small delay so cancel() settles before the next utterance (a Chrome quirk).
        setTimeout(() => {
          const utter = new SpeechSynthesisUtterance(
            text.replace(/,/g, ", ... ").replace(/\./g, ". ... "),
          );
          utter.voice = voice;
          utter.rate = 0.95;
          utter.pitch = 1.05;
          utter.volume = 1;
          utter.onstart = () => {
            isAISpeakingRef.current = true;
            setIsAISpeaking(true);
            // Mute the student's mic while the interviewer speaks so their voice isn't transcribed.
            micOnRef.current = false;
            setMicOn(false);
            stopMic();
            aiVideoRef.current?.play().catch(() => {});
          };
          utter.onend = () => {
            aiVideoRef.current?.pause();
            isAISpeakingRef.current = false;
            setIsAISpeaking(false);
            // As soon as the interviewer stops speaking, turn the student's mic on automatically so
            // they can answer hands-free. Only when the browser supports recognition.
            if (recognitionRef.current) {
              micOnRef.current = true;
              setMicOn(true);
              startMic();
            }
            setTimeout(() => {
              setSubtitle("");
              resolve();
            }, 250);
          };
          setSubtitle(text);
          window.speechSynthesis.speak(utter);
        }, 150);
      }),
    [startMic, stopMic],
  );

  // Pick a synthesized voice once they're available. Voice list loads asynchronously in Chrome, so
  // we also listen for `voiceschanged`. The chosen voice's gender drives which avatar clip plays.
  useEffect(() => {
    if (!speechSynthesisAvailable()) return;
    const load = () => {
      const voices = window.speechSynthesis.getVoices();
      if (!voices.length) return;
      const female = voices.find((v) => /zira|samantha|female|zoe|karen/i.test(v.name));
      const male = voices.find((v) => /david|mark|male|daniel|alex/i.test(v.name));
      const chosen = female ?? male ?? voices.find((v) => v.lang?.startsWith("en")) ?? voices[0];
      selectedVoiceRef.current = chosen;
      setVoiceGender(female && chosen === female ? "female" : male && chosen === male ? "male" : "female");
      setVoiceReady(true);
    };
    load();
    window.speechSynthesis.onvoiceschanged = load;
    return () => {
      window.speechSynthesis.onvoiceschanged = null;
    };
  }, []);

  // The student's first name personalises the spoken intro. Advisory only — a failure just drops the
  // name from the greeting; it never affects scoring or eligibility.
  useEffect(() => {
    let cancelled = false;
    getPrimaryStudent()
      .then((student) => {
        if (!cancelled && student?.name) setFirstName(student.name.split(/\s+/)[0] ?? "");
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, []);

  // ── Camera toggle ── request the webcam on enable, release all tracks on disable/unmount.
  const stopCamera = useCallback(() => {
    streamRef.current?.getTracks().forEach((t) => t.stop());
    streamRef.current = null;
    if (userVideoRef.current) userVideoRef.current.srcObject = null;
  }, []);
  const toggleCamera = useCallback(async () => {
    if (cameraOn) {
      stopCamera();
      setCameraOn(false);
      return;
    }
    try {
      const stream = await navigator.mediaDevices.getUserMedia({ video: true, audio: false });
      streamRef.current = stream;
      if (userVideoRef.current) {
        userVideoRef.current.srcObject = stream;
        userVideoRef.current.play().catch(() => {});
      }
      setCameraOn(true);
    } catch {
      pushToast("Camera unavailable", "Couldn't access your camera — you can still answer by voice or typing.");
    }
  }, [cameraOn, stopCamera, pushToast]);

  // ── Mic toggle ── flips recognition on/off; while the AI is speaking, recognition stays paused and
  // resumes automatically (via onend) once the interviewer finishes.
  const toggleMic = useCallback(() => {
    if (!recognitionRef.current) {
      pushToast("Voice input unavailable", "This browser doesn't support speech recognition — type your answer instead.");
      return;
    }
    setMicOn((on) => {
      const next = !on;
      micOnRef.current = next;
      if (next && !isAISpeakingRef.current) startMic();
      else stopMic();
      return next;
    });
  }, [pushToast, startMic, stopMic]);

  // ── Intro: once voices are ready and the run has loaded, greet the student and read question one. ──
  useEffect(() => {
    if (!voiceReady || !data || !current || introSpokenRef.current) return;
    if (data.status !== "IN_PROGRESS") return;
    introSpokenRef.current = true;
    spokenQuestionIdRef.current = current.id;
    const greeting = firstName
      ? `Welcome ${firstName}! Let's begin your interview.`
      : "Welcome! Let's begin your interview.";
    let cancelled = false;
    const run = async () => {
      await new Promise((r) => setTimeout(r, 1000));
      if (cancelled) return;
      await speakText(greeting);
      await new Promise((r) => setTimeout(r, 600));
      if (cancelled) return;
      await speakText(current.prompt);
    };
    run();
    return () => {
      cancelled = true;
    };
  }, [voiceReady, data, current, firstName, speakText]);

  // ── Question change: after the intro, speak each new question as the run advances. ──
  useEffect(() => {
    if (!introSpokenRef.current || !current) return;
    if (spokenQuestionIdRef.current === current.id) return;
    spokenQuestionIdRef.current = current.id;
    speakText(current.prompt);
  }, [current, speakText]);

  // ── Cleanup on unmount: silence the interviewer, stop listening, release the camera. ──
  useEffect(() => {
    return () => {
      if (speechSynthesisAvailable()) window.speechSynthesis.cancel();
      micOnRef.current = false;
      try {
        recognitionRef.current?.stop();
      } catch {
        /* noop */
      }
      streamRef.current?.getTracks().forEach((t) => t.stop());
    };
  }, []);

  // ── Submit the current answer ── scores it server-side, speaks the feedback, then either advances
  // to the next question or navigates to the report when the run completes.
  const submitAnswer = useCallback(
    async (raw?: string) => {
      if (submitting || !data || !current) return;
      const text = (raw ?? answerRef.current).trim() || "No answer provided.";
      setSubmitting(true);
      stopMic();
      try {
        const res = await submitMockInterviewAnswer(id, text);
        setLastFeedback(res.feedback);
        setAnswer("");
        if (res.feedback?.feedback) await speakText(res.feedback.feedback);
        if (res.completed || res.status === "COMPLETED") {
          if (speechSynthesisAvailable()) window.speechSynthesis.cancel();
          router.push(`/student/mock-interview/${id}/report`);
          return;
        }
        await reload();
      } catch (e) {
        pushToast("Couldn't submit answer", e instanceof Error ? e.message : "Please try again.");
      } finally {
        setSubmitting(false);
      }
    },
    [submitting, data, current, id, speakText, reload, pushToast, stopMic, router],
  );

  // Timer expiry: announce it, then submit whatever's been captured (or a placeholder).
  const onTimeUp = useCallback(() => {
    if (submitting) return;
    const text = answerRef.current.trim() || "No answer provided. Time over.";
    speakText("Time is up. Submitting your answer now.").finally(() => submitAnswer(text));
  }, [submitting, speakText, submitAnswer]);

  const progress = total > 0 ? Math.round((position / total) * 100) : 0;
  const difficulty = current?.difficulty?.toLowerCase() ?? null;

  return (
    <div className="min-h-screen px-4 py-5 sm:px-6 lg:px-8">
      {/* Slim immersive header (the app sidebar/topbar are hidden on this route) */}
      <div className="mx-auto mb-5 flex max-w-6xl items-center justify-between gap-3">
        <div className="flex items-center gap-2">
          <span className="flex h-8 w-8 items-center justify-center rounded-lg bg-violet/15 text-violet-bright">
            <Video className="h-4 w-4" />
          </span>
          <div>
            <p className="text-sm font-semibold leading-tight text-foreground">AI Mock Interview</p>
            <p className="text-xs leading-tight text-muted-foreground">
              Practice only — never affects your placement record
            </p>
          </div>
        </div>
        <Button asChild variant="outline" size="sm">
          <Link href="/student/mock-interview">
            <X className="mr-1.5 h-4 w-4" /> Exit
          </Link>
        </Button>
      </div>

      <div className="mx-auto max-w-6xl">
        <AsyncBoundary
          loading={loading}
          error={error}
          empty={!data}
          onRetry={reload}
          emptyTitle="Interview unavailable"
          emptyMessage="This practice interview doesn't exist or isn't yours."
        >
        {data && current && (
          <div className="grid gap-5 lg:grid-cols-[36%_64%]">
            {/* ── Left: the video-call column ── */}
            <div className="space-y-4">
              {/* Interviewer tile */}
              <DepthCard className="overflow-hidden p-0">
                <div className="relative aspect-video bg-black">
                  <video
                    ref={aiVideoRef}
                    src={AVATAR_SRC[voiceGender]}
                    muted
                    playsInline
                    loop
                    className="h-full w-full object-cover"
                  />
                  <div className="absolute left-3 top-3 rounded-full bg-black/55 px-2.5 py-1 text-xs font-medium text-white backdrop-blur">
                    AI Interviewer
                  </div>
                  {isAISpeaking && (
                    <div className="absolute right-3 top-3 flex items-center gap-1.5 rounded-full bg-violet/80 px-2.5 py-1 text-xs font-semibold text-white shadow-glow backdrop-blur">
                      <Volume2 className="h-3.5 w-3.5" />
                      <span className="flex items-end gap-0.5">
                        {[0, 1, 2].map((i) => (
                          <span
                            key={i}
                            className="w-0.5 animate-pulse rounded-full bg-white"
                            style={{ height: `${6 + i * 3}px`, animationDelay: `${i * 120}ms` }}
                          />
                        ))}
                      </span>
                      AI Speaking
                    </div>
                  )}
                  {/* Subtitle overlay — what the interviewer is currently saying */}
                  {subtitle && (
                    <div className="absolute inset-x-0 bottom-0 bg-gradient-to-t from-black/85 to-transparent px-4 pb-3 pt-8">
                      <p className="text-center text-sm leading-snug text-white">{subtitle}</p>
                    </div>
                  )}
                </div>
              </DepthCard>

              {/* Student self-view (mirrored) */}
              <DepthCard className="overflow-hidden p-0">
                <div className="relative aspect-video bg-muted/40">
                  <video
                    ref={userVideoRef}
                    muted
                    playsInline
                    className="h-full w-full scale-x-[-1] object-cover"
                  />
                  <div className="absolute left-3 top-3 rounded-full bg-black/55 px-2.5 py-1 text-xs font-medium text-white backdrop-blur">
                    {firstName ? firstName : "You"}
                  </div>
                  {!cameraOn && (
                    <div className="absolute inset-0 flex flex-col items-center justify-center gap-1 text-xs text-muted-foreground">
                      <CameraOff className="h-6 w-6 opacity-60" />
                      Camera off
                    </div>
                  )}
                </div>
              </DepthCard>

              {/* Icon-only call controls */}
              <div className="flex items-center justify-center gap-3">
                <ControlButton
                  active={micOn}
                  onClick={toggleMic}
                  label={micOn ? "Mute mic" : "Unmute mic"}
                >
                  {micOn ? <Mic className="h-5 w-5" /> : <MicOff className="h-5 w-5" />}
                </ControlButton>
                <ControlButton
                  active={cameraOn}
                  onClick={toggleCamera}
                  label={cameraOn ? "Turn camera off" : "Turn camera on"}
                >
                  {cameraOn ? <Camera className="h-5 w-5" /> : <CameraOff className="h-5 w-5" />}
                </ControlButton>
                {current.coding && (
                  <ControlButton active={codeOpen} onClick={() => setCodeOpen(true)} label="Open code editor">
                    <Code2 className="h-5 w-5" />
                  </ControlButton>
                )}
              </div>

              {isAISpeaking && (
                <p className="flex items-center justify-center gap-1.5 text-xs font-medium text-risk">
                  <MicOff className="h-3.5 w-3.5" /> Mic paused — AI is speaking
                </p>
              )}
            </div>

            {/* ── Right: the AI Interview panel ── */}
            <div className="space-y-4">
              <DepthCard className="p-5">
                {/* Header row: title + difficulty | circular countdown ring */}
                <div className="mb-4 flex items-start justify-between gap-4">
                  <div>
                    <h1 className="text-lg font-semibold text-foreground">AI Interview</h1>
                    <p className="mt-0.5 text-xs text-muted-foreground">
                      {data.role ? `${data.role} · ` : ""}
                      {difficulty ? (
                        <span className="capitalize">{difficulty} difficulty</span>
                      ) : (
                        "Practice session"
                      )}
                    </p>
                  </div>
                  {current.timerSeconds ? (
                    <CircularTimer seconds={current.timerSeconds} resetKey={current.id} onExpire={onTimeUp} />
                  ) : (
                    <span className="flex items-center gap-1 text-xs text-muted-foreground">
                      <Clock className="h-3.5 w-3.5" /> Untimed
                    </span>
                  )}
                </div>

                {/* Progress */}
                <div className="mb-1 flex items-center justify-between text-xs font-medium text-muted-foreground">
                  <span>Progress</span>
                  <span className="tabular-nums">
                    {position}/{total}
                  </span>
                </div>
                <Progress value={progress} className="mb-4" />

                {/* Question */}
                <div className="mb-2 flex flex-wrap items-center gap-2">
                  {current.coding && (
                    <Badge variant="default">
                      <Code2 className="mr-1 h-3 w-3" /> Coding
                    </Badge>
                  )}
                  {difficulty && <Badge variant="muted" className="capitalize">{difficulty}</Badge>}
                </div>
                <p className="text-[15px] font-medium leading-relaxed text-foreground">{current.prompt}</p>
              </DepthCard>

              {/* Answer */}
              <DepthCard className="p-5">
                <div className="mb-2 flex items-center justify-between gap-2">
                  <label className="text-sm font-semibold text-foreground">Your Answer</label>
                  {current.coding && (
                    <Button type="button" variant="outline" size="sm" onClick={() => setCodeOpen(true)}>
                      <Code2 className="mr-1.5 h-3.5 w-3.5" /> Code editor
                    </Button>
                  )}
                </div>
                <textarea
                  value={answer}
                  onChange={(e) => setAnswer(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
                      e.preventDefault();
                      submitAnswer();
                    }
                  }}
                  placeholder={
                    current.coding
                      ? "Explain your approach here, then use the code editor to add your solution…"
                      : "Speak with the mic on, or type your answer here…"
                  }
                  rows={current.coding ? 6 : 9}
                  className="w-full resize-y rounded-lg border border-border bg-background px-3 py-2 text-sm text-foreground outline-none focus:border-violet/50"
                />

                <div className="mt-3 flex items-center justify-between gap-3">
                  <p className="text-xs text-muted-foreground">
                    Press <kbd className="rounded border border-border bg-muted px-1 py-0.5 text-[10px] font-medium">Ctrl+Enter</kbd> to submit
                  </p>
                  <Button type="button" onClick={() => submitAnswer()} disabled={submitting}>
                    {submitting ? (
                      <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
                    ) : position >= total ? (
                      <CheckCircle2 className="mr-1.5 h-4 w-4" />
                    ) : (
                      <Send className="mr-1.5 h-4 w-4" />
                    )}
                    {position >= total ? "Submit & Finish" : "Submit Answer"}
                  </Button>
                </div>
              </DepthCard>

              {/* AI feedback (green) */}
              {lastFeedback && (
                <div className="rounded-xl border border-success/40 bg-success/10 p-5">
                  <div className="mb-1.5 flex items-center justify-between gap-2">
                    <span className="flex items-center gap-1.5 text-sm font-bold uppercase tracking-wide text-success">
                      <CheckCircle2 className="h-4 w-4" /> AI Feedback
                    </span>
                    <Badge variant="muted">{lastFeedback.score}/100</Badge>
                  </div>
                  <p className="text-sm text-foreground/90">{lastFeedback.feedback}</p>
                  {lastFeedback.improvements.length > 0 && (
                    <ul className="mt-2 list-inside list-disc space-y-0.5 text-xs text-muted-foreground">
                      {lastFeedback.improvements.map((imp, i) => (
                        <li key={i}>{imp}</li>
                      ))}
                    </ul>
                  )}
                </div>
              )}
            </div>
          </div>
        )}
        </AsyncBoundary>
      </div>

      {/* ── Full-screen code editor modal ── */}
      <Dialog open={codeOpen} onOpenChange={setCodeOpen}>
        <DialogContent className="max-w-4xl">
          <div className="mb-3">
            <h2 className="text-base font-semibold text-foreground">Code editor</h2>
            <p className="text-xs text-muted-foreground">
              Write your solution, then add it to your answer. Code is reviewed as text — it is not executed.
            </p>
          </div>
          <CodeEditor
            value={codeDraft}
            onChange={setCodeDraft}
            language={language}
            onLanguageChange={setLanguage}
            height={400}
          />
          <div className="mt-3 flex items-center justify-end gap-2">
            <Button type="button" variant="ghost" onClick={() => setCodeOpen(false)}>
              Cancel
            </Button>
            <Button
              type="button"
              disabled={!codeDraft.trim()}
              onClick={() => {
                const block = `\n\n\`\`\`${language}\n${codeDraft.trim()}\n\`\`\`\n`;
                setAnswer((prev) => (prev ? prev + block : block.trimStart()));
                setCodeOpen(false);
              }}
            >
              <Code2 className="mr-1.5 h-4 w-4" /> Add to Answer
            </Button>
          </div>
        </DialogContent>
      </Dialog>
    </div>
  );
}

/** A round icon control for the video-call bar (mic / camera / code). */
function ControlButton({
  active,
  onClick,
  label,
  children,
}: {
  active: boolean;
  onClick: () => void;
  label: string;
  children: React.ReactNode;
}) {
  return (
    <button
      type="button"
      onClick={onClick}
      aria-label={label}
      title={label}
      className={
        "flex h-11 w-11 items-center justify-center rounded-full border transition-colors " +
        (active
          ? "border-violet/50 bg-violet/20 text-violet-bright shadow-glow"
          : "border-border bg-muted/40 text-muted-foreground hover:text-foreground")
      }
    >
      {children}
    </button>
  );
}

