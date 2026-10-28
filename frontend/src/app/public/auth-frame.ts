import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { Brand, ThemeToggle } from '../shared/theme-toggle';

@Component({
  selector: 'app-auth-frame',
  imports: [RouterLink, Brand, ThemeToggle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="wrap">
      <div class="bar container row-between">
        <a routerLink="/" aria-label="Início"><app-brand /></a>
        <app-theme-toggle />
      </div>
      <main id="conteudo" class="card glass panel fade-in" [style.max-width.px]="width()">
        <h1>{{ title() }}</h1>
        @if (subtitle()) { <p class="text-2">{{ subtitle() }}</p> }
        <ng-content />
      </main>
    </div>`,
  styles: [`
    .wrap { min-height: 100vh; display: flex; flex-direction: column; align-items: center; padding: 0 16px 48px; }
    .bar { min-height: 64px; }
    .panel { width: 100%; margin-top: 4vh; padding: 28px; border-radius: 20px; box-shadow: var(--shadow); }
    .panel h1 { font-size: 1.45rem; }
  `],
})
export class AuthFrame {
  readonly title = input.required<string>();
  readonly subtitle = input('');
  readonly width = input(440);
}
