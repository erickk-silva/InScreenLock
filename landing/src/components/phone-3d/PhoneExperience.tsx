'use client';

import dynamic from 'next/dynamic';
import Image from 'next/image';
import { useCallback, useEffect, useRef, useState } from 'react';
import { RotateCcw } from 'lucide-react';
import { INITIAL_POSE, applyDrag, releaseVelocity } from '@/lib/phone-motion';

const PhoneScene = dynamic(() => import('./PhoneScene'), { ssr: false, loading: () => null });
type Pose = { yaw: number; pitch: number; roll: number; targetYaw: number; targetPitch: number; velocityYaw: number; velocityPitch: number; dragging: boolean; hasInteracted: boolean; reducedMotion: boolean };

export default function PhoneExperience() {
  const pose = useRef<Pose>({ yaw: INITIAL_POSE.yaw, pitch: INITIAL_POSE.pitch, roll: INITIAL_POSE.roll, targetYaw: INITIAL_POSE.yaw, targetPitch: INITIAL_POSE.pitch, velocityYaw: 0, velocityPitch: 0, dragging: false, hasInteracted: false, reducedMotion: false });
  const pointer = useRef<{ id: number; x: number; y: number; at: number } | null>(null);
  const [angles, setAngles] = useState<{ yaw: number; pitch: number }>({ yaw: INITIAL_POSE.yaw, pitch: INITIAL_POSE.pitch });
  const [webgl, setWebgl] = useState(true);
  const [canvasReady, setCanvasReady] = useState(false);
  const [announcement, setAnnouncement] = useState('');
  const [hasRotated, setHasRotated] = useState(false);
  const [scaleFactor, setScaleFactor] = useState(() => typeof window === 'undefined' ? 0 : Math.max(0, Math.min(1, (window.innerWidth - 1024) / 648)));
  const [inView, setInView] = useState(true);
  const root = useRef<HTMLDivElement>(null);
  useEffect(() => {
    const el = root.current;
    if (!el) return;
    const observer = new IntersectionObserver(([entry]) => setInView(entry.isIntersecting));
    observer.observe(el);
    return () => observer.disconnect();
  }, []);
  useEffect(() => {
    const resize = () => setScaleFactor(Math.max(0, Math.min(1, (window.innerWidth - 1024) / 648)));
    window.addEventListener('resize', resize);
    return () => window.removeEventListener('resize', resize);
  }, []);
  useEffect(() => () => {
    const el = root.current; const active = pointer.current;
    if (el && active && el.hasPointerCapture(active.id)) el.releasePointerCapture(active.id);
  }, []);
  useEffect(() => {
    const media = window.matchMedia('(prefers-reduced-motion: reduce)');
    const sync = () => { pose.current.reducedMotion = media.matches || new URLSearchParams(window.location.search).get('visualTest') === '1'; };
    sync(); media.addEventListener('change', sync);
    return () => media.removeEventListener('change', sync);
  }, []);
  const update = useCallback(() => setAngles({ yaw: pose.current.yaw, pitch: pose.current.pitch }), []);
  const markFailure = useCallback(() => { setCanvasReady(false); setWebgl(false); }, []);
  const markReady = useCallback(() => setCanvasReady(true), []);
  const reportPose = useCallback((yaw: number, pitch: number) => setAngles({ yaw: Number(yaw.toFixed(1)), pitch: Number(pitch.toFixed(1)) }), []);
  const stopIdle = useCallback(() => {
    if (!pose.current.hasInteracted) {
      pose.current.hasInteracted = true;
      pose.current.targetYaw = pose.current.yaw;
      pose.current.targetPitch = pose.current.pitch;
    }
  }, []);
  const down = (event: React.PointerEvent<HTMLDivElement>) => {
    if (event.button !== 0) return;
    stopIdle();
    pointer.current = { id: event.pointerId, x: event.clientX, y: event.clientY, at: performance.now() };
    pose.current.dragging = true; pose.current.velocityYaw = 0; pose.current.velocityPitch = 0;
    event.currentTarget.setPointerCapture(event.pointerId);
    setAnnouncement('Arraste para girar o aparelho.');
  };
  const move = (event: React.PointerEvent<HTMLDivElement>) => {
    const last = pointer.current;
    if (!last || last.id !== event.pointerId) return;
    const dx = event.clientX - last.x; const dy = event.clientY - last.y; const now = performance.now(); const elapsed = Math.max(8, now - last.at) / 1000;
    const next = applyDrag(pose.current.targetYaw, pose.current.targetPitch, dx, dy);
    pose.current.targetYaw = next.yaw; pose.current.targetPitch = next.pitch;
    if (dx !== 0 || dy !== 0) setHasRotated(true);
    pose.current.velocityYaw = dx * 0.55 / elapsed; pose.current.velocityPitch = dy * 0.42 / elapsed;
    pointer.current = { ...last, x: event.clientX, y: event.clientY, at: now };
    update();
  };
  const release = (event: React.PointerEvent<HTMLDivElement>) => {
    if (pointer.current?.id !== event.pointerId) return;
    pointer.current = null;
    pose.current.dragging = false;
    if (event.currentTarget.hasPointerCapture(event.pointerId)) event.currentTarget.releasePointerCapture(event.pointerId);
    pose.current.velocityYaw = releaseVelocity(pose.current.velocityYaw, .06);
    pose.current.velocityPitch = releaseVelocity(pose.current.velocityPitch, .06);
    setAnnouncement('Giro liberado. O aparelho continua se movendo suavemente.');
  };
  const reset = () => { pose.current.hasInteracted = true; pose.current.yaw = INITIAL_POSE.yaw; pose.current.pitch = INITIAL_POSE.pitch; pose.current.targetYaw = INITIAL_POSE.yaw; pose.current.targetPitch = INITIAL_POSE.pitch; pose.current.roll = INITIAL_POSE.roll; pose.current.velocityYaw = 0; pose.current.velocityPitch = 0; update(); setAnnouncement('Aparelho recentrado.'); };
  const keyDown = (event: React.KeyboardEvent<HTMLDivElement>) => {
    const step = event.shiftKey ? 30 : 12;
    if (event.key === 'Home') { event.preventDefault(); reset(); }
    else if (event.key === 'ArrowLeft' || event.key === 'ArrowRight' || event.key === 'ArrowUp' || event.key === 'ArrowDown') {
      event.preventDefault();
      stopIdle();
      const yaw = pose.current.targetYaw + (event.key === 'ArrowLeft' ? -step : event.key === 'ArrowRight' ? step : 0);
      const pitch = pose.current.targetPitch + (event.key === 'ArrowUp' ? -step : event.key === 'ArrowDown' ? step : 0);
      setHasRotated(true);
      pose.current.targetYaw = yaw; pose.current.targetPitch = Math.max(-30, Math.min(30, pitch)); update();
      setAnnouncement(`Posição ${Math.round(yaw)} graus.`);
    }
  };
  return <div ref={root} className="phone-wrap" tabIndex={0} role="group" aria-label="Smartphone 3D demonstrativo. Use as setas para girar e Home para recentrar."
    onPointerEnter={stopIdle} onPointerDown={down} onPointerMove={move} onPointerUp={release} onPointerCancel={release} onKeyDown={keyDown}
    data-testid="phone" data-yaw-deg={angles.yaw} data-pitch-deg={angles.pitch} data-roll-deg={INITIAL_POSE.roll}>
    <Image className="phone-poster" style={{ display: canvasReady && webgl ? 'none' : undefined }} src="/app-screens/phone-poster.svg" alt="Demonstração fictícia da tela Início do InScreenLock, com 21 minutos usados e meta de 17 minutos." fill unoptimized priority />
    {webgl && inView && <div className="phone-canvas" aria-hidden="true"><PhoneScene pose={pose} scaleFactor={scaleFactor} onFailure={markFailure} onReady={markReady} onPose={reportPose} /></div>}
    <div className="phone-controls"><button className={`recenter${hasRotated ? ' recenter-active' : ''}`} type="button" onClick={reset}><RotateCcw size={13} /> Recentrar aparelho</button><p className="gesture-hint">Arraste o aparelho para girar</p></div>
    <span className="sr-only" aria-live="polite">{announcement}</span>
  </div>;
}
