'use client';
/* eslint-disable react-hooks/immutability -- Three.js render-loop pose is intentionally mutable to avoid React renders per frame. */

import { Component, useCallback, useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { Environment, Lightformer, RoundedBox, Text } from '@react-three/drei';
import * as THREE from 'three';
import { dampAngle, FINAL_FRAME, idleOffsets, INITIAL_POSE, INTRO, LIMIT_CROSS_MS, OFF_FRAME, releaseVelocity, ringTimeline, springSettled, stepSpring, type PhonePose, type ScreenFrame, type Spring } from '@/lib/phone-motion';
import { drawPhoneScreen } from '@/lib/phone-screen';

const TEX_W = 720;
const TEX_H = 1584;
const FOV = 36;
const CAMERA_Z = 9;
const smooth = (t: number) => { const x = Math.max(0, Math.min(1, t)); return x * x * (3 - 2 * x); };

type SceneHooks = { onPose?: (yaw: number, pitch: number) => void; onLimit?: () => void; onIntroDone?: () => void };

/** The app screen is drawn in code on a canvas, so it can be alive (power on, ring sweep, counter) and always matches the poster. */
function useScreenTexture() {
  const screen = useMemo(() => {
    const canvas = document.createElement('canvas'); canvas.width = TEX_W; canvas.height = TEX_H;
    const texture = new THREE.CanvasTexture(canvas);
    texture.colorSpace = THREE.SRGBColorSpace; texture.anisotropy = 8; texture.generateMipmaps = true; texture.minFilter = THREE.LinearMipmapLinearFilter;
    const ctx = canvas.getContext('2d');
    let frame: ScreenFrame = OFF_FRAME;
    const paint = (next: ScreenFrame) => { frame = next; if (ctx) drawPhoneScreen(ctx, TEX_W, TEX_H, next); texture.needsUpdate = true; };
    paint(OFF_FRAME);
    return { texture, paint, current: () => frame };
  }, []);
  useEffect(() => {
    let alive = true;
    const weights = [400, 500, 600, 700, 800];
    Promise.all(weights.map((w) => document.fonts?.load(`italic ${w} 20px "Inter Variable"`))).then(() => { if (alive) screen.paint(screen.current()); }).catch(() => undefined);
    return () => { alive = false; screen.texture.dispose(); };
  }, [screen]);
  return screen;
}

function Device({ pose, scaleFactor, onPose, onLimit, onIntroDone }: { pose: React.MutableRefObject<PhonePose>; scaleFactor: number } & SceneHooks) {
  const root = useRef<THREE.Group>(null);
  const screenGlow = useRef<THREE.PointLight>(null);
  const lastPoseReport = useRef(0);
  const intro = useRef({ ms: 0, frames: 0, wall: 0, yaw: { value: INTRO.yawOffset, velocity: 0 } as Spring, rise: { value: -INTRO.rise, velocity: 0 } as Spring, limit: false, done: false });
  const screen = useScreenTexture();
  // uniform scale (no stretched phone); the stage tablet/mobile layouts use the lower end of each range
  const scale = 0.95 + scaleFactor * 0.1;
  const x = -0.1 - scaleFactor * 0.15;
  const y = 0.1 + scaleFactor * 0.08;
  useFrame((state, rawDelta) => {
    if (!root.current) return;
    const p = pose.current; const it = intro.current;
    const delta = Math.min(rawDelta, 0.05);
    const wall = performance.now();
    const wallDelta = Math.min(wall - (it.wall || wall), 250) / 1000; // the choreography follows real time, but one hitch (shader compile, tab switch) never skips more than 250 ms
    const unitPerPx = (2 * CAMERA_Z * Math.tan(THREE.MathUtils.degToRad(FOV / 2))) / state.size.height;
    root.current.scale.setScalar(scale);

    // ---- entrance: rise + yaw spring, then screen power-on / ring sweep / counter ----
    it.frames += 1;
    if (p.reducedMotion) {
      if (!it.done) { it.done = true; it.limit = true; it.yaw = { value: 0, velocity: 0 }; it.rise = { value: 0, velocity: 0 }; screen.paint(FINAL_FRAME); p.introDone = true; onLimit?.(); onIntroDone?.(); }
    } else if (it.frames > 2 && !it.done) {
      it.ms += wallDelta * 1000;
      it.yaw = stepSpring(it.yaw, 0, wallDelta, INTRO.omega, INTRO.zeta);
      it.rise = stepSpring(it.rise, 0, wallDelta, INTRO.omega * 0.95, INTRO.zeta);
      if (it.ms <= INTRO.endMs) screen.paint(ringTimeline(it.ms));
      if (!it.limit && it.ms >= LIMIT_CROSS_MS) { it.limit = true; onLimit?.(); }
      if (it.ms >= INTRO.endMs && springSettled(it.yaw, 0, 0.03) && springSettled(it.rise, 0, 0.002)) {
        it.done = true; it.yaw = { value: 0, velocity: 0 }; it.rise = { value: 0, velocity: 0 }; screen.paint(FINAL_FRAME); p.introDone = true; onIntroDone?.();
      }
    }
    it.wall = wall;
    p.introYaw = it.yaw.value;
    const on = smooth((it.ms - INTRO.screenOnMs) / INTRO.screenOnDurationMs);
    if (screenGlow.current) screenGlow.current.intensity = p.reducedMotion || it.done ? 5 : on * 5;

    // ---- user inertia ----
    if (!p.dragging && (Math.abs(p.velocityYaw) + Math.abs(p.velocityPitch) > 0.2)) {
      p.targetYaw += p.velocityYaw * delta;
      p.targetPitch = THREE.MathUtils.clamp(p.targetPitch + p.velocityPitch * delta, -30, 30);
      p.velocityYaw = releaseVelocity(p.velocityYaw, delta);
      p.velocityPitch = releaseVelocity(p.velocityPitch, delta);
    }

    // ---- idle float (pauses on first hover/touch, keeps whatever offset it had) ----
    if (!p.hasInteracted && !p.reducedMotion) {
      const ramp = smooth((it.ms - 900) / 1200); // idle fades in while the entrance settles
      const idle = idleOffsets(state.clock.elapsedTime);
      p.yaw = INITIAL_POSE.yaw + idle.yaw * ramp;
      p.pitch = INITIAL_POSE.pitch + idle.pitch * ramp;
      p.floatY = idle.y * ramp;
    } else {
      const snap = p.recentering;
      const dt = Math.min(rawDelta, snap ? 0.1 : 0.04);
      p.yaw = dampAngle(p.yaw, p.targetYaw, dt, snap ? 9 : 11);
      p.pitch = dampAngle(p.pitch, p.targetPitch, dt, snap ? 9 : 11);
      if (snap) {
        p.floatY = dampAngle(p.floatY, 0, dt, 9);
        if (Math.abs(p.yaw - p.targetYaw) < 0.25 && Math.abs(p.pitch - p.targetPitch) < 0.25 && Math.abs(p.floatY) < 0.25) { p.yaw = p.targetYaw; p.pitch = p.targetPitch; p.floatY = 0; p.recentering = false; }
      }
    }

    root.current.position.set(x, y + it.rise.value + p.floatY * unitPerPx, 0);
    root.current.rotation.set(THREE.MathUtils.degToRad(p.pitch), THREE.MathUtils.degToRad(p.yaw + it.yaw.value), THREE.MathUtils.degToRad(p.roll));

    // moving key light: the glossy edge highlights follow the pointer, and the rotation sweeps the reflections
    const key = state.scene.getObjectByName('key-light');
    if (key) { key.position.x = THREE.MathUtils.lerp(key.position.x, 5 + state.pointer.x * 3.2, 0.06); key.position.y = THREE.MathUtils.lerp(key.position.y, 5 + state.pointer.y * 2.6, 0.06); }

    if (state.clock.elapsedTime - lastPoseReport.current > 0.12) {
      lastPoseReport.current = state.clock.elapsedTime;
      onPose?.(p.yaw + it.yaw.value, p.pitch);
    }
  });
  return <group ref={root} position={[x, y, 0]} scale={scale} rotation={[THREE.MathUtils.degToRad(INITIAL_POSE.pitch), THREE.MathUtils.degToRad(INITIAL_POSE.yaw), THREE.MathUtils.degToRad(INITIAL_POSE.roll)]}>
    <pointLight ref={screenGlow} position={[0, 0.2, 1.1]} intensity={0} distance={3.2} color="#22e3b0" />
    <RoundedBox args={[2.35, 5.08, 0.24]} radius={0.22} smoothness={8}>
      <meshPhysicalMaterial color="#303842" metalness={0.82} roughness={0.24} clearcoat={0.8} clearcoatRoughness={0.16} />
    </RoundedBox>
    <RoundedBox args={[2.29, 5.00, 0.12]} radius={0.205} smoothness={8} position={[0, 0, 0.10]}>
      <meshStandardMaterial color="#080c11" metalness={0.35} roughness={0.2} />
    </RoundedBox>
    <RoundedBox args={[2.23, 4.87, 0.035]} radius={0.19} smoothness={8} position={[0, 0.01, 0.169]}>
      <meshPhysicalMaterial color="#4e5965" metalness={0.92} roughness={0.18} clearcoat={0.85} clearcoatRoughness={0.1} />
    </RoundedBox>
    <RoundedBox args={[2.17, 4.77, 0.025]} radius={0.18} smoothness={8} position={[0, 0.01, 0.192]}>
      <meshStandardMaterial color="#080c11" metalness={0.12} roughness={0.28} />
    </RoundedBox>
    <mesh position={[0, 0.01, 0.208]}>
      <planeGeometry args={[2.12, 4.66]} />
      <meshBasicMaterial map={screen.texture} toneMapped={false} side={THREE.FrontSide} />
    </mesh>
    <mesh position={[0, 0.01, 0.210]}>
      <planeGeometry args={[2.1, 4.63]} />
      <meshPhysicalMaterial color="#ffffff" transparent opacity={0.06} metalness={0.05} roughness={0.1} clearcoat={1} clearcoatRoughness={0.06} envMapIntensity={1.6} depthWrite={false} />
    </mesh>
    <RoundedBox args={[0.24, 0.105, 0.018]} radius={0.052} smoothness={8} position={[0, 2.245, 0.215]}>
      <meshBasicMaterial color="#03070b" />
    </RoundedBox>
    <mesh position={[0.075, 2.245, 0.226]}>
      <circleGeometry args={[0.027, 24]} />
      <meshBasicMaterial color="#182b37" />
    </mesh>
    <RoundedBox args={[2.33, 5.05, 0.13]} radius={0.22} smoothness={8} position={[0, 0, -0.10]}>
      <meshStandardMaterial color="#171d24" metalness={0.82} roughness={0.29} />
    </RoundedBox>
    <group position={[0.62, 1.72, -0.18]}>
      <RoundedBox args={[0.72, 0.76, 0.09]} radius={0.13} smoothness={6}>
        <meshPhysicalMaterial color="#303943" metalness={0.78} roughness={0.2} clearcoat={0.75} />
      </RoundedBox>
      {[-0.18, 0.18].map((x) => <mesh key={x} position={[x, 0, 0.05]}>
        <cylinderGeometry args={[0.1, 0.1, 0.025, 24]} />
        <meshStandardMaterial color="#090d11" metalness={0.2} roughness={0.2} />
      </mesh>)}
      <Text position={[0, -0.27, 0.05]} fontSize={0.08} color="#aab5c0" anchorX="center">INSCREEN</Text>
    </group>
    <mesh position={[0, 0, -0.18]}><circleGeometry args={[0.11, 32]} /><meshStandardMaterial color="#313a43" metalness={0.8} /></mesh>
    <mesh position={[1.19, 0.95, 0]}><boxGeometry args={[0.09, 0.53, 0.12]} /><meshPhysicalMaterial color="#56616d" metalness={0.92} roughness={0.16} clearcoat={0.85} /></mesh>
    <mesh position={[1.19, 0.28, 0]}><boxGeometry args={[0.09, 0.34, 0.12]} /><meshPhysicalMaterial color="#56616d" metalness={0.92} roughness={0.16} clearcoat={0.85} /></mesh>
    <mesh position={[-1.19, 0.75, 0]}><boxGeometry args={[0.09, 0.42, 0.12]} /><meshPhysicalMaterial color="#56616d" metalness={0.92} roughness={0.16} clearcoat={0.85} /></mesh>
  </group>;
}

function SceneContent({ pose, scaleFactor, ...hooks }: { pose: React.MutableRefObject<PhonePose>; scaleFactor: number } & SceneHooks) {
  const { invalidate } = useThree();
  useEffect(() => { invalidate(); }, [invalidate]);
  return <>
    <ambientLight intensity={1.15} />
    <directionalLight name="key-light" position={[5, 5, 6]} intensity={3.1} color="#e7fff9" />
    <pointLight position={[-4, 1, 2]} intensity={32} color="#19d9ae" distance={9} />
    <pointLight position={[3, -4, -2]} intensity={20} color="#15b891" distance={8} />
    <Device pose={pose} scaleFactor={scaleFactor} {...hooks} />
    <Environment resolution={64} frames={1} background={false}>
      <Lightformer form="rect" intensity={2.4} color="#dcfff7" scale={[8, 8, 1]} position={[0, 5, -5]} />
      <Lightformer form="rect" intensity={1.8} color="#21dcb4" scale={[5, 8, 1]} position={[-5, 0, -3]} />
      <Lightformer form="ring" intensity={1.2} color="#a2b1c2" scale={[4, 4, 1]} position={[5, 2, -4]} />
    </Environment>
  </>;
}

class CanvasFallback extends Component<{ children: React.ReactNode; onFailure?: () => void }, { failed: boolean }> {
  state = { failed: false };
  static getDerivedStateFromError() { return { failed: true }; }
  componentDidCatch() { this.props.onFailure?.(); }
  render() { return this.state.failed ? null : this.props.children; }
}

export default function PhoneScene({ pose, scaleFactor, active = true, onFailure, onReady, ...hooks }: { pose: React.MutableRefObject<PhonePose>; scaleFactor: number; active?: boolean; onFailure?: () => void; onReady?: () => void } & SceneHooks) {
  const canvas = useRef<HTMLCanvasElement | null>(null);
  const lost = useCallback((event: Event) => { event.preventDefault(); onFailure?.(); }, [onFailure]);
  const created = useCallback((state: { gl: THREE.WebGLRenderer }) => {
    canvas.current = state.gl.domElement;
    canvas.current.addEventListener('webglcontextlost', lost);
    onReady?.();
  }, [lost, onReady]);
  useEffect(() => () => canvas.current?.removeEventListener('webglcontextlost', lost), [lost]);
  return <CanvasFallback onFailure={onFailure}><Canvas onCreated={created} camera={{ position: [0, 0, CAMERA_Z], fov: FOV }} dpr={[1, 1.5]} frameloop={active ? 'always' : 'never'} gl={{ antialias: true, alpha: true, powerPreference: 'low-power' }}>
    <SceneContent pose={pose} scaleFactor={scaleFactor} {...hooks} />
  </Canvas></CanvasFallback>;
}
