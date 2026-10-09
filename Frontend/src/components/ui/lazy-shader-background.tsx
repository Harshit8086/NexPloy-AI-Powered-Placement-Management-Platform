"use client";

import dynamic from "next/dynamic";

/**
 * Lazy, SSR-free wrapper around the raw-WebGL ShaderBackground (see
 * mesh-gradient.tsx). It's pure client-side decoration with no SSR value,
 * so it's split into its own chunk instead of loading synchronously with
 * whichever page renders it. Reuse this wherever the shader backdrop is
 * needed instead of importing ShaderBackground directly — one shared lazy
 * wrapper instead of a `dynamic()` call copy-pasted per page.
 */
export const LazyShaderBackground = dynamic(
  () => import("@/components/ui/mesh-gradient").then((m) => m.ShaderBackground),
  { ssr: false, loading: () => null },
);
