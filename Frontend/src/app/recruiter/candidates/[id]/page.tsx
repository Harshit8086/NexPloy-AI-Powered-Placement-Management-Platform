import { CandidateMatchingPage } from "@/features/recruiter/CandidateMatching";

/**
 * A recruiter's view of one candidate, matched against their headline drive. The student id comes
 * from the route; the drive and the match itself are resolved live inside the feature (no mock
 * data, no featured-drive constant). The route is dynamic — there is no build-time param list.
 */
export default function Page({ params }: { params: { id: string } }) {
  return <CandidateMatchingPage studentId={params.id} />;
}
