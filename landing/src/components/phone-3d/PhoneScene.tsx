'use client';
/* eslint-disable react-hooks/immutability -- Three.js render-loop pose is intentionally mutable to avoid React renders per frame. */

import { useCallback, useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { Environment, Lightformer } from '@react-three/drei';
import * as THREE from 'three';
import { dampAngle, FINAL_FRAME, idleOffsets, clampPitch, INITIAL_POSE, INTRO, LIMIT_CROSS_MS, OFF_FRAME, releaseVelocity, ringTimeline, springSettled, stepSpring, type PhonePose, type ScreenFrame, type Spring } from '@/lib/phone-motion';
import { drawPhoneScreen } from '@/lib/phone-screen';
import { createBodyGeometry, createPlateGeometry, DISPLAY, GLASS, paintBackTexture, PHONE, Z } from './phone-geometry';

const TEX_W = 788;
const TEX_H = 1584;
/** A long lens far from the device: strong enough perspective to read as a camera (right edge nearer than the left), weak enough to keep the screen legible. */
const FOV = 24;
const CAMERA_Z = 14;
const CAMERA_NEAR = 7;
const CAMERA_FAR = 24;
/** The canvas is taller than the stage by this fraction on each side, so a rotated device is never cropped. */
const CANVAS_PAD = 0.12;
const VISIBLE_HEIGHT = 2 * CAMERA_Z * Math.tan(THREE.MathUtils.degToRad(FOV / 2));
/** Phone height as a fraction of the stage height at rest (the approved hero shows the device about 95% of the stage). */
const STAGE_FILL = 1.0;
/** R = Rz(roll) * Rx(pitch) * Ry(yaw): the 360 turn is about the device's own long axis, the roll leans the whole thing in the view plane. */
const POSE_ORDER = 'ZXY' as const;
const smooth = (t: number) => { const x = Math.max(0, Math.min(1, t)); return x * x * (3 - 2 * x); };

/** The four corners of the lit display projected into the canvas, as fractions of its width/height: top-left, top-right, bottom-right, bottom-left. */
export type ScreenQuad = readonly [number, number, number, number, number, number, number, number];
export type SceneHooks = { onPose?: (yaw: number, pitch: number, quad: ScreenQuad) => void; onLimit?: () => void; onIntroDone?: () => void };

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
    Promise.all(weights.map((w) => document.fonts?.load(`${w} 20px "Inter Variable"`))).then(() => { if (alive) screen.paint(screen.current()); }).catch(() => undefined);
    return () => { alive = false; screen.texture.dispose(); };
  }, [screen]);
  return screen;
}
/** Offset of the device from the canvas centre, in units of the device scale. */
const HOME_X = -0.12;
const HOME_Y = 0.10;

/** Geometries and the back texture are created once per scene and disposed with it. */
function useDeviceParts() {
  const parts = useMemo(() => {
    const canvas = document.createElement('canvas'); canvas.width = 512; canvas.height = 1100;
    const ctx = canvas.getContext('2d'); if (ctx) paintBackTexture(ctx, canvas.width, canvas.height);
    const backTexture = new THREE.CanvasTexture(canvas); backTexture.colorSpace = THREE.SRGBColorSpace; backTexture.anisotropy = 8;
    return { body: createBodyGeometry(), glass: createPlateGeometry(GLASS.width, GLASS.height, GLASS.corner), display: createPlateGeometry(DISPLAY.width, DISPLAY.height, DISPLAY.corner), back: createPlateGeometry(GLASS.width, GLASS.height + 0.04, GLASS.corner + 0.04), island: createPlateGeometry(0.8, 0.88, 0.17), islandRim: createPlateGeometry(0.86, 0.94, 0.2), backTexture };
  }, []);
  useEffect(() => () => { parts.body.dispose(); parts.glass.dispose(); parts.display.dispose(); parts.back.dispose(); parts.island.dispose(); parts.islandRim.dispose(); parts.backTexture.dispose(); }, [parts]);
  return parts;
}

