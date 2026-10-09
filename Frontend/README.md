# NEXPLOY Prototype

An interactive, frontend-only demo of **NEXPLOY** — an AI-powered Placement Intelligence & Career
Success Platform. This exists to show the product vision, UI/UX, navigation, role experiences, and
demo story before real development begins.

> **This repository contains the interactive NEXPLOY product prototype. It uses simulated data and
> does not contain the production backend, ML models, optimization engine, or Groq integration.**

## Stack

- Next.js 14 (App Router) + TypeScript
- Tailwind CSS + a hand-built shadcn/ui-style component layer (dark "Placement Intelligence" theme)
- framer-motion for motion, recharts for charts, lucide-react for icons
- zustand for small cross-page app state (demo role, active student, copilot, notifications)
- No backend, no database, no auth provider, no external AI calls

## Installation

```bash
npm install
```

## Development

```bash
npm run dev
```

Visit `http://localhost:3000`.

## Build

```bash
npm run build
npm run start
```

## Demo Roles

Use the role selector on `/login`, or the "Switch Demo Role" control in the topbar once inside the
app, to move between:

- **Student** — Rahul Sharma's placement journey (Digital Twin, Skill Gap, Roadmap, What-If
  Simulator, Drives, Applications, Offers, Documents).
- **Recruiter** — TechNova's Backend Developer drive, ranked candidates, and explainable matching.
- **Placement Officer** — Command Center, Risk Radar, Drive Orchestrator, Analytics.

Switching roles is a UI-only convenience — it is not real authentication or authorization.

## Demo Flow

The primary walkthrough this prototype is built around:

1. **Landing** → *Explore NEXPLOY* → **Student Login**
2. **Student Dashboard** → **Digital Twin** (refresh it) → **Skill Gap** → **What-If Simulator**
   (stack AWS + DSA + Mock Interviews and watch probability climb)
3. **Placement Drives** → **TechNova** (eligibility + explainable match breakdown)
4. **Switch Role → Recruiter** → **Candidate Ranking** → **Rahul Sharma** → *Analyze Candidate* →
   91% explainable match
5. **Switch Role → Placement Officer** → **Command Center** → **Risk Radar** → **Drive
   Orchestrator** → open the CloudSphere conflict → *Optimize Schedule* → Conflict Resolved

Every button on this path is fully wired. Ctrl+K (or the "Ask Nexploy anything..." button) opens the
Nexploy Copilot from anywhere in the app — a role-aware canned-response palette, not a real LLM call.

## Architecture Notes

All mock data lives in `src/data/mock/*.ts`, typed and cross-referenced by id — the same entity's
numbers stay identical everywhere it appears. All simulated "intelligence" (Digital Twin refresh,
What-If scenarios, candidate matching, schedule optimization, Copilot answers) lives in
`src/lib/simulate.ts` as deterministic functions with no `Math.random`. This separation is
deliberate: production can swap mock data → real APIs, simulated auth → real auth/RBAC, simulated
readiness → a real readiness engine, mock matching → a real matching pipeline, mock prediction → ML,
mock scheduling → OR-Tools, and mock Copilot → a Groq-backed Copilot, without touching presentation
components.

## API Layer

`src/lib/api/` sits between the UI and the data. Every function in it is `async` and typed, even
though today it just wraps `src/data/mock/*.ts` and `src/lib/simulate.ts` — so a real backend swap
never touches a call site's signature, only its body.

- **Mode flag** — `src/lib/api/config.ts` exports `API_MODE`, read from `NEXT_PUBLIC_API_MODE`
  (`.env.example`), defaulting to `"mock"`. Nothing branches on it yet; it exists for the day a
  domain file's mock branch gets a `"live"` counterpart.
- **Domain files** — one per `src/data/mock/*.ts` file (`students.ts`, `drives.ts`,
  `applications.ts`, `matches.ts`, `offers.ts`, `documents.ts`, `notifications.ts`, `risk.ts`,
  `schedules.ts`, `mentors.ts`, `campuses.ts`, `analytics.ts`, `recruiters.ts`, `interviews.ts`).
  Each function has a `/** METHOD /api/path */` doc comment above it — that comment is the source
  for `docs/API_CONTRACT.md`, the document to hand your backend teammate.
- **`client.ts`** — a thin `fetch` wrapper (`apiRequest<T>()`) for the future `"live"` branch. It is
  not called anywhere yet.
- **Wired end-to-end today**: `src/features/student/Dashboard.tsx` and
  `src/features/student/DigitalTwin.tsx` call `src/lib/api/students.ts` instead of importing
  `src/data/mock/students.ts` directly, fetch with `useEffect`/`useState`, and render a `<Skeleton>`
  loading state while the (currently instant) mock "request" resolves. Every other screen still
  imports mock data directly — extend the pattern to a screen only when you're ready to move it.

**To extend the pattern to a new screen**, using `students.ts` as the reference:

1. Confirm the domain file in `src/lib/api/` already exports what you need (it should — Step 2 of
   the handoff pass wrapped every mock/simulate export). If not, add a function there that wraps
   the existing `src/data/mock/*.ts` or `src/lib/simulate.ts` export — never reimplement the logic.
2. In the feature component, replace the direct `@/data/mock/...` import with the matching
   `@/lib/api/...` import.
3. Add `useState` for the data (typed `null` initial state) and a `useEffect` that calls the API
   function on mount and sets state from its resolved value.
4. Render a loading state (reuse `<Skeleton>` from `@/components/ui/skeleton.tsx`) while the state
   is `null`, matching the screen's real layout so the swap-in doesn't jump.
5. Leave every other data source on that screen (other domains, `useAppStore`) untouched unless
   you're deliberately migrating them too — migrate one domain at a time.

See `docs/API_CONTRACT.md` for the full function-by-function contract.

## What's Not Real Yet

- **Role switching and route restrictions are presentation-only.** `RoleGuard.tsx` says so directly
  in its own comment: "production will enforce this on the server; here it is presentation only."
  Picking a role in the topbar or demo login, and the `canAccess` check that blocks a route for the
  wrong role, are both client-side UI conveniences with zero real security behind them — anyone can
  bypass them by editing client state. **A real backend must independently authenticate and
  authorize every request; it must never trust a role the frontend claims.**
- **No real network calls exist yet.** `API_MODE` defaults to `"mock"` and nothing in `src/lib/api/`
  currently calls `client.ts`'s `apiRequest()` — every function resolves from local mock data.
- **All "intelligence" is deterministic, canned logic**, not a real model: Digital Twin scoring,
  What-If projections, candidate matching, schedule optimization, and the Nexploy Copilot's answers
  all live in `src/lib/simulate.ts` with fixed lookup tables and no `Math.random`, labeled "Demo
  Data" / "Prototype Data" in the UI throughout.

## Deployment

This is a standard Next.js app — import the repository on [Vercel](https://vercel.com/new); it
auto-detects the framework. Set `NEXT_PUBLIC_API_MODE` and `NEXT_PUBLIC_API_BASE_URL` (see
`.env.example`) once a real backend exists — the prototype runs with no environment variables set.
