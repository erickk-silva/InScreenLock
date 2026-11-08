'use client';

import Image from 'next/image';
import { Component, useCallback, useEffect, useRef, useState, type ComponentType, type ReactNode } from 'react';
import { MoveHorizontal, RotateCcw } from 'lucide-react';
import { applyDrag, capFlick, clampPitch, createPose, degreesFromHome, INITIAL_POSE, nearestEquivalentYaw, releaseVelocity, settleVelocity, type PhonePose } from '@/lib/phone-motion';
import { useWebglSupport } from '@/lib/webgl-support';
import type { PhoneSceneProps, ScreenQuad } from './PhoneScene';

const POSTER_ALT = 'Demonstração fictícia da tela Início do InScreenLock, com 21 minutos usados e meta de 17 minutos.';
const DEMO_DESCRIPTION = 'Tela demonstrativa com dados fictícios: Início do InScreenLock com 21 minutos usados, meta de 17 minutos, Instagram com 8 minutos e TikTok com 13 minutos.';
const POSTER_SIZE = { width: 955, height: 1380 } as const;
const HINT_DELAY_MS = 2000;
const TAP_SLOP_PX = 8;
const DOUBLE_TAP_MS = 320;
const KEY_ANNOUNCE_MS = 1000;

type Tap = { at: number; x: number; y: number };
type Drag = { id: number; x: number; y: number; startX: number; startY: number; at: number; travelled: number; touch: boolean; axis: 'pending' | 'horizontal' | 'vertical'; announced: boolean };
type CaptureBox = { left: number; top: number; width: number; height: number };

/** Any failure inside the 3D scene (render error, shader, bad context) falls back to the poster instead of taking the page down. */
class SceneBoundary extends Component<{ children: ReactNode; onFailure: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { this.props.onFailure(); }
  render() { return this.state.failed ? null : this.props.children; }
}

