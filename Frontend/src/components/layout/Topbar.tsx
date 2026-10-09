"use client";

import { usePathname, useRouter } from "next/navigation";
import Link from "next/link";
import { Menu, Sparkles, LogOut, Settings, FileText, FileSearch, Video, UserRound } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { Avatar, AvatarFallback } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { CampusSwitcher } from "@/components/layout/CampusSwitcher";
import { NotificationsPanel } from "@/components/layout/NotificationsPanel";
import { ThemeToggle } from "@/components/layout/ThemeToggle";
import { SidebarNav } from "@/components/layout/Sidebar";
import { useAppStore } from "@/hooks/useAppStore";
import { useRole } from "@/hooks/useRole";
import { roleLabel } from "@/components/layout/nav-config";
import { getCurrentUser, logout } from "@/lib/api/auth";
import { getPrimaryStudent } from "@/lib/api/students";
import { getActiveRecruiter } from "@/lib/api/recruiters";
import { useApiResource } from "@/hooks/useApiResource";
import { useEffect, useState } from "react";

interface Identity {
  name: string;
  email: string;
  initials: string;
  campusId: string;
}

/** Initials from a display name — first letters of the first two words, uppercased. */
function initialsFrom(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return "?";
  return (parts[0][0] + (parts[1]?.[0] ?? "")).toUpperCase();
}

/**
 * The signed-in user's account identity, resolved live from the real session (GET /auth/me) and
 * enriched from the authenticated role's own profile: a student's name/avatar (GET /students/me),
 * a recruiter's company (GET /recruiters/me). Officers, mentors and admins have no profile entity
 * on the backend, so their identity is just the account's email + role. No mock data.
 */
async function resolveIdentity(): Promise<Identity> {
  const me = await getCurrentUser();
  const base: Identity = {
    name: roleLabel[me.role],
    email: me.username,
    initials: initialsFrom(roleLabel[me.role]),
    campusId: me.campusId,
  };

  if (me.role === "student") {
    const s = await getPrimaryStudent();
    return { name: s.name, email: s.email || me.username, initials: s.avatarInitials, campusId: me.campusId };
  }
  if (me.role === "recruiter") {
    const r = await getActiveRecruiter();
    return {
      name: `${r.companyName} Talent Team`,
      email: me.username,
      initials: r.logoInitial || initialsFrom(r.companyName),
      campusId: me.campusId,
    };
  }
  return base;
}