const CORNER = new THREE.Vector3();
/** Where the display sits in the canvas right now (normalised 0..1, y down). Reported with the pose so the layout can be verified against real pixels. */
function projectDisplay(device: THREE.Object3D, camera: THREE.Camera): ScreenQuad {
  device.updateMatrixWorld(true);
  const out: number[] = [];
  for (const [sx, sy] of [[-1, 1], [1, 1], [1, -1], [-1, -1]]) {
    CORNER.set((sx * DISPLAY.width) / 2, DISPLAY.y + (sy * DISPLAY.height) / 2, Z.display).applyMatrix4(device.matrixWorld).project(camera);
    out.push(Number(((CORNER.x + 1) / 2).toFixed(4)), Number(((1 - CORNER.y) / 2).toFixed(4)));
  }
  return out as unknown as ScreenQuad;
}

function Device({ pose, onPose, onLimit, onIntroDone }: { pose: React.MutableRefObject<PhonePose> } & SceneHooks) {
  const root = useRef<THREE.Group>(null);
  const screenGlow = useRef<THREE.PointLight>(null);
  const lastPoseReport = useRef(0);
  const intro = useRef({ ms: 0, frames: 0, wall: 0, yaw: { value: INTRO.yawOffset, velocity: 0 } as Spring, rise: { value: -INTRO.rise, velocity: 0 } as Spring, limit: false, done: false });
  const screen = useScreenTexture();
  const parts = useDeviceParts();
  useFrame((state, rawDelta) => {
    if (!root.current) return;
    const p = pose.current; const it = intro.current;
    const delta = Math.min(rawDelta, 0.05);
    const wall = performance.now();
    const wallDelta = Math.min(wall - (it.wall || wall), 250) / 1000; // the choreography follows real time, but one hitch (shader compile, tab switch) never skips more than 250 ms
    const unitPerPx = VISIBLE_HEIGHT / state.size.height;
    // uniform scale from the canvas size: the device fills STAGE_FILL of the stage height, and never more than the width allows
    const heightFit = (STAGE_FILL * VISIBLE_HEIGHT) / ((1 + CANVAS_PAD * 2) * PHONE.height);
    const widthFit = (0.94 * VISIBLE_HEIGHT * (state.size.width / state.size.height)) / 4.2;
    const scale = Math.min(heightFit, widthFit);
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
      p.targetPitch = clampPitch(p.targetPitch + p.velocityPitch * delta);
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

    root.current.position.set(HOME_X * scale, HOME_Y * scale + it.rise.value + p.floatY * unitPerPx, 0);
    root.current.rotation.set(THREE.MathUtils.degToRad(p.pitch), THREE.MathUtils.degToRad(p.yaw + it.yaw.value), THREE.MathUtils.degToRad(p.roll), POSE_ORDER);

    // moving key light: the glossy edge highlights follow the pointer, and the rotation sweeps the reflections
    const key = state.scene.getObjectByName('key-light');
    if (key) { key.position.x = THREE.MathUtils.lerp(key.position.x, 5 + state.pointer.x * 3.2, 0.06); key.position.y = THREE.MathUtils.lerp(key.position.y, 5 + state.pointer.y * 2.6, 0.06); }

    if (state.clock.elapsedTime - lastPoseReport.current > 0.12) {
      lastPoseReport.current = state.clock.elapsedTime;
      onPose?.(p.yaw + it.yaw.value, p.pitch, projectDisplay(root.current, state.camera));
    }
  });
  const frameMetal = { color: '#46515d', metalness: 1, roughness: 0.25, clearcoat: 0.4, clearcoatRoughness: 0.2, envMapIntensity: 1.45 } as const;
  const bright = { color: '#9aa7b4', metalness: 1, roughness: 0.18, envMapIntensity: 1.9 } as const;
  const shell = parts.body;
  return <group ref={root} rotation={[THREE.MathUtils.degToRad(INITIAL_POSE.pitch), THREE.MathUtils.degToRad(INITIAL_POSE.yaw), THREE.MathUtils.degToRad(INITIAL_POSE.roll), POSE_ORDER]}>
    <pointLight ref={screenGlow} position={[0, 0.2, 1.1]} intensity={0} distance={3.2} color="#22e3b0" />
    <mesh geometry={shell}><meshPhysicalMaterial {...frameMetal} /></mesh>
    {/* front: black glass with a thin bezel, the live display, a faint reflection layer and the circular punch-hole camera */}
    <mesh geometry={parts.glass} position={[0, 0, Z.glass]}><meshPhysicalMaterial color="#04070a" metalness={0.15} roughness={0.14} clearcoat={1} clearcoatRoughness={0.05} polygonOffset polygonOffsetFactor={-1} /></mesh>
    <mesh geometry={parts.display} position={[0, DISPLAY.y, Z.display]}><meshBasicMaterial map={screen.texture} toneMapped={false} side={THREE.FrontSide} polygonOffset polygonOffsetFactor={-2} /></mesh>
    <mesh geometry={parts.display} position={[0, DISPLAY.y, Z.sheen]}><meshPhysicalMaterial color="#ffffff" transparent opacity={0.05} metalness={0.05} roughness={0.08} clearcoat={1} clearcoatRoughness={0.05} envMapIntensity={1.7} depthWrite={false} polygonOffset polygonOffsetFactor={-3} /></mesh>
    <mesh position={[0, DISPLAY.height / 2 - 0.085, Z.hole]}><circleGeometry args={[0.052, 32]} /><meshBasicMaterial color="#010203" polygonOffset polygonOffsetFactor={-5} /></mesh>
    <mesh position={[0, DISPLAY.height / 2 - 0.085, Z.hole - 0.0003]}><ringGeometry args={[0.052, 0.066, 32]} /><meshStandardMaterial color="#3d4852" metalness={0.9} roughness={0.3} polygonOffset polygonOffsetFactor={-5} /></mesh>
    <mesh position={[0.012, DISPLAY.height / 2 - 0.095, Z.hole + 0.0004]}><circleGeometry args={[0.012, 16]} /><meshBasicMaterial color="#1b3340" polygonOffset polygonOffsetFactor={-6} /></mesh>
    {/* back: textured glass panel and the camera module (a different face, never a mirrored screen) */}
    <mesh geometry={parts.back} position={[0, 0, Z.back]} rotation={[0, Math.PI, 0]}><meshPhysicalMaterial map={parts.backTexture} color="#ffffff" metalness={0.2} roughness={0.45} clearcoat={0.5} clearcoatRoughness={0.3} envMapIntensity={0.55} /></mesh>
    <group position={[0.5, 1.78, Z.back - 0.0015]} rotation={[0, Math.PI, 0]}>
      <mesh geometry={parts.islandRim} position={[0, 0, 0.004]}><meshStandardMaterial color="#9aa6b3" metalness={1} roughness={0.25} envMapIntensity={1.4} /></mesh>
      <mesh geometry={parts.island} position={[0, 0, 0.012]}><meshPhysicalMaterial color="#2c353f" metalness={0.7} roughness={0.28} clearcoat={0.9} polygonOffset polygonOffsetFactor={-1} /></mesh>
      {[0.2, -0.2].map((y) => <group key={y} position={[0, y, 0.026]}>
        <mesh rotation={[Math.PI / 2, 0, 0]}><cylinderGeometry args={[0.15, 0.15, 0.03, 40]} /><meshStandardMaterial color="#8d99a6" metalness={1} roughness={0.22} /></mesh>
        <mesh position={[0, 0, 0.017]} rotation={[Math.PI / 2, 0, 0]}><cylinderGeometry args={[0.112, 0.112, 0.012, 40]} /><meshStandardMaterial color="#070b10" metalness={0.4} roughness={0.08} /></mesh>
        <mesh position={[-0.03, 0.03, 0.024]}><circleGeometry args={[0.032, 20]} /><meshBasicMaterial color="#2b4a5c" /></mesh>
      </group>)}
      <mesh position={[0.24, 0.3, 0.025]}><circleGeometry args={[0.045, 20]} /><meshStandardMaterial color="#d9e2ea" emissive="#fff7df" emissiveIntensity={0.25} /></mesh>
    </group>
    {/* side keys proud of the right edge (power above, volume rocker below) and a small key on the left; lighter metal so the profile reads at a glance */}
    <mesh position={[PHONE.width / 2 + 0.012, 0.95, 0]}><boxGeometry args={[0.07, 0.62, 0.13]} /><meshPhysicalMaterial {...bright} /></mesh>
    <mesh position={[PHONE.width / 2 + 0.012, 0.18, 0]}><boxGeometry args={[0.07, 0.46, 0.13]} /><meshPhysicalMaterial {...bright} /></mesh>
    <mesh position={[-PHONE.width / 2 - 0.008, 0.8, 0]}><boxGeometry args={[0.05, 0.4, 0.1]} /><meshPhysicalMaterial {...bright} /></mesh>
  </group>;
}

