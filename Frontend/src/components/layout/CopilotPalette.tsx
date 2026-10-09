"use client";

import { useEffect, useRef, useState } from "react";
import { motion } from "framer-motion";
import { Sparkles, CornerDownLeft, User } from "lucide-react";
import { CommandDialog } from "@/components/ui/command";
import { ScrollArea } from "@/components/ui/scroll-area";
import { useAppStore } from "@/hooks/useAppStore";
import { useRole } from "@/hooks/useRole";
import { useCopilot } from "@/hooks/useCopilot";
import { IntelligencePulse } from "@/components/intelligence/IntelligencePulse";
import { cn } from "@/lib/utils";

export function CopilotPalette() {
  const { role } = useRole();
  const copilotOpen = useAppStore((s) => s.copilotOpen);
  const setCopilotOpen = useAppStore((s) => s.setCopilotOpen);
  const { messages, thinking, suggestions, ask, reset } = useCopilot(role);
  const inputRef = useRef<HTMLInputElement>(null);
  const [pulsingId, setPulsingId] = useState<string | null>(null);

  // Trigger the Intelligence Pulse on the newest assistant response — the
  // Copilot is one of the six actions that must fire the signature effect.
  useEffect(() => {
    const last = messages[messages.length - 1];
    if (last?.role === "assistant") {
      setPulsingId(last.id);
      const t = window.setTimeout(() => setPulsingId(null), 900);
      return () => window.clearTimeout(t);
    }
  }, [messages]);

  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.metaKey || e.ctrlKey) && e.key.toLowerCase() === "k") {
        e.preventDefault();
        setCopilotOpen(!copilotOpen);
      }
      if (e.key === "Escape") setCopilotOpen(false);
    };
    window.addEventListener("keydown", handler);
    return () => window.removeEventListener("keydown", handler);
  }, [copilotOpen, setCopilotOpen]);

  const handleSubmit = (value: string) => {
    if (!value.trim()) return;
    ask(value);
    if (inputRef.current) inputRef.current.value = "";
  };

  return (
    <CommandDialog
      open={copilotOpen}
      onOpenChange={(open) => {
        setCopilotOpen(open);
        if (!open) reset();
      }}
    >
      <div className="flex items-center gap-2 border-b border-border px-3">
        <Sparkles className="h-4 w-4 shrink-0 text-violet-bright" />
        <input
          ref={inputRef}
          autoFocus
          placeholder="Ask Nexploy anything..."
          className="flex h-12 w-full bg-transparent py-3 text-sm outline-none placeholder:text-muted-foreground"
          onKeyDown={(e) => {
            if (e.key === "Enter") handleSubmit(e.currentTarget.value);
          }}
        />
        <kbd className="hidden shrink-0 rounded border border-border px-1.5 py-0.5 text-[10px] text-muted-foreground sm:inline">
          Esc
        </kbd>
      </div>

      <div className="p-3">
        {messages.length === 0 ? (
          <div className="space-y-1 py-2">
            <p className="px-1 pb-2 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground/60">
              Try asking
            </p>
            {suggestions.map((q) => (
              <button
                key={q}
                onClick={() => handleSubmit(q)}
                className="flex w-full items-center gap-2 rounded-md px-2 py-2.5 text-left text-sm text-foreground hover:bg-accent"
              >
                <CornerDownLeft className="h-3.5 w-3.5 text-muted-foreground" />
                {q}
              </button>
            ))}
          </div>
        ) : (
          <ScrollArea className="max-h-80">
            <div className="space-y-3 px-1 py-1">
              {messages.map((m) => (
                <motion.div
                  key={m.id}
                  initial={{ opacity: 0, y: 8 }}
                  animate={{ opacity: 1, y: 0 }}
                  transition={{ duration: 0.25, ease: "easeOut" }}
                  className={cn("flex gap-2.5", m.role === "user" && "justify-end")}
                >
                  {m.role === "assistant" && (
                    <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-violet/15">
                      <Sparkles className="h-3 w-3 text-violet-bright" />
                    </div>
                  )}
                  <IntelligencePulse active={m.id === pulsingId} className={cn(m.role === "user" && "max-w-[80%]")}>
                    <div
                      className={cn(
                        "rounded-lg px-3 py-2 text-sm leading-relaxed",
                        m.role === "user" ? "bg-primary/15 text-foreground" : "bg-elevated text-foreground",
                      )}
                    >
                      {m.text}
                    </div>
                  </IntelligencePulse>
                  {m.role === "user" && (
                    <div className="mt-0.5 flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-muted">
                      <User className="h-3 w-3 text-muted-foreground" />
                    </div>
                  )}
                </motion.div>
              ))}
              {thinking && (
                <div className="flex items-center gap-2.5">
                  <div className="flex h-6 w-6 shrink-0 items-center justify-center rounded-full bg-violet/15">
                    <Sparkles className="h-3 w-3 animate-pulse-glow text-violet-bright" />
                  </div>
                  <div className="flex gap-1 rounded-lg bg-elevated px-3 py-2.5">
                    <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:-0.3s]" />
                    <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground [animation-delay:-0.15s]" />
                    <span className="h-1.5 w-1.5 animate-bounce rounded-full bg-muted-foreground" />
                  </div>
                </div>
              )}
            </div>
          </ScrollArea>
        )}
      </div>
    </CommandDialog>
  );
}
