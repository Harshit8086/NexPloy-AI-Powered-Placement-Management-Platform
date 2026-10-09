import type { LucideIcon } from "lucide-react";
import {
  LayoutDashboard,
  Fingerprint,
  Gauge,
  Target,
  Map,
  Briefcase,
  ClipboardList,
  Award,
  FileText,
  Users,
  Building2,
  Sparkles,
  ShieldAlert,
  ShieldCheck,
  CalendarClock,
  BarChart3,
  Bell,
  ListChecks,
  GraduationCap,
  HeartHandshake,
  MessagesSquare,
  Network,
  Landmark,
  KeyRound,
  FileSearch,
  MessageSquareText,
  Settings,
  UserRound,
  BrainCircuit,
  GitCompare,
  Waypoints,
  BriefcaseBusiness,
  BadgeCheck,
  ClipboardCheck,
  Brain,
} from "lucide-react";
import type { Role } from "@/types";

export interface NavItem {
  label: string;
  href: string;
  icon: LucideIcon;
}

export interface NavSection {
  title?: string;
  items: NavItem[];
}

// ============================================================
// PROTOTYPE ROLE MODEL
//
// Role switching here is DEMO AUTHENTICATION only — there is no real
// auth server, token, or backend authorization. What it does mirror is
// production RBAC's *shape*: one place defines every role, its home
// route, its navigation, and which route prefixes it may open. Route
// access is enforced client-side by RoleGuard.
// ============================================================

export const roleLabel: Record<Role, string> = {
  student: "Student",
  recruiter: "Recruiter",
  officer: "Placement Officer",
  mentor: "Mentor",
  admin: "Super Admin",
};

export const roleIcon: Record<Role, LucideIcon> = {
  student: GraduationCap,
  recruiter: Building2,
  officer: ShieldCheck,
  mentor: HeartHandshake,
  admin: Landmark,
};

export const roleDescription: Record<Role, string> = {
  student: "Track readiness & opportunities",
  recruiter: "Hire from ranked candidates",
  officer: "Run the placement cycle",
  mentor: "Support at-risk students",
  admin: "Oversee every campus",
};

export const roleHome: Record<Role, string> = {
  student: "/student",
  recruiter: "/recruiter",
  officer: "/officer",
  mentor: "/mentor",
  admin: "/admin",
};

export const roleOrder: Role[] = ["student", "recruiter", "officer", "mentor", "admin"];

/** Punchier one-line hook used on the per-role sign-in page hero — distinct
 * from the short roleDescription used in compact nav/picker contexts. */
export const roleTagline: Record<Role, string> = {
  student: "Your placement readiness, decoded in real time.",
  recruiter: "Ranked, explainable candidates — not a resume pile.",
  officer: "Run the entire placement cycle from one command center.",
  mentor: "See who needs you before they fall behind.",
  admin: "Every campus, one governed view.",
};

/** Demo-only credentials shown pre-filled on each role's sign-in page.
 * No real auth — see the PROTOTYPE ROLE MODEL note above. */
export const roleDemoEmail: Record<Role, string> = {
  student: "rahul.sharma@nexploy.demo",
  recruiter: "talent@technova.demo",
  officer: "placements@nexploy.demo",
  mentor: "anita.mishra@nexploy.demo",
  admin: "admin@gmail.com",
};

/** Three feature highlights shown on each role's sign-in page — named,
 * real features pulled from navByRole below (not invented, and no
 * fabricated numbers), just surfaced earlier as a preview. */
export const roleSignals: Record<Role, { icon: LucideIcon; label: string }[]> = {
  student: [
    { icon: Fingerprint, label: "Digital Twin" },
    { icon: Target, label: "Skill Gap" },
    { icon: Briefcase, label: "Placement Drives" },
  ],
  recruiter: [
    { icon: Users, label: "Candidate Pool" },
    { icon: BarChart3, label: "Analytics" },
    { icon: Award, label: "Offers" },
  ],
  officer: [
    { icon: Sparkles, label: "Command Center" },
    { icon: ShieldAlert, label: "Risk Radar" },
    { icon: CalendarClock, label: "Drive Orchestrator" },
  ],
  mentor: [
    { icon: HeartHandshake, label: "Intervention Plans" },
    { icon: ShieldAlert, label: "At-Risk Students" },
    { icon: BarChart3, label: "Progress Tracking" },
  ],
  admin: [
    { icon: Network, label: "Campus Management" },
    { icon: BarChart3, label: "Cross-Campus Analytics" },
    { icon: KeyRound, label: "Role & Access" },
  ],
};

/** Route prefixes each role is allowed to open in the prototype. */
export const roleRouteAccess: Record<Role, string[]> = {
  student: ["/student"],
  recruiter: ["/recruiter"],
  officer: ["/officer"],
  mentor: ["/mentor"],
  admin: ["/admin", "/officer"], // institute admins can inspect campus operations
};

export function canAccess(role: Role, pathname: string): boolean {
  return roleRouteAccess[role].some((prefix) => pathname.startsWith(prefix));
}

/** Which role owns a route — used to explain an Access Restricted state. */
export function roleForPath(pathname: string): Role | undefined {
  return roleOrder.find((r) => pathname.startsWith(roleHome[r]));
}

