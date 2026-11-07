export function BrandMark() {
  return <svg className="brand-mark" viewBox="0 0 56 56" aria-hidden="true"><rect x="1" y="1" width="54" height="54" rx="16" fill="#07120f" stroke="#12634f" strokeWidth="2"/><circle cx="28" cy="28" r="17" fill="none" stroke="#22e3b0" strokeWidth="5"/><path d="M28 24v14" stroke="#e6eaf0" strokeWidth="4" strokeLinecap="round"/><circle cx="28" cy="17" r="2.4" fill="#e6eaf0"/><path d="M39 12l4-3" stroke="#22e3b0" strokeWidth="2" strokeLinecap="round"/></svg>;
}

export function AndroidGlyph({ size = 25, color = 'currentColor' }: { size?: number; color?: string }) {
  return <svg width={size} height={size} viewBox="0 0 24 24" aria-hidden="true" fill={color}><path d="M7.1 8.4 5.5 5.7l.9-.5L8 7.9a9 9 0 0 1 8 0l1.6-2.7.9.5-1.6 2.7a7.6 7.6 0 0 1 3.2 5.9H3.9a7.6 7.6 0 0 1 3.2-5.9ZM8 11.5a.8.8 0 1 0 0 1.6.8.8 0 0 0 0-1.6Zm8 0a.8.8 0 1 0 0 1.6.8.8 0 0 0 0-1.6ZM5 15.4h14v4.1a1.5 1.5 0 0 1-1.5 1.5H6.5A1.5 1.5 0 0 1 5 19.5v-4.1ZM2 15.4h2v4.2H2zm18 0h2v4.2h-2zM8 21.5h2v1.8H8zm6 0h2v1.8h-2z"/></svg>;
}

/** Padlock whose shackle drops into place when the card appears. */
export function LockGlyph({ size = 38 }: { size?: number }) {
  return <svg className="lock-icon" width={size} height={size} viewBox="0 0 32 32" aria-hidden="true">
    <path className="lock-shackle" d="M10.5 14V10.5a5.5 5.5 0 0 1 11 0V14" fill="none" stroke="#22e3b0" strokeWidth="3.2" strokeLinecap="round"/>
    <rect x="5.5" y="13.5" width="21" height="15" rx="4.2" fill="#22e3b0"/>
    <rect x="14.6" y="18.2" width="2.8" height="6" rx="1.4" fill="#08231e"/><circle cx="16" cy="19.4" r="2.1" fill="#08231e"/>
  </svg>;
}
