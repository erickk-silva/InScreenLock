import { describe, expect, it } from 'vitest';
import { applyDrag, clampPitch, dampAngle, INITIAL_POSE, releaseVelocity } from '@/lib/phone-motion';

describe('phone rotation math', () => {
  it('keeps the approved pose as the initial orientation', () => expect(INITIAL_POSE).toEqual({ yaw: 17, pitch: 2, roll: -10 }));
  it('allows continuous yaw over multiple rotations', () => expect(applyDrag(359, 0, 1000, 0).yaw).toBeGreaterThan(719));
  it('clamps only pitch at both ends', () => {
    expect(clampPitch(90)).toBe(30); expect(clampPitch(-90)).toBe(-30);
    expect(applyDrag(400, 25, 20, 100).yaw).toBe(411);
  });
  it('damps toward target while retaining user pose on release', () => {
    expect(dampAngle(0, 100, .02)).toBeGreaterThan(0);
    expect(releaseVelocity(4, .1)).toBeLessThan(4);
  });
});
