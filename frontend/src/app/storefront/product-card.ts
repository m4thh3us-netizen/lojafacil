import { ChangeDetectionStrategy, Component, inject, input } from '@angular/core';
import { RouterLink } from '@angular/router';

import { BrlPipe } from '../core/format';
import { PublicProductCard } from '../core/models';
import { Icon } from '../shared/icon';
import { StoreContext } from './store-context';

@Component({
  selector: 'app-product-card',
  imports: [RouterLink, BrlPipe, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let p = product();
    <a class="pcard" [routerLink]="['/loja', ctx.slug(), 'produto', p.slug]">
      <div class="img">
        @if (p.imageUrl) { <img [src]="p.imageUrl" [alt]="p.name" loading="lazy" width="400" height="400"> } @else { <app-icon name="image" [size]="28" /> }
        @if (p.onSale) { <span class="badge danger tag">Oferta</span> }
        @if (!p.available) { <span class="badge tag out">Esgotado</span> }
      </div>
      <div class="info">
        <span class="name">{{ p.name }}</span>
        <span class="price">@if (p.onSale) { <s>{{ p.price | brl }}</s> } <b>{{ p.finalPrice | brl }}</b>@if (p.hasVariants) { <small> a partir de</small> }</span>
      </div>
    </a>`,
  styles: [`
    .pcard { display: flex; flex-direction: column; background: var(--surface); border: 1px solid var(--border); border-radius: 16px; overflow: hidden; color: var(--text); text-decoration: none; transition: transform .15s var(--ease), box-shadow .15s; height: 100%; }
    .pcard:hover { transform: translateY(-2px); box-shadow: 0 10px 30px rgba(0,0,0,.08); text-decoration: none; }
    .img { aspect-ratio: 1; background: var(--surface-3); display: grid; place-items: center; color: var(--text-3); position: relative; }
    .img img { width: 100%; height: 100%; object-fit: cover; }
    .tag { position: absolute; top: 10px; left: 10px; }
    .out { left: auto; right: 10px; background: #111827; color: #fff; }
    .info { padding: 12px 14px 14px; display: flex; flex-direction: column; gap: 4px; }
    .name { font-weight: 550; line-height: 1.3; }
    .price s { color: var(--text-3); font-size: .85rem; margin-right: 4px; }
    .price b { font-size: 1.05rem; }
  `],
})
export class ProductCard {
  readonly ctx = inject(StoreContext);
  readonly product = input.required<PublicProductCard>();
}

@Component({
  selector: 'app-product-skeleton',
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `<div style="border:1px solid var(--border);border-radius:16px;overflow:hidden;background:var(--surface)">
    <span class="skeleton" style="aspect-ratio:1;border-radius:0"></span>
    <div style="padding:12px 14px"><span class="skeleton line"></span><span class="skeleton line" style="width:40%"></span></div></div>`,
})
export class ProductSkeleton {}
