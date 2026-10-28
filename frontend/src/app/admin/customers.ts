import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';
import { DatePipeBr } from '../core/format';
import { CustomerListItem } from '../core/models';
import { Icon } from '../shared/icon';
import { PagedList } from '../shared/paged-list';
import { Empty, Pager, TableSkeleton } from '../shared/ui';

@Component({
  selector: 'app-customers',
  imports: [FormsModule, RouterLink, DatePipeBr, Icon, Empty, Pager, TableSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div><h1>Clientes</h1><p>Compradores da loja virtual e de pedidos manuais.</p></div>
      <a class="btn btn-primary" routerLink="/painel/clientes/novo"><app-icon name="plus" /> Novo cliente</a>
    </div>
    <div class="toolbar" role="search">
      <div class="field grow"><label for="q">Buscar</label><input id="q" class="input" type="search" placeholder="Nome, e-mail ou telefone" [(ngModel)]="q" (ngModelChange)="list.search(true)"></div>
    </div>
    @if (list.firstLoad) { <app-table-skeleton [cols]="4" /> }
    @else if (!list.items.length) { <div class="card"><app-empty icon="users" title="Nenhum cliente encontrado" text="Clientes são criados automaticamente nas compras ou cadastrados manualmente." /></div> }
    @else {
      <div class="table-wrap" [style.opacity]="list.loading() ? .6 : 1">
        <table class="table cards">
          <thead><tr><th>Nome</th><th>E-mail</th><th>Telefone</th><th>Cliente desde</th></tr></thead>
          <tbody>
            @for (c of list.items; track c.id) {
              <tr>
                <td data-label="Nome"><a class="row-link" [routerLink]="['/painel/clientes', c.id]">{{ c.name }}</a></td>
                <td data-label="E-mail">{{ c.email }}</td>
                <td data-label="Telefone">{{ c.phone || '—' }}</td>
                <td data-label="Cliente desde">{{ c.createdAt | dt }}</td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [page]="list.page" [pages]="list.data()?.totalPages ?? 0" [total]="list.data()?.totalElements ?? 0" (change)="list.goTo($event)" />
    }`,
})
export class CustomersPage {
  private api = inject(AdminApi);
  q = '';
  readonly list = new PagedList<CustomerListItem>(page => this.api.customers({ q: this.q, page, size: 20 }));
  constructor() { this.list.search(); }
}
