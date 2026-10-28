import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, signal } from '@angular/core';

export type ThemeMode = 'light' | 'dark' | 'auto';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  readonly mode = signal<ThemeMode>(this.read());
  private readonly systemDark = signal(false);

  readonly isDark = computed(() => this.mode() === 'dark' || (this.mode() === 'auto' && this.systemDark()));

  constructor() {
    const mq = window.matchMedia?.('(prefers-color-scheme: dark)');
    if (mq) {
      this.systemDark.set(mq.matches);
      mq.addEventListener('change', e => this.systemDark.set(e.matches));
    }
  }

  set(mode: ThemeMode): void {
    this.mode.set(mode);
    try { localStorage.setItem('lf-theme', mode); } catch {}
    if (mode === 'auto') {
      document.documentElement.removeAttribute('data-theme');
    } else {
      document.documentElement.setAttribute('data-theme', mode);
    }
  }

  cycle(): void {
    const order: ThemeMode[] = ['auto', 'light', 'dark'];
    this.set(order[(order.indexOf(this.mode()) + 1) % order.length]);
  }

  private read(): ThemeMode {
    try {
      const v = localStorage.getItem('lf-theme');
      return v === 'light' || v === 'dark' ? v : 'auto';
    } catch {
      return 'auto';
    }
  }
}

export interface Toast { id: number; kind: 'success' | 'error' | 'info'; text: string; }

@Injectable({ providedIn: 'root' })
export class ToastService {
  readonly toasts = signal<Toast[]>([]);
  private seq = 0;

  success(text: string) { this.push('success', text); }
  info(text: string) { this.push('info', text); }
  error(errOrText: unknown) { this.push('error', typeof errOrText === 'string' ? errOrText : errorMessage(errOrText)); }

  dismiss(id: number) { this.toasts.update(list => list.filter(t => t.id !== id)); }

  private push(kind: Toast['kind'], text: string) {
    const id = ++this.seq;
    this.toasts.update(list => [...list.slice(-3), { id, kind, text }]);
    setTimeout(() => this.dismiss(id), kind === 'error' ? 7000 : 4000);
  }
}

export function errorMessage(err: unknown): string {
  if (err instanceof HttpErrorResponse) {
    if (err.status === 0) return 'Sem conexão com o servidor. Verifique sua internet e tente novamente.';
    const body = err.error as { detail?: string } | null;
    if (body?.detail) return body.detail;
    if (err.status === 403) return 'Seu perfil não tem permissão para esta ação.';
    if (err.status === 404) return 'Registro não encontrado.';
    if (err.status === 429) return 'Muitas tentativas. Aguarde alguns minutos.';
  }
  return 'Não foi possível concluir a operação. Tente novamente.';
}

export function fieldErrors(err: unknown): Record<string, string> {
  if (err instanceof HttpErrorResponse && err.error && typeof err.error === 'object' && 'errors' in err.error) {
    return (err.error as { errors: Record<string, string> }).errors ?? {};
  }
  return {};
}
