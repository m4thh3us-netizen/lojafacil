import { ChangeDetectionStrategy, Component, DestroyRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';

import { PublicApi } from '../core/api';
import { Category, PublicProductCard } from '../core/models';
import { PagedList } from '../shared/paged-list';
import { Empty, Pager } from '../shared/ui';
import { ProductCard, ProductSkeleton } from './product-card';
import { StoreContext } from './store-context';

@Component({
  selector: 'app-store-catalog',
  imports: [FormsModule, ProductCard, ProductSkeleton, Empty, Pager],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header"><div><h1>Produtos</h1>@if (q) { <p>Resultados para "{{ q }}"</p> }</div></div>
    <div class="layout">
      <aside class="filters card" aria-label="Filtros">
        <div class="field"><label for="sq">Buscar</label><input id="sq" class="input" type="search" [(ngModel)]="q" (ngModelChange)="apply(true)"></div>
        <div class="field"><label for="sc">Categoria</label>
          <select id="sc" class="input" [(ngModel)]="category" (ngModelChange)="apply()"><option value="">Todas</option>
            @for (c of categories(); track c.id) { <option [value]="c.slug">{{ c.name }}</option> }</select></div>
        <div class="row" style="flex-wrap:nowrap">
          <div class="field"><label for="mn">Preço mín.</label><input id="mn" class="input" type="number" min="0" [(ngModel)]="minPrice" (ngModelChange)="apply(true)"></div>
          <div class="field"><label for="mx">Preço máx.</label><input id="mx" class="input" type="number" min="0" [(ngModel)]="maxPrice" (ngModelChange)="apply(true)"></div>
        </div>
        <label class="check"><input type="checkbox" [(ngModel)]="onSale" (ngModelChange)="apply()"> Somente ofertas</label>
        <div class="field"><label for="so">Ordenar por</label>
          <select id="so" class="input" [(ngModel)]="sort" (ngModelChange)="apply()">
            <option value="">Relevância</option><option value="price_asc">Menor preço</option><option value="price_desc">Maior preço</option><option value="name">Nome</option></select></div>
      </aside>
      <div>
        <div class="products" [style.opacity]="list.loading() && !list.firstLoad ? .6 : 1" [attr.aria-busy]="list.loading()">
          @if (list.firstLoad) { @for (i of [1,2,3,4,5,6]; track i) { <app-product-skeleton /> } }
          @else { @for (p of list.items; track p.id) { <app-product-card [product]="p" /> } }
        </div>
        @if (!list.loading() && !list.items.length) { <app-empty icon="search" title="Nenhum produto encontrado" text="Tente outros termos ou remova filtros." /> }
        <app-pager [page]="list.page" [pages]="list.data()?.totalPages ?? 0" [total]="list.data()?.totalElements ?? 0" (change)="list.goTo($event)" />
      </div>
    </div>`,
  styles: [`
    .layout { display: grid; grid-template-columns: 250px minmax(0, 1fr); gap: 20px; align-items: start; }
    .filters { display: flex; flex-direction: column; gap: 12px; position: sticky; top: 90px; }
    .products { display: grid; grid-template-columns: repeat(auto-fill, minmax(190px, 1fr)); gap: 16px; transition: opacity .15s; }
    @media (max-width: 800px) { .layout { grid-template-columns: minmax(0, 1fr); } .filters { position: static; } }
    @media (max-width: 480px) { .products { grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; } }
  `],
})
export class StoreCatalog {
  private api = inject(PublicApi);
  private router = inject(Router);
  private route = inject(ActivatedRoute);
  readonly ctx = inject(StoreContext);
  readonly categories = signal<Category[]>([]);
  q = ''; category = ''; minPrice: number | null = null; maxPrice: number | null = null; onSale = false; sort = '';

  readonly list = new PagedList<PublicProductCard>(page => this.api.products(this.ctx.slug(), {
    q: this.q, category: this.category, minPrice: this.minPrice, maxPrice: this.maxPrice, onSale: this.onSale || null, sort: this.sort, page, size: 12,
  }));

  constructor() {
    this.api.categories(this.ctx.slug()).subscribe(c => this.categories.set(c));
    let first = true;
    this.route.queryParamMap.pipe(takeUntilDestroyed(inject(DestroyRef))).subscribe(p => {
      const q = p.get('q') ?? '';
      const category = p.get('categoria') ?? '';

      if (first || q !== this.q || category !== this.category) {
        first = false;
        this.q = q;
        this.category = category;
        this.list.search();
      }
    });
  }

  apply(debounced = false) {
    this.router.navigate([], { relativeTo: this.route, queryParams: { q: this.q || null, categoria: this.category || null }, replaceUrl: true });
    this.list.search(debounced);
  }
}
