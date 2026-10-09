"use client";

import { useEffect, useRef, useState } from "react";
import { useReactToPrint } from "react-to-print";
import { useRouter } from "next/navigation";
import { ArrowLeft, ArrowRight, Download, Eye, Loader2, Pencil, RotateCcw, UserRoundCheck, Wand2 } from "lucide-react";
import { DepthCard } from "@/components/intelligence/DepthCard";
import { Button } from "@/components/ui/button";
import { ATSTemplate } from "@/components/resume/ATSTemplate";
import { ResumeWizardSteps, WIZARD_STEPS, WIZARD_STEP_COUNT } from "@/components/resume/ResumeWizardSteps";
import { emptyResume, mergeSkillsCsv, resumeDataToText, type ResumeData } from "@/components/resume/resumeBuilderTypes";
import { getPrimaryStudent, listSkills, analyzeResumeFile } from "@/lib/api/students";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";

const LEGACY_DRAFT_KEY = "nexploy.resumeBuilder.draft";
const getDraftKey = (studentId: string) => `nexploy.resumeBuilder.draft.${studentId}`;

/**
 * Map the student's CampusLink profile + recorded skills into the résumé authoring model. This is
 * a one-way prefill convenience — the builder still lives entirely in the browser and nothing is
 * written back. Fields CampusLink doesn't track (LinkedIn/GitHub/college/degree) are left blank for
 * the student to fill; education is derived from branch + graduation year + CGPA.
 */
async function buildPrefill(): Promise<{ prefill: ResumeData; studentId: string; skillNames: string[] }> {
  const [student, skills] = await Promise.all([
    getPrimaryStudent(),
    listSkills().catch(() => []),
  ]);
  // Recorded skills first (student-rated), then résumé strengths not already covered.
  const skillNames = [
    ...skills.map((s) => s.skill),
    ...student.strengths.filter((s) => !skills.some((k) => k.skill.toLowerCase() === s.toLowerCase())),
  ];
  return {
    studentId: student.id,
    skillNames,
    prefill: {
      ...emptyResume,
      name: student.name ?? "",
      email: student.email ?? "",
      phone: student.phone ?? "",
      summary: student.targetRole ? `Aspiring ${student.targetRole}.` : "",
      skills: skillNames.join(", "),
      experience: student.experience.map((e) => ({
        role: e.role,
        company: e.company,
        duration: e.duration,
        description: e.summary,
      })),
      projects: student.projects.map((p) => ({
        name: p.name,
        github: "",
        liveLink: "",
        techStack: p.stack.join(", "),
        description: p.summary,
      })),
      education: [
        {
          degree: "",
          branch: student.branch ?? "",
          college: "",
          year: student.graduationYear ? String(student.graduationYear) : "",
          cgpa: student.cgpa ? String(student.cgpa) : "",
        },
      ],
    },
  };
}

/**
 * A guided 6-step résumé wizard (Personal → Summary → Skills → Experience → Projects → Education)
 * followed by an ATS preview + PDF export, mirroring the FresherAI flow. Entirely client-side: the
 * draft is stored in localStorage and never uploaded, and the PDF is produced in-browser via
 * react-to-print. CampusLink stays the source of truth — this only authors a document.
 */
