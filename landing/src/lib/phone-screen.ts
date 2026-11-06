import { FINAL_FRAME, GOAL_MINUTES, TOTAL_MINUTES, type ScreenFrame } from './phone-motion';

/** Design space of the demonstration screen. The texture is a uniform scale of it. */
export const SCREEN_W = 440;
export const SCREEN_H = 968;

const MINT = '#22e3b0';
const CORAL = '#ff6b7a';
const FONT = '"Inter Variable", Inter, Roboto, system-ui, sans-serif';
const INSTAGRAM_MINUTES = 8;
const TIKTOK_MINUTES = 13;

type Ctx = CanvasRenderingContext2D;

const mix = (a: [number, number, number], b: [number, number, number], t: number) => `rgb(${a.map((v, i) => Math.round(v + (b[i] - v) * t)).join(',')})`;
const MINT_RGB: [number, number, number] = [34, 227, 176];
const CORAL_RGB: [number, number, number] = [255, 107, 122];

function roundRect(ctx: Ctx, x: number, y: number, w: number, h: number, r: number) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}

function text(ctx: Ctx, value: string, x: number, y: number, size: number, weight: number, color: string, align: CanvasTextAlign = 'left', italic = false) {
  ctx.font = `${italic ? 'italic ' : ''}${weight} ${size}px ${FONT}`;
  ctx.fillStyle = color;
  ctx.textAlign = align;
  ctx.textBaseline = 'middle';
  ctx.fillText(value, x, y);
}

function strokeIcon(ctx: Ctx, color: string, width: number, draw: () => void) {
  ctx.save();
  ctx.strokeStyle = color; ctx.lineWidth = width; ctx.lineCap = 'round'; ctx.lineJoin = 'round';
  draw();
  ctx.restore();
}

function calendarIcon(ctx: Ctx, cx: number, cy: number, size: number, color: string, width: number) {
  const s = size / 24; const x = cx - 12 * s; const y = cy - 12 * s;
  strokeIcon(ctx, color, width, () => {
    roundRect(ctx, x + 3 * s, y + 5 * s, 18 * s, 16 * s, 3 * s); ctx.stroke();
    ctx.beginPath(); ctx.moveTo(x + 16 * s, y + 3 * s); ctx.lineTo(x + 16 * s, y + 7 * s); ctx.moveTo(x + 8 * s, y + 3 * s); ctx.lineTo(x + 8 * s, y + 7 * s); ctx.moveTo(x + 3 * s, y + 10 * s); ctx.lineTo(x + 21 * s, y + 10 * s); ctx.stroke();
  });
}

function drawStatusBar(ctx: Ctx) {
  text(ctx, '01:07', 38, 58, 16, 700, '#f2f5f8');
  // signal, wifi and battery drawn as simple solid glyphs, as in the app status bar
  ctx.fillStyle = '#f2f5f8';
  ctx.beginPath(); ctx.moveTo(342, 60); ctx.lineTo(364, 60); ctx.lineTo(353, 49); ctx.closePath(); ctx.fill();
  ctx.beginPath(); ctx.moveTo(368, 60); ctx.lineTo(381, 60); ctx.lineTo(381, 47); ctx.closePath(); ctx.fill();
  roundRect(ctx, 389, 47, 11, 16, 3); ctx.fill();
}

function drawBrand(ctx: Ctx) {
  strokeIcon(ctx, MINT, 3, () => { ctx.beginPath(); ctx.arc(52, 112, 13, 0, Math.PI * 2); ctx.stroke(); });
  ctx.fillStyle = MINT;
  ctx.beginPath(); ctx.arc(52, 106.5, 1.9, 0, Math.PI * 2); ctx.fill();
  roundRect(ctx, 50.4, 110.5, 3.2, 9, 1.6); ctx.fill();
  text(ctx, 'InScreenLock', 76, 112, 22, 700, MINT, 'left', true);
  // discreet, honest label
  ctx.save(); ctx.globalAlpha = 0.62;
  roundRect(ctx, 308, 102, 100, 20, 10); ctx.fillStyle = '#ffffff12'; ctx.fill();
  ctx.strokeStyle = '#ffffff26'; ctx.lineWidth = 1; ctx.stroke();
  text(ctx, 'Demonstração', 358, 112.5, 10.5, 500, '#c7d0db', 'center');
  ctx.restore();
}

