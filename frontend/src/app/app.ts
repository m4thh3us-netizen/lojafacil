import { ChangeDetectionStrategy, Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { Toasts } from './shared/ui';

@Component({
  selector: 'app-root',
  imports: [RouterOutlet, Toasts],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<a class="skip-link" href="#conteudo">Pular para o conteúdo</a><router-outlet /><app-toasts />`,
})
export class App {}
