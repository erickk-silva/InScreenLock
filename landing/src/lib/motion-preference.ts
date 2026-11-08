/** True when entrance motion and loops must be skipped: OS preference, or the deterministic visual-test mode. */
export const prefersReducedMotion = () =>
  typeof window !== 'undefined' &&
  (window.matchMedia('(prefers-reduced-motion: reduce)').matches || new URLSearchParams(window.location.search).has('visualTest'));
