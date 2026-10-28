import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';

import { PublicApi } from '../core/api';
import { Category, PublicProductCard } from '../core/models';
import { Icon } from '../shared/icon';
import { ProductCard, ProductSkeleton } from './product-card';
import { StoreContext } from './store-context';

@Component({
  selector: 'app-store-home',
  imports: [RouterLink, Icon, ProductCard, ProductSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="banner" [style.background-image]="ctx.store()?.bannerUrl ? 'url(' + ctx.store()!.bannerUrl + ')' : null">
      <div class="banner-inner">
        @if (ctx.store(); as s) {
          <h1>{{ s.name }}</h1>
          @if (s.description) { <p>{{ s.description }}</p> }
          <a class="btn btn-primary" [routerLink]="['/loja', ctx.slug(), 'produtos']">Ver produtos <app-icon name="arrowRight" /></a>
        } @else { <span class="skeleton title" style="width:50%"></span><span class="skeleton line" style="width:70%"></span> }
      </div>
    </section>

    @if (categories().length) {
      <section class="section">
        <h2>Categorias</h2>
        <div class="chips">
          @for (c of categories(); track c.id) { <a class="chip" [routerLink]="['/loja', ctx.slug(), 'produtos']" [queryParams]="{ categoria: c.slug }">{{ c.name }}</a> }
        </div>
      </section>
    }

    <section class="section">
      <div class="row-between"><h2>Destaques</h2><a [routerLink]="['/loja', ctx.slug(), 'produtos']">Ver todos</a></div>
      <div class="products">
        @if (loading()) { @for (i of [1,2,3,4]; track i) { <app-product-skeleton /> } }
        @else { @for (p of featured(); track p.id) { <app-product-card [product]="p" /> } @empty { <p class="muted">Em breve, novidades por aqui.</p> } }
      </div>
    </section>

    @if (recent().length) {
      <section class="section">
        <h2>Seus pedidos neste navegador</h2>
        <div class="chips">@for (o of recent(); track o.token) { <a class="chip" [routerLink]="['/loja', ctx.slug(), 'pedido', o.token]">Pedido #{{ o.number }}</a> }</div>
      </section>
    }`,
  styles: [`
    .banner { border-radius: 22px; min-height: 260px; display: flex; align-items: flex-end; background-color: var(--brand-2); background-size: cover; background-position: center; overflow: hidden; position: relative; }
    .banner::before { content: ''; position: absolute; inset: 0; background: rgb(0 0 0 / .42); }
    .banner-inner { position: relative; padding: 32px; color: #fff; max-width: 640px; }
    .banner h1 { font-size: clamp(1.6rem, 4vw, 2.4rem); }
    .banner p { opacity: .92; }
    .section { margin-top: 32px; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .chip { padding: 8px 14px; border-radius: 999px; background: var(--surface); border: 1px solid var(--border); color: var(--text); text-decoration: none; font-weight: 550; }
    .chip:hover { border-color: var(--brand); color: var(--brand); text-decoration: none; }
    .products { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
    @media (max-width: 480px) { .products { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } }
  `],
})
export class StoreHome {
  private api = inject(PublicApi);
  readonly ctx = inject(StoreContext);
  readonly categories = signal<Category[]>([]);
  readonly featured = signal<PublicProductCard[]>([]);
  readonly loading = signal(true);
  readonly recent = signal(this.ctx.recentOrders());

  constructor() {
    const slug = this.ctx.slug();
    this.api.categories(slug).subscribe(c => this.categories.set(c));
    this.api.products(slug, { featured: true, size: 8 }).subscribe({
      next: p => {
        if (p.content.length) { this.featured.set(p.content); this.loading.set(false); return; }
        this.api.products(slug, { size: 8 }).subscribe(all => { this.featured.set(all.content); this.loading.set(false); });
      },
      error: () => this.loading.set(false),
    });
  }
}