export const navByRole: Record<Role, NavSection[]> = {
  student: [
    { items: [{ label: "Dashboard", href: "/student", icon: LayoutDashboard }] },
    {
      title: "Profile & Portfolio",
      items: [
        { label: "Profile", href: "/student/profile", icon: UserRound },
        { label: "My Skills & Evidence", href: "/student/skills", icon: Award },
        { label: "Documents", href: "/student/documents", icon: FileText },
      ],
    },
    {
      title: "Growth & Roadmap",
      items: [
        { label: "Placement Readiness", href: "/student/readiness", icon: Gauge },
        { label: "Skill Gap Analysis", href: "/student/skill-gap", icon: GitCompare },
        { label: "What-If Simulator", href: "/student/what-if", icon: Waypoints },
        { label: "Career Roadmap", href: "/student/roadmap", icon: Map },
        { label: "Digital Twin", href: "/student/digital-twin", icon: BrainCircuit },
      ],
    },
    {
      title: "Opportunities",
      items: [
        { label: "Placement Drives", href: "/student/drives", icon: BriefcaseBusiness },
        { label: "Career Matches", href: "/student/career", icon: Target },
        { label: "Applications", href: "/student/applications", icon: ClipboardList },
        { label: "Offers", href: "/student/offers", icon: BadgeCheck },
      ],
    },
    {
      title: "Preparation & Tests",
      items: [
        { label: "Resume Intelligence", href: "/student/resume", icon: FileSearch },
        { label: "Interview Prep", href: "/student/interview-prep", icon: MessageSquareText },
        { label: "Assessments", href: "/student/assessments", icon: ClipboardCheck },
        { label: "Aptitude Test", href: "/student/aptitude", icon: Brain },
      ],
    },
    {
      items: [{ label: "Notifications", href: "/student/notifications", icon: Bell }],
    },
  ],

  recruiter: [
    { items: [{ label: "Dashboard", href: "/recruiter", icon: LayoutDashboard }] },
    {
      title: "Company",
      items: [{ label: "Company Profile", href: "/recruiter/company", icon: Building2 }],
    },
    {
      title: "Hiring",
      items: [
        { label: "Placement Drives", href: "/recruiter/drives", icon: Briefcase },
        { label: "Candidate Pool", href: "/recruiter/candidates", icon: Users },
        { label: "Interview Scheduling", href: "/recruiter/interviews", icon: CalendarClock },
        { label: "Offers", href: "/recruiter/offers", icon: Award },
      ],
    },
    {
      title: "Intelligence",
      items: [{ label: "Analytics", href: "/recruiter/analytics", icon: BarChart3 }],
    },
    {
      title: "Account",
      items: [
        { label: "Notifications", href: "/recruiter/notifications", icon: Bell },
        { label: "Settings", href: "/recruiter/settings", icon: Settings },
      ],
    },
  ],

  officer: [
    { items: [{ label: "Command Center", href: "/officer", icon: Sparkles }] },
    {
      title: "Students",
      items: [
        { label: "Student Directory", href: "/officer/students", icon: Users },
        { label: "Readiness", href: "/officer/readiness", icon: Gauge },
        { label: "Risk Radar", href: "/officer/risk", icon: ShieldAlert },
        { label: "Aptitude Bank", href: "/officer/aptitude-bank", icon: Brain },
      ],
    },
    {
      title: "Placement",
      items: [
        { label: "Drives", href: "/officer/drives", icon: Building2 },
        { label: "Recruiters", href: "/officer/recruiters", icon: Briefcase },
        { label: "Placement Cycles", href: "/officer/cycles", icon: CalendarClock },
        { label: "Eligibility", href: "/officer/eligibility", icon: ListChecks },
        { label: "Matching Review", href: "/officer/matching", icon: Target },
        { label: "Drive Orchestrator", href: "/officer/orchestrator", icon: CalendarClock },
      ],
    },
    {
      title: "Operations",
      items: [
        { label: "Interviews", href: "/officer/interviews", icon: MessagesSquare },
        { label: "Venues & Panels", href: "/officer/venues", icon: Network },
        { label: "Offers", href: "/officer/offers", icon: Award },
        { label: "Documents", href: "/officer/documents", icon: FileText },
      ],
    },
    {
      title: "Support",
      items: [
        { label: "Mentors", href: "/officer/mentors", icon: HeartHandshake },
        { label: "Notifications", href: "/officer/notifications", icon: Bell },
      ],
    },
    {
      title: "Intelligence",
      items: [{ label: "Analytics", href: "/officer/analytics", icon: BarChart3 }],
    },
  ],

  mentor: [
    { items: [{ label: "Mentor Dashboard", href: "/mentor", icon: LayoutDashboard }] },
    {
      title: "Mentees",
      items: [
        { label: "Assigned Students", href: "/mentor/students", icon: Users },
        { label: "At-Risk Students", href: "/mentor/at-risk", icon: ShieldAlert },
      ],
    },
    {
      title: "Intervention",
      items: [
        { label: "Intervention Plans", href: "/mentor/interventions", icon: HeartHandshake },
        { label: "Progress Tracking", href: "/mentor/progress", icon: BarChart3 },
      ],
    },
    {
      title: "Activity",
      items: [{ label: "Notifications", href: "/mentor/notifications", icon: Bell }],
    },
  ],

  admin: [
    { items: [{ label: "Institute Overview", href: "/admin", icon: Landmark }] },
    {
      title: "Campuses",
      items: [
        { label: "Campus Management", href: "/admin/campuses", icon: Network },
        { label: "Cross-Campus Analytics", href: "/admin/analytics", icon: BarChart3 },
      ],
    },
    {
      title: "Governance",
      items: [{ label: "Role & Access", href: "/admin/roles", icon: KeyRound }],
    },
    {
      title: "Operations",
      items: [{ label: "Campus Command Center", href: "/officer", icon: Sparkles }],
    },
    {
      title: "Activity",
      items: [{ label: "Notifications", href: "/admin/notifications", icon: Bell }],
    },
  ],
};

export const copilotNavItem = {
  label: "NEXPLOY Copilot",
  icon: MessageSquareText,
};
