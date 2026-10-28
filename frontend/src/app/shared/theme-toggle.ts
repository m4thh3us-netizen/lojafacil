import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';

import { ThemeService } from '../core/ui.services';
import { Icon } from './icon';

@Component({
  selector: 'app-theme-toggle',
  imports: [Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button type="button" class="btn btn-ghost btn-icon" (click)="theme.cycle()" [attr.aria-label]="label()" [title]="label()">
      <app-icon [name]="icon()" />
    </button>`,
})
export class ThemeToggle {
  readonly theme = inject(ThemeService);
  readonly icon = computed(() => ({ auto: 'monitor', light: 'sun', dark: 'moon' })[this.theme.mode()]);
  readonly label = computed(() => 'Tema: ' + ({ auto: 'automático', light: 'claro', dark: 'escuro' })[this.theme.mode()] + ' (clique para alternar)');
}

@Component({
  selector: 'app-brand',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <span class="brand">
      <svg width="28" height="28" viewBox="0 0 32 32" aria-hidden="true">
        <rect width="32" height="32" rx="9" fill="var(--primary)"/>
        <path d="M9 21V10h3v8.4h6V21Z" fill="var(--on-primary)"/><circle cx="22" cy="12" r="2.4" fill="var(--on-primary)"/>
      </svg>
      <span>Loja<b>Fácil</b></span>
    </span>`,
  styles: [`.brand { display: inline-flex; align-items: center; gap: 8px; font-weight: 600; font-size: 1.05rem; color: var(--text); letter-spacing: -0.01em; } b { color: var(--primary); font-weight: 700; }`],
})
export class Brand {}