function drawToday(ctx: Ctx) {
  text(ctx, 'Hoje', 30, 168, 38, 800, '#f4f6f9', 'left', true);
  text(ctx, 'Sex, 6 de nov.', 32, 214, 17, 400, '#a9b3c0', 'left', true);
  calendarIcon(ctx, 171, 214, 17, '#a9b3c0', 1.8);
  roundRect(ctx, 360, 148, 52, 52, 15); ctx.fillStyle = '#1a2028'; ctx.fill();
  ctx.strokeStyle = '#ffffff14'; ctx.lineWidth = 1; ctx.stroke();
  calendarIcon(ctx, 386, 174, 26, '#dbe2ea', 1.9);
}

function drawRing(ctx: Ctx, frame: ScreenFrame) {
  const cx = 220; const cy = 392; const radius = 126; const stroke = 21;
  const start = -Math.PI / 2;
  const color = mix(MINT_RGB, CORAL_RGB, frame.coral);
  ctx.save();
  ctx.lineCap = 'round';
  // track
  ctx.beginPath(); ctx.arc(cx, cy, radius, 0, Math.PI * 2);
  ctx.strokeStyle = 'rgba(255,255,255,0.075)'; ctx.lineWidth = stroke; ctx.stroke();
  // soft glow plus the progress arc
  if (frame.ringFraction > 0.002) {
    const end = start + Math.PI * 2 * frame.ringFraction;
    ctx.shadowColor = color; ctx.shadowBlur = 14; ctx.globalAlpha = 0.4 + 0.3 * frame.coral;
    ctx.beginPath(); ctx.arc(cx, cy, radius, start, end); ctx.strokeStyle = color; ctx.lineWidth = stroke; ctx.stroke();
    ctx.shadowBlur = 0; ctx.globalAlpha = 1;
    ctx.beginPath(); ctx.arc(cx, cy, radius, start, end); ctx.stroke();
    // inner highlight gives the stroke volume
    ctx.beginPath(); ctx.arc(cx, cy, radius - stroke * 0.28, start, end); ctx.strokeStyle = 'rgba(255,255,255,0.16)'; ctx.lineWidth = 2.5; ctx.stroke();
  }
  // overshoot cap above the goal
  if (frame.overshoot > 0.01) {
    const span = (Math.PI * 2 * 22 / 360) * Math.min(1, frame.overshoot / (TOTAL_MINUTES - GOAL_MINUTES));
    ctx.beginPath(); ctx.arc(cx, cy, radius, start, start + span); ctx.strokeStyle = 'rgba(255,170,178,0.9)'; ctx.lineWidth = stroke * 0.5; ctx.stroke();
  }
  ctx.restore();
  const shown = Math.max(0, Math.min(TOTAL_MINUTES, Math.round(frame.minutes)));
  text(ctx, `${shown} min`, cx, cy - 6, 48, 800, '#f6f8fb', 'center', true);
  text(ctx, `Meta ${GOAL_MINUTES} min`, cx, cy + 44, 20, 400, '#aab3bf', 'center', true);
}

function warningTriangle(ctx: Ctx, cx: number, cy: number, size: number, color: string) {
  ctx.save(); ctx.fillStyle = color; ctx.lineJoin = 'round'; ctx.strokeStyle = color; ctx.lineWidth = 3;
  ctx.beginPath(); ctx.moveTo(cx, cy - size / 2); ctx.lineTo(cx + size / 2, cy + size / 2); ctx.lineTo(cx - size / 2, cy + size / 2); ctx.closePath(); ctx.fill(); ctx.stroke();
  ctx.fillStyle = '#3a1d24'; roundRect(ctx, cx - 1.6, cy - size * 0.18, 3.2, size * 0.32, 1.6); ctx.fill();
  ctx.beginPath(); ctx.arc(cx, cy + size * 0.27, 1.9, 0, Math.PI * 2); ctx.fill();
  ctx.restore();
}

