import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';
import { BrlPipe } from '../core/format';
import { Category, ProductListItem } from '../core/models';
import { ToastService } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { PagedList } from '../shared/paged-list';
import { Empty, Modal, Pager, TableSkeleton } from '../shared/ui';

@Component({
  selector: 'app-products',
  imports: [FormsModule, RouterLink, BrlPipe, Icon, Empty, Pager, TableSkeleton, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div><h1>Produtos</h1><p>Catálogo, preços, variações e visibilidade na vitrine.</p></div>
      <div class="row">
        <button class="btn" type="button" (click)="catOpen.set(true)"><app-icon name="tag" /> Categorias</button>
        <a class="btn btn-primary" routerLink="/painel/produtos/novo"><app-icon name="plus" /> Novo produto</a>
      </div>
    </div>

    <div class="toolbar" role="search">
      <div class="field grow"><label for="q">Buscar</label>
        <input id="q" class="input" type="search" placeholder="Nome, SKU ou código de barras" [(ngModel)]="q" (ngModelChange)="list.search(true)"></div>
      <div class="field"><label for="cat">Categoria</label>
        <select id="cat" class="input" [(ngModel)]="categoryId" (ngModelChange)="list.search()">
          <option [ngValue]="null">Todas</option>
          @for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }
        </select></div>
      <div class="field"><label for="st">Situação</label>
        <select id="st" class="input" [(ngModel)]="status" (ngModelChange)="list.search()">
          <option value="">Não arquivados</option><option value="active">Ativos</option>
          <option value="inactive">Inativos</option><option value="archived">Arquivados</option><option value="all">Todos</option>
        </select></div>
      <div class="field"><label for="so">Ordenar</label>
        <select id="so" class="input" [(ngModel)]="sort" (ngModelChange)="list.search()">
          <option value="">Atualizados recentemente</option><option value="name">Nome</option>
          <option value="price_asc">Menor preço</option><option value="price_desc">Maior preço</option>
        </select></div>
    </div>

    @if (list.firstLoad) {
      <app-table-skeleton [cols]="6" />
    } @else if (list.error()) {
      <div class="alert danger">{{ list.error() }} <button class="link-btn" (click)="list.refresh()">Tentar novamente</button></div>
    } @else if (!list.items.length) {
      <div class="card"><app-empty icon="box" title="Nenhum produto encontrado"
        [text]="q || categoryId || status ? 'Ajuste os filtros para ver outros produtos.' : 'Cadastre o primeiro produto para começar a vender.'">
        <a class="btn btn-primary" routerLink="/painel/produtos/novo">Cadastrar produto</a></app-empty></div>
    } @else {
      <div class="table-wrap" [style.opacity]="list.loading() ? .6 : 1" [attr.aria-busy]="list.loading()">
        <table class="table cards">
          <thead><tr><th>Produto</th><th>Categoria</th><th class="num">Preço</th><th class="num">Disponível</th><th>Situação</th><th></th></tr></thead>
          <tbody>
            @for (p of list.items; track p.id) {
              <tr>
                <td data-label="Produto">
                  <div class="prod">
                    @if (p.imageUrl) { <img [src]="p.imageUrl" alt="" width="40" height="40" loading="lazy"> } @else { <span class="ph"><app-icon name="image" /></span> }
                    <div><a class="row-link" [routerLink]="['/painel/produtos', p.id]">{{ p.name }}</a><br><small>{{ p.sku }}@if (p.hasVariants) { · {{ p.variantCount }} variações }</small></div>
                  </div>
                </td>
                <td data-label="Categoria">{{ p.categoryName || '—' }}</td>
                <td data-label="Preço" class="num">
                  @if (p.promoPrice) { <s class="muted">{{ p.salePrice | brl }}</s> {{ p.promoPrice | brl }} } @else { {{ p.salePrice | brl }} }
                </td>
                <td data-label="Disponível" class="num"><span [class]="p.lowStock ? 'badge warning' : 'badge'">{{ p.availableQty }}</span>
                  @if (p.reservedQty) { <small> ({{ p.reservedQty }} reserv.)</small> }</td>
                <td data-label="Situação"><div class="row" style="gap:4px;justify-content:flex-end">
                  @if (p.archived) { <span class="badge">Arquivado</span> }
                  @else { <span class="badge" [class.success]="p.active">{{ p.active ? 'Ativo' : 'Inativo' }}</span>
                    @if (!p.visible) { <span class="badge">Oculto</span> }
                    @if (p.featured) { <span class="badge primary">Destaque</span> } }
                </div></td>
                <td class="num"><a class="btn btn-sm" [routerLink]="['/painel/produtos', p.id]" [attr.aria-label]="'Editar ' + p.name"><app-icon name="edit" [size]="15" /> Editar</a></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [page]="list.page" [pages]="list.data()?.totalPages ?? 0" [total]="list.data()?.totalElements ?? 0" (change)="list.goTo($event)" />
    }

    <app-modal title="Categorias" [open]="catOpen()" (closed)="catOpen.set(false)" [busy]="catBusy()">
      <form class="row" (ngSubmit)="saveCategory()">
        <input class="input spacer" [(ngModel)]="catName" name="catName" placeholder="Nova categoria" aria-label="Nome da categoria" maxlength="80">
        <button class="btn btn-primary" [disabled]="catBusy() || !catName.trim()">{{ catBusy() ? 'Salvando...' : (editingCat ? 'Salvar' : 'Adicionar') }}</button>
      </form>
      <ul class="cats">
        @for (c of categories(); track c.id) {
          <li>
            <span class="spacer">{{ c.name }} @if (!c.active) { <span class="badge">inativa</span> }</span>
            <button class="btn btn-sm btn-ghost" type="button" (click)="editCat(c)">Renomear</button>
            <button class="btn btn-sm btn-ghost" type="button" (click)="toggleCat(c)">{{ c.active ? 'Desativar' : 'Ativar' }}</button>
          </li>
        } @empty { <li class="muted">Nenhuma categoria ainda.</li> }
      </ul>
    </app-modal>`,
  styles: [`
    .prod { display: flex; align-items: center; gap: 10px; text-align: left; }
    .prod img, .ph { width: 40px; height: 40px; border-radius: 10px; object-fit: cover; background: var(--surface-3); flex-shrink: 0; }
    .ph { display: grid; place-items: center; color: var(--text-3); }
    .cats { list-style: none; padding: 0; margin: 14px 0 0; }
    .cats li { display: flex; align-items: center; gap: 6px; padding: 8px 0; border-bottom: 1px solid var(--border); }
  `],
})
export class ProductsPage {
  private api = inject(AdminApi);
  private toast = inject(ToastService);
  q = '';
  categoryId: number | null = null;
  status = '';
  sort = '';
  readonly categories = signal<Category[]>([]);
  readonly list = new PagedList<ProductListItem>(page =>
    this.api.products({ q: this.q, categoryId: this.categoryId, status: this.status, sort: this.sort, page, size: 15 }));

  readonly catOpen = signal(false);
  readonly catBusy = signal(false);
  catName = '';
  editingCat: Category | null = null;

  constructor() {
    this.list.search();
    this.loadCategories();
  }

  loadCategories() { this.api.categories().subscribe(c => this.categories.set(c)); }

  editCat(c: Category) { this.editingCat = c; this.catName = c.name; }

  saveCategory() {
    if (this.catBusy() || !this.catName.trim()) return;
    this.catBusy.set(true);
    this.api.saveCategory(this.editingCat?.id ?? null, { name: this.catName.trim(), active: this.editingCat?.active ?? true }).subscribe({
      next: () => { this.toast.success('Categoria salva.'); this.catName = ''; this.editingCat = null; this.catBusy.set(false); this.loadCategories(); },
      error: e => { this.catBusy.set(false); this.toast.error(e); },
    });
  }

  toggleCat(c: Category) {
    this.api.saveCategory(c.id, { name: c.name, active: !c.active }).subscribe({
      next: () => this.loadCategories(), error: e => this.toast.error(e),
    });
  }
}
