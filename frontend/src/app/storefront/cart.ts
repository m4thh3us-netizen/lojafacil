import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { RouterLink } from '@angular/router';
import { Subject, catchError, debounceTime, of, switchMap } from 'rxjs';

import { PublicApi } from '../core/api';
import { BrlPipe } from '../core/format';
import { DeliveryMethod, Quote } from '../core/models';
import { errorMessage } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { Empty } from '../shared/ui';
import { StoreContext } from './store-context';

/** Resumo do carrinho calculado pelo servidor (componente reutilizado no checkout). */
@Component({
  selector: 'app-store-cart',
  imports: [RouterLink, BrlPipe, Icon, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <h1>Carrinho</h1>
    @if (!ctx.cart().length) {
      <div class="card"><app-empty icon="cart" title="Seu carrinho está vazio" text="Explore os produtos da loja.">
        <a class="btn btn-primary" [routerLink]="['/loja', ctx.slug(), 'produtos']">Ver produtos</a></app-empty></div>
    } @else {
      <div class="layout">
        <div class="card list">
          @for (i of ctx.cart(); track i.variantId) {
            @let q = lineOf(i.variantId);
            <div class="item">
              <div class="thumb">@if (i.imageUrl) { <img [src]="i.imageUrl" alt="" loading="lazy"> }</div>
              <div class="spacer">
                <a [routerLink]="['/loja', ctx.slug(), 'produto', i.productSlug]"><b>{{ i.name }}</b></a>
                @if (i.variantName) { <br><small>{{ i.variantName }}</small> }
                @if (q?.problem) { <br><span class="error-text">{{ q!.problem }}</span> }
              </div>
              <div class="qty" role="group" [attr.aria-label]="'Quantidade de ' + i.name">
                <button type="button" class="btn btn-icon btn-sm" (click)="change(i.variantId, i.quantity - 1)" [disabled]="i.quantity <= 1" aria-label="Diminuir"><app-icon name="minus" [size]="14" /></button>
                <span>{{ i.quantity }}</span>
                <button type="button" class="btn btn-icon btn-sm" (click)="change(i.variantId, i.quantity + 1)" aria-label="Aumentar"><app-icon name="plus" [size]="14" /></button>
              </div>
              <div class="line-total">@if (q) { {{ q.lineTotal | brl }} } @else { <span class="skeleton line" style="width:60px"></span> }</div>
              <button type="button" class="btn btn-ghost btn-icon btn-sm" (click)="remove(i.variantId)" [attr.aria-label]="'Remover ' + i.name"><app-icon name="trash" [size]="15" /></button>
            </div>
          }
        </div>
        <aside class="card summary">
          <h2>Resumo</h2>
          <div class="row" role="radiogroup" aria-label="Forma de entrega">
            @if (ctx.store()?.pickupEnabled) { <label class="check"><input type="radio" name="dm" value="PICKUP" [checked]="delivery() === 'PICKUP'" (change)="setDelivery('PICKUP')"> Retirada</label> }
            @if (ctx.store()?.localDeliveryEnabled) { <label class="check"><input type="radio" name="dm" value="LOCAL_DELIVERY" [checked]="delivery() === 'LOCAL_DELIVERY'" (change)="setDelivery('LOCAL_DELIVERY')"> Entrega local</label> }
          </div>
          @if (quote(); as q) {
            <div class="row-between"><span>Subtotal</span><span>{{ q.subtotal | brl }}</span></div>
            @if (q.discountTotal > 0) { <div class="row-between" style="color:var(--success)"><span>Descontos</span><span>−{{ q.discountTotal | brl }}</span></div> }
            <div class="row-between"><span>Entrega</span><span>{{ q.deliveryFee > 0 ? (q.deliveryFee | brl) : 'Grátis' }}</span></div>
            <div class="row-between total"><span>Total</span><span>{{ q.total | brl }}</span></div>
            @if (!q.valid) { <div class="alert warning">Ajuste os itens indicados para continuar.</div> }
            <a class="btn btn-primary btn-block" [class.disabled]="!q.valid" [attr.aria-disabled]="!q.valid" [routerLink]="q.valid ? ['/loja', ctx.slug(), 'checkout'] : null" [queryParams]="{ entrega: delivery() }">Finalizar compra</a>
          } @else if (error()) { <div class="alert danger">{{ error() }}</div> }
          @else { @for (i of [1,2,3]; track i) { <span class="skeleton line"></span> } }
          <small class="muted">Preços e disponibilidade confirmados pelo servidor.</small>
        </aside>
      </div>
    }`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 20px; align-items: start; }
    @media (max-width: 860px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .item { display: flex; align-items: center; gap: 12px; padding: 12px 0; border-bottom: 1px solid var(--border); }
    .item:last-child { border-bottom: 0; }
    .item a { color: var(--text); }
    .thumb { width: 64px; height: 64px; border-radius: 12px; overflow: hidden; background: var(--surface-3); flex-shrink: 0; }
    .thumb img { width: 100%; height: 100%; object-fit: cover; }
    .qty { display: inline-flex; align-items: center; gap: 4px; }
    .qty span { min-width: 22px; text-align: center; font-weight: 600; }
    .line-total { min-width: 90px; text-align: right; font-weight: 600; font-variant-numeric: tabular-nums; }
    @media (max-width: 560px) { .item { flex-wrap: wrap; } .line-total { min-width: 0; } }
    .summary { display: flex; flex-direction: column; gap: 8px; position: sticky; top: 90px; }
    .total { font-size: 1.15rem; font-weight: 700; padding-top: 8px; border-top: 1px solid var(--border); }
    a.disabled { opacity: .5; pointer-events: none; }
  `],
})
export class StoreCart {
  private api = inject(PublicApi);
  readonly ctx = inject(StoreContext);
  readonly quote = signal<Quote | null>(null);
  readonly error = signal('');
  readonly delivery = signal<DeliveryMethod>('PICKUP');
  private quote$ = new Subject<void>();

  constructor() {
    this.quote$.pipe(
      debounceTime(150),
      switchMap(() => this.ctx.cart().length
        ? this.api.quote(this.ctx.slug(), { deliveryMethod: this.delivery(), items: this.ctx.lines() })
            .pipe(catchError(e => { this.error.set(errorMessage(e)); return of(null); }))
        : of(null)),
      takeUntilDestroyed(inject(DestroyRef)),
    ).subscribe(q => { this.quote.set(q); if (q) this.error.set(''); });
    const s = this.ctx.store();
    if (s && !s.pickupEnabled && s.localDeliveryEnabled) this.delivery.set('LOCAL_DELIVERY');
    this.quote$.next();
  }

  lineOf(variantId: number) { return this.quote()?.items.find(i => i.variantId === variantId); }
  change(variantId: number, q: number) { this.ctx.setQuantity(variantId, q); this.quote$.next(); }
  remove(variantId: number) { this.ctx.remove(variantId); this.quote$.next(); }
  setDelivery(d: DeliveryMethod) { this.delivery.set(d); this.quote$.next(); }
}
