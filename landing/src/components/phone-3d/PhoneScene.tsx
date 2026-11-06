'use client';
/* eslint-disable react-hooks/immutability -- Three.js render-loop pose is intentionally mutable to avoid React renders per frame. */

import { Component, useCallback, useEffect, useMemo, useRef } from 'react';
import { Canvas, useFrame, useThree } from '@react-three/fiber';
import { Environment, Lightformer, RoundedBox, Text } from '@react-three/drei';
import * as THREE from 'three';
import { dampAngle, INITIAL_POSE, releaseVelocity } from '@/lib/phone-motion';

type PhonePose = { yaw: number; pitch: number; roll: number; targetYaw: number; targetPitch: number; velocityYaw: number; velocityPitch: number; dragging: boolean; hasInteracted: boolean; reducedMotion: boolean };
function Device({ pose, scaleFactor, onPose }: { pose: React.MutableRefObject<PhonePose>; scaleFactor: number; onPose?: (yaw: number, pitch: number) => void }) {
  const root = useRef<THREE.Group>(null);
  const lastPoseReport = useRef(0);
  const scaleX = 1.6 + scaleFactor * 0.7;
  const scaleY = 1.1 + scaleFactor * 0.1;
  const x = -0.55 - scaleFactor * 1.25;
  const y = 0.35 + scaleFactor * 0.6;
  const texture = useMemo(() => {
    const loader = new THREE.TextureLoader();
    return loader.load('/app-screens/dashboard-demo.png', (loaded) => {
      loaded.colorSpace = THREE.SRGBColorSpace;
      loaded.anisotropy = 8;
    });
  }, []);
  useFrame((state, delta) => {
    if (!root.current) return;
    const p = pose.current;
    root.current.scale.set(scaleX, scaleY, 1);
    root.current.position.set(x, y, 0);
    if (!p.dragging && (Math.abs(p.velocityYaw) + Math.abs(p.velocityPitch) > 0.2)) {
      p.targetYaw += p.velocityYaw * delta;
      p.targetPitch = THREE.MathUtils.clamp(p.targetPitch + p.velocityPitch * delta, -30, 30);
      p.velocityYaw = releaseVelocity(p.velocityYaw, delta);
      p.velocityPitch = releaseVelocity(p.velocityPitch, delta);
    }
    if (!p.hasInteracted && !p.reducedMotion) {
      p.yaw = INITIAL_POSE.yaw + Math.sin(state.clock.elapsedTime * 0.34) * 2;
      p.pitch = INITIAL_POSE.pitch + Math.sin(state.clock.elapsedTime * 0.22) * 0.8;
    } else {
      p.yaw = dampAngle(p.yaw, p.targetYaw, Math.min(delta, 0.04));
      p.pitch = dampAngle(p.pitch, p.targetPitch, Math.min(delta, 0.04));
    }
    root.current.rotation.set(THREE.MathUtils.degToRad(p.pitch), THREE.MathUtils.degToRad(p.yaw), THREE.MathUtils.degToRad(p.roll));
    if (state.clock.elapsedTime - lastPoseReport.current > 0.12) {
      lastPoseReport.current = state.clock.elapsedTime;
      onPose?.(p.yaw, p.pitch);
    }
  });
  useEffect(() => () => texture.dispose(), [texture]);
  return <group ref={root} position={[x, y, 0]} scale={[scaleX, scaleY, 1]} rotation={[0, THREE.MathUtils.degToRad(17), THREE.MathUtils.degToRad(-10)]}>
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
      <meshBasicMaterial map={texture} toneMapped={false} side={THREE.FrontSide} />
    </mesh>
    <mesh position={[0, 0.01, 0.210]}>
      <planeGeometry args={[2.1, 4.63]} />
      <meshPhysicalMaterial color="#ffffff" transparent opacity={0.035} metalness={0.05} roughness={0.12} clearcoat={1} clearcoatRoughness={0.08} depthWrite={false} />
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

function SceneContent({ pose, scaleFactor, onPose }: { pose: React.MutableRefObject<PhonePose>; scaleFactor: number; onPose?: (yaw: number, pitch: number) => void }) {
  const { invalidate } = useThree();
  useEffect(() => { invalidate(); }, [invalidate]);
  return <>
    <ambientLight intensity={1.15} />
    <directionalLight position={[5, 5, 6]} intensity={3.1} color="#e7fff9" />
    <pointLight position={[-4, 1, 2]} intensity={32} color="#19d9ae" distance={9} />
    <pointLight position={[3, -4, -2]} intensity={20} color="#15b891" distance={8} />
    <Device pose={pose} scaleFactor={scaleFactor} onPose={onPose} />
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

export default function PhoneScene({ pose, scaleFactor, onFailure, onReady, onPose }: { pose: React.MutableRefObject<PhonePose>; scaleFactor: number; onFailure?: () => void; onReady?: () => void; onPose?: (yaw: number, pitch: number) => void }) {
  const canvas = useRef<HTMLCanvasElement | null>(null);
  const lost = useCallback((event: Event) => { event.preventDefault(); onFailure?.(); }, [onFailure]);
  const created = useCallback((state: { gl: THREE.WebGLRenderer }) => {
    canvas.current = state.gl.domElement;
    canvas.current.addEventListener('webglcontextlost', lost);
    onReady?.();
  }, [lost, onReady]);
  useEffect(() => () => canvas.current?.removeEventListener('webglcontextlost', lost), [lost]);
  return <CanvasFallback onFailure={onFailure}><Canvas onCreated={created} camera={{ position: [0, 0, 9], fov: 36 }} dpr={[1, 1.5]} frameloop="always" gl={{ antialias: true, alpha: true, powerPreference: 'low-power' }}>
    <SceneContent pose={pose} scaleFactor={scaleFactor} onPose={onPose} />
  </Canvas></CanvasFallback>;
}
