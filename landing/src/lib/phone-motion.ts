/** Approved hero pose: the phone shows its right edge (buttons) to the viewer, leaning like the reference. */
export const INITIAL_POSE = { yaw: -17, pitch: 10, roll: -13 } as const;
export const clampPitch = (pitch: number) => Math.max(-30, Math.min(30, pitch));
export const applyDrag = (yaw: number, pitch: number, dx: number, dy: number) => ({
  yaw: yaw + dx * 0.55,
  pitch: clampPitch(pitch + dy * 0.42),
});
export const dampAngle = (current: number, target: number, deltaSeconds: number, lambda = 11) =>
  current + (target - current) * (1 - Math.exp(-lambda * deltaSeconds));
export const releaseVelocity = (velocity: number, deltaSeconds: number, friction = 4.8) =>
  velocity * Math.exp(-friction * deltaSeconds);

/** Shortest way back to the approved pose: the equivalent yaw (mod 360) closest to where the phone is now. */
export const nearestEquivalentYaw = (yaw: number, home: number = INITIAL_POSE.yaw) =>
  home + 360 * Math.round((yaw - home) / 360);

/* ---------- Spring (damped harmonic oscillator, semi-implicit Euler with sub-steps) ---------- */
export type Spring = { value: number; velocity: number };
export const stepSpring = (spring: Spring, target: number, deltaSeconds: number, omega = 8, zeta = 0.8): Spring => {
  const steps = Math.max(1, Math.ceil(deltaSeconds / (1 / 240)));
  const h = deltaSeconds / steps;
  let { value, velocity } = spring;
  for (let i = 0; i < steps; i++) {
    velocity += (-omega * omega * (value - target) - 2 * zeta * omega * velocity) * h;
    value += velocity * h;
  }
  return { value, velocity };
};
export const springSettled = (spring: Spring, target: number, epsilon = 0.01) =>
  Math.abs(spring.value - target) < epsilon && Math.abs(spring.velocity) < epsilon;

/* ---------- Entrance choreography of the phone (ms since the scene is ready) ---------- */
export const INTRO = {
  yawOffset: 35, // degrees the phone starts rotated away from the approved pose
  rise: 0.55, // world units below the resting position (about 60 px)
  omega: 7.4,
  zeta: 0.74,
  screenOnMs: 850,
  screenOnDurationMs: 350,
  ringStartMs: 1000,
  ringDurationMs: 900,
  coralBlendMs: 140,
  bannerDelayMs: 80,
  bannerDurationMs: 320,
  endMs: 2500,
} as const;

export const GOAL_MINUTES = 17;
export const TOTAL_MINUTES = 21;

const clamp01 = (value: number) => Math.max(0, Math.min(1, value));
const smoothstep = (value: number) => {
  const t = clamp01(value);
  return t * t * (3 - 2 * t);
};
export const easeOutCubic = (value: number) => 1 - Math.pow(1 - clamp01(value), 3);
const inverseEaseOutCubic = (value: number) => 1 - Math.cbrt(1 - clamp01(value));

/** Time (ms since scene ready) at which the counter crosses the 17 min goal and the ring turns coral. */
export const LIMIT_CROSS_MS = INTRO.ringStartMs + INTRO.ringDurationMs * inverseEaseOutCubic(GOAL_MINUTES / TOTAL_MINUTES);

export type ScreenFrame = {
  screenOn: number; // 0 backlight off, 1 fully lit
  minutes: number; // counter shown in the ring (0 -> 21)
  ringFraction: number; // 0 -> 1 progress of the ring against the 17 min goal
  overshoot: number; // minutes above the goal
  coral: number; // 0 mint, 1 coral
  banner: number; // opacity of the "Limite diário alcançado" banner
};

export const FINAL_FRAME: ScreenFrame = { screenOn: 1, minutes: TOTAL_MINUTES, ringFraction: 1, overshoot: TOTAL_MINUTES - GOAL_MINUTES, coral: 1, banner: 1 };
export const OFF_FRAME: ScreenFrame = { screenOn: 0, minutes: 0, ringFraction: 0, overshoot: 0, coral: 0, banner: 0 };

/** Pure timeline of the app screen: back-light on, ring sweep with counter, coral switch and banner. */
export const ringTimeline = (tMs: number): ScreenFrame => {
  const screenOn = smoothstep((tMs - INTRO.screenOnMs) / INTRO.screenOnDurationMs);
  const progress = easeOutCubic((tMs - INTRO.ringStartMs) / INTRO.ringDurationMs);
  const minutes = TOTAL_MINUTES * progress;
  const sinceCross = tMs - LIMIT_CROSS_MS;
  return {
    screenOn,
    minutes,
    ringFraction: Math.min(1, minutes / GOAL_MINUTES),
    overshoot: Math.max(0, minutes - GOAL_MINUTES),
    coral: smoothstep(sinceCross / INTRO.coralBlendMs),
    banner: smoothstep((sinceCross - INTRO.bannerDelayMs) / INTRO.bannerDurationMs),
  };
};

/* ---------- Idle ---------- */
export const IDLE = { floatPx: 6, floatPeriodS: 6, yawDeg: 2, pitchDeg: 0.8 } as const;
export const idleOffsets = (seconds: number) => ({
  y: Math.sin((seconds * Math.PI * 2) / IDLE.floatPeriodS) * IDLE.floatPx,
  yaw: Math.sin(seconds * 0.34) * IDLE.yawDeg,
  pitch: Math.sin(seconds * 0.22) * IDLE.pitchDeg,
});

export type PhonePose = {
  yaw: number; pitch: number; roll: number;
  targetYaw: number; targetPitch: number;
  velocityYaw: number; velocityPitch: number;
  dragging: boolean; hasInteracted: boolean; reducedMotion: boolean;
  /** Vertical float (px) frozen when the user takes over, so nothing snaps back. */
  floatY: number;
  /** Extra yaw (degrees) still left from the entrance spring; reported with the pose. */
  introYaw: number;
  introDone: boolean;
  /** Set to true to ease the float offset back to 0 together with a recenter. */
  recentering: boolean;
};

export const createPose = (reducedMotion: boolean): PhonePose => ({
  yaw: INITIAL_POSE.yaw, pitch: INITIAL_POSE.pitch, roll: INITIAL_POSE.roll,
  targetYaw: INITIAL_POSE.yaw, targetPitch: INITIAL_POSE.pitch,
  velocityYaw: 0, velocityPitch: 0, dragging: false, hasInteracted: false, reducedMotion,
  floatY: 0, introYaw: 0, introDone: reducedMotion, recentering: false,
});
