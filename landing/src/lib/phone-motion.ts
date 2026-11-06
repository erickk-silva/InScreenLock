export const INITIAL_POSE = { yaw: 17, pitch: 2, roll: -10 } as const;
export const clampPitch = (pitch: number) => Math.max(-30, Math.min(30, pitch));
export const applyDrag = (yaw: number, pitch: number, dx: number, dy: number) => ({
  yaw: yaw + dx * 0.55,
  pitch: clampPitch(pitch + dy * 0.42),
});
export const dampAngle = (current: number, target: number, deltaSeconds: number, lambda = 11) =>
  current + (target - current) * (1 - Math.exp(-lambda * deltaSeconds));
export const releaseVelocity = (velocity: number, deltaSeconds: number, friction = 4.8) =>
  velocity * Math.exp(-friction * deltaSeconds);

