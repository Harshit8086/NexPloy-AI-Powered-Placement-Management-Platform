"use client";

import { useCallback, useEffect, useState } from "react";

/**
 * Loads data from an async API function (src/lib/api/*), handling loading and error state.
 * In "live" mode the api functions hit the backend; on error the caller shows an error/retry
 * state and never falls back to fabricated data. `reload()` re-runs the fetcher (used by the
 * retry affordance in AsyncBoundary).
 *
 * `deps` controls re-fetching, like useEffect's dependency array. `initial` is the value
 * returned before the first resolve (typically [] or a safe default) so render never sees
 * undefined.
 */
export function useApiResource<T>(
  fetcher: () => Promise<T>,
  deps: unknown[],
  initial: T,
): { data: T; loading: boolean; error: string | null; reload: () => void } {
  const [data, setData] = useState<T>(initial);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [nonce, setNonce] = useState(0);

  const reload = useCallback(() => setNonce((n) => n + 1), []);

  useEffect(() => {
    let active = true;
    setLoading(true);
    setError(null);
    fetcher()
      .then((result) => {
        if (active) {
          setData(result);
          setLoading(false);
        }
      })
      .catch((err) => {
        if (active) {
          setError(err instanceof Error ? err.message : "Failed to load");
          setLoading(false);
        }
      });
    return () => {
      active = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [...deps, nonce]);

  return { data, loading, error, reload };
}
