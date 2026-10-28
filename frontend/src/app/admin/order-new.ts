import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';

import { AdminApi } from '../core/api';
import { BrlPipe } from '../core/format';
import { Address, CheckoutResponse, CustomerListItem, DeliveryMethod, Quote, StockView } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';
import { AddressFields, emptyAddress } from '../shared/address-fields';
import { Icon } from '../shared/icon';

interface Line { variantId: number; label: string; quantity: number; available: number; }

/** Pedido manual: usa exatamente as mesmas regras do checkout da loja (preço, estoque, reserva). */
@Component({
  selector: 'app-order-new',
  imports: [FormsModule, RouterLink, BrlPipe, Icon, AddressFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/painel/pedidos" class="back"><app-icon name="arrowLeft" [size]="14" /> Pedidos</a>
    <div class="page-header" style="margin-top:6px"><div><h1>Novo pedido manual</h1><p>Venda no balcão, WhatsApp ou telefone, com as mesmas regras da loja virtual.</p></div></div>

    @if (result(); as r) {
      <div class="card fade-in stack">
        <div class="alert success"><app-icon name="check" /> Pedido #{{ r.orderNumber }} criado — total {{ r.total | brl }}.</div>
        @if (r.checkoutUrl) {
          <div class="field"><label>Link de pagamento para enviar ao cliente @if (r.simulated) { <span class="badge sim">SIMULADO</span> }</label>
            <div class="row"><input class="input spacer" [value]="r.checkoutUrl" readonly aria-label="Link de pagamento"><button class="btn" type="button" (click)="copy(r.checkoutUrl!)"><app-icon name="copy" /> Copiar</button></div></div>
        }
        <div class="field"><label>Link de acompanhamento (pessoal, não compartilhe publicamente)</label>
          <div class="row"><input class="input spacer" [value]="r.trackingUrl" readonly aria-label="Link de acompanhamento"><button class="btn" type="button" (click)="copy(r.trackingUrl)"><app-icon name="copy" /> Copiar</button></div></div>
        <div class="row"><button class="btn btn-primary" type="button" (click)="openOrder(r.orderNumber)">Ver pedido</button><button class="btn" type="button" (click)="reset()">Criar outro</button></div>
      </div>
    } @else {
      <div class="layout">
        <div class="stack">
          <section class="card">
            <h2>Cliente</h2>
            <div class="row" role="radiogroup" aria-label="Tipo de cliente">
              <label class="check"><input type="radio" name="cm" [(ngModel)]="customerMode" value="existing"> Cliente cadastrado</label>
              <label class="check"><input type="radio" name="cm" [(ngModel)]="customerMode" value="new"> Novo comprador</label>
            </div>
            @if (customerMode === 'existing') {
              @if (customer(); as c) {
                <div class="row-between chosen"><span><b>{{ c.name }}</b> <small>{{ c.email }}</small></span><button class="btn btn-sm" type="button" (click)="customer.set(null)">Trocar</button></div>
              } @else {
                <div class="field" style="margin-top:10px"><label for="cq">Buscar cliente</label><input id="cq" class="input" type="search" [(ngModel)]="customerQ" (ngModelChange)="customerSearch$.next($event)" placeholder="Nome, e-mail ou telefone"></div>
                <ul class="pick">@for (c of customerResults(); track c.id) { <li><button type="button" (click)="customer.set(c)">{{ c.name }} <small>{{ c.email }}</small></button></li> }</ul>
              }
            } @else {
              <div class="grid grid-3" style="margin-top:10px">
                <div class="field"><label for="nn">Nome *</label><input id="nn" class="input" [(ngModel)]="buyer.name"></div>
                <div class="field"><label for="ne">E-mail *</label><input id="ne" class="input" type="email" [(ngModel)]="buyer.email"></div>
                <div class="field"><label for="np">Telefone *</label><input id="np" class="input" [(ngModel)]="buyer.phone"></div>
              </div>
            }
          </section>

          <section class="card">
            <h2>Itens</h2>
            <div class="field"><label for="pq">Adicionar produto</label><input id="pq" class="input" type="search" [(ngModel)]="productQ" (ngModelChange)="productSearch$.next($event)" placeholder="Nome ou SKU"></div>
            <ul class="pick">
              @for (s of productResults(); track s.variantId) {
                <li><button type="button" (click)="addLine(s)" [disabled]="s.availableQty <= 0">{{ s.productName }}@if (s.variantName) { · {{ s.variantName }} } <small>{{ s.sku }} · {{ s.availableQty }} disp.</small></button></li>
              }
            </ul>
            @for (l of lines(); track l.variantId; let i = $index) {
              <div class="row-between line">
                <span class="spacer">{{ l.label }} <small>({{ l.available }} disp.)</small></span>
                <input class="input" style="width:80px" type="number" min="1" [max]="l.available" [ngModel]="l.quantity" (ngModelChange)="setQty(i, $event)" [attr.aria-label]="'Quantidade de ' + l.label">
                <button class="btn btn-ghost btn-icon btn-sm" type="button" (click)="removeLine(i)" aria-label="Remover item"><app-icon name="trash" [size]="15" /></button>
              </div>
            } @empty { <p class="muted">Nenhum item adicionado.</p> }
          </section>

          <section class="card">
            <h2>Entrega e pagamento</h2>
            <div class="row">
              <label class="check"><input type="radio" name="dm" [(ngModel)]="deliveryMethod" value="PICKUP" (ngModelChange)="requote()"> Retirada no local</label>
              <label class="check"><input type="radio" name="dm" [(ngModel)]="deliveryMethod" value="LOCAL_DELIVERY" (ngModelChange)="requote()"> Entrega local</label>
            </div>
            @if (deliveryMethod === 'LOCAL_DELIVERY') { <app-address-fields [(address)]="address" /> }
            <div class="row" style="margin-top:12px">
              <label class="check"><input type="radio" name="pm" [(ngModel)]="paymentMode" value="OFFLINE"> Pagamento na loja (registrar depois)</label>
              <label class="check"><input type="radio" name="pm" [(ngModel)]="paymentMode" value="LINK"> Gerar link de pagamento online</label>
            </div>
            <div class="field" style="margin-top:10px"><label for="obs">Observações</label><input id="obs" class="input" [(ngModel)]="notes" maxlength="500"></div>
          </section>
        </div>

        <aside class="card summary">
          <h2>Resumo</h2>
          @if (quote(); as q) {
            @for (i of q.items; track i.variantId) {
              <div class="row-between"><span>{{ i.quantity }}x {{ i.productName }}</span><span>{{ i.lineTotal | brl }}</span></div>
              @if (i.problem) { <small class="error-text">{{ i.problem }}</small> }
            }
            <hr class="divider">
            <div class="row-between"><span>Subtotal</span><span>{{ q.subtotal | brl }}</span></div>
            <div class="row-between"><span>Descontos</span><span>−{{ q.discountTotal | brl }}</span></div>
            <div class="row-between"><span>Entrega</span><span>{{ q.deliveryFee | brl }}</span></div>
            <div class="row-between total"><span>Total</span><span>{{ q.total | brl }}</span></div>
            <small class="muted">Valores calculados pelo servidor.</small>
          } @else { <p class="muted">Adicione itens para ver o total.</p> }
          @if (error()) { <div class="alert danger" role="alert" style="margin-top:10px">{{ error() }}</div> }
          <button class="btn btn-primary btn-block" style="margin-top:12px" type="button" (click)="submit()" [disabled]="busy() || !lines().length">{{ busy() ? 'Criando pedido...' : 'Criar pedido' }}</button>
          <p class="hint">O estoque fica reservado até o pagamento ou a expiração do prazo.</p>
        </aside>
      </div>
    }`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
    .summary { position: sticky; top: 84px; display: flex; flex-direction: column; gap: 6px; }
    @media (max-width: 960px) { .layout { grid-template-columns: minmax(0, 1fr); } .summary { position: static; } }
    .total { font-weight: 700; font-size: 1.1rem; }
    .pick { list-style: none; padding: 0; margin: 6px 0; max-height: 220px; overflow: auto; }
    .pick button { width: 100%; text-align: left; padding: 8px 10px; border: 0; background: none; color: var(--text); border-radius: 8px; cursor: pointer; font: inherit; }
    .pick button:hover:not(:disabled) { background: var(--surface-3); }
    .pick button:disabled { opacity: .5; cursor: not-allowed; }
    .line { padding: 8px 0; border-bottom: 1px solid var(--border); flex-wrap: nowrap; }
    .chosen { margin-top: 10px; padding: 10px; border-radius: 10px; background: var(--surface-2); }
  `],
})
export class OrderNewPage {
  private api = inject(AdminApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  private destroyRef = inject(DestroyRef);

  customerMode: 'existing' | 'new' = 'new';
  customerQ = ''; productQ = ''; notes = '';
  buyer = { name: '', email: '', phone: '' };
  deliveryMethod: DeliveryMethod = 'PICKUP';
  paymentMode: 'OFFLINE' | 'LINK' = 'OFFLINE';
  address: Address = emptyAddress();
  readonly customer = signal<CustomerListItem | null>(null);
  readonly customerResults = signal<CustomerListItem[]>([]);
  readonly productResults = signal<StockView[]>([]);
  readonly lines = signal<Line[]>([]);
  readonly quote = signal<Quote | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly result = signal<CheckoutResponse | null>(null);
  readonly customerSearch$ = new Subject<string>();
  readonly productSearch$ = new Subject<string>();
  private quote$ = new Subject<void>();

  constructor() {
    this.customerSearch$.pipe(debounceTime(300), switchMap(q => q.trim().length < 2 ? of(null) : this.api.customers({ q, size: 8 })), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => this.customerResults.set(p?.content ?? []));
    this.productSearch$.pipe(debounceTime(300), switchMap(q => q.trim().length < 2 ? of(null) : this.api.stock({ q, size: 10 })), takeUntilDestroyed(this.destroyRef))
      .subscribe(p => this.productResults.set(p?.content ?? []));
    this.quote$.pipe(debounceTime(200), switchMap(() => this.lines().length
        ? this.api.quote({ deliveryMethod: this.deliveryMethod, items: this.cartLines() })
            .pipe(catchError(e => { this.error.set(errorMessage(e)); return of(null); }))
        : of(null)), takeUntilDestroyed(this.destroyRef))
      .subscribe(q => this.quote.set(q));
  }

  private cartLines() { return this.lines().map(l => ({ variantId: l.variantId, quantity: l.quantity })); }

  addLine(s: StockView) {
    const existing = this.lines().findIndex(l => l.variantId === s.variantId);
    if (existing >= 0) { this.setQty(existing, this.lines()[existing].quantity + 1); return; }
    this.lines.update(l => [...l, { variantId: s.variantId, label: s.productName + (s.variantName ? ' · ' + s.variantName : ''), quantity: 1, available: s.availableQty }]);
    this.productQ = ''; this.productResults.set([]);
    this.requote();
  }

  setQty(i: number, q: number) {
    this.lines.update(l => l.map((x, j) => j === i ? { ...x, quantity: Math.max(1, Number(q) || 1) } : x));
    this.requote();
  }

  removeLine(i: number) { this.lines.update(l => l.filter((_, j) => j !== i)); this.requote(); }

  requote() { this.quote$.next(); }

  submit() {
    this.error.set('');
    if (this.customerMode === 'existing' && !this.customer()) { this.error.set('Selecione o cliente.'); return; }
    if (this.customerMode === 'new' && (!this.buyer.name || !this.buyer.email || !this.buyer.phone)) { this.error.set('Informe nome, e-mail e telefone do comprador.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.api.createOrder({
      customerId: this.customerMode === 'existing' ? this.customer()!.id : null,
      customer: this.customerMode === 'new' ? this.buyer : null,
      deliveryMethod: this.deliveryMethod, address: this.deliveryMethod === 'LOCAL_DELIVERY' ? this.address : null,
      items: this.cartLines(), paymentMode: this.paymentMode, notes: this.notes || null,
    }).subscribe({
      next: r => { this.busy.set(false); this.result.set(r); this.toast.success(`Pedido #${r.orderNumber} criado.`); if (r.paymentError) this.toast.error(r.paymentError); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  openOrder(number: number) {
    this.api.orders({ q: String(number), size: 1 }).subscribe(p => {
      const o = p.content.find(x => x.number === number);
      this.router.navigate(o ? ['/painel/pedidos', o.id] : ['/painel/pedidos']);
    });
  }

  copy(text: string) {
    navigator.clipboard?.writeText(text).then(() => this.toast.success('Link copiado.'), () => this.toast.error('Não foi possível copiar.'));
  }

  reset() { this.result.set(null); this.lines.set([]); this.quote.set(null); this.customer.set(null); this.buyer = { name: '', email: '', phone: '' }; }
}
