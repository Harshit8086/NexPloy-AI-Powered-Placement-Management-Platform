"use client";

import { useEffect, useState } from "react";
import { Moon, Sun } from "lucide-react";
import { useTheme } from "next-themes";
import { Button } from "@/components/ui/button";

/**
 * Light/dark switch. Dark is the app's default look — this only ever
 * flips between the two explicit themes, no "system" option, since
 * NEXPLOY doesn't want light mode silently kicking in from someone's OS
 * setting behind their back.
 */
export function ThemeToggle({ className }: { className?: string }) {
  const { resolvedTheme, setTheme } = useTheme();
  const [mounted, setMounted] = useState(false);

  // The real theme only exists client-side (it lives in localStorage), so
  // render a neutral placeholder until after mount instead of guessing —
  // guessing wrong here is exactly what causes hydration mismatches.
  useEffect(() => setMounted(true), []);

  const isLight = mounted && resolvedTheme === "light";

  return (
    <Button
      variant="ghost"
      size="icon"
      className={className}
      aria-label={mounted ? `Switch to ${isLight ? "dark" : "light"} mode` : "Toggle theme"}
      onClick={() => setTheme(isLight ? "dark" : "light")}
    >
      {mounted ? (
        isLight ? <Moon className="h-4 w-4" /> : <Sun className="h-4 w-4" />
      ) : (
        <Sun className="h-4 w-4 opacity-0" />
      )}
    </Button>
  );
}
