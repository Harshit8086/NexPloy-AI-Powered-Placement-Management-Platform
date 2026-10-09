"use client";

import { useState } from "react";
import Link from "next/link";
import { usePathname } from "next/navigation";
import { cn } from "@/lib/utils";
import { useRole } from "@/hooks/useRole";
import { navByRole } from "@/components/layout/nav-config";
import { Logo } from "@/components/layout/Logo";
import { useAppStore } from "@/hooks/useAppStore";
import { ChevronDown, MessageSquareText, PanelLeftClose, PanelLeftOpen } from "lucide-react";

/**
 * The role's primary navigation. Rendered in two contexts:
 *  - the desktop rail (AppShell) — where `onToggleCollapse` is passed, so a collapse control
 *    shows and `collapsed` shrinks it to an icon-only rail;
 *  - the mobile drawer (Topbar Sheet) — where neither prop is passed, so it always renders
 *    expanded with no collapse control (the drawer has its own width).
 */
export function SidebarNav({
  collapsed = false,
  onToggleCollapse,
}: {
  collapsed?: boolean;
  onToggleCollapse?: () => void;
}) {
  const { role } = useRole();
  const pathname = usePathname();
  const sections = navByRole[role];
  const toggleCopilot = useAppStore((s) => s.toggleCopilot);

  // Which titled sections are collapsed. Empty = everything expanded (the default).
  const [collapsedSections, setCollapsedSections] = useState<Record<string, boolean>>({});
  const toggleSection = (title: string) =>
    setCollapsedSections((prev) => ({ ...prev, [title]: !prev[title] }));

  return (
    <div className="flex h-full flex-col">
      {/* Header: logo + collapse toggle, pinned to the top of the rail. When expanded the toggle
          sits at the top-right beside the logo; when collapsed the narrow rail has no room beside
          the logo icon, so the expand toggle gets its own centred row just below (see next block). */}
      <div
        className={cn(
          "flex h-14 items-center border-b border-border",
          collapsed ? "justify-center px-0" : "justify-between px-4",
        )}
      >
        <Link href="/" aria-label="NEXPLOY home">
          <Logo iconOnly={collapsed} />
        </Link>
        {onToggleCollapse && !collapsed && (
          <button
            onClick={onToggleCollapse}
            title="Collapse sidebar"
            aria-label="Collapse sidebar"
            aria-pressed={collapsed}
            className="flex shrink-0 items-center justify-center rounded-lg p-1.5 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            <PanelLeftClose className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
          </button>
        )}
      </div>

      {onToggleCollapse && collapsed && (
        <div className="flex justify-center border-b border-border py-2">
          <button
            onClick={onToggleCollapse}
            title="Expand sidebar"
            aria-label="Expand sidebar"
            aria-pressed={collapsed}
            className="flex items-center justify-center rounded-lg p-2 text-muted-foreground transition-colors hover:bg-accent hover:text-foreground"
          >
            <PanelLeftOpen className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
          </button>
        </div>
      )}

      {/* Independently scrollable nav — the Copilot shortcut below stays pinned. */}
      <nav className={cn("flex-1 overflow-y-auto py-4", collapsed ? "px-2" : "px-3")}>
        {sections.map((section, si) => {
          // In the collapsed rail there is no room for section headers, so titles are dropped
          // and every item is shown as an icon regardless of its section's expand state.
          const isSectionCollapsed = !collapsed && section.title ? collapsedSections[section.title] : false;
          return (
            <div key={si} className={cn(section.title ? "mb-4" : "mb-2")}>
              {section.title && !collapsed && (
                <button
                  type="button"
                  onClick={() => toggleSection(section.title!)}
                  className="mb-1 flex w-full items-center justify-between rounded-md px-2 py-1 text-[10px] font-semibold uppercase tracking-wider text-muted-foreground/60 transition-colors hover:text-muted-foreground"
                  aria-expanded={!isSectionCollapsed}
                >
                  <span>{section.title}</span>
                  <ChevronDown
                    className={cn("h-3.5 w-3.5 transition-transform", isSectionCollapsed && "-rotate-90")}
                  />
                </button>
              )}
              {/* A thin rule stands in for the (hidden) section title on the collapsed rail. */}
              {section.title && collapsed && si > 0 && <div className="mx-2 mb-2 border-t border-border/60" />}
              {!isSectionCollapsed && (
                <div className="space-y-0.5">
                  {section.items.map((item) => {
                    const active = pathname === item.href;
                    const Icon = item.icon;
                    return (
                      <Link
                        key={item.href}
                        href={item.href}
                        title={collapsed ? item.label : undefined}
                        aria-label={collapsed ? item.label : undefined}
                        className={cn(
                          "flex items-center rounded-lg text-sm font-medium transition-colors",
                          collapsed ? "justify-center px-0 py-2" : "gap-2.5 px-2.5 py-1.5",
                          active
                            ? "bg-violet/15 text-foreground"
                            : "text-muted-foreground hover:bg-accent hover:text-foreground",
                        )}
                      >
                        <Icon
                          className={cn("h-[18px] w-[18px] shrink-0", active && "text-violet-bright")}
                          strokeWidth={1.75}
                        />
                        {!collapsed && <span className="truncate">{item.label}</span>}
                      </Link>
                    );
                  })}
                </div>
              )}
            </div>
          );
        })}
      </nav>

      <div className={cn("border-t border-border", collapsed ? "space-y-1 p-2" : "space-y-1 p-3")}>
        <button
          onClick={toggleCopilot}
          title={collapsed ? "NEXPLOY Copilot" : undefined}
          aria-label={collapsed ? "NEXPLOY Copilot" : undefined}
          className={cn(
            "flex w-full items-center rounded-lg border border-violet/30 bg-violet/10 text-sm font-medium text-violet-bright transition-colors hover:bg-violet/20",
            collapsed ? "justify-center px-0 py-2" : "gap-2.5 px-2.5 py-2",
          )}
        >
          <MessageSquareText className="h-[18px] w-[18px] shrink-0" strokeWidth={1.75} />
          {!collapsed && (
            <>
              NEXPLOY Copilot
              <kbd className="ml-auto rounded border border-violet/30 bg-background/40 px-1.5 py-0.5 text-[10px] text-violet-bright">
                Ctrl K
              </kbd>
            </>
          )}
        </button>
      </div>
    </div>
  );
}
