// ============================================================
// BROWSER SPEECH HELPERS — thin, typed wrappers over the Web Speech APIs used by the AI mock
// interview (the interviewer speaks questions/feedback aloud, and the student may answer by voice).
// Everything here is browser-only and feature-detected: on a browser without support the helpers
// return null / no-op, so the interview still works as a typed experience. Nothing is sent anywhere —
// speech synthesis and recognition run entirely on-device.
// ============================================================

/** Minimal shape of a SpeechRecognition result event — enough to read the latest transcript. */
export interface RecognitionResultEvent {
  results: ArrayLike<ArrayLike<{ transcript: string }>>;
}

/** The subset of the (vendor-prefixed) SpeechRecognition interface we drive. */
export interface SpeechRecognitionLike {
  lang: string;
  continuous: boolean;
  interimResults: boolean;
  onresult: ((e: RecognitionResultEvent) => void) | null;
  onstart: (() => void) | null;
  onend: (() => void) | null;
  onerror: ((e: { error: string }) => void) | null;
  start(): void;
  stop(): void;
}

/**
 * Construct a SpeechRecognition instance if the browser supports it (Chrome/Edge expose
 * `webkitSpeechRecognition`). Returns null when unavailable so callers degrade to typing only.
 */
export function createSpeechRecognition(): SpeechRecognitionLike | null {
  if (typeof window === "undefined") return null;
  const w = window as unknown as {
    webkitSpeechRecognition?: new () => SpeechRecognitionLike;
    SpeechRecognition?: new () => SpeechRecognitionLike;
  };
  const Ctor = w.webkitSpeechRecognition ?? w.SpeechRecognition;
  return Ctor ? new Ctor() : null;
}

/** True when speech synthesis (the interviewer's voice) is available in this browser. */
export function speechSynthesisAvailable(): boolean {
  return typeof window !== "undefined" && "speechSynthesis" in window;
}