function drawBanner(ctx: Ctx, alpha: number) {
  if (alpha <= 0.002) return;
  ctx.save(); ctx.globalAlpha = alpha;
  ctx.translate(0, (1 - alpha) * 8);
  roundRect(ctx, 28, 548, 384, 82, 18); ctx.fillStyle = 'rgba(255,107,122,0.15)'; ctx.fill();
  ctx.strokeStyle = 'rgba(255,107,122,0.28)'; ctx.lineWidth = 1; ctx.stroke();
  warningTriangle(ctx, 64, 589, 24, CORAL);
  text(ctx, 'Limite diário alcançado', 98, 577, 19, 700, '#ff7787', 'left', true);
  text(ctx, '4 min acima da meta', 98, 604, 15, 400, '#e9808d', 'left', true);
  ctx.restore();
}

function instagramGlyph(ctx: Ctx, x: number, y: number, size: number) {
  const gradient = ctx.createLinearGradient(x, y + size, x + size, y);
  gradient.addColorStop(0, '#ffb32c'); gradient.addColorStop(0.45, '#fa2874'); gradient.addColorStop(1, '#713add');
  roundRect(ctx, x, y, size, size, size * 0.28); ctx.fillStyle = gradient; ctx.fill();
  strokeIcon(ctx, '#ffffff', 2.4, () => {
    roundRect(ctx, x + size * 0.22, y + size * 0.22, size * 0.56, size * 0.56, size * 0.17); ctx.stroke();
    ctx.beginPath(); ctx.arc(x + size / 2, y + size / 2, size * 0.14, 0, Math.PI * 2); ctx.stroke();
  });
  ctx.fillStyle = '#ffffff'; ctx.beginPath(); ctx.arc(x + size * 0.68, y + size * 0.31, 1.7, 0, Math.PI * 2); ctx.fill();
}

function tiktokGlyph(ctx: Ctx, x: number, y: number, size: number) {
  roundRect(ctx, x, y, size, size, size * 0.28); ctx.fillStyle = '#05070a'; ctx.fill();
  ctx.strokeStyle = '#ffffff1f'; ctx.lineWidth = 1; ctx.stroke();
  const note = (dx: number, dy: number, color: string) => {
    ctx.save(); ctx.translate(x + size / 2 + dx, y + size / 2 + dy); ctx.fillStyle = color; ctx.strokeStyle = color; ctx.lineCap = 'round';
    const k = size / 36;
    ctx.beginPath(); ctx.arc(-3 * k, 7 * k, 5.2 * k, 0, Math.PI * 2); ctx.fill();
    ctx.lineWidth = 3.4 * k; ctx.beginPath(); ctx.moveTo(2 * k, 7 * k); ctx.lineTo(2 * k, -10 * k); ctx.stroke();
    ctx.lineWidth = 3.2 * k; ctx.beginPath(); ctx.moveTo(2 * k, -10 * k); ctx.bezierCurveTo(3 * k, -5 * k, 6.5 * k, -3.5 * k, 10 * k, -3.5 * k); ctx.stroke();
    ctx.restore();
  };
  note(-1.6, 1, '#25f4ee'); note(1.6, -1, '#fe2c55'); note(0, 0, '#ffffff');
}

function drawRows(ctx: Ctx, frame: ScreenFrame) {
  const total = Math.max(0.0001, TOTAL_MINUTES);
  const rows: Array<[number, string, number, number, (c: Ctx, x: number, y: number, s: number) => void]> = [
    [652, 'Instagram', INSTAGRAM_MINUTES, 0, instagramGlyph],
    [724, 'TikTok', TIKTOK_MINUTES, 0, tiktokGlyph],
  ];
  for (const [y, name, minutes, , glyph] of rows) {
    roundRect(ctx, 28, y, 384, 62, 17); ctx.fillStyle = '#1b2129'; ctx.fill();
    ctx.strokeStyle = '#ffffff0d'; ctx.lineWidth = 1; ctx.stroke();
    glyph(ctx, 46, y + 13, 36);
    text(ctx, name, 96, y + 31, 20, 500, '#eef2f6', 'left', true);
    const shown = frame.minutes >= total - 0.0001 ? minutes : Math.round((minutes * frame.minutes) / total);
    text(ctx, `${shown} min`, 392, y + 32, 19, 400, '#d4dce5', 'right', true);
  }
}

