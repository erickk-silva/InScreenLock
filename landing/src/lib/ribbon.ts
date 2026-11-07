/**
 * Open aurora ribbons for the hero (pure geometry, no DOM): a crescent follows an arc of a rotated ellipse and its width swells and tapers,
 * so the band has a bright, wide body and two pointed, fading ends instead of the uniform stroke of a closed halo.
 */
export type RibbonSpec = {
  cx: number; cy: number; rx: number; ry: number;
  /** Rotation of the whole ellipse in degrees (screen space, clockwise positive). */
  rotate: number;
  /** Arc start and end angles in degrees (0 = right, 90 = bottom, 180 = left, 270 = top; y grows downward). */
  from: number; to: number;
  /** Width of the band at its thickest point, in svg units. */
  width: number;
  /** Where the thickest point sits along the arc, 0..1. */
  peak?: number;
};

type Point = { x: number; y: number };
const rad = (degrees: number) => (degrees * Math.PI) / 180;
const fix = (value: number) => Math.round(value * 10) / 10;

/** Smooth swell: 0 at both ends, 1 at `peak`. */
export const swell = (u: number, peak = 0.5) => {
  const t = u <= peak ? u / peak : (1 - u) / (1 - peak);
  return Math.pow(Math.sin((Math.min(1, Math.max(0, t)) * Math.PI) / 2), 1.35);
};

export function ribbonPoints(spec: RibbonSpec, steps = 56) {
  const turn = rad(spec.rotate); const cos = Math.cos(turn); const sin = Math.sin(turn);
  const place = (x: number, y: number): Point => ({ x: spec.cx + x * cos - y * sin, y: spec.cy + x * sin + y * cos });
  const centre: Point[] = []; const outer: Point[] = []; const inner: Point[] = [];
  for (let i = 0; i <= steps; i++) {
    const u = i / steps; const angle = rad(spec.from + (spec.to - spec.from) * u);
    const px = spec.rx * Math.cos(angle); const py = spec.ry * Math.sin(angle);
    // the outward normal of an ellipse is (cos/rx, sin/ry) normalised
    const nx = Math.cos(angle) / spec.rx; const ny = Math.sin(angle) / spec.ry; const length = Math.hypot(nx, ny);
    const half = (spec.width * swell(u, spec.peak)) / 2;
    centre.push(place(px, py));
    outer.push(place(px + (nx / length) * half, py + (ny / length) * half));
    inner.push(place(px - (nx / length) * half, py - (ny / length) * half));
  }
  return { centre, outer, inner };
}

const line = (points: Point[]) => points.map((point, index) => `${index === 0 ? 'M' : 'L'}${fix(point.x)} ${fix(point.y)}`).join('');

/** Closed outline of the crescent (filled with a gradient and blurred). */
export const ribbonOutline = (spec: RibbonSpec) => {
  const { outer, inner } = ribbonPoints(spec);
  return `${line(outer)}${line([...inner].reverse()).replace(/^M/, 'L')}Z`;
};

/** The outer (lit) edge only, for a thin highlight that fades with the ends. */
export const ribbonRim = (spec: RibbonSpec) => line(ribbonPoints(spec).outer);
