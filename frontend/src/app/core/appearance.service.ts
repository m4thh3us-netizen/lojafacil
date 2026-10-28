import { HttpClient } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { Observable, tap } from 'rxjs';

import { PhotoStats, hexLum, measurePhoto, requiredAlpha } from './legibility';
import { ThemeService } from './ui.services';

export interface Appearance {
  mode: 'PHOTO' | 'COLOR';
  imagePath?: string | null;
  imageUrl?: string | null;
  color: string;
  posX: number;
  posY: number;

  dim: number;

  glass: number;
}

export const DEFAULT_APPEARANCE: Appearance = { mode: 'COLOR', imagePath: null, imageUrl: null, color: '#3B4656', posX: 50, posY: 50, dim: 25, glass: 74 };

@Injectable({ providedIn: 'root' })
export class AppearanceService {
  private http = inject(HttpClient);
  readonly saved = signal<Appearance>(DEFAULT_APPEARANCE);
  readonly draft = signal<Appearance | null>(null);
  readonly current = computed(() => this.draft() ?? this.saved());
  readonly previewing = computed(() => this.draft() !== null);

  private readonly theme = inject(ThemeService);

  private readonly photoStats = signal<{ url: string; stats: PhotoStats } | null>(null);

  readonly minGlass = computed(() => {
    const a = this.current();
    let stats: PhotoStats | null;
    if (a.mode === 'PHOTO' && a.imageUrl) {
      const measured = this.photoStats();
      if (!measured || measured.url !== a.imageUrl) return 0.9;
      const k = 1 - a.dim / 100;
      stats = { p10: measured.stats.p10 * k, p90: measured.stats.p90 * k };
    } else {
      const l = hexLum(a.color);
      stats = { p10: l, p90: l };
    }
    return Math.min(0.97, Math.ceil(requiredAlpha(stats, this.theme.isDark()) * 100) / 100);
  });

  readonly effectiveGlass = computed(() => Math.max(this.current().glass / 100, this.minGlass()));
  readonly autoRaised = computed(() => this.effectiveGlass() > this.current().glass / 100 + 0.005);

  constructor() {
    effect(() => {
      document.documentElement.style.setProperty('--panel-alpha', this.effectiveGlass().toFixed(2));
    });

    effect(() => {
      const a = this.current();
      const url = a.mode === 'PHOTO' ? a.imageUrl : null;
      if (!url || this.photoStats()?.url === url) return;
      measurePhoto(url).then(
        stats => this.photoStats.set({ url, stats }),
        () => this.photoStats.set({ url, stats: { p10: 0.5, p90: 0.5 } }),
      );
    });
  }

  load(): void {
    this.http.get<Appearance>('/api/admin/store/appearance').subscribe({
      next: a => this.saved.set(a),
      error: () => this.saved.set(DEFAULT_APPEARANCE),
    });
  }

  save(a: Appearance): Observable<Appearance> {
    return this.http.put<Appearance>('/api/admin/store/appearance', a).pipe(tap(r => { this.saved.set(r); this.draft.set(null); }));
  }

  reset(): Observable<Appearance> {
    return this.http.post<Appearance>('/api/admin/store/appearance/reset', {}).pipe(tap(r => { this.saved.set(r); this.draft.set(null); }));
  }

  uploadPhoto(file: File): Observable<{ path: string; url: string }> {
    const fd = new FormData();
    fd.append('file', file);
    return this.http.post<{ path: string; url: string }>('/api/admin/store/appearance/photo', fd);
  }

  clear(): void {
    this.draft.set(null);
    this.saved.set(DEFAULT_APPEARANCE);
  }
}
