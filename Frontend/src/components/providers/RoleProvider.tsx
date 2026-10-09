"use client";

import { createContext } from "react";
import type { Role } from "@/types";

/**
 * The role the current route segment is being viewed as.
 *
 * RoleGuard publishes this so the shell (sidebar, topbar, copilot) renders
 * the right workspace on the very first paint — including on the server.
 * Reading the zustand store alone would render every non-student segment
 * as "student" until an effect corrected it.
 */
export const RoleContext = createContext<Role | null>(null);