export default function ResumeBuilderPage() {
  const pushToast = useAppStore((st) => st.pushToast);
  const setPendingResumeAnalysis = useAppStore((st) => st.setPendingResumeAnalysis);
  const router = useRouter();
  const [data, setData] = useState<ResumeData>(emptyResume);
  const [currentStudentId, setCurrentStudentId] = useState<string | null>(null);
  const [step, setStep] = useState(0); // 0-5 wizard step
  const [preview, setPreview] = useState(false);
  const [prefilling, setPrefilling] = useState(false);
  const [analyzing, setAnalyzing] = useState(false);
  const printRef = useRef<HTMLDivElement>(null);

  // Restore draft for the CURRENT signed-in student, or prefill from their profile.
  // Purges legacy un-scoped draft key so it never leaks between different accounts.
  useEffect(() => {
    let cancelled = false;

    try {
      localStorage.removeItem(LEGACY_DRAFT_KEY);
    } catch {
      // ignore
    }

    buildPrefill()
      .then(({ prefill, studentId, skillNames }) => {
        if (cancelled) return;
        setCurrentStudentId(studentId);

        // Check for a saved draft belonging to THIS student
        try {
          const raw = localStorage.getItem(getDraftKey(studentId));
          if (raw) {
            const draft = { ...emptyResume, ...JSON.parse(raw) } as ResumeData;
            // Auto-sync: fold any newly-added skills into the draft without clobbering the
            // student's own edits (union, case-insensitive). Everything else stays as drafted.
            setData({ ...draft, skills: mergeSkillsCsv(draft.skills, skillNames) });
            return;
          }
        } catch {
          // ignore malformed storage
        }

        // No draft for this student -> populate with their live profile data
        setData(prefill);
      })
      .catch(() => {
        // Not signed in as a student / profile unavailable
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // Persist the draft as the student edits, scoped strictly to the signed-in student
  useEffect(() => {
    if (!currentStudentId) return;
    try {
      localStorage.setItem(getDraftKey(currentStudentId), JSON.stringify(data));
    } catch {
      // storage may be full or blocked — non-fatal
    }
  }, [data, currentStudentId]);

  const handlePrint = useReactToPrint({
    contentRef: printRef,
    documentTitle: data.name ? `${data.name} — Résumé` : "Résumé",
  });

  const onReset = () => {
    setData(emptyResume);
    setStep(0);
    setPreview(false);
    if (currentStudentId) {
      try {
        localStorage.removeItem(getDraftKey(currentStudentId));
      } catch {
        // non-fatal
      }
    }
    pushToast("Draft cleared", "Your résumé builder has been reset.");
  };

  const onPrefill = async () => {
    setPrefilling(true);
    try {
      const { prefill, studentId } = await buildPrefill();
      setCurrentStudentId(studentId);
      setData(prefill);
      pushToast("Prefilled from your profile", "Review and edit — nothing is saved to the server.");
    } catch (e) {
      pushToast("Couldn't prefill", e instanceof Error ? e.message : "Sign in as a student and try again.");
    } finally {
      setPrefilling(false);
    }
  };

  // One-click: turn the built résumé into a text file and run it through the SAME Resume
  // Intelligence analyze endpoint a manual upload uses, then hand the result to that page.
  const onAnalyze = async () => {
    const text = resumeDataToText(data);
    // Needs real content for a meaningful analysis — the backend also rejects an empty file.
    if (text.replace(/\s/g, "").length < 40) {
      pushToast("Add more detail first", "Fill in your résumé (skills, experience, projects) before analyzing.");
      return;
    }
    setAnalyzing(true);
    try {
      const fileName = `${(data.name || "resume").trim().replace(/\s+/g, "_") || "resume"}.txt`;
      const file = new File([text], fileName, { type: "text/plain" });
      const analysis = await analyzeResumeFile(file);
      // Hand the fresh analysis to the Resume Intelligence page, which shows it on mount.
      setPendingResumeAnalysis(analysis);
      router.push("/student/resume");
    } catch (e) {
      pushToast("Couldn't analyze", e instanceof Error ? e.message : "Please try again.");
      setAnalyzing(false); // on success we navigate away, so only reset on failure
    }
  };

  const progress = Math.round(((step + 1) / WIZARD_STEP_COUNT) * 100);
  const stepMeta = WIZARD_STEPS[step];

  // ── Preview screen ─────────────────────────────────────────────────────────
  if (preview) {
    return (
      <div className="min-h-screen bg-background">
        {/* Immersive Top Bar */}
        <header className="sticky top-0 z-30 border-b border-border/80 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
          <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
            <div className="flex items-center gap-3">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => router.push("/student/resume")}
                className="gap-2 text-muted-foreground hover:text-foreground"
              >
                <ArrowLeft className="h-4 w-4" />
                <span className="font-medium">Back to Resume</span>
              </Button>
              <div className="h-4 w-px bg-border hidden sm:block" />
              <div className="hidden sm:flex items-center gap-2">
                <h1 className="text-sm font-semibold text-foreground">ATS Resume Builder</h1>
                <span className="rounded-full bg-violet/10 px-2 py-0.5 text-[10px] font-medium text-violet-bright border border-violet/20">
                  Preview Mode
                </span>
              </div>
            </div>
            <div className="flex flex-wrap items-center gap-2">
              <Button variant="outline" size="sm" onClick={() => setPreview(false)}>
                <Pencil className="mr-1.5 h-3.5 w-3.5" /> Back to Edit
              </Button>
              <Button variant="outline" size="sm" onClick={onAnalyze} disabled={analyzing}>
                {analyzing ? (
                  <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
                ) : (
                  <Wand2 className="mr-1.5 h-3.5 w-3.5" />
                )}
                {analyzing ? "Analyzing…" : "Analyze Score"}
              </Button>
              <Button size="sm" onClick={() => handlePrint()} className="bg-violet hover:bg-violet/90 text-white shadow-sm">
                <Download className="mr-1.5 h-3.5 w-3.5" /> Download PDF
              </Button>
            </div>
          </div>
        </header>

        <main className="mx-auto max-w-5xl px-4 py-8 sm:px-6 lg:px-8">
          <div className="mb-4">
            <h2 className="text-lg font-semibold text-foreground">Resume Preview</h2>
            <p className="text-sm text-muted-foreground">Review your formatted ATS-compliant resume before downloading.</p>
          </div>
          {/* The preview is scaled to fit; react-to-print prints the full-size node. */}
          <div className="overflow-auto rounded-xl border border-border bg-muted/30 p-4 sm:p-8 shadow-sm">
            <div className="mx-auto w-fit origin-top scale-[0.62] sm:scale-90 lg:scale-100">
              <ATSTemplate ref={printRef} data={data} />
            </div>
          </div>
        </main>
      </div>
    );
  }

  // ── Wizard screen ──────────────────────────────────────────────────────────
  return (
    <div className="min-h-screen bg-background">
      {/* Immersive Top Bar */}
      <header className="sticky top-0 z-30 border-b border-border/80 bg-background/95 backdrop-blur supports-[backdrop-filter]:bg-background/80">
        <div className="mx-auto flex h-16 max-w-7xl items-center justify-between px-4 sm:px-6 lg:px-8">
          <div className="flex items-center gap-3">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => router.push("/student/resume")}
              className="gap-2 text-muted-foreground hover:text-foreground"
            >
              <ArrowLeft className="h-4 w-4" />
              <span className="font-medium">Back to Resume</span>
            </Button>
            <div className="h-4 w-px bg-border hidden sm:block" />
            <div className="hidden sm:flex items-center gap-2">
              <h1 className="text-sm font-semibold text-foreground">ATS Resume Builder</h1>
              <span className="rounded-full bg-violet/10 px-2 py-0.5 text-[10px] font-medium text-violet-bright border border-violet/20">
                Step {step + 1} of {WIZARD_STEP_COUNT}
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <Button variant="outline" size="sm" onClick={onPrefill} disabled={prefilling}>
              {prefilling ? (
                <Loader2 className="mr-1.5 h-3.5 w-3.5 animate-spin" />
              ) : (
                <UserRoundCheck className="mr-1.5 h-3.5 w-3.5" />
              )}
              Prefill
            </Button>
            <Button variant="outline" size="sm" onClick={onReset}>
              <RotateCcw className="mr-1.5 h-3.5 w-3.5" /> Reset
            </Button>
            <Button variant="outline" size="sm" onClick={() => setPreview(true)} className="gap-1.5">
              <Eye className="h-3.5 w-3.5" /> Preview
            </Button>
          </div>
        </div>
      </header>

      <main className="mx-auto max-w-2xl px-4 py-8 sm:px-6">
        <DepthCard className="p-6 sm:p-8">
          <div className="mb-1.5 flex items-center justify-between text-xs font-semibold">
            <span className="uppercase tracking-wide text-muted-foreground">
              Step {step + 1} of {WIZARD_STEP_COUNT}
            </span>
            <span className="text-violet-bright">{progress}% complete</span>
          </div>
          <div className="mb-6 h-2 w-full overflow-hidden rounded-full bg-muted/60">
            <div
              className="h-full rounded-full bg-violet transition-all duration-300"
              style={{ width: `${progress}%` }}
            />
          </div>

          <h2 className="text-lg font-semibold text-foreground">{stepMeta.title}</h2>
          <p className="mb-5 text-sm text-muted-foreground">{stepMeta.subtitle}</p>

          <ResumeWizardSteps step={step} data={data} onChange={setData} />

          <div className="mt-7 flex items-center justify-between">
            <Button
              variant="outline"
              size="sm"
              disabled={step === 0}
              onClick={() => setStep((s) => Math.max(0, s - 1))}
            >
              <ArrowLeft className="mr-1.5 h-4 w-4" /> Previous
            </Button>
            <div className="flex items-center gap-1.5">
              {WIZARD_STEPS.map((_, i) => (
                <button
                  key={i}
                  type="button"
                  aria-label={`Go to step ${i + 1}`}
                  onClick={() => setStep(i)}
                  className={cn(
                    "h-2 rounded-full transition-all",
                    i === step ? "w-5 bg-violet" : i < step ? "w-2 bg-violet/50" : "w-2 bg-muted",
                  )}
                />
              ))}
            </div>
            {step < WIZARD_STEP_COUNT - 1 ? (
              <Button size="sm" onClick={() => setStep((s) => Math.min(WIZARD_STEP_COUNT - 1, s + 1))}>
                Next <ArrowRight className="ml-1.5 h-4 w-4" />
              </Button>
            ) : (
              <Button size="sm" onClick={() => setPreview(true)}>
                <Eye className="mr-1.5 h-4 w-4" /> Preview Resume
              </Button>
            )}
          </div>
        </DepthCard>

        <div className="mt-4 text-center">
          <Button variant="ghost" size="sm" onClick={onAnalyze} disabled={analyzing}>
            {analyzing ? (
              <Loader2 className="mr-1.5 h-4 w-4 animate-spin" />
            ) : (
              <Wand2 className="mr-1.5 h-4 w-4" />
            )}
            {analyzing ? "Analyzing…" : "Analyze in Resume Intelligence"}
          </Button>
        </div>
      </main>
    </div>
  );
}
