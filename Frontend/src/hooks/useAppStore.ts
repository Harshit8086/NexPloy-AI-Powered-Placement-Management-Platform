"use client";

import { create } from "zustand";
import type { DocumentStatus, NotificationItem, Role } from "@/types";
import type { ResumeAnalysis } from "@/lib/api/students";
import { DEFAULT_CAMPUS_ID } from "@/lib/api/campuses";
import { listNotifications, markNotificationRead, getUnreadCount, markAllNotificationsRead } from "@/lib/api/notifications";

// ============================================================
// Single lightweight store for cross-navigation UI interaction state:
// role, campus, notifications, shortlisting, offer decisions and document
// verification. Mentor interventions now live entirely on the backend and are
// fetched per page, so they are no longer held here.
//
// Deliberately NOT Redux — the prototype has no server state to
// normalize, and the existing zustand store already covers it.
// ============================================================

interface AppState {
  role: Role;
  /** True once the user has explicitly picked a role (topbar switcher or
   * demo login) — until then, the URL segment decides the role. */
  roleExplicit: boolean;
  setRole: (role: Role) => void;
  /** Used by RoleGuard to keep the store in sync with the URL segment
   * without marking the role as explicitly chosen. */
  adoptRole: (role: Role) => void;

  activeCampusId: string;
  setActiveCampusId: (id: string) => void;

  activeStudentId: string;
  setActiveStudentId: (id: string) => void;

  copilotOpen: boolean;
  setCopilotOpen: (open: boolean) => void;
  toggleCopilot: () => void;

  /** Desktop sidebar collapsed to an icon-only rail. Persisted to localStorage so the
   * choice survives reloads; hydrated after mount (see AppShell) to avoid an SSR mismatch.
   * The mobile nav drawer ignores this and always renders expanded. */
  sidebarCollapsed: boolean;
  setSidebarCollapsed: (collapsed: boolean) => void;
  toggleSidebar: () => void;

  notifications: NotificationItem[];
  /** Authoritative unread badge count for the signed-in user's own feed (GET /unread-count). */
  unreadCount: number;
  /** Seeds notifications from the live API (GET /api/notifications). */
  loadNotifications: () => Promise<void>;
  /** Refreshes just the badge count from GET /api/notifications/unread-count — cheap to poll. */
  refreshUnreadCount: () => Promise<void>;
  /** Replaces the notification list (e.g. from a page that fetched via useApiResource). */
  setNotifications: (items: NotificationItem[]) => void;
  markRead: (id: string) => void;
  markAllRead: () => void;

  /** Candidate ids shortlisted through the recruiter bulk action. */
  shortlisted: string[];
  shortlistCandidates: (studentIds: string[]) => void;

  /** Document verifications made during the demo, keyed by document id. */
  documentDecisions: Record<string, DocumentStatus>;
  setDocumentStatus: (documentId: string, status: DocumentStatus) => void;

  /** Transient handoff: a résumé analysis produced by the Resume Builder's one-click "Analyze"
   * action, consumed once by the Resume Intelligence page on mount (then cleared). Null when
   * nothing is pending. Not persisted — it only survives the single client navigation. */
  pendingResumeAnalysis: ResumeAnalysis | null;
  setPendingResumeAnalysis: (analysis: ResumeAnalysis | null) => void;

  toasts: { id: string; title: string; message: string }[];
  pushToast: (title: string, message: string) => void;
  dismissToast: (id: string) => void;
}

let toastCounter = 0;

export const useAppStore = create<AppState>((set) => ({
  role: "student",
  roleExplicit: false,
  setRole: (role) => set({ role, roleExplicit: true }),
  adoptRole: (role) => set({ role }),

  activeCampusId: DEFAULT_CAMPUS_ID,
  setActiveCampusId: (id) => set({ activeCampusId: id }),

  // No student is active until one is resolved from the authenticated session
  // (student pages use the `me` endpoints); no hardcoded/fabricated id.
  activeStudentId: "",
  setActiveStudentId: (id) => set({ activeStudentId: id }),

  copilotOpen: false,
  setCopilotOpen: (open) => set({ copilotOpen: open }),
  toggleCopilot: () => set((s) => ({ copilotOpen: !s.copilotOpen })),

  // Starts expanded on both server and first client render; AppShell reads the stored
  // preference after mount so hydration never mismatches. Writes persist immediately.
  sidebarCollapsed: false,
  setSidebarCollapsed: (collapsed) => {
    try {
      window.localStorage.setItem("nexploy.sidebarCollapsed", collapsed ? "1" : "0");
    } catch {
      // Private mode / storage disabled — keep the in-memory value only.
    }
    set({ sidebarCollapsed: collapsed });
  },
  toggleSidebar: () =>
    set((s) => {
      const next = !s.sidebarCollapsed;
      try {
        window.localStorage.setItem("nexploy.sidebarCollapsed", next ? "1" : "0");
      } catch {
        // no-op
      }
      return { sidebarCollapsed: next };
    }),

  notifications: [],
  unreadCount: 0,
  loadNotifications: async () => {
    const items = await listNotifications();
    set({ notifications: items });
  },
  refreshUnreadCount: async () => {
    try {
      set({ unreadCount: await getUnreadCount() });
    } catch {
      // Polling errors are non-fatal — keep the last known count.
    }
  },
  setNotifications: (items) => set({ notifications: items }),
  markRead: (id) => {
    // Optimistic local update; persist the authoritative read-state to the backend.
    set((s) => {
      const wasUnread = s.notifications.some((n) => n.id === id && !n.read);
      return {
        notifications: s.notifications.map((n) => (n.id === id ? { ...n, read: true } : n)),
        unreadCount: wasUnread ? Math.max(0, s.unreadCount - 1) : s.unreadCount,
      };
    });
    void markNotificationRead(id).catch(() => {});
  },
  markAllRead: () => {
    // Optimistic: clear both the list read-state and the badge, then persist in one server call
    // (POST /read-all) instead of a per-item loop. The server only touches the caller's own feed.
    set((s) => ({
      notifications: s.notifications.map((n) => ({ ...n, read: true })),
      unreadCount: 0,
    }));
    void markAllNotificationsRead().catch(() => {});
  },

  shortlisted: [],
  shortlistCandidates: (studentIds) =>
    set((s) => ({ shortlisted: Array.from(new Set([...s.shortlisted, ...studentIds])) })),

  documentDecisions: {},
  setDocumentStatus: (documentId, status) =>
    set((s) => ({ documentDecisions: { ...s.documentDecisions, [documentId]: status } })),

  pendingResumeAnalysis: null,
  setPendingResumeAnalysis: (analysis) => set({ pendingResumeAnalysis: analysis }),

  toasts: [],
  pushToast: (title, message) =>
    set((s) => ({
      toasts: [...s.toasts, { id: `toast_${++toastCounter}`, title, message }],
    })),
  dismissToast: (id) => set((s) => ({ toasts: s.toasts.filter((t) => t.id !== id) })),
}));
