"use client";

import { ChevronDown, Network } from "lucide-react";
import { useEffect } from "react";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuLabel,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Button } from "@/components/ui/button";
import { useAppStore } from "@/hooks/useAppStore";
import { useRole } from "@/hooks/useRole";
import { useApiResource } from "@/hooks/useApiResource";
import { listCampuses, INSTITUTE_NAME } from "@/lib/api/campuses";
import type { CampusSummary } from "@/types";

/** Campus scope selector. Shown only for the Super Admin — the one role
 * that operates across campuses. Every institutional metric in the app
 * reads `activeCampusId` from the store; officers are pinned to their
 * assigned campus and never see this switcher. Campuses come from the
 * live API (identity only); per-campus metrics live on the analytics screens. */
export function CampusSwitcher() {
  const { role } = useRole();
  const activeCampusId = useAppStore((s) => s.activeCampusId);
  const setActiveCampusId = useAppStore((s) => s.setActiveCampusId);
  const pushToast = useAppStore((s) => s.pushToast);

  const isCrossCampus = role === "admin";
  const { data: campuses } = useApiResource<CampusSummary[]>(
    () => (isCrossCampus ? listCampuses() : Promise.resolve([])),
    [isCrossCampus],
    [],
  );

  // Keep the active scope pointed at a real campus once the live list loads.
  useEffect(() => {
    if (campuses.length > 0 && !campuses.some((c) => c.id === activeCampusId)) {
      setActiveCampusId(campuses[0].id);
    }
  }, [campuses, activeCampusId, setActiveCampusId]);

  if (!isCrossCampus || campuses.length === 0) return null;

  const active = campuses.find((c) => c.id === activeCampusId) ?? campuses[0];

  const switchCampus = (id: string) => {
    const next = campuses.find((c) => c.id === id);
    setActiveCampusId(id);
    if (next) {
      pushToast("Campus scope changed", `Now showing ${next.name}.`);
    }
  };

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button
          variant="ghost"
          size="sm"
          className="gap-2 text-muted-foreground hover:text-foreground"
          aria-label={`Switch campus — currently ${active.name}`}
        >
          <Network className="h-3.5 w-3.5 text-violet-bright" />
          <span className="hidden md:inline">{active.name}</span>
          <ChevronDown className="h-3 w-3 opacity-60" />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end" className="w-64">
        <DropdownMenuLabel className="text-[10px] uppercase tracking-wider">
          {INSTITUTE_NAME}
        </DropdownMenuLabel>
        <DropdownMenuSeparator />
        {campuses.map((c) => (
          <DropdownMenuItem key={c.id} onClick={() => switchCampus(c.id)} className="gap-2.5 py-2">
            <span className="min-w-0">
              <span className="block text-sm">{c.name}</span>
              <span className="block text-[10px] text-muted-foreground">{c.city}</span>
            </span>
            {activeCampusId === c.id && (
              <span className="ml-auto shrink-0 text-[10px] text-violet-bright">Active</span>
            )}
          </DropdownMenuItem>
        ))}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
