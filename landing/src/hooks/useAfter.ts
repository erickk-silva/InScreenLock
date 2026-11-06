import { useEffect, useState } from 'react';
import { prefersReducedMotion } from '@/lib/motion-preference';

/** Becomes true after `ms` (immediately when motion is reduced). Used to stage the entrance timeline of React Bits text effects. */
export function useAfter(ms: number) {
  const [ready, setReady] = useState(false);
  useEffect(() => {
    const timer = window.setTimeout(() => setReady(true), prefersReducedMotion() ? 0 : ms);
    return () => window.clearTimeout(timer);
  }, [ms]);
  return ready;
}