function SceneContent({ pose, ...hooks }: { pose: React.MutableRefObject<PhonePose> } & SceneHooks) {
  const { invalidate } = useThree();
  useEffect(() => { invalidate(); }, [invalidate]);
  return <>
    <ambientLight intensity={1.0} />
    <directionalLight name="key-light" position={[5, 5, 6]} intensity={3.0} color="#e7fff9" />
    <pointLight position={[3, -4, -2]} intensity={20} color="#15b891" distance={8} />
    <Device pose={pose} {...hooks} />
    <Environment resolution={64} frames={1} background={false}>
      <Lightformer form="rect" intensity={2.2} color="#dcfff7" scale={[8, 8, 1]} position={[0, 5, -5]} />
      <Lightformer form="rect" intensity={1.8} color="#21dcb4" scale={[5, 8, 1]} position={[-5, 0, -3]} />
      <Lightformer form="rect" intensity={9} color="#ffffff" scale={[2.2, 11, 1]} position={[7, 0.5, -4.5]} />
      <Lightformer form="rect" intensity={4} color="#e6fff9" scale={[1.4, 8, 1]} position={[6.5, 0, 0.5]} />
      <Lightformer form="rect" intensity={3.2} color="#bfe9ff" scale={[1.2, 7, 1]} position={[-6, 1, 2]} />
      <Lightformer form="ring" intensity={1.2} color="#a2b1c2" scale={[4, 4, 1]} position={[5, 2, -4]} />
    </Environment>
  </>;
}

export type PhoneSceneProps = { pose: React.MutableRefObject<PhonePose>; active?: boolean; onFailure?: () => void; onReady?: () => void } & SceneHooks;

export default function PhoneScene({ pose, active = true, onFailure, onReady, ...hooks }: PhoneSceneProps) {
  const canvas = useRef<HTMLCanvasElement | null>(null);
  const lost = useCallback((event: Event) => { event.preventDefault(); onFailure?.(); }, [onFailure]);
  const created = useCallback((state: { gl: THREE.WebGLRenderer }) => {
    canvas.current = state.gl.domElement;
    canvas.current.addEventListener('webglcontextlost', lost);
    onReady?.();
  }, [lost, onReady]);
  useEffect(() => () => canvas.current?.removeEventListener('webglcontextlost', lost), [lost]);
  return <Canvas onCreated={created} camera={{ position: [0, 0, CAMERA_Z], fov: FOV, near: CAMERA_NEAR, far: CAMERA_FAR }} dpr={1} frameloop={active ? 'always' : 'never'} gl={{ antialias: true, alpha: true, powerPreference: 'high-performance' }}>
    <SceneContent pose={pose} {...hooks} />
  </Canvas>;
}
