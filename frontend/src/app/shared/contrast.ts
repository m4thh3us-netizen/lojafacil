/** Contraste WCAG 2.1 — mesma fórmula usada pelo backend (ColorContrast.java). */

function channel(v: number): number {
  const c = v / 255;
  return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4);
}

function luminance(hex: string): number {
  const n = parseInt(hex.slice(1), 16);
  return 0.2126 * channel((n >> 16) & 255) + 0.7152 * channel((n >> 8) & 255) + 0.0722 * channel(n & 255);
}

export function contrast(a: string, b: string): number {
  const la = luminance(a);
  const lb = luminance(b);
  return (Math.max(la, lb) + 0.05) / (Math.min(la, lb) + 0.05);
}

export function bestText(bg: string): string {
  if (!/^#[0-9a-fA-F]{6}$/.test(bg)) return '#FFFFFF';
  return contrast(bg, '#FFFFFF') >= contrast(bg, '#111827') ? '#FFFFFF' : '#111827';
}