export function Topbar() {
  const setCopilotOpen = useAppStore((s) => s.setCopilotOpen);
  const activeCampusId = useAppStore((s) => s.activeCampusId);
  const setActiveCampusId = useAppStore((s) => s.setActiveCampusId);
  const { role } = useRole();
  const router = useRouter();
  const pathname = usePathname();
  // Controlled so the mobile drawer closes after a nav item is selected (route change)
  // as well as on overlay-click / Esc (handled by Radix via onOpenChange).
  const [mobileNavOpen, setMobileNavOpen] = useState(false);
  const { data: resolved } = useApiResource<Identity | null>(() => resolveIdentity(), [], null);
  const identity: Identity = resolved ?? {
    name: roleLabel[role],
    email: "",
    initials: initialsFrom(roleLabel[role]),
    campusId: "",
  };

  // Seed the active campus scope from the signed-in user's own campus once identity resolves.
  // Officers are always pinned to their assigned campus — no cross-campus selection, even from
  // a stale persisted value; only admins may later switch via CampusSwitcher.
  useEffect(() => {
    if (!resolved?.campusId) return;
    if (role === "officer") {
      if (activeCampusId !== resolved.campusId) {
        setActiveCampusId(resolved.campusId);
      }
      return;
    }
    if (!activeCampusId) {
      setActiveCampusId(resolved.campusId);
    }
  }, [resolved?.campusId, activeCampusId, setActiveCampusId, role]);

  // Close the mobile navigation drawer whenever the route changes (i.e. after a nav item is tapped).
  useEffect(() => {
    setMobileNavOpen(false);
  }, [pathname]);

  // Real sign-out: clear the stored access token before leaving so the session actually ends
  // (the old behaviour only routed to /login and left the token in place).
  const handleSignOut = () => {
    logout();
    router.push("/login");
  };

  return (
    <header className="sticky top-0 z-30 flex h-14 items-center justify-between border-b border-border bg-background/80 px-4 backdrop-blur-md">
      {/* Left section: mobile navigation trigger */}
      <div className="flex flex-1 items-center justify-start">
        <Sheet open={mobileNavOpen} onOpenChange={setMobileNavOpen}>
          <SheetTrigger asChild>
            <Button variant="ghost" size="icon" className="md:hidden" aria-label="Open navigation">
              <Menu className="h-4 w-4" />
            </Button>
          </SheetTrigger>
          <SheetContent side="left" className="w-72 p-0">
            <SidebarNav />
          </SheetContent>
        </Sheet>
      </div>

      {/* Center section: centered search bar */}
      <div className="flex w-full max-w-md shrink-0 items-center justify-center px-2">
        <button
          onClick={() => setCopilotOpen(true)}
          className="flex w-full items-center gap-2 rounded-full border border-border bg-secondary/40 px-3.5 py-1.5 text-sm text-muted-foreground transition-colors hover:text-foreground hover:bg-secondary/60"
        >
          <Sparkles className="h-3.5 w-3.5 shrink-0 text-violet-bright" />
          <span className="hidden truncate sm:inline">Ask NEXPLOY anything...</span>
          <span className="truncate sm:hidden">Ask NEXPLOY</span>
          <kbd className="ml-auto hidden shrink-0 rounded border border-border bg-background px-1.5 py-0.5 text-[10px] sm:inline">
            Ctrl K
          </kbd>
        </button>
      </div>

      {/* Right section: actions + Resume Builder + switcher + theme + notifs + avatar */}
      <div className="flex flex-1 items-center justify-end gap-1.5 sm:gap-2">
        {role === "student" && (
          <Button
            asChild
            variant="outline"
            size="sm"
            className="hidden sm:inline-flex h-8 gap-1.5 border-violet/30 bg-violet/10 text-xs font-medium text-foreground hover:bg-violet/20 hover:border-violet/50"
          >
            <Link href="/student/resume/builder">
              <FileText className="h-3.5 w-3.5 text-violet-bright" />
              <span>Resume Builder</span>
            </Link>
          </Button>
        )}

        <CampusSwitcher />
        <ThemeToggle />
        <NotificationsPanel />
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <button
              className="ml-1 rounded-full ring-offset-background transition-shadow focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring"
              aria-label="Account menu"
            >
              <Avatar className="h-8 w-8">
                <AvatarFallback>{identity.initials}</AvatarFallback>
              </Avatar>
            </button>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-60">
            <DropdownMenuLabel>
              <div className="text-sm font-medium">{identity.name}</div>
              <div className="text-xs font-normal text-muted-foreground">{identity.email}</div>
              <div className="mt-1 text-[10px] uppercase tracking-wider text-violet-bright">
                {roleLabel[role]}
              </div>
            </DropdownMenuLabel>
            <DropdownMenuSeparator />
            {role === "student" && (
              <>
                <DropdownMenuItem onClick={() => router.push("/student/profile")} className="gap-2 cursor-pointer">
                  <UserRound className="h-4 w-4 text-violet-bright" /> My Profile
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => router.push("/student/resume/builder")} className="gap-2 cursor-pointer">
                  <FileText className="h-4 w-4 text-violet-bright" /> Resume Builder
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => router.push("/student/resume")} className="gap-2 cursor-pointer">
                  <FileSearch className="h-4 w-4 text-violet-bright" /> Resume ATS Intelligence
                </DropdownMenuItem>
                <DropdownMenuItem onClick={() => router.push("/student/mock-interview")} className="gap-2 cursor-pointer">
                  <Video className="h-4 w-4 text-violet-bright" /> AI Mock Interview
                </DropdownMenuItem>
                <DropdownMenuSeparator />
              </>
            )}
            {role === "recruiter" && (
              <>
                <DropdownMenuItem onClick={() => router.push("/recruiter/settings")} className="gap-2">
                  <Settings className="h-4 w-4" /> Settings
                </DropdownMenuItem>
                <DropdownMenuSeparator />
              </>
            )}
            <DropdownMenuItem onClick={handleSignOut} className="gap-2 text-risk cursor-pointer">
              <LogOut className="h-4 w-4" /> Sign out
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </header>
  );
}
