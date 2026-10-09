"use client";

import { useEffect } from "react";
import { usePathname, useRouter } from "next/navigation";
import { ShieldAlert, ArrowRight } from "lucide-react";
import { Button } from "@/components/ui/button";
import { Card } from "@/components/ui/card";
import { useAppStore } from "@/hooks/useAppStore";
import { RoleContext } from "@/components/providers/RoleProvider";
import { canAccess, roleHome, roleLabel, roleForPath } from "@/components/layout/nav-config";
import type { Role } from "@/types";

/**
 * Prototype route scoping.
 *
 * Until the user explicitly picks a role (the topbar switcher or the demo
 * login), the URL is the source of truth — landing on /officer, refreshing
 * it, or opening a shared link makes you the Placement Officer. That is how
 * the demo is meant to be navigated.
 *
 * Once a role has been chosen in-session, deep-linking into a segment that
 * role cannot open renders a clean Access Restricted state instead of a
 * broken page. Production will enforce this on the server; here it is
 * presentation only.
 */
export function RoleGuard({
  segmentRole,
  children,
}: {
  segmentRole: Role;
  children: React.ReactNode;
}) {
  const storeRole = useAppStore((s) => s.role);
  const roleExplicit = useAppStore((s) => s.roleExplicit);
  const adoptRole = useAppStore((s) => s.adoptRole);
  const pathname = usePathname();

  const effectiveRole = roleExplicit ? storeRole : segmentRole;

  // Keep the store in sync for anything reading it outside the shell.
  useEffect(() => {
    if (!roleExplicit && storeRole !== segmentRole) adoptRole(segmentRole);
  }, [roleExplicit, storeRole, segmentRole, adoptRole]);

  if (!canAccess(effectiveRole, pathname)) {
    return <AccessRestricted pathname={pathname} currentRole={effectiveRole} />;
  }

  return <RoleContext.Provider value={effectiveRole}>{children}</RoleContext.Provider>;
}

export function AccessRestricted({
  pathname,
  currentRole,
}: {
  pathname: string;
  currentRole: Role;
}) {
  const router = useRouter();
  const setRole = useAppStore((s) => s.setRole);
  const owner = roleForPath(pathname);

  return (
    <div className="mx-auto flex max-w-lg flex-col items-center justify-center py-20 text-center">
      <Card className="w-full p-8">
        <div className="mx-auto mb-4 flex h-12 w-12 items-center justify-center rounded-full border border-warning/40 bg-warning/10">
          <ShieldAlert className="h-5 w-5 text-warning" />
        </div>
        <h1 className="text-lg font-semibold">Access Restricted</h1>
        <p className="mt-2 text-sm text-muted-foreground">
          You are signed in to this demo as{" "}
          <span className="font-medium text-foreground">{roleLabel[currentRole]}</span>.
          {owner
            ? ` This screen belongs to the ${roleLabel[owner]} workspace.`
            : " This screen is outside your workspace."}
        </p>
        <div className="mt-6 flex flex-col gap-2 sm:flex-row sm:justify-center">
          <Button variant="glow" onClick={() => router.push(roleHome[currentRole])}>
            Back to {roleLabel[currentRole]} workspace
          </Button>
          {owner && (
            <Button
              variant="outline"
              onClick={() => {
                setRole(owner);
                router.push(roleHome[owner]);
              }}
            >
              Switch to {roleLabel[owner]} <ArrowRight className="h-4 w-4" />
            </Button>
          )}
        </div>
        <p className="mt-5 text-[11px] text-muted-foreground/60">
          Prototype route scoping — production will enforce this server-side.
        </p>
      </Card>
    </div>
  );
}
