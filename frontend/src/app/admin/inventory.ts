import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';
import { DatePipeBr, LabelPipe } from '../core/format';
import { MovementView, StockView, Supplier } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';

import { PagedList } from '../shared/paged-list';
import { Empty, Modal, Pager, TableSkeleton } from '../shared/ui';

type MoveType = 'ENTRY' | 'EXIT' | 'ADJUSTMENT';

@Component({
  selector: 'app-inventory',
  imports: [FormsModule, RouterLink, DatePipeBr, LabelPipe, Empty, Modal, Pager, TableSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div><h1>Estoque</h1><p>Disponível = físico − reservado para pedidos aguardando pagamento.</p></div>
    </div>
    <div class="tabs" role="tablist">
      <button role="tab" [class.active]="tab() === 'stock'" [attr.aria-selected]="tab() === 'stock'" (click)="tab.set('stock')">Saldos</button>
      <button role="tab" [class.active]="tab() === 'moves'" [attr.aria-selected]="tab() === 'moves'" (click)="openMoves()">Histórico de movimentações</button>
    </div>

    @if (tab() === 'stock') {
      <div class="toolbar">
        <div class="field grow"><label for="q">Buscar</label><input id="q" class="input" type="search" placeholder="Produto ou SKU" [(ngModel)]="q" (ngModelChange)="stock.search(true)"></div>
        <label class="check" style="min-height:42px"><input type="checkbox" [(ngModel)]="lowOnly" (ngModelChange)="stock.search()"> Apenas estoque baixo</label>
      </div>
      @if (stock.firstLoad) { <app-table-skeleton [cols]="6" /> }
      @else if (!stock.items.length) { <div class="card"><app-empty icon="layers" title="Nada por aqui" [text]="lowOnly ? 'Nenhum item abaixo do mínimo.' : 'Cadastre produtos para controlar o estoque.'" /></div> }
      @else {
        <div class="table-wrap" [style.opacity]="stock.loading() ? .6 : 1">
          <table class="table cards">
            <thead><tr><th>Produto</th><th>SKU</th><th class="num">Físico</th><th class="num">Reservado</th><th class="num">Disponível</th><th></th></tr></thead>
            <tbody>
              @for (s of stock.items; track s.variantId) {
                <tr>
                  <td data-label="Produto"><a class="row-link" [routerLink]="['/painel/produtos', s.productId]">{{ s.productName }}</a>@if (s.variantName) { <small> · {{ s.variantName }}</small> }</td>
                  <td data-label="SKU"><code>{{ s.sku }}</code></td>
                  <td data-label="Físico" class="num">{{ s.physicalQty }}</td>
                  <td data-label="Reservado" class="num">{{ s.reservedQty }}</td>
                  <td data-label="Disponível" class="num"><span class="badge" [class.danger]="s.availableQty <= 0" [class.warning]="s.lowStock && s.availableQty > 0">{{ s.availableQty }}</span>
                    @if (s.lowStock) { <small> mín. {{ s.minStock }}</small> }</td>
                  <td class="num"><button class="btn btn-sm" type="button" (click)="startMove(s)">Movimentar</button></td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pager [page]="stock.page" [pages]="stock.data()?.totalPages ?? 0" [total]="stock.data()?.totalElements ?? 0" (change)="stock.goTo($event)" />
      }
    } @else {
      <div class="toolbar">
        <div class="field"><label for="t">Tipo</label><select id="t" class="input" [(ngModel)]="type" (ngModelChange)="moves.search()">
          <option value="">Todos</option>@for (t of types; track t) { <option [value]="t">{{ t | label }}</option> }</select></div>
        <div class="field"><label for="f">De</label><input id="f" type="date" class="input" [(ngModel)]="from" (change)="moves.search()"></div>
        <div class="field"><label for="to">Até</label><input id="to" type="date" class="input" [(ngModel)]="to" (change)="moves.search()"></div>
      </div>
      @if (moves.firstLoad) { <app-table-skeleton [cols]="6" /> }
      @else if (!moves.items.length) { <div class="card"><app-empty icon="history" title="Sem movimentações no período" /></div> }
      @else {
        <div class="table-wrap" [style.opacity]="moves.loading() ? .6 : 1">
          <table class="table cards">
            <thead><tr><th>Data</th><th>Produto</th><th>Tipo</th><th class="num">Qtd.</th><th class="num">Saldo</th><th>Motivo</th><th>Usuário</th></tr></thead>
            <tbody>
              @for (m of moves.items; track m.id) {
                <tr>
                  <td data-label="Data">{{ m.createdAt | dt: true }}</td>
                  <td data-label="Produto">{{ m.productName }}@if (m.variantName) { <small> · {{ m.variantName }}</small> }</td>
                  <td data-label="Tipo"><span class="badge" [class.success]="m.quantity > 0" [class.danger]="m.quantity < 0">{{ m.type | label }}</span></td>
                  <td data-label="Qtd." class="num">{{ m.quantity > 0 ? '+' : '' }}{{ m.quantity }}</td>
                  <td data-label="Saldo" class="num">{{ m.balanceAfter }}</td>
                  <td data-label="Motivo">{{ m.reason }}@if (m.orderId) { · <a [routerLink]="['/painel/pedidos', m.orderId]">pedido</a> }</td>
                  <td data-label="Usuário">{{ m.userName }}</td>
                </tr>
              }
            </tbody>
          </table>
        </div>
        <app-pager [page]="moves.page" [pages]="moves.data()?.totalPages ?? 0" [total]="moves.data()?.totalElements ?? 0" (change)="moves.goTo($event)" />
      }
    }

    <app-modal [title]="'Movimentar: ' + (selected()?.productName ?? '')" [open]="!!selected()" (closed)="selected.set(null)" [busy]="busy()">
      @if (selected(); as s) {
        <p class="hint">{{ s.sku }}@if (s.variantName) { · {{ s.variantName }} } — físico {{ s.physicalQty }}, reservado {{ s.reservedQty }}, disponível {{ s.availableQty }}</p>
        <form class="stack" (ngSubmit)="submitMove()" id="moveForm">
          <fieldset><legend class="label">Tipo</legend>
            <div class="row">
              @for (t of manualTypes; track t[0]) { <label class="check"><input type="radio" name="mt" [value]="t[0]" [(ngModel)]="moveType"> {{ t[1] }}</label> }
            </div>
          </fieldset>
          <div class="field"><label for="qty">{{ moveType === 'ADJUSTMENT' ? 'Novo saldo físico (contagem)' : 'Quantidade' }}</label>
            <input id="qty" class="input" type="number" min="0" name="qty" [(ngModel)]="qty" required></div>
          @if (moveType === 'ENTRY') {
            <div class="field"><label for="sup">Fornecedor (opcional)</label><select id="sup" class="input" name="sup" [(ngModel)]="supplierId">
              <option [ngValue]="null">—</option>@for (f of suppliers(); track f.id) { <option [ngValue]="f.id">{{ f.name }}</option> }</select></div>
          }
          <div class="field"><label for="rs">Motivo / justificativa *</label><input id="rs" class="input" name="reason" [(ngModel)]="reason" required maxlength="300"
            [placeholder]="moveType === 'ENTRY' ? 'Ex.: reposição - NF 123' : moveType === 'EXIT' ? 'Ex.: avaria, uso interno' : 'Ex.: contagem de inventário'"></div>
          @if (moveError()) { <div class="alert danger" role="alert">{{ moveError() }}</div> }
        </form>
      }
      <div footer>
        <button class="btn" type="button" (click)="selected.set(null)" [disabled]="busy()">Cancelar</button>
        <button class="btn btn-primary" type="submit" form="moveForm" [disabled]="busy()">{{ busy() ? 'Salvando...' : 'Registrar' }}</button>
      </div>
    </app-modal>`,
})
export class InventoryPage {
  private api = inject(AdminApi);
  private toast = inject(ToastService);
  readonly tab = signal<'stock' | 'moves'>('stock');
  readonly types = ['ENTRY', 'EXIT', 'ADJUSTMENT', 'SALE', 'RETURN'];
  readonly manualTypes: [MoveType, string][] = [['ENTRY', 'Entrada'], ['EXIT', 'Saída'], ['ADJUSTMENT', 'Ajuste']];
  q = ''; lowOnly = false; type = ''; from = ''; to = '';
  readonly stock = new PagedList<StockView>(page => this.api.stock({ q: this.q, lowOnly: this.lowOnly, page, size: 20 }));
  readonly moves = new PagedList<MovementView>(page => this.api.movements({ type: this.type, from: this.from, to: this.to, page, size: 30 }));
  readonly suppliers = signal<Supplier[]>([]);

  readonly selected = signal<StockView | null>(null);
  readonly busy = signal(false);
  readonly moveError = signal('');
  moveType: MoveType = 'ENTRY';
  qty: number | null = null;
  reason = '';
  supplierId: number | null = null;
  private movesLoaded = false;

  constructor() { this.stock.search(); }

  openMoves() {
    this.tab.set('moves');
    if (!this.movesLoaded) { this.movesLoaded = true; this.moves.search(); }
  }

  startMove(s: StockView) {
    this.moveType = 'ENTRY'; this.qty = null; this.reason = ''; this.supplierId = null; this.moveError.set('');
    if (!this.suppliers().length) this.api.activeSuppliers().subscribe(l => this.suppliers.set(l));
    this.selected.set(s);
  }

  submitMove() {
    const s = this.selected();
    if (!s || this.busy()) return;
    if (this.qty === null || this.qty < 0 || (this.moveType !== 'ADJUSTMENT' && this.qty <= 0)) { this.moveError.set('Informe uma quantidade válida.'); return; }
    if (this.reason.trim().length < 3) { this.moveError.set('Descreva o motivo da movimentação.'); return; }
    this.busy.set(true);
    this.api.move({ variantId: s.variantId, type: this.moveType, quantity: this.qty, reason: this.reason.trim(), supplierId: this.supplierId }).subscribe({
      next: () => { this.busy.set(false); this.selected.set(null); this.toast.success('Movimentação registrada.'); this.stock.refresh(); if (this.movesLoaded) this.moves.refresh(); },
      error: e => { this.busy.set(false); this.moveError.set(errorMessage(e)); },
    });
  }
}
