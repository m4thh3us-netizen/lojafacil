/**
 * Garantia de leitura sobre a fotografia de fundo.
 *
 * Um painel de vidro com opacidade "a" sobre um fundo de luminância P resulta (aproximadamente, em
 * espaço linear) em a·S + (1 − a)·P, onde S é a luminância da cor do painel. Calculamos a menor
 * opacidade que mantém o texto mais fraco do tema (--text-3) com contraste WCAG AA de 4,5:1.
 *
 * Como o desfoque mistura a vizinhança, usamos a foto reduzida (≈ blocos de 30 px) e o percentil
 * mais desfavorável: no tema escuro importam as áreas mais claras (p90); no claro, as mais escuras (p10).
 */

const MIN_RATIO = 4.5;

/** Luminâncias relativas (WCAG) das cores dos tokens em styles.scss. */
const DARK = { surface: lum(20, 25, 35), text3: lum(0x9a, 0xa6, 0xb8) };
const LIGHT = { surface: 1, text3: lum(0x55, 0x62, 0x75) };

export interface PhotoStats { p10: number; p90: number; }

export function lum(r: number, g: number, b: number): number {
  const ch = (v: number) => { const c = v / 255; return c <= 0.03928 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4); };
  return 0.2126 * ch(r) + 0.7152 * ch(g) + 0.0722 * ch(b);
}

export function hexLum(hex: string): number {
  const n = parseInt(hex.slice(1), 16);
  return lum((n >> 16) & 255, (n >> 8) & 255, n & 255);
}

/** Opacidade mínima (0–1) para o painel garantir 4,5:1 com o texto mais fraco. */
export function requiredAlpha(stats: PhotoStats, dark: boolean): number {
  if (dark) {
    // fundo resultante precisa ser escuro o bastante: Lc ≤ (Ltext + 0,05) / 4,5 − 0,05
    const maxL = (DARK.text3 + 0.05) / MIN_RATIO - 0.05;
    const p = stats.p90;
    return p <= maxL ? 0 : (p - maxL) / (p - DARK.surface);
  }
  // fundo resultante precisa ser claro o bastante: Lc ≥ 4,5 · (Ltext + 0,05) − 0,05
  const minL = MIN_RATIO * (LIGHT.text3 + 0.05) - 0.05;
  const p = stats.p10;
  return p >= minL ? 0 : (minL - p) / (LIGHT.surface - p);
}

/**
 * Amostra a fotografia (mesma origem) reduzida e devolve os percentis de luminância SEM escurecimento.
 * A camada preta uniforme apenas multiplica a luminância, então basta medir uma vez por foto
 * e aplicar o escurecimento depois: p · (1 − dim).
 */
export function measurePhoto(url: string): Promise<PhotoStats> {
  return new Promise((resolve, reject) => {
    const img = new Image();
    img.decoding = 'async';
    img.onload = () => {
      const w = 48;
      const h = Math.max(1, Math.round((img.naturalHeight / img.naturalWidth) * w));
      const canvas = document.createElement('canvas');
      canvas.width = w;
      canvas.height = h;
      const ctx = canvas.getContext('2d', { willReadFrequently: true });
      if (!ctx) { reject(new Error('canvas')); return; }
      ctx.drawImage(img, 0, 0, w, h);
      const data = ctx.getImageData(0, 0, w, h).data;
      const values: number[] = [];
      for (let i = 0; i < data.length; i += 4) {
        values.push(lum(data[i], data[i + 1], data[i + 2]));
      }
      values.sort((a, b) => a - b);
      const at = (q: number) => values[Math.min(values.length - 1, Math.floor(q * values.length))];
      resolve({ p10: at(0.1), p90: at(0.9) });
    };
    img.onerror = () => reject(new Error('imagem'));
    img.src = url;
  });
}
