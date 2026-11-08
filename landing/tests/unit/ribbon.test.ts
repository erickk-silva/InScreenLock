import { describe, expect, it } from 'vitest';
import { ribbonOutline, ribbonPoints, ribbonRim, swell, type RibbonSpec } from '@/lib/ribbon';

const spec: RibbonSpec = { cx: 300, cy: 400, rx: 300, ry: 200, rotate: -20, from: 240, to: 90, width: 120, peak: 0.4 };

describe('open aurora ribbon', () => {
  it('swells from a point, peaks where asked and tapers back to a point', () => {
    expect(swell(0, 0.4)).toBe(0); expect(swell(1, 0.4)).toBe(0); expect(swell(0.4, 0.4)).toBeCloseTo(1, 5);
    expect(swell(0.2, 0.4)).toBeGreaterThan(0); expect(swell(0.2, 0.4)).toBeLessThan(1);
  });
  it('has a pointed end on both sides, so it is open rather than a closed halo', () => {
    const { outer, inner } = ribbonPoints(spec);
    const gap = (i: number) => Math.hypot(outer[i].x - inner[i].x, outer[i].y - inner[i].y);
    expect(gap(0)).toBeLessThan(0.001); expect(gap(outer.length - 1)).toBeLessThan(0.001);
    expect(Math.max(...outer.map((_, i) => gap(i)))).toBeGreaterThan(100);
  });
  it('varies in width along its length (not a constant-width stroke)', () => {
    const { outer, inner } = ribbonPoints(spec);
    const widths = outer.map((_, i) => Math.hypot(outer[i].x - inner[i].x, outer[i].y - inner[i].y));
    expect(Math.max(...widths) - Math.min(...widths)).toBeGreaterThan(80);
  });
  it('produces closed outline and open rim paths', () => {
    expect(ribbonOutline(spec)).toMatch(/^M[\d.\- ]+L/); expect(ribbonOutline(spec).endsWith('Z')).toBe(true);
    expect(ribbonRim(spec).endsWith('Z')).toBe(false);
  });
});
