import * as THREE from 'three';

/** Physical size of the device in scene units (about 1 unit = 3.2 cm, so 2.3 x 5.0 is a 6.7" phone). */
export const PHONE = { width: 2.5, height: 5.0, thickness: 0.22, corner: 0.34, bevel: 0.045 } as const;
/** Front glass, display and punch hole, all measured from the centre of the device. */
export const GLASS = { width: 2.37, height: 4.78, corner: 0.27 } as const;
export const DISPLAY = { width: 2.3, height: 4.62, corner: 0.235, y: 0.005 } as const;
const FRONT_Z = PHONE.thickness / 2;

export const roundedRectShape = (width: number, height: number, radius: number) => {
  const x = -width / 2; const y = -height / 2; const r = Math.min(radius, width / 2, height / 2);
  const shape = new THREE.Shape();
  shape.moveTo(x + r, y);
  shape.lineTo(x + width - r, y); shape.absarc(x + width - r, y + r, r, -Math.PI / 2, 0, false);
  shape.lineTo(x + width, y + height - r); shape.absarc(x + width - r, y + height - r, r, 0, Math.PI / 2, false);
  shape.lineTo(x + r, y + height); shape.absarc(x + r, y + height - r, r, Math.PI / 2, Math.PI, false);
  shape.lineTo(x, y + r); shape.absarc(x + r, y + r, r, Math.PI, Math.PI * 1.5, false);
  return shape;
};

/** One continuous frame: a single extrusion with a rounded edge, so the side reads as one metal band instead of stacked plates. */
export const createBodyGeometry = () => {
  const { width, height, thickness, corner, bevel } = PHONE;
  const geometry = new THREE.ExtrudeGeometry(roundedRectShape(width - bevel * 2, height - bevel * 2, corner - bevel), {
    depth: thickness - bevel * 2, bevelEnabled: true, bevelSize: bevel, bevelThickness: bevel, bevelSegments: 5, curveSegments: 16, steps: 1,
  });
  geometry.translate(0, 0, -(thickness - bevel * 2) / 2);
  geometry.computeVertexNormals();
  return geometry;
};

/** Flat rounded plate with 0..1 UVs, so a texture maps exactly onto it. */
export const createPlateGeometry = (width: number, height: number, radius: number) => {
  const geometry = new THREE.ShapeGeometry(roundedRectShape(width, height, radius), 14);
  const position = geometry.getAttribute('position'); const uv = geometry.getAttribute('uv');
  for (let i = 0; i < position.count; i++) uv.setXY(i, position.getX(i) / width + 0.5, position.getY(i) / height + 0.5);
  uv.needsUpdate = true;
  return geometry;
};

export const Z = { front: FRONT_Z, glass: FRONT_Z + 0.0012, display: FRONT_Z + 0.0026, sheen: FRONT_Z + 0.0042, hole: FRONT_Z + 0.0056, back: -FRONT_Z - 0.0012 } as const;

/** Back glass: subtle diagonal sheen, brushed grain and a faint brand ring, so it is not a flat black slab. Deterministic. */
export const paintBackTexture = (ctx: CanvasRenderingContext2D, width: number, height: number) => {
  const base = ctx.createLinearGradient(0, 0, width, height);
  base.addColorStop(0, '#56626f'); base.addColorStop(0.35, '#2e3843'); base.addColorStop(0.7, '#202830'); base.addColorStop(1, '#4a5663');
  ctx.fillStyle = base; ctx.fillRect(0, 0, width, height);
  const sheen = ctx.createLinearGradient(width * 0.05, 0, width * 0.95, height * 0.55);
  sheen.addColorStop(0, 'rgba(255,255,255,0)'); sheen.addColorStop(0.42, 'rgba(180,255,236,0.20)'); sheen.addColorStop(0.55, 'rgba(255,255,255,0.10)'); sheen.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = sheen; ctx.fillRect(0, 0, width, height);
  let seed = 7; const random = () => { seed = (seed * 16807) % 2147483647; return seed / 2147483647; };
  for (let i = 0; i < 900; i++) { ctx.fillStyle = `rgba(255,255,255,${0.012 + random() * 0.03})`; ctx.fillRect(random() * width, random() * height, 1 + random() * 36, 1); }
  ctx.strokeStyle = 'rgba(34,227,176,0.55)'; ctx.lineWidth = width * 0.014; ctx.beginPath(); ctx.arc(width / 2, height * 0.62, width * 0.11, 0, Math.PI * 2); ctx.stroke();
  ctx.fillStyle = 'rgba(34,227,176,0.55)'; ctx.fillRect(width / 2 - width * 0.007, height * 0.62 - width * 0.045, width * 0.014, width * 0.09);
};
