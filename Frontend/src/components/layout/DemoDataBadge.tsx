import { cn } from "@/lib/utils";

export function DemoDataBadge({ className }: { className?: string }) {
  return (
    <span
      className={cn(
        "inline-flex items-center gap-1 rounded-full border border-border bg-muted px-2 py-0.5 text-[9px] font-semibold uppercase tracking-wider text-muted-foreground/80",
        className,
      )}
    >
      Demo Data
    </span>
  );
}
