"use client";

import dynamic from "next/dynamic";
import { Loader2 } from "lucide-react";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";

// Monaco is a heavy, browser-only editor: load it lazily and never on the server (ssr:false),
// matching the plan's mitigation for its SSR-unfriendliness.
const MonacoEditor = dynamic(() => import("@monaco-editor/react"), {
  ssr: false,
  loading: () => (
    <div className="flex h-full items-center justify-center gap-2 text-sm text-muted-foreground">
      <Loader2 className="h-4 w-4 animate-spin" /> Loading editor…
    </div>
  ),
});

const LANGUAGES = ["javascript", "typescript", "python", "java", "cpp", "go"] as const;
export type CodeLanguage = (typeof LANGUAGES)[number];

/**
 * The coding-question editor for a mock interview. It captures the student's code as text — nothing
 * is ever executed (the backend scores code as text via the LLM), so there is no runtime/RCE surface
 * here. Controlled: the parent owns `value`/`language` and persists them into the answer payload.
 */
export function CodeEditor({
  value,
  onChange,
  language,
  onLanguageChange,
  height = 340,
  readOnly = false,
}: {
  value: string;
  onChange: (next: string) => void;
  language: string;
  onLanguageChange?: (next: string) => void;
  height?: number;
  readOnly?: boolean;
}) {
  const lang = (LANGUAGES as readonly string[]).includes(language) ? language : "javascript";

  return (
    <div className="overflow-hidden rounded-lg border border-border">
      <div className="flex items-center justify-between gap-2 border-b border-border bg-muted/40 px-3 py-2">
        <span className="text-xs font-medium text-muted-foreground">Code editor</span>
        {onLanguageChange && !readOnly ? (
          <Select value={lang} onValueChange={onLanguageChange}>
            <SelectTrigger className="h-7 w-[140px] text-xs">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {LANGUAGES.map((l) => (
                <SelectItem key={l} value={l} className="text-xs">
                  {l}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        ) : (
          <span className="text-xs text-muted-foreground">{lang}</span>
        )}
      </div>
      <MonacoEditor
        height={height}
        language={lang}
        theme="vs-dark"
        value={value}
        onChange={(next) => onChange(next ?? "")}
        options={{
          readOnly,
          minimap: { enabled: false },
          fontSize: 13,
          scrollBeyondLastLine: false,
          automaticLayout: true,
          tabSize: 2,
          wordWrap: "on",
        }}
      />
      <p className="border-t border-border bg-muted/40 px-3 py-1.5 text-[11px] text-muted-foreground">
        Your code is reviewed as text by the interviewer — it is not executed.
      </p>
    </div>
  );
}
