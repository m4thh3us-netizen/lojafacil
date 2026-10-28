import { ChangeDetectionStrategy, Component, DestroyRef, ElementRef, WritableSignal, computed, inject, input, signal } from '@angular/core';

import { brl, date } from '../core/format';

/**
 * Gráficos em SVG puro (sem biblioteca externa).
 * Regras seguidas: um único eixo Y, marcas finas, grade discreta, tooltip ao passar o mouse
 * ou focar pelo teclado, e alternativa em tabela para leitores de tela.
 */

const W = 640;
const H = 220;
const PAD = { l: 56, r: 12, t: 12, b: 26 };

/**
 * A largura interna do SVG acompanha a largura real do gráfico. Assim os textos dos eixos mantêm
 * o mesmo tamanho em qualquer tela, em vez de serem ampliados junto com o desenho.
 */
function trackWidth(): WritableSignal<number> {
  const width = signal(W);
  const host = inject(ElementRef<HTMLElement>).nativeElement as HTMLElement;
  if (typeof ResizeObserver !== 'undefined') {
    const ro = new ResizeObserver(([entry]) => {
      const w = Math.round(entry.contentRect.width);
      if (w > 0) width.set(Math.max(280, Math.min(1600, w)));
    });
    ro.observe(host);
    inject(DestroyRef).onDestroy(() => ro.disconnect());
  }
  return width;
}

function niceMax(v: number): number {
  if (v <= 0) return 1;
  const p = Math.pow(10, Math.floor(Math.log10(v)));
  return Math.ceil(v / p) * p;
}

function compact(v: number): string {
  const a = Math.abs(v);
  if (a >= 1000) return (v / 1000).toLocaleString('pt-BR', { maximumFractionDigits: 1 }) + ' mil';
  return v.toLocaleString('pt-BR', { maximumFractionDigits: 0 });
}

export interface BarPoint { label: string; value: number; extra?: string; }

