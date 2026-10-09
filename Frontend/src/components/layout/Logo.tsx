import Image from "next/image";
import { cn } from "@/lib/utils";

export function Logo({ className, iconOnly = false }: { className?: string; iconOnly?: boolean }) {
  return (
    <div className={cn("flex items-center gap-2", className)}>
      <Image
        src="/logo-mark.webp"
        alt={iconOnly ? "NEXPLOY" : ""}
        width={493}
        height={240}
        priority
        className="h-7 w-auto shrink-0"
      />
      {!iconOnly && <span className="text-[15px] font-semibold tracking-tight text-foreground">NEXPLOY</span>}
    </div>
  );
}
