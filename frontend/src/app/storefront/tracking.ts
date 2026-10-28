import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicApi } from '../core/api';
import { BrlPipe, DatePipeBr, LabelPipe, TonePipe } from '../core/format';
import { PublicOrder } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { StoreContext } from './store-context';

/**
 * Acompanhamento do pedido por link com token secreto (o número do pedido sozinho não dá acesso).
 * Ao abrir a página (inclusive voltando do checkout), o servidor consulta o provedor de pagamento:
 * a confirmação nunca depende de parâmetros do redirecionamento.
 */
@Component({
  selector: 'app-store-tracking',
  imports: [RouterLink, BrlPipe, DatePipeBr, LabelPipe, TonePipe, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @if (notFound()) {
      <div class="card"><h1>Pedido não encontrado</h1><p class="text-2">Verifique se o link está completo.</p></div>
    } @else if (!o()) {
      <div class="card"><span class="skeleton title"></span>@for (i of [1,2,3,4]; track i) { <span class="skeleton line"></span> }</div>
    } @else {
      @let order = o()!;
      <div class="page-header">
        <div><h1>Pedido #{{ order.number }}</h1><p>Feito em {{ order.createdAt | dt: true }} · {{ order.customerName }}</p></div>
        <button class="btn" type="button" (click)="refresh()" [disabled]="syncing()"><app-icon name="refresh" /> {{ syncing() ? 'Atualizando...' : 'Atualizar' }}</button>
      </div>

      <div class="steps card" aria-label="Situação do pedido">
        @for (s of steps(); track s.label) {
          <div class="step" [class.done]="s.done" [class.current]="s.current"><span class="dot">@if (s.done) { <app-icon name="check" [size]="13" /> }</span>{{ s.label }}</div>
        }
      </div>

      @if (order.status === 'CANCELLED') {
        <div class="alert danger">Este pedido foi cancelado.@if (order.paymentStatus === 'EXPIRED') { O prazo de pagamento terminou e os itens voltaram ao estoque. }</div>
      } @else if (order.paymentStatus === 'APPROVED' || order.paymentStatus === 'PARTIALLY_REFUNDED') {
        <div class="alert success"><app-icon name="check" /> Pagamento confirmado@if (order.simulated) { <span class="badge sim">SIMULADO</span> }. Obrigado pela compra!</div>
      } @else if (order.status === 'OPEN') {
        <div class="alert warning stack-sm">
          <span><b>Aguardando pagamento.</b> @if (order.paymentStatus === 'DECLINED') { O último pagamento foi recusado; tente novamente. }
            @if (order.reservationExpiresAt) { Seus itens estão reservados até {{ order.reservationExpiresAt | dt: true }}. }</span>
          <div><button class="btn btn-primary" type="button" (click)="pay()" [disabled]="paying()">{{ paying() ? 'Abrindo...' : 'Pagar agora' }}</button>
            @if (order.simulated) { <span class="badge sim" style="margin-left:8px">pagamento simulado</span> }</div>
        </div>
      }

      <div class="layout" style="margin-top:16px">
        <section class="card">
          <h2>Itens</h2>
          @for (i of order.items; track i.id) {
            <div class="row-between line"><span>{{ i.quantity }}× {{ i.productName }}@if (i.variantName) { <small> · {{ i.variantName }}</small> }</span><span>{{ i.lineTotal | brl }}</span></div>
          }
          <div class="row-between"><span>Subtotal</span><span>{{ order.subtotal | brl }}</span></div>
          @if (order.discountTotal > 0) { <div class="row-between"><span>Descontos</span><span>−{{ order.discountTotal | brl }}</span></div> }
          <div class="row-between"><span>Entrega</span><span>{{ order.deliveryFee | brl }}</span></div>
          <div class="row-between total"><span>Total</span><span>{{ order.total | brl }}</span></div>
        </section>
        <section class="card">
          <h2>{{ order.deliveryMethod | label }}</h2>
          @if (order.deliveryAddress) { <p class="text-2">{{ order.deliveryAddress }}</p> }
          @else if (ctx.store()?.address) { <p class="text-2">Retire em: {{ ctx.store()!.address }}@if (ctx.store()!.serviceHours) { <br>{{ ctx.store()!.serviceHours }} }</p> }
          <h3>Linha do tempo</h3>
          <ol class="timeline">
            @for (h of order.timeline; track h.id) { <li><span [class]="'badge ' + (h.toValue | tone)">{{ h.toValue | label }}</span> <small>{{ h.createdAt | dt: true }}</small></li> }
          </ol>
          <p class="hint">Guarde este link: ele é a sua forma de acompanhar o pedido.</p>
        </section>
      </div>
      <a class="btn" [routerLink]="['/loja', ctx.slug()]" style="margin-top:16px">Continuar comprando</a>
    }`,
  styles: [`
    .steps { display: flex; gap: 8px; justify-content: space-between; margin-bottom: 16px; overflow-x: auto; }
    .step { display: flex; align-items: center; gap: 8px; color: var(--text-3); font-weight: 550; font-size: .9rem; white-space: nowrap; }
    .step.done { color: var(--text); }
    .step.current { color: var(--brand); }
    .dot { width: 22px; height: 22px; border-radius: 50%; border: 2px solid var(--border-strong); display: grid; place-items: center; }
    .step.done .dot { background: var(--brand); border-color: var(--brand); color: var(--brand-ink); }
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 16px; }
    @media (max-width: 800px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .line { padding: 8px 0; border-bottom: 1px solid var(--border); }
    .total { font-weight: 700; font-size: 1.1rem; margin-top: 6px; }
    .timeline { list-style: none; padding: 0; display: flex; flex-direction: column; gap: 8px; }
  `],
})
export class StoreTracking implements OnInit {
  private api = inject(PublicApi);
  private toast = inject(ToastService);
  readonly ctx = inject(StoreContext);
  readonly token = input.required<string>();
  readonly o = signal<PublicOrder | null>(null);
  readonly notFound = signal(false);
  readonly syncing = signal(false);
  readonly paying = signal(false);

  readonly steps = computed(() => {
    const o = this.o();
    if (!o) return [];
    const paid = ['APPROVED', 'PARTIALLY_REFUNDED', 'REFUNDED'].includes(o.paymentStatus);
    const f = o.fulfillmentStatus;
    const order = ['AWAITING', 'PICKING', 'READY_FOR_PICKUP', 'SHIPPED', 'DELIVERED'];
    const idx = order.indexOf(f);
    const ready = o.deliveryMethod === 'PICKUP' ? 'Pronto para retirada' : 'Enviado';
    return [
      { label: 'Pedido recebido', done: true, current: !paid },
      { label: 'Pagamento confirmado', done: paid, current: paid && idx <= 0 },
      { label: 'Em separação', done: idx >= 1, current: idx === 1 },
      { label: ready, done: idx >= 2, current: idx === 2 || idx === 3 },
      { label: 'Entregue', done: idx === 4 || f === 'RETURNED', current: idx === 4 },
    ];
  });

  ngOnInit() {
    // primeiro mostra o que já sabemos; em seguida pede ao servidor para conferir o pagamento no provedor
    this.api.order(this.ctx.slug(), this.token()).subscribe({
      next: o => { this.o.set(o); if (o.status === 'OPEN') this.refresh(); },
      error: () => this.notFound.set(true),
    });
  }

  refresh() {
    this.syncing.set(true);
    this.api.syncOrder(this.ctx.slug(), this.token()).subscribe({
      next: o => { this.o.set(o); this.syncing.set(false); },
      error: () => this.syncing.set(false),
    });
  }

  pay() {
    const url = this.o()?.checkoutUrl;
    if (url) { window.location.href = url; return; }
    this.paying.set(true);
    this.api.pay(this.ctx.slug(), this.token()).subscribe({
      next: r => { window.location.href = r.checkoutUrl; },
      error: e => { this.paying.set(false); this.toast.error(errorMessage(e)); },
    });
  }
}
