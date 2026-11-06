import { describe, expect, it } from 'vitest';
import {
  applyDrag, clampPitch, dampAngle, easeOutCubic, FINAL_FRAME, GOAL_MINUTES, idleOffsets, IDLE, INITIAL_POSE, INTRO,
  LIMIT_CROSS_MS, nearestEquivalentYaw, releaseVelocity, ringTimeline, springSettled, stepSpring, TOTAL_MINUTES, type Spring,
} from '@/lib/phone-motion';

describe('phone rotation math', () => {
  it('keeps the approved pose as the initial orientation', () => expect(INITIAL_POSE).toEqual({ yaw: -17, pitch: 10, roll: -13 }));
  it('allows continuous yaw over multiple rotations', () => expect(applyDrag(359, 0, 1000, 0).yaw).toBeGreaterThan(719));
  it('clamps only pitch at both ends', () => {
    expect(clampPitch(90)).toBe(30); expect(clampPitch(-90)).toBe(-30);
    expect(applyDrag(400, 25, 20, 100).yaw).toBe(411);
  });
  it('damps toward target while retaining user pose on release', () => {
    expect(dampAngle(0, 100, .02)).toBeGreaterThan(0);
    expect(releaseVelocity(4, .1)).toBeLessThan(4);
  });
  it('recenters through the shortest equivalent yaw instead of unwinding every turn', () => {
    expect(nearestEquivalentYaw(INITIAL_POSE.yaw + 720 + 40)).toBe(INITIAL_POSE.yaw + 720);
    expect(nearestEquivalentYaw(INITIAL_POSE.yaw - 700)).toBe(INITIAL_POSE.yaw - 720);
    expect(nearestEquivalentYaw(INITIAL_POSE.yaw + 12)).toBe(INITIAL_POSE.yaw);
  });
});

describe('spring', () => {
  it('settles on the target with at most a small overshoot', () => {
    let spring: Spring = { value: INTRO.yawOffset, velocity: 0 }; let min = Infinity;
    for (let i = 0; i < 60 * 3; i++) { spring = stepSpring(spring, 0, 1 / 60, INTRO.omega, INTRO.zeta); min = Math.min(min, spring.value); }
    expect(springSettled(spring, 0, 0.05)).toBe(true);
    expect(min).toBeGreaterThan(-INTRO.yawOffset * 0.08);
  });
  it('is frame-rate independent', () => {
    const run = (fps: number) => { let s: Spring = { value: 35, velocity: 0 }; for (let i = 0; i < fps * 0.5; i++) s = stepSpring(s, 0, 1 / fps, 7.4, 0.74); return s.value; };
    expect(Math.abs(run(30) - run(144))).toBeLessThan(0.1);
  });
});

describe('ring and counter timeline', () => {
  it('starts dark with an empty ring', () => {
    const frame = ringTimeline(0);
    expect(frame).toMatchObject({ screenOn: 0, minutes: 0, ringFraction: 0, coral: 0, banner: 0 });
  });
  it('lights the screen before the ring starts sweeping', () => {
    expect(ringTimeline(INTRO.screenOnMs - 1).screenOn).toBe(0);
    expect(ringTimeline(INTRO.ringStartMs).screenOn).toBeGreaterThan(0.2);
    expect(ringTimeline(INTRO.ringStartMs).minutes).toBe(0);
  });
  it('counts monotonically from 0 to 21 and never overshoots', () => {
    let previous = -1;
    for (let t = 0; t <= INTRO.endMs; t += 16) { const m = ringTimeline(t).minutes; expect(m).toBeGreaterThanOrEqual(previous); expect(m).toBeLessThanOrEqual(TOTAL_MINUTES); previous = m; }
    expect(ringTimeline(INTRO.endMs).minutes).toBe(TOTAL_MINUTES);
  });
  it('turns coral exactly when the counter crosses the 17 min goal, and the banner follows', () => {
    expect(ringTimeline(LIMIT_CROSS_MS - 1).minutes).toBeLessThan(GOAL_MINUTES);
    expect(ringTimeline(LIMIT_CROSS_MS + 1).minutes).toBeGreaterThan(GOAL_MINUTES);
    expect(ringTimeline(LIMIT_CROSS_MS - 20).coral).toBe(0);
    expect(ringTimeline(LIMIT_CROSS_MS + INTRO.coralBlendMs).coral).toBe(1);
    expect(ringTimeline(LIMIT_CROSS_MS).banner).toBe(0);
    expect(ringTimeline(LIMIT_CROSS_MS + 500).banner).toBe(1);
    expect(ringTimeline(LIMIT_CROSS_MS - 1).ringFraction).toBeLessThan(1);
  });
  it('lands exactly on the final frame', () => expect(ringTimeline(INTRO.endMs)).toEqual({ ...FINAL_FRAME, overshoot: 4 }));
  it('uses an ease-out curve', () => expect(easeOutCubic(0.5)).toBeGreaterThan(0.5));
  it('crosses the goal before the card choreography at ~1.7 s', () => expect(LIMIT_CROSS_MS).toBeLessThan(1700));
});

describe('idle float', () => {
  it('stays inside +-6 px and +-2 degrees', () => {
    for (let t = 0; t < 60; t += 0.1) {
      const o = idleOffsets(t);
      expect(Math.abs(o.y)).toBeLessThanOrEqual(IDLE.floatPx);
      expect(Math.abs(o.yaw)).toBeLessThanOrEqual(IDLE.yawDeg);
    }
  });
});
