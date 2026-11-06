'use client';

import dynamic from 'next/dynamic';
import Image from 'next/image';
import { useCallback, useEffect, useRef, useState } from 'react';
import { MoveHorizontal, RotateCcw } from 'lucide-react';
import { applyDrag, createPose, INITIAL_POSE, nearestEquivalentYaw, releaseVelocity, type PhonePose } from '@/lib/phone-motion';

const PhoneScene = dynamic(() => import('./PhoneScene'), { ssr: false, loading: () => null });
const POSTER_ALT = 'Demonstração fictícia da tela Início do InScreenLock, com 21 minutos usados e meta de 17 minutos.';
const HINT_DELAY_MS = 2000;
const TAP_SLOP_PX = 8;
const DOUBLE_TAP_MS = 320;

type Tap = { at: number; x: number; y: number };

export default function PhoneExperience({ onLimitReached }: { onLimitReached?: () => void }) {
  const pose = useRef<PhonePose>(createPose(false));
  const pointer = useRef<{ id: number; x: number; y: number; at: number; startX: number; startY: number; travelled: number } | null>(null);
  const lastTap = useRef<Tap | null>(null);
  const hintTimer = useRef(0);
  const [angles, setAngles] = useState<{ yaw: number; pitch: number }>({ yaw: INITIAL_POSE.yaw, pitch: INITIAL_POSE.pitch });
  const [webgl, setWebgl] = useState(true);
  const [canvasReady, setCanvasReady] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const [hasRotated, setHasRotated] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [hint, setHint] = useState(false);
  const [intro, setIntro] = useState<'pending' | 'done'>('pending');
  const [scaleFactor, setScaleFactor] = useState(() => typeof window === 'undefined' ? 0 : Math.max(0, Math.min(1, (window.innerWidth - 1024) / 648)));
  const [inView, setInView] = useState(false);
  const [everInView, setEverInView] = useState(false);
  const root = useRef<HTMLDivElement>(null);
  const rotated = useRef(false);

  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const observer = new IntersectionObserver(([entry]) => { setInView(entry.isIntersecting); if (entry.isIntersecting) setEverInView(true); });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const resize = () => setScaleFactor(Math.max(0, Math.min(1, (window.innerWidth - 1024) / 648)));
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, []);
  useEffect(() => () => {
    window.clearTimeout(hintTimer.current);
    const el = root.current; const active = pointer.current;
    if (el && active && el.hasPointerCapture(active.id)) el.releasePointerCapture(active.id);
  }, []);
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const sync = () => { pose.current.reducedMotion = media.matches || new URLSearchParams(window.location.search).get('visualTest') === '1'; };
    sync(); media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);

  // "Arraste para girar": after ~2 s hovering without dragging (fine pointer) or after the entrance (touch); gone at the first drag.
  const armHint = useCallback((delay: number) => {
    window.clearTimeout(hintTimer.current);
    if (rotated.current) return;
    hintTimer.current = window.setTimeout(() => { if (!rotated.current) setHint(true); }, delay);
  }, []);
  useEffect(() => {
    if (intro === 'done' && window.matchMedia('(hover: none)').matches) armHint(1200);
  }, [intro, armHint]);

  const reportPose = useCallback((yaw: number, pitch: number) => setAngles({ yaw: Number(yaw.toFixed(1)), pitch: Number(pitch.toFixed(1)) }), []);
  const markFailure = useCallback(() => { setCanvasReady(false); setWebgl(false); onLimitReached?.(); }, [onLimitReached]);
  const markReady = useCallback(() => setCanvasReady(true), []);
  const introDone = useCallback(() => setIntro('done'), []);
  const markRotated = useCallback(() => { if (!rotated.current) { rotated.current = true; window.clearTimeout(hintTimer.current); setHint(false); } setHasRotated(true); }, []);
  const stopIdle = useCallback(() => {
    if (!pose.current.hasInteracted) {
      pose.current.hasInteracted = true;
      pose.current.targetYaw = pose.current.yaw;
      pose.current.targetPitch = pose.current.pitch;
    }
  }, []);
  const reset = useCallback(() => {
    const p = pose.current;
    p.hasInteracted = true; p.recentering = true;
    p.targetYaw = nearestEquivalentYaw(p.targetYaw); p.targetPitch = INITIAL_POSE.pitch; p.velocityYaw = 0; p.velocityPitch = 0;
    setAnnouncement('Aparelho recentrado.');
  }, []);

  const down = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    stopIdle();
    pose.current.recentering = false;
    pointer.current = { id: event.pointerId, x: event.clientX, y: event.clientY, at: performance.now(), startX: event.clientX, startY: event.clientY, travelled: 0 };
    pose.current.dragging = true; pose.current.velocityYaw = 0; pose.current.velocityPitch = 0;
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
    setAnnouncement('Arraste para girar o aparelho.');
  };
  const move = (event: React.PointerEvent<HTMLDivElement>) => {
    const last = pointer.current;
    if (!last || last.id !== event.pointerId) return;
    const dx = event.clientX - last.x; const dy = event.clientY - last.y; const now = performance.now(); const elapsed = Math.max(8, now - last.at) / 1000;
    const next = applyDrag(pose.current.targetYaw, pose.current.targetPitch, dx, dy);
    pose.current.targetYaw = next.yaw; pose.current.targetPitch = next.pitch;
    const travelled = last.travelled + Math.hypot(dx, dy);
    if (travelled > TAP_SLOP_PX) markRotated();
    pose.current.velocityYaw = dx * 0.55 / elapsed; pose.current.velocityPitch = dy * 0.42 / elapsed;
    pointer.current = { ...last, x: event.clientX, y: event.clientY, at: now, travelled };
  };
  const release = (event: React.PointerEvent<HTMLDivElement>) => {
    const active = pointer.current;
    if (active?.id !== event.pointerId) return;
    pointer.current = null;
    pose.current.dragging = false; setDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    if (event.type === 'pointerup' && active.travelled <= TAP_SLOP_PX) {
      // a still press is a tap; two quick taps recentre the phone with a spring
      const now = performance.now(); const previous = lastTap.current;
      if (previous && now - previous.at < DOUBLE_TAP_MS && Math.hypot(event.clientX - previous.x, event.clientY - previous.y) < 40) { lastTap.current = null; reset(); return; }
      lastTap.current = { at: now, x: event.clientX, y: event.clientY };
    }
    pose.current.velocityYaw = releaseVelocity(pose.current.velocityYaw, .06);
    pose.current.velocityPitch = releaseVelocity(pose.current.velocityPitch, .06);
    setAnnouncement('Giro liberado. O aparelho continua se movendo suavemente.');
  };
  const keyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    const step = event.shiftKey ? 30 : 12;
    if (event.key === 'Home') { event.preventDefault(); stopIdle(); reset(); }
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight' || event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      event.preventDefault();
      stopIdle(); pose.current.recentering = false;
      const yaw = pose.current.targetYaw + (event.key === 'ArrowLeft' ? -step : event.key === 'ArrowRight' ? step : 0);
      const pitch = pose.current.targetPitch + (event.key === 'ArrowUp' ? -step : event.key === 'ArrowDown' ? step : 0);
      markRotated();
      pose.current.targetYaw = yaw; pose.current.targetPitch = Math.max(-30, Math.min(30, pitch));
      setAnnouncement(`Posição ${Math.round(yaw)} graus.`);
    }
  };
  const enter = (event: React.PointerEvent<HTMLDivElement>) => { stopIdle(); if (event.pointerType === 'mouse') armHint(HINT_DELAY_MS); };
  const leave = () => { window.clearTimeout(hintTimer.current); setHint(false); };

  const showPoster = !webgl || !canvasReady;
  return <div ref={root} className="phone-wrap" tabIndex={0} role="group" aria-label="Smartphone 3D demonstrativo. Use as setas para girar e Home para recentrar."
    onPointerEnter={enter} onPointerLeave={leave} onPointerDown={down} onPointerMove={move} onPointerUp={release} onPointerCancel={release} onKeyDown={keyDown}
    data-testid="phone" data-dragging={dragging} data-intro={intro} data-webgl={webgl && canvasReady ? 'live' : 'poster'}
    data-yaw-deg={angles.yaw} data-pitch-deg={angles.pitch} data-roll-deg={INITIAL_POSE.roll}>
    <Image className={`phone-poster${webgl ? ' phone-poster-pending' : ''}`} style={{ display: showPoster ? undefined : 'none' }} src="/app-screens/phone-poster.svg" alt={POSTER_ALT} fill unoptimized priority />
    {webgl && everInView && <div className={`phone-canvas${canvasReady ? ' is-ready' : ''}`} aria-hidden="true"><PhoneScene pose={pose} scaleFactor={scaleFactor} active={inView} onFailure={markFailure} onReady={markReady} onPose={reportPose} onLimit={onLimitReached} onIntroDone={introDone} /></div>}
    <p className={`gesture-hint${hint ? ' is-visible' : ''}`} aria-hidden="true"><MoveHorizontal size={16} strokeWidth={2.2} />Arraste para girar</p>
    <div className="phone-controls"><button className={`recenter${hasRotated ? ' recenter-active' : ''}`} type="button" onClick={reset}><RotateCcw size={14} /> Recentrar aparelho</button></div>
    <span className="sr-only" aria-live="polite">{announcement}</span>
  </div>;
}
