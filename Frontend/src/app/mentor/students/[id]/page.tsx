import { InterventionDetail } from "@/features/mentor/InterventionDetail";

// Dynamic route: the student id comes from the URL at request time. There is no
// build-time list of mentee ids (that lives on the backend, per-mentor), so this
// route is rendered on demand rather than statically pre-generated.
export const dynamic = "force-dynamic";

export default function Page({ params }: { params: { id: string } }) {
  return <InterventionDetail studentId={params.id} />;
}
