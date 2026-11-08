import { useEffect, useState } from 'react';
import { prefersReducedMotion } from '@/lib/motion-preference';

/**
 * Stage of a text entrance: `pending` (server HTML and first client render, copy is plain and visible unless `.js-motion` hides it)
 * or `motion` (after `ms`, the React Bits effect takes over). With reduced motion it never leaves `pending`, so the plain copy stays and nothing animates.
 */
export function useMotionStage(ms: number): 'pending' | 'motion' {
  const [stage, setStage] = useState<'pending' | 'motion'>('pending');
  useEffect(() => {
    if (prefersReducedMotion()) return undefined;
    const timer = window.setTimeout(() => setStage('motion'), ms);
    return () => window.clearTimeout(timer);
  }, [ms]);
  return stage;
}
