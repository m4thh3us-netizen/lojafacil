import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';

import { PublicApi, SimulatedCharge } from '../core/api';
import { BrlPipe } from '../core/format';
import { errorMessage } from '../core/ui.services';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-simulated-payment',
  imports: [BrlPipe, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main id="conteudo" class="wrap">
      <div class="card panel">
        <div class="ribbon">AMBIENTE SIMULADO · NENHUM VALOR REAL É COBRADO</div>
        @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
        @if (!c()) { <span class="skeleton title"></span><span class="skeleton line"></span><span class="skeleton line"></span> }
        @else {
          @let charge = c()!;
          <p class="muted">Pagamento para</p>
          <h1>{{ charge.storeName }}</h1>
          <p>{{ charge.description }}</p>
          <div class="amount">{{ charge.amount | brl }}</div>
          @if (charge.status === 'PENDING' || charge.status === 'REJECTED') {
            @if (charge.status === 'REJECTED') { <div class="alert warning">O pagamento anterior foi recusado. Você pode tentar de novo.</div> }
            <div class="stack-sm">
              <button class="btn btn-primary btn-block" type="button" (click)="decide('approve')" [disabled]="busy()">{{ busy() === 'approve' ? 'Processando...' : 'Simular pagamento aprovado' }}</button>
              <button class="btn btn-block" type="button" (click)="decide('decline')" [disabled]="busy()">{{ busy() === 'decline' ? 'Processando...' : 'Simular pagamento recusado' }}</button>
            </div>
          } @else {
            <div class="alert success"><app-icon name="check" /> Pagamento simulado {{ charge.status === 'APPROVED' ? 'aprovado' : 'registrado' }}.</div>
          }
          <a class="btn btn-ghost btn-block" [href]="charge.returnUrl" style="margin-top:10px">Voltar para a loja</a>
          <p class="hint">Ao voltar, a loja consulta o "provedor" para confirmar a situação — exatamente como faria com o Mercado Pago.</p>
        }
      </div>
    </main>`,
  styles: [`
    .wrap { min-height: 100vh; display: grid; place-items: center; padding: 24px 16px; }
    .panel { width: min(440px, 100%); padding: 28px; border-radius: 20px; text-align: center; box-shadow: var(--shadow); }
    .ribbon { margin: -28px -28px 20px; padding: 8px; border-radius: 20px 20px 0 0; background: var(--warning-soft); color: var(--warning); font-size: .75rem; font-weight: 700; letter-spacing: .04em; }
    .amount { font-size: 2.2rem; font-weight: 700; margin: 10px 0 20px; font-variant-numeric: tabular-nums; }
  `],
})
export class SimulatedPayment implements OnInit {
  private api = inject(PublicApi);
  readonly id = input.required<string>();
  readonly c = signal<SimulatedCharge | null>(null);
  readonly busy = signal<string | false>(false);
  readonly error = signal('');

  ngOnInit() {
    this.api.simulatedCharge(this.id()).subscribe({ next: c => this.c.set(c), error: e => this.error.set(errorMessage(e)) });
  }

  decide(decision: 'approve' | 'decline') {
    if (this.busy()) return;
    this.busy.set(decision);
    this.api.decideSimulated(this.id(), decision).subscribe({
      next: c => {
        this.c.set(c);
        this.busy.set(false);
        if (decision === 'approve') window.location.href = c.returnUrl;
      },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }
}
