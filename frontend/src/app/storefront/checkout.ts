import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { PublicApi } from '../core/api';
import { BrlPipe } from '../core/format';
import { Address, DeliveryMethod, Quote } from '../core/models';
import { errorMessage, fieldErrors } from '../core/ui.services';
import { AddressFields, addressComplete, emptyAddress } from '../shared/address-fields';
import { Icon } from '../shared/icon';
import { StoreContext } from './store-context';

@Component({
  selector: 'app-store-checkout',
  imports: [FormsModule, RouterLink, BrlPipe, Icon, AddressFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a [routerLink]="['/loja', ctx.slug(), 'carrinho']" class="back"><app-icon name="arrowLeft" [size]="14" /> Carrinho</a>
    <h1 style="margin-top:6px">Finalizar compra</h1>
    @if (!ctx.cart().length) {
      <div class="card"><p>Seu carrinho está vazio.</p><a class="btn btn-primary" [routerLink]="['/loja', ctx.slug(), 'produtos']">Ver produtos</a></div>
    } @else {
      <form class="layout" (ngSubmit)="submit()" novalidate>
        <div class="stack">
          <section class="card">
            <h2>Seus dados</h2>
            <div class="grid grid-3">
              <div class="field"><label for="cn">Nome completo *</label><input id="cn" class="input" name="name" autocomplete="name" [(ngModel)]="customer.name" required maxlength="120">
                @if (errs()['customer.name']) { <span class="error-text">{{ errs()['customer.name'] }}</span> }</div>
              <div class="field"><label for="ce">E-mail *</label><input id="ce" class="input" type="email" name="email" autocomplete="email" [(ngModel)]="customer.email" required>
                @if (errs()['customer.email']) { <span class="error-text">{{ errs()['customer.email'] }}</span> }</div>
              <div class="field"><label for="cp">Telefone *</label><input id="cp" class="input" type="tel" name="phone" autocomplete="tel" [(ngModel)]="customer.phone" required>
                @if (errs()['customer.phone']) { <span class="error-text">{{ errs()['customer.phone'] }}</span> }</div>
            </div>
            <p class="hint">Usamos esses dados apenas para o pedido. Você receberá por e-mail o link de acompanhamento.</p>
          </section>
          <section class="card">
            <h2>Entrega</h2>
            <div class="row" role="radiogroup" aria-label="Forma de entrega">
              @if (ctx.store()?.pickupEnabled) { <label class="check"><input type="radio" name="dm" value="PICKUP" [(ngModel)]="delivery" (ngModelChange)="requote()"> Retirar na loja @if (ctx.store()?.address) { <small>({{ ctx.store()!.address }})</small> }</label> }
              @if (ctx.store()?.localDeliveryEnabled) { <label class="check"><input type="radio" name="dm" value="LOCAL_DELIVERY" [(ngModel)]="delivery" (ngModelChange)="requote()"> Entrega local ({{ ctx.store()!.localDeliveryFee | brl }})</label> }
            </div>
            @if (delivery === 'LOCAL_DELIVERY') { <app-address-fields [(address)]="address" /> }
            <div class="field" style="margin-top:12px"><label for="nt">Observações</label><input id="nt" class="input" name="notes" [(ngModel)]="notes" maxlength="500"></div>
          </section>
        </div>
        <aside class="card summary">
          <h2>Resumo do pedido</h2>
          @if (quote(); as q) {
            @for (i of q.items; track i.variantId) {
              <div class="row-between"><span>{{ i.quantity }}× {{ i.productName }}@if (i.variantName) { <small> · {{ i.variantName }}</small> }</span><span>{{ i.lineTotal | brl }}</span></div>
              @if (i.problem) { <small class="error-text">{{ i.problem }}</small> }
            }
            <hr class="divider">
            <div class="row-between"><span>Subtotal</span><span>{{ q.subtotal | brl }}</span></div>
            @if (q.discountTotal > 0) { <div class="row-between" style="color:var(--success)"><span>Descontos</span><span>−{{ q.discountTotal | brl }}</span></div> }
            <div class="row-between"><span>Entrega</span><span>{{ q.deliveryFee > 0 ? (q.deliveryFee | brl) : 'Grátis' }}</span></div>
            <div class="row-between total"><span>Total</span><span>{{ q.total | brl }}</span></div>
          } @else { @for (i of [1,2,3,4]; track i) { <span class="skeleton line"></span> } }
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
          <button class="btn btn-primary btn-block" type="submit" [disabled]="busy() || !quote()?.valid">{{ busy() ? 'Processando...' : 'Ir para o pagamento' }}</button>
          @if (ctx.store()?.demo) { <span class="badge sim" style="align-self:center">Pagamento simulado (demonstração)</span> }
          <small class="muted"><app-icon name="shield" [size]="13" /> O pagamento é feito na página segura do provedor. Não pedimos nem guardamos dados do cartão.</small>
        </aside>
      </form>
    }`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 360px; gap: 20px; align-items: start; }
    @media (max-width: 900px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .summary { display: flex; flex-direction: column; gap: 8px; position: sticky; top: 90px; }
    .total { font-size: 1.15rem; font-weight: 700; padding-top: 8px; border-top: 1px solid var(--border); }
  `],
})
export class StoreCheckout implements OnInit {
  private api = inject(PublicApi);
  private router = inject(Router);
  readonly ctx = inject(StoreContext);
  readonly entrega = input<string>();
  readonly quote = signal<Quote | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly errs = signal<Record<string, string>>({});
  customer = { name: '', email: '', phone: '' };
  delivery: DeliveryMethod = 'PICKUP';
  address: Address = emptyAddress();
  notes = '';

  ngOnInit() {
    const s = this.ctx.store();
    if (this.entrega() === 'LOCAL_DELIVERY' || (s && !s.pickupEnabled)) this.delivery = 'LOCAL_DELIVERY';
    this.requote();
  }

  requote() {
    if (!this.ctx.cart().length) return;
    this.api.quote(this.ctx.slug(), { deliveryMethod: this.delivery, items: this.ctx.lines() }).subscribe({
      next: q => this.quote.set(q), error: e => this.error.set(errorMessage(e)),
    });
  }

  submit() {
    this.error.set('');
    this.errs.set({});
    const c = this.customer;
    if (!c.name.trim() || !/^\S+@\S+\.\S+$/.test(c.email) || c.phone.replace(/\D/g, '').length < 8) {
      this.error.set('Preencha nome, e-mail e telefone válidos.'); return;
    }
    if (this.delivery === 'LOCAL_DELIVERY' && !addressComplete(this.address)) { this.error.set('Complete o endereço de entrega.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.api.checkout(this.ctx.slug(), {
      customer: c, deliveryMethod: this.delivery, address: this.delivery === 'LOCAL_DELIVERY' ? this.address : null,
      items: this.ctx.lines(), notes: this.notes || null,
    }).subscribe({
      next: r => {
        this.ctx.rememberOrder(r.orderNumber, r.trackingToken);
        this.ctx.clear();
        if (r.checkoutUrl) {
          window.location.href = r.checkoutUrl;
        } else {
          this.router.navigate(['/loja', this.ctx.slug(), 'pedido', r.trackingToken]);
        }
      },
      error: e => { this.busy.set(false); this.errs.set(fieldErrors(e)); this.error.set(errorMessage(e)); this.requote(); },
    });
  }
}