export default function PhoneExperience({ onLimitReached }: { onLimitReached?: () => void }) {
  const pose = useRef<PhonePose>(createPose(false));
  const pointer = useRef<Drag | null>(null);
  const lastTap = useRef<Tap | null>(null);
  const hintTimer = useRef(0);
  const keyTimer = useRef(0);
  const keyLast = useRef(0);
  const keyPending = useRef('');
  const lastPoseReport = useRef(0);
  const [frozen, setFrozen] = useState(false);
  const support = useWebglSupport();
  const [Scene, setScene] = useState<ComponentType<PhoneSceneProps> | null>(null);
  const [failed, setFailed] = useState(false);
  const [canvasReady, setCanvasReady] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const [hasRotated, setHasRotated] = useState(false);
  const [dragging, setDragging] = useState(false);
  const [hint, setHint] = useState(false);
  const [intro, setIntro] = useState<'pending' | 'done'>('pending');
  const [inView, setInView] = useState(false);
  const [everInView, setEverInView] = useState(false);
  const [captureBox, setCaptureBox] = useState<CaptureBox | null>(null);
  const root = useRef<HTMLDivElement>(null);
  const rotated = useRef(false);

  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const observer = new IntersectionObserver(([entry]) => { setInView(entry.isIntersecting); if (entry.isIntersecting) setEverInView(true); });
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  useEffect(() => () => {
    window.clearTimeout(hintTimer.current); window.clearTimeout(keyTimer.current);
    const el = root.current; const active = pointer.current;
    if (el && active && el.hasPointerCapture(active.id)) el.releasePointerCapture(active.id);
  }, []);
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const sync = () => { pose.current.reducedMotion = media.matches || new URLSearchParams(window.location.search).get('visualTest') === '1'; };
    sync(); media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);

  const markFailure = useCallback(() => { setCanvasReady(false); setFailed(true); onLimitReached?.(); }, [onLimitReached]);
  // the 3D chunk is loaded only when WebGL exists and the device scrolled into view; a failed download degrades to the poster
  useEffect(() => {
    if (support !== true || !everInView || failed || Scene) return undefined;
    let alive = true;
    import('./PhoneScene').then((module) => { if (alive) setScene(() => module.default); }).catch(() => { if (alive) markFailure(); });
    return () => { alive = false; };
  }, [support, everInView, failed, Scene, markFailure]);

  useEffect(() => { if (support === false) onLimitReached?.(); }, [support, onLimitReached]); // no WebGL: the poster is final, so the limit card must not wait for a 3D intro
  // "Arraste para girar": after ~2 s hovering without dragging (fine pointer) or after the entrance (touch); gone at the first drag.
  const armHint = useCallback((delay: number) => {
    window.clearTimeout(hintTimer.current);
    if (rotated.current) return;
    hintTimer.current = window.setTimeout(() => { if (!rotated.current) setHint(true); }, delay);
  }, []);
  useEffect(() => {
    if (intro === 'done' && window.matchMedia('(hover: none)').matches) armHint(1200);
  }, [intro, armHint]);

  const reportPose = useCallback((yaw: number, pitch: number, corners: ScreenQuad) => {
    const now = performance.now();
    if (now - lastPoseReport.current < 100) return;
    lastPoseReport.current = now;
    const el = root.current;
    if (!el) return;
    el.dataset.yawDeg = String(Number(yaw.toFixed(1))); el.dataset.pitchDeg = String(Number(pitch.toFixed(1))); el.dataset.screenQuad = corners.join(',');
    const canvas = el.querySelector('.phone-canvas canvas');
    if (canvas) {
      const canvasRect = canvas.getBoundingClientRect(); const rootRect = el.getBoundingClientRect();
      const xs = [corners[0], corners[2], corners[4], corners[6]]; const ys = [corners[1], corners[3], corners[5], corners[7]];
      const left = Math.min(...xs) * canvasRect.width + canvasRect.left - rootRect.left;
      const top = Math.min(...ys) * canvasRect.height + canvasRect.top - rootRect.top;
      const next = { left, top, width: (Math.max(...xs) - Math.min(...xs)) * canvasRect.width, height: (Math.max(...ys) - Math.min(...ys)) * canvasRect.height };
      setCaptureBox((previous) => previous && Math.max(Math.abs(previous.left - next.left), Math.abs(previous.top - next.top), Math.abs(previous.width - next.width), Math.abs(previous.height - next.height)) < 1 ? previous : next);
    }
  }, []);
  const markReady = useCallback(() => setCanvasReady(true), []);
  const introDone = useCallback(() => setIntro('done'), []);
  const markRotated = useCallback(() => { if (!rotated.current) { rotated.current = true; window.clearTimeout(hintTimer.current); setHint(false); setHasRotated(true); } }, []);
  const stopIdle = useCallback(() => {
    if (!pose.current.hasInteracted) {
      pose.current.hasInteracted = true; setFrozen(true);
      pose.current.targetYaw = pose.current.yaw;
      pose.current.targetPitch = pose.current.pitch;
    }
  }, []);
  const reset = useCallback(() => {
    const p = pose.current;
    p.hasInteracted = true; p.recentering = true;
    p.targetYaw = nearestEquivalentYaw(p.targetYaw); p.targetPitch = INITIAL_POSE.pitch; p.velocityYaw = 0; p.velocityPitch = 0;
    rotated.current = false; setHasRotated(false);
    setAnnouncement('Aparelho recentrado.');
  }, []);
  // keyboard steps are announced at most once per second, with the angle relative to the home pose (0-359)
  const announceKeys = useCallback((text: string) => {
    keyPending.current = text;
    if (keyTimer.current) return;
    keyTimer.current = window.setTimeout(() => { keyTimer.current = 0; keyLast.current = performance.now(); setAnnouncement(keyPending.current); }, Math.max(0, KEY_ANNOUNCE_MS - (performance.now() - keyLast.current)));
  }, []);

  const down = (event: React.PointerEvent<HTMLDivElement>) => {
    // buttons inside the device area (Recentrar) are plain controls: never start a drag or capture the pointer for them
    if (event.button !== 0 || (event.target as Element).closest('.phone-controls')) return;
    stopIdle();
    pose.current.recentering = false;
    const touch = event.pointerType === 'touch';
    pointer.current = { id: event.pointerId, x: event.clientX, y: event.clientY, startX: event.clientX, startY: event.clientY, at: event.timeStamp, travelled: 0, touch, axis: 'horizontal', announced: false };
    pose.current.dragging = true; pose.current.velocityYaw = 0; pose.current.velocityPitch = 0;
    event.currentTarget.setPointerCapture(event.pointerId);
    setDragging(true);
  };
  const move = (event: React.PointerEvent<HTMLDivElement>) => {
    const last = pointer.current;
    if (!last || last.id !== event.pointerId) return;
    const now = event.timeStamp;
    const axis = last.axis;
    const dx = event.clientX - last.x;
    const dy = event.clientY - last.y; const elapsed = Math.max(8, now - last.at) / 1000;
    const next = applyDrag(pose.current.targetYaw, pose.current.targetPitch, dx, dy);
    pose.current.targetYaw = next.yaw; pose.current.targetPitch = next.pitch;
    const travelled = last.travelled + Math.hypot(dx, event.clientY - last.y);
    let announced = last.announced;
    if (travelled > TAP_SLOP_PX) { markRotated(); if (!announced) { announced = true; setAnnouncement('Girando o aparelho.'); } }
    pose.current.velocityYaw = capFlick(dx * 0.55 / elapsed); pose.current.velocityPitch = capFlick(dy * 0.42 / elapsed);
    pointer.current = { ...last, axis, x: event.clientX, y: event.clientY, at: now, travelled, announced };
  };
  const release = (event: React.PointerEvent<HTMLDivElement>) => {
    const active = pointer.current;
    if (active?.id !== event.pointerId) return;
    pointer.current = null;
    pose.current.dragging = false; setDragging(false);
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    const cancelled = event.type === 'pointercancel';
    if (!cancelled && active.travelled <= TAP_SLOP_PX) {
      // a still press is a tap; two quick taps recentre the phone with a spring
      const now = event.timeStamp; const previous = lastTap.current; // event.timeStamp is when the tap happened, not when a busy main thread got to it
      if (previous && now - previous.at < DOUBLE_TAP_MS && Math.hypot(event.clientX - previous.x, event.clientY - previous.y) < 40) { lastTap.current = null; reset(); return; }
      lastTap.current = { at: now, x: event.clientX, y: event.clientY };
    }
    // a finger that rested before lifting (or a cancelled gesture) must not fling the device with its last stale velocity
    const stillFor = cancelled ? Infinity : event.timeStamp - active.at;
    pose.current.velocityYaw = releaseVelocity(settleVelocity(pose.current.velocityYaw, stillFor), .06);
    pose.current.velocityPitch = releaseVelocity(settleVelocity(pose.current.velocityPitch, stillFor), .06);
    if (active.announced) setAnnouncement('Giro liberado.');
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
      pose.current.targetYaw = yaw; pose.current.targetPitch = clampPitch(pitch);
      announceKeys(`Posição ${degreesFromHome(yaw)} graus.`);
    }
  };
  const enter = (event: React.PointerEvent<HTMLDivElement>) => { stopIdle(); if (event.pointerType === 'mouse') armHint(HINT_DELAY_MS); };
  const leave = () => { window.clearTimeout(hintTimer.current); setHint(false); };

  const live = Scene !== null && !failed && canvasReady;
  return <div ref={root} className="phone-wrap" tabIndex={0} role="group" aria-label="Smartphone 3D demonstrativo. Use as setas para girar e Home para recentrar." aria-describedby="phone-demo-description"
    onPointerEnter={enter} onPointerLeave={leave} onKeyDown={keyDown}
    data-testid="phone" data-dragging={dragging} data-intro={intro} data-webgl={live ? 'live' : 'poster'}
    data-idle={frozen ? 'frozen' : 'drifting'} data-screen-quad="" data-yaw-deg={INITIAL_POSE.yaw} data-pitch-deg={INITIAL_POSE.pitch} data-roll-deg={INITIAL_POSE.roll}>
    {/* the poster is a capture of this very scene at the approved pose: it is the no-WebGL fallback and, dimmed, the stand-in silhouette while the 3D chunk loads */}
    <Image className={`phone-poster${failed || support === false ? '' : ' is-dim'}${live ? ' is-handoff' : ''}`} src="/app-screens/phone-poster.webp" width={POSTER_SIZE.width} height={POSTER_SIZE.height} alt={POSTER_ALT} unoptimized priority />
    {Scene && !failed && <div className={`phone-canvas${canvasReady ? ' is-ready' : ''}`} aria-hidden="true">
      <SceneBoundary onFailure={markFailure}><Scene pose={pose} active={inView} onFailure={markFailure} onReady={markReady} onPose={reportPose} onLimit={onLimitReached} onIntroDone={introDone} /></SceneBoundary>
    </div>}
    <div className="phone-capture" aria-hidden="true" data-testid="phone-capture" style={captureBox ? { left: captureBox.left, top: captureBox.top, width: captureBox.width, height: captureBox.height } : undefined} onPointerEnter={enter} onPointerLeave={leave} onPointerDown={down} onPointerMove={move} onPointerUp={release} onPointerCancel={release}/>
    <p className={`gesture-hint${hint ? ' is-visible' : ''}`} aria-hidden="true"><MoveHorizontal size={16} strokeWidth={2.2} />Arraste para girar</p>
    <div className="phone-controls"><button className={`recenter${hasRotated ? ' recenter-active' : ''}`} type="button" onClick={reset}><RotateCcw size={14} /> Recentrar aparelho</button></div>
    <p className="demo-caption" data-testid="demo-caption">Tela demonstrativa — dados fictícios</p>
    <p className="sr-only" id="phone-demo-description">{DEMO_DESCRIPTION}</p>
    <span className="sr-only" aria-live="polite">{announcement}</span>
  </div>;
}
