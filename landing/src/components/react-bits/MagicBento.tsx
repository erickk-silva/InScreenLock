'use client';

import { useRef, type ComponentType, type PointerEvent } from 'react';
import type { LucideProps } from 'lucide-react';

export type MagicBentoCard = {
  icon: ComponentType<LucideProps>;
  title: string;
  body: string;
  size: 'wide' | 'tall' | 'normal';
  label: string;
};

/** Small React Bits Magic Bento adaptation: pointer edge light + staggered asymmetry, using the app's existing lucide icons. */
export default function MagicBento({ items }: { items: MagicBentoCard[] }) {
  const root = useRef<HTMLDivElement>(null);
  function track(event: PointerEvent<HTMLElement>) {
    if (event.pointerType !== 'mouse') return;
    const bounds = event.currentTarget.getBoundingClientRect();
    event.currentTarget.style.setProperty('--pointer-x', `${event.clientX - bounds.left}px`);
    event.currentTarget.style.setProperty('--pointer-y', `${event.clientY - bounds.top}px`);
  }
  return <div ref={root} className="magic-bento-grid" aria-label="Recursos do InScreenLock">{items.map(({ icon: Icon, title, body, size, label }) => <article className={`feature-card bento-${size}`} key={title} onPointerMove={track} onPointerEnter={track}>
    <span className="feature-icon"><Icon size={22}/></span><span className="feature-index">{label}</span><h3>{title}</h3><p>{body}</p>
  </article>)}</div>;
}
