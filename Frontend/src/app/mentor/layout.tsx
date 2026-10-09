import { AppShell } from "@/components/layout/AppShell";
import { RoleGuard } from "@/components/layout/RoleGuard";

export default function Layout({ children }: { children: React.ReactNode }) {
  return (
    <RoleGuard segmentRole="mentor">
      <AppShell>{children}</AppShell>
    </RoleGuard>
  );
}
