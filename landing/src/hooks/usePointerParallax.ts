import { useEffect, type RefObject } from 'react';
import { prefersReducedMotion } from '@/lib/motion-preference';
import { stepSpring, springSettled, type Spring } from '@/lib/phone-motion';

/**
 * Differential pointer parallax: every `[data-parallax]` child moves up to N px (the attribute value) with a spring.
 * Only transforms are written; runs on fine pointers only and sleeps as soon as everything settled.
 */
export function usePointerParallax(root: RefObject<HTMLElement | null>) {
  useEffect(() => {
    const el = root.current;
    if (!el || prefersReducedMotion() || !window.matchMedia('(pointer: fine)').matches) return undefined;
    const layers = Array.from(el.querySelectorAll<HTMLElement>('[data-parallax]'));
    let target = { x: 0, y: 0 };
    let x: Spring = { value: 0, velocity: 0 };
    let y: Spring = { value: 0, velocity: 0 };
    let raf = 0; let last = 0;
    const apply = () => layers.forEach((layer) => {
      const depth = Number(layer.dataset.parallax) || 0;
      layer.style.transform = `translate3d(${(x.value * depth).toFixed(2)}px, ${(y.value * depth).toFixed(2)}px, 0)`;
    });
    const tick = (now: number) => {
      const dt = Math.min(0.05, (now - last) / 1000); last = now;
      x = stepSpring(x, target.x, dt, 9, 0.85); y = stepSpring(y, target.y, dt, 9, 0.85);
      apply();
      raf = springSettled(x, target.x, 0.002) && springSettled(y, target.y, 0.002) ? 0 : requestAnimationFrame(tick);
    };
    const onMove = (event: PointerEvent) => {
      if (event.pointerType !== 'mouse') return;
      target = { x: (event.clientX / window.innerWidth - 0.5) * 2, y: (event.clientY / window.innerHeight - 0.5) * 2 };
      if (!raf) { last = performance.now(); raf = requestAnimationFrame(tick); }
    };
    window.addEventListener('pointermove', onMove, { passive: true });
    return () => { window.removeEventListener('pointermove', onMove); cancelAnimationFrame(raf); layers.forEach((layer) => { layer.style.transform = ''; }); };
  }, [root]);
}