function drawNav(ctx: Ctx) {
  ctx.fillStyle = '#10151b'; ctx.fillRect(0, 828, SCREEN_W, SCREEN_H - 828);
  ctx.fillStyle = '#2c343e'; ctx.fillRect(0, 828, SCREEN_W, 1);
  const items: Array<[string, number, string]> = [['Início', 76, 'home'], ['Análises', 172, 'bars'], ['Limites', 268, 'clock'], ['Mais', 364, 'menu']];
  for (const [label, cx, icon] of items) {
    const active = icon === 'home';
    const color = active ? MINT : '#a5afbc';
    if (active) { roundRect(ctx, cx - 36, 842, 72, 62, 18); ctx.fillStyle = 'rgba(34,227,176,0.09)'; ctx.fill(); }
    const cy = 866; const s = 28 / 24; const x = cx - 12 * s; const y = cy - 12 * s;
    strokeIcon(ctx, color, 2, () => {
      ctx.beginPath();
      if (icon === 'home') {
        ctx.moveTo(x + 3 * s, y + 11 * s); ctx.lineTo(x + 12 * s, y + 3.5 * s); ctx.lineTo(x + 21 * s, y + 11 * s); ctx.lineTo(x + 21 * s, y + 20 * s); ctx.lineTo(x + 15 * s, y + 20 * s); ctx.lineTo(x + 15 * s, y + 14 * s); ctx.lineTo(x + 9 * s, y + 14 * s); ctx.lineTo(x + 9 * s, y + 20 * s); ctx.lineTo(x + 3 * s, y + 20 * s); ctx.closePath();
        ctx.fillStyle = color; ctx.fill(); ctx.stroke();
      } else if (icon === 'bars') {
        ctx.fillStyle = color;
        for (const [bx, top] of [[5, 12], [10.5, 5], [16, 9]] as const) { roundRect(ctx, x + bx * s, y + top * s, 3.2 * s, (19 - top) * s, 1.2 * s); ctx.fill(); }
      } else if (icon === 'clock') {
        ctx.arc(cx, cy, 9.5 * s, 0, Math.PI * 2); ctx.stroke();
        ctx.beginPath(); ctx.moveTo(cx, cy - 5 * s); ctx.lineTo(cx, cy); ctx.lineTo(cx + 4 * s, cy + 2.4 * s); ctx.stroke();
      } else {
        for (const ly of [6, 12, 18]) { ctx.moveTo(x + 4 * s, y + ly * s); ctx.lineTo(x + 20 * s, y + ly * s); }
        ctx.stroke();
      }
    });
    text(ctx, label, cx, 893, 13, active ? 600 : 400, color, 'center');
  }
  roundRect(ctx, 160, 930, 120, 5, 2.5); ctx.fillStyle = '#f3f6f8'; ctx.fill();
}

/**
 * Draws the "Início" screen of the app in design space (440x968) onto any 2D canvas.
 * The same function feeds the live WebGL texture and the static poster, so both always match.
 */
export function drawPhoneScreen(ctx: Ctx, width: number, height: number, frame: ScreenFrame = FINAL_FRAME) {
  ctx.save();
  ctx.setTransform(width / SCREEN_W, 0, 0, height / SCREEN_H, 0, 0);
  ctx.fillStyle = '#030507'; ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);
  if (frame.screenOn > 0.001) {
    ctx.save();
    ctx.globalAlpha = frame.screenOn;
    const bg = ctx.createLinearGradient(0, 0, SCREEN_W * 0.4, SCREEN_H);
    bg.addColorStop(0, '#11171e'); bg.addColorStop(0.6, '#0b0f15'); bg.addColorStop(1, '#0f151c');
    ctx.fillStyle = bg; ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);
    const glow = ctx.createRadialGradient(220, 392, 20, 220, 392, 260);
    glow.addColorStop(0, mix(MINT_RGB, CORAL_RGB, frame.coral).replace('rgb', 'rgba').replace(')', ',0.10)')); glow.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = glow; ctx.fillRect(0, 150, SCREEN_W, 480);
    drawStatusBar(ctx); drawBrand(ctx); drawToday(ctx); drawRing(ctx, frame); drawBanner(ctx, frame.banner); drawRows(ctx, frame); drawNav(ctx);
    ctx.restore();
  }
  ctx.restore();
}
