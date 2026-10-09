import { NotificationCenter } from "@/features/shared/NotificationCenter";
import { NotificationAdminConsole } from "@/features/shared/NotificationAdminConsole";

export default function Page() {
  return (
    <>
      <NotificationCenter role="admin" eyebrow="Activity" />
      <NotificationAdminConsole />
    </>
  );
}