@Component({
  selector: 'app-bar-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="chart">
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="ariaLabel()">
        @for (t of ticks(); track t.v) {
          <line [attr.x1]="PAD.l" [attr.x2]="W - PAD.r" [attr.y1]="t.y" [attr.y2]="t.y" class="grid" />
          <text [attr.x]="PAD.l - 8" [attr.y]="t.y + 4" text-anchor="end" class="axis">{{ t.label }}</text>
        }
        @for (b of bars(); track b.i) {
          <rect [attr.x]="b.x" [attr.y]="b.y" [attr.width]="b.w" [attr.height]="b.h" rx="3" class="bar" [class.on]="hover() === b.i" />
          <rect [attr.x]="b.hitX" [attr.y]="PAD.t" [attr.width]="b.hitW" [attr.height]="H - PAD.t - PAD.b" class="hit"
                tabindex="0" [attr.aria-label]="b.p.label + ': ' + fmt()(b.p.value)"
                (mouseenter)="hover.set(b.i)" (mouseleave)="hover.set(-1)" (focus)="hover.set(b.i)" (blur)="hover.set(-1)" />
          @if (b.showLabel) { <text [attr.x]="b.x + b.w / 2" [attr.y]="H - 8" text-anchor="middle" class="axis">{{ b.short }}</text> }
        }
        <line [attr.x1]="PAD.l" [attr.x2]="W - PAD.r" [attr.y1]="H - PAD.b" [attr.y2]="H - PAD.b" class="base" />
      </svg>
      @if (hover() >= 0 && bars()[hover()]; as b) {
        <div class="tip glass-strong" [style.left.%]="(b.x + b.w / 2) / W * 100">
          <b>{{ b.p.label }}</b><span>{{ fmt()(b.p.value) }}</span>@if (b.p.extra) { <small>{{ b.p.extra }}</small> }
        </div>
      }
      <table class="sr-only"><caption>{{ ariaLabel() }}</caption>
        <tbody>@for (p of data(); track $index) { <tr><th>{{ p.label }}</th><td>{{ fmt()(p.value) }}</td></tr> }</tbody></table>
    </figure>`,
  styleUrl: './charts.scss',
})
export class BarChart {
  readonly width = trackWidth();
  get W() { return this.width(); }
  readonly H = H; readonly PAD = PAD;
  readonly data = input.required<BarPoint[]>();
  readonly money = input(true);
  readonly ariaLabel = input('Gráfico de barras');
  readonly hover = signal(-1);
  readonly fmt = computed(() => this.money() ? (v: number) => brl(v) : (v: number) => v.toLocaleString('pt-BR'));
  readonly max = computed(() => niceMax(Math.max(0, ...this.data().map(d => d.value))));
  readonly ticks = computed(() => [0, 0.5, 1].map(f => {
    const v = this.max() * f;
    return { v, y: H - PAD.b - f * (H - PAD.t - PAD.b), label: this.money() ? compact(v) : String(Math.round(v)) };
  }));
  readonly bars = computed(() => {
    const d = this.data();
    const slot = (this.width() - PAD.l - PAD.r) / Math.max(d.length, 1);
    const w = Math.max(2, Math.min(28, slot - 2)); // 2px de respiro entre barras
    const every = Math.ceil(d.length / Math.max(4, Math.floor(this.width() / 80)));
    return d.map((p, i) => {
      const h = (p.value / this.max()) * (H - PAD.t - PAD.b);
      const x = PAD.l + i * slot + (slot - w) / 2;
      return { i, p, x, w, h, y: H - PAD.b - h, hitX: PAD.l + i * slot, hitW: slot,
        showLabel: i % every === 0, short: p.label.slice(0, 5) };
    });
  });
}

export interface LineSeries { name: string; color: string; dashed?: boolean; values: number[]; }

@Component({
  selector: 'app-line-chart',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <figure class="chart">
      <div class="legend">
        @for (s of series(); track s.name) {
          <span class="key"><i [style.background]="s.color" [class.dashed]="s.dashed"></i>{{ s.name }}</span>
        }
      </div>
      <svg [attr.viewBox]="'0 0 ' + W + ' ' + H" role="img" [attr.aria-label]="ariaLabel()"
           (mouseleave)="hover.set(-1)">
        @for (t of ticks(); track $index) {
          <line [attr.x1]="PAD.l" [attr.x2]="W - PAD.r" [attr.y1]="t.y" [attr.y2]="t.y" [class]="t.zero ? 'base' : 'grid'" />
          <text [attr.x]="PAD.l - 8" [attr.y]="t.y + 4" text-anchor="end" class="axis">{{ t.label }}</text>
        }
        @for (s of paths(); track s.name) {
          <path [attr.d]="s.d" fill="none" [attr.stroke]="s.color" stroke-width="2" [attr.stroke-dasharray]="s.dashed ? '5 4' : null"
                stroke-linejoin="round" stroke-linecap="round" vector-effect="non-scaling-stroke" />
        }
        @if (hover() >= 0) {
          <line [attr.x1]="xAt(hover())" [attr.x2]="xAt(hover())" [attr.y1]="PAD.t" [attr.y2]="H - PAD.b" class="cross" />
          @for (s of series(); track s.name) {
            <circle [attr.cx]="xAt(hover())" [attr.cy]="yAt(s.values[hover()])" r="4" [attr.fill]="s.color" class="dot" />
          }
        }
        @for (l of labels(); track l.i; let idx = $index) {
          @if (l.show) { <text [attr.x]="xAt(l.i)" [attr.y]="H - 8" text-anchor="middle" class="axis">{{ l.text }}</text> }
          <rect [attr.x]="xAt(l.i) - step() / 2" [attr.y]="PAD.t" [attr.width]="step()" [attr.height]="H - PAD.t - PAD.b" class="hit"
                tabindex="0" [attr.aria-label]="l.full" (mouseenter)="hover.set(l.i)" (focus)="hover.set(l.i)" (blur)="hover.set(-1)" />
        }
      </svg>
      @if (hover() >= 0) {
        <div class="tip glass-strong" [style.left.%]="xAt(hover()) / W * 100">
          <b>{{ labelsIn()[hover()] }}</b>
          @for (s of series(); track s.name) { <span><i [style.background]="s.color"></i>{{ s.name }}: {{ fmt(s.values[hover()]) }}</span> }
        </div>
      }
      <table class="sr-only"><caption>{{ ariaLabel() }}</caption>
        <thead><tr><th>Data</th>@for (s of series(); track s.name) { <th>{{ s.name }}</th> }</tr></thead>
        <tbody>@for (l of labelsIn(); track $index; let i = $index) {
          <tr><th>{{ l }}</th>@for (s of series(); track s.name) { <td>{{ fmt(s.values[i]) }}</td> }</tr>
        }</tbody></table>
    </figure>`,
  styleUrl: './charts.scss',
})
export class LineChart {
  readonly width = trackWidth();
  get W() { return this.width(); }
  readonly H = H; readonly PAD = PAD;
  readonly labelsIn = input.required<string[]>();
  readonly series = input.required<LineSeries[]>();
  readonly ariaLabel = input('Gráfico de linhas');
  readonly hover = signal(-1);
  readonly fmt = brl;

  readonly range = computed(() => {
    const all = this.series().flatMap(s => s.values);
    const max = niceMax(Math.max(0, ...all));
    const minRaw = Math.min(0, ...all);
    const min = minRaw < 0 ? -niceMax(-minRaw) : 0;
    return { min, max };
  });
  readonly step = computed(() => (this.width() - PAD.l - PAD.r) / Math.max(this.labelsIn().length - 1, 1));
  xAt(i: number) { return PAD.l + i * this.step(); }
  yAt(v: number) {
    const { min, max } = this.range();
    return H - PAD.b - ((v - min) / (max - min || 1)) * (H - PAD.t - PAD.b);
  }
  readonly ticks = computed(() => {
    const { min, max } = this.range();
    const vals = min < 0 ? [min, 0, max] : [0, max / 2, max];
    const ticks = vals.map(v => ({ y: this.yAt(v), label: compact(v), zero: v === 0 }));
    // evita rótulos sobrepostos: some o que estiver colado na linha do zero
    const zeroY = this.yAt(0);
    return ticks.map(t => (!t.zero && Math.abs(t.y - zeroY) < 14 ? { ...t, label: '' } : t));
  });
  readonly paths = computed(() => this.series().map(s => ({
    ...s, d: s.values.map((v, i) => `${i === 0 ? 'M' : 'L'}${this.xAt(i).toFixed(1)},${this.yAt(v).toFixed(1)}`).join(' '),
  })));
  readonly labels = computed(() => {
    const l = this.labelsIn();
    const every = Math.ceil(l.length / Math.max(4, Math.floor(this.width() / 90)));
    return l.map((text, i) => ({ i, text: text.slice(0, 5), full: text, show: i % every === 0 }));
  });
}

export function chartDay(d: string): string {
  return date(d).slice(0, 5);
}
