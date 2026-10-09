"use client";

import { useEffect } from "react";
import { usePathname } from "next/navigation";
import { SidebarNav } from "@/components/layout/Sidebar";
import { Topbar } from "@/components/layout/Topbar";
import { CopilotPalette } from "@/components/layout/CopilotPalette";
import { AmbientBackground } from "@/components/intelligence/AmbientBackground";
import { PageTransition } from "@/components/intelligence/PageTransition";
import { useAppStore } from "@/hooks/useAppStore";
import { cn } from "@/lib/utils";

// Immersive routes run full-screen with no sidebar or topbar — e.g. the live mock-interview
// run page (`/student/mock-interview/{id}`) and the ATS Resume Builder (`/student/resume/builder`).
const IMMERSIVE_PATTERNS = [
  /^\/student\/mock-interview\/[^/]+$/,
  /^\/student\/resume\/builder(\/.*)?$/,
];

export function AppShell({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const immersive = IMMERSIVE_PATTERNS.some((re) => re.test(pathname ?? ""));

  const sidebarCollapsed = useAppStore((s) => s.sidebarCollapsed);
  const setSidebarCollapsed = useAppStore((s) => s.setSidebarCollapsed);
  const toggleSidebar = useAppStore((s) => s.toggleSidebar);

  // Restore the stored collapse preference after mount. Doing it here (not at store init) keeps
  // the server and first client render identical (both expanded), so hydration never mismatches.
  useEffect(() => {
    try {
      const stored = window.localStorage.getItem("nexploy.sidebarCollapsed");
      if (stored !== null) setSidebarCollapsed(stored === "1");
    } catch {
      // Storage unavailable — keep the default (expanded).
    }
  }, [setSidebarCollapsed]);

  if (immersive) {
    return (
      <div className="relative min-h-screen bg-background">
        <AmbientBackground />
        <main className="min-h-screen">
          <PageTransition>{children}</PageTransition>
        </main>
        <CopilotPalette />
      </div>
    );
  }

  return (
    <div className="flex min-h-screen bg-background">
      <AmbientBackground />
      <aside
        className={cn(
          "hidden shrink-0 border-r border-border transition-[width] duration-200 ease-in-out md:block",
          sidebarCollapsed ? "w-16" : "w-64",
        )}
      >
        <div
          className={cn(
            "fixed h-screen transition-[width] duration-200 ease-in-out",
            sidebarCollapsed ? "w-16" : "w-64",
          )}
        >
          <SidebarNav collapsed={sidebarCollapsed} onToggleCollapse={toggleSidebar} />
        </div>
      </aside>
      <div className="flex min-h-screen min-w-0 flex-1 flex-col">
        <Topbar />
        <main className="flex-1 px-4 py-6 sm:px-6 lg:px-8">
          <PageTransition>{children}</PageTransition>
        </main>
      </div>
      <CopilotPalette />
    </div>
  );
}
