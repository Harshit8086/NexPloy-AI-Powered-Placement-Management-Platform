"use client";

import { useContext } from "react";
import { useAppStore } from "@/hooks/useAppStore";
import { RoleContext } from "@/components/providers/RoleProvider";
import type { Role } from "@/types";

// Thin convenience wrapper — the "Switch Demo Role" control in the topbar
// writes here. This is NOT real auth/RBAC; it only changes which nav and
// screens are shown, exactly like production RBAC will later gate routes.
//
// Reads the role RoleGuard published for the current route segment so the
// shell is correct on the first paint, falling back to the store outside a
// guarded segment (e.g. the login screen).
export function useRole(): { role: Role; setRole: (role: Role) => void } {
  const segmentRole = useContext(RoleContext);
  const storeRole = useAppStore((s) => s.role);
  const setRole = useAppStore((s) => s.setRole);
  return { role: segmentRole ?? storeRole, setRole };
}
