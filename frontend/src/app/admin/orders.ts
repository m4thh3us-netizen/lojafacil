import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';
import { BrlPipe, DatePipeBr, LabelPipe, TonePipe } from '../core/format';
import { OrderListItem } from '../core/models';
import { Icon } from '../shared/icon';
import { PagedList } from '../shared/paged-list';
import { Empty, Pager, TableSkeleton } from '../shared/ui';

@Component({
  selector: 'app-orders',
  imports: [FormsModule, RouterLink, BrlPipe, DatePipeBr, LabelPipe, TonePipe, Icon, Empty, Pager, TableSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div><h1>Pedidos</h1><p>Pedido, pagamento e atendimento têm estados independentes.</p></div>
      <a class="btn btn-primary" routerLink="/painel/pedidos/novo"><app-icon name="plus" /> Pedido manual</a>
    </div>
    <div class="toolbar" role="search">
      <div class="field grow"><label for="q">Buscar</label><input id="q" class="input" type="search" placeholder="Número, nome ou e-mail" [(ngModel)]="f.q" (ngModelChange)="list.search(true)"></div>
      <div class="field"><label for="s">Pedido</label><select id="s" class="input" [(ngModel)]="f.status" (ngModelChange)="list.search()">
        <option value="">Todos</option>@for (v of statuses; track v) { <option [value]="v">{{ v | label }}</option> }</select></div>
      <div class="field"><label for="p">Pagamento</label><select id="p" class="input" [(ngModel)]="f.paymentStatus" (ngModelChange)="list.search()">
        <option value="">Todos</option>@for (v of payments; track v) { <option [value]="v">{{ v | label }}</option> }</select></div>
      <div class="field"><label for="a">Atendimento</label><select id="a" class="input" [(ngModel)]="f.fulfillmentStatus" (ngModelChange)="list.search()">
        <option value="">Todos</option>@for (v of fulfillments; track v) { <option [value]="v">{{ v | label }}</option> }</select></div>
      <div class="field"><label for="from">De</label><input id="from" type="date" class="input" [(ngModel)]="f.from" (change)="list.search()"></div>
      <div class="field"><label for="to">Até</label><input id="to" type="date" class="input" [(ngModel)]="f.to" (change)="list.search()"></div>
      <label class="check" style="min-height:42px"><input type="checkbox" [(ngModel)]="f.attentionOnly" (ngModelChange)="list.search()"> Exigem atenção</label>
    </div>

    @if (list.firstLoad) { <app-table-skeleton [cols]="7" /> }
    @else if (list.error()) { <div class="alert danger">{{ list.error() }}</div> }
    @else if (!list.items.length) { <div class="card"><app-empty icon="receipt" title="Nenhum pedido encontrado" text="Pedidos da loja virtual e manuais aparecem aqui." /></div> }
    @else {
      <div class="table-wrap" [style.opacity]="list.loading() ? .6 : 1">
        <table class="table cards">
          <thead><tr><th>Nº</th><th>Data</th><th>Cliente</th><th class="num">Total</th><th>Pedido</th><th>Pagamento</th><th>Atendimento</th></tr></thead>
          <tbody>
            @for (o of list.items; track o.id) {
              <tr>
                <td data-label="Nº"><a class="row-link" [routerLink]="['/painel/pedidos', o.id]">#{{ o.number }}</a>
                  @if (o.requiresAttention) { <span class="badge danger" title="Exige atenção"><app-icon name="alert" [size]="12" /></span> }</td>
                <td data-label="Data">{{ o.createdAt | dt: true }}</td>
                <td data-label="Cliente">{{ o.customerName }}<br><small>{{ o.channel | label }} · {{ o.deliveryMethod | label }}</small></td>
                <td data-label="Total" class="num">{{ o.total | brl }}</td>
                <td data-label="Pedido"><span class="badge" [class]="'badge ' + (o.status | tone)">{{ o.status | label }}</span></td>
                <td data-label="Pagamento"><span class="badge" [class]="'badge ' + (o.paymentStatus | tone)">{{ o.paymentStatus | label }}</span></td>
                <td data-label="Atendimento"><span class="badge" [class]="'badge ' + (o.fulfillmentStatus | tone)">{{ o.fulfillmentStatus | label }}</span></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [page]="list.page" [pages]="list.data()?.totalPages ?? 0" [total]="list.data()?.totalElements ?? 0" (change)="list.goTo($event)" />
    }`,
})
export class OrdersPage {
  private api = inject(AdminApi);
  readonly statuses = ['OPEN', 'CONFIRMED', 'COMPLETED', 'CANCELLED'];
  readonly payments = ['PENDING', 'APPROVED', 'DECLINED', 'EXPIRED', 'PARTIALLY_REFUNDED', 'REFUNDED'];
  readonly fulfillments = ['AWAITING', 'PICKING', 'READY_FOR_PICKUP', 'SHIPPED', 'DELIVERED', 'RETURNED'];
  f = { q: '', status: '', paymentStatus: '', fulfillmentStatus: '', from: '', to: '', attentionOnly: false };
  readonly list = new PagedList<OrderListItem>(page => this.api.orders({ ...this.f, page, size: 20 }));
  constructor() { this.list.search(); }
}
