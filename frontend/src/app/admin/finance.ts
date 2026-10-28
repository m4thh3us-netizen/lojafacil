import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';
import { BrlPipe, DatePipeBr, LabelPipe, daysAgo, isoDate } from '../core/format';
import { CashFlow, Entry, EntryType, FinCategory, FinSummary } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';
import { LineChart, chartDay } from '../shared/charts';
import { Icon } from '../shared/icon';
import { PagedList } from '../shared/paged-list';
import { Empty, Modal, Pager, TableSkeleton } from '../shared/ui';

type Tab = 'entries' | 'cash' | 'summary' | 'categories';

@Component({
  selector: 'app-finance',
  imports: [FormsModule, RouterLink, BrlPipe, DatePipeBr, LabelPipe, Icon, Empty, Modal, Pager, TableSkeleton, LineChart],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div><h1>Financeiro</h1><p>Receitas, despesas, contas a pagar e a receber. Vendas e taxas entram automaticamente.</p></div>
      <button class="btn btn-primary" type="button" (click)="openNew('EXPENSE')"><app-icon name="plus" /> Novo lançamento</button>
    </div>
    <div class="tabs" role="tablist">
      @for (t of tabs; track t[0]) { <button role="tab" [class.active]="tab() === t[0]" [attr.aria-selected]="tab() === t[0]" (click)="go(t[0])">{{ t[1] }}</button> }
    </div>

    @switch (tab()) {
      @case ('entries') {
        <div class="toolbar">
          <div class="field grow"><label for="q">Buscar</label><input id="q" class="input" type="search" [(ngModel)]="f.q" (ngModelChange)="list.search(true)" placeholder="Descrição ou contraparte"></div>
          <div class="field"><label for="t">Tipo</label><select id="t" class="input" [(ngModel)]="f.type" (ngModelChange)="list.search()"><option value="">Todos</option><option value="INCOME">Receitas</option><option value="EXPENSE">Despesas</option></select></div>
          <div class="field"><label for="s">Situação</label><select id="s" class="input" [(ngModel)]="f.status" (ngModelChange)="list.search()"><option value="">Todas</option><option value="PENDING">Pendentes (a pagar/receber)</option><option value="PAID">Realizados</option><option value="CANCELLED">Cancelados</option></select></div>
          <div class="field"><label for="c">Categoria</label><select id="c" class="input" [(ngModel)]="f.categoryId" (ngModelChange)="list.search()"><option [ngValue]="null">Todas</option>@for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select></div>
          <div class="field"><label for="df">Data</label><select id="df" class="input" [(ngModel)]="f.dateField" (ngModelChange)="list.search()"><option value="due">Vencimento</option><option value="paid">Pagamento</option></select></div>
          <div class="field"><label for="fr">De</label><input id="fr" type="date" class="input" [(ngModel)]="f.from" (change)="list.search()"></div>
          <div class="field"><label for="to">Até</label><input id="to" type="date" class="input" [(ngModel)]="f.to" (change)="list.search()"></div>
          <label class="check" style="min-height:42px"><input type="checkbox" [(ngModel)]="f.overdueOnly" (ngModelChange)="list.search()"> Vencidos</label>
        </div>
        @if (list.firstLoad) { <app-table-skeleton [cols]="7" /> }
        @else if (!list.items.length) { <div class="card"><app-empty icon="wallet" title="Nenhum lançamento encontrado" text="Cadastre despesas como aluguel e compras; as vendas pagas entram sozinhas." /></div> }
        @else {
          <div class="table-wrap" [style.opacity]="list.loading() ? .6 : 1">
            <table class="table cards">
              <thead><tr><th>Vencimento</th><th>Descrição</th><th>Categoria</th><th>Origem</th><th class="num">Valor</th><th>Situação</th><th></th></tr></thead>
              <tbody>
                @for (e of list.items; track e.id) {
                  <tr [class.reversed]="e.reversed">
                    <td data-label="Vencimento">{{ e.dueDate | dt }}@if (e.paidDate) { <br><small>pago {{ e.paidDate | dt }}</small> }</td>
                    <td data-label="Descrição">{{ e.description }}@if (e.counterparty) { <br><small>{{ e.counterparty }}</small> }
                      @if (e.orderId) { <br><a [routerLink]="['/painel/pedidos', e.orderId]" class="hint">ver pedido</a> }</td>
                    <td data-label="Categoria">{{ e.categoryName }}</td>
                    <td data-label="Origem"><span class="badge">{{ e.sourceType === 'MANUAL' ? 'Manual' : (e.sourceType | label) }}</span></td>
                    <td data-label="Valor" class="num" [style.color]="e.type === 'INCOME' ? 'var(--success)' : 'var(--danger)'">{{ e.type === 'INCOME' ? '+' : '−' }}{{ e.amount | brl }}</td>
                    <td data-label="Situação">
                      @if (e.reversed) { <span class="badge">Estornado</span> }
                      @else if (e.overdue) { <span class="badge danger">Vencido</span> }
                      @else { <span class="badge" [class.success]="e.status === 'PAID'" [class.warning]="e.status === 'PENDING'">{{ e.status === 'PENDING' ? (e.type === 'INCOME' ? 'A receber' : 'A pagar') : (e.status | label) }}</span> }
                    </td>
                    <td class="num"><div class="row" style="justify-content:flex-end;gap:4px">
                      @if (e.status === 'PENDING') {
                        <button class="btn btn-sm" type="button" (click)="act(e, 'settle')">Baixar</button>
                        @if (e.sourceType === 'MANUAL') { <button class="btn btn-sm btn-ghost" type="button" (click)="act(e, 'cancel')">Cancelar</button> }
                      } @else if (e.status === 'PAID' && e.sourceType === 'MANUAL' && !e.reversed) {
                        <button class="btn btn-sm btn-ghost" type="button" (click)="act(e, 'reverse')"><app-icon name="undo" [size]="14" /> Estornar</button>
                      }
                    </div></td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <app-pager [page]="list.page" [pages]="list.data()?.totalPages ?? 0" [total]="list.data()?.totalElements ?? 0" (change)="list.goTo($event)" />
        }
      }
      @case ('cash') {
        <div class="toolbar">
          <div class="field"><label for="cf">De</label><input id="cf" type="date" class="input" [(ngModel)]="cashFrom" (change)="loadCash()"></div>
          <div class="field"><label for="ct">Até</label><input id="ct" type="date" class="input" [(ngModel)]="cashTo" (change)="loadCash()"></div>
        </div>
        <div class="card">
          @if (!cash()) { <span class="skeleton block" style="height:240px"></span> }
          @else {
            <app-line-chart [labelsIn]="cashLabels()" [series]="cashSeries()" ariaLabel="Fluxo de caixa: saldo realizado e projetado" />
            <p class="hint">Realizado: entradas e saídas já pagas. Projetado: realizado + pendentes pelo vencimento (pendentes atrasados entram no primeiro dia). Saldo inicial: {{ cash()!.openingBalance | brl }}.</p>
            <div class="table-wrap" style="margin-top:12px">
              <table class="table cards">
                <thead><tr><th>{{ cash()!.granularity === 'month' ? 'Mês' : 'Dia' }}</th><th class="num">Entradas</th><th class="num">Saídas</th><th class="num">A receber</th><th class="num">A pagar</th><th class="num">Saldo realizado</th><th class="num">Saldo projetado</th></tr></thead>
                <tbody>
                  @for (p of cashRows(); track p.date) {
                    <tr><td data-label="Data">{{ p.date | dt }}</td><td data-label="Entradas" class="num">{{ p.realizedIn | brl }}</td><td data-label="Saídas" class="num">{{ p.realizedOut | brl }}</td>
                      <td data-label="A receber" class="num">{{ p.forecastIn | brl }}</td><td data-label="A pagar" class="num">{{ p.forecastOut | brl }}</td>
                      <td data-label="Realizado" class="num">{{ p.realizedBalance | brl }}</td><td data-label="Projetado" class="num">{{ p.projectedBalance | brl }}</td></tr>
                  }
                </tbody>
              </table>
            </div>
          }
        </div>
      }
      @case ('summary') {
        <div class="toolbar">
          <div class="field"><label for="sf">De</label><input id="sf" type="date" class="input" [(ngModel)]="cashFrom" (change)="loadSummary()"></div>
          <div class="field"><label for="st">Até</label><input id="st" type="date" class="input" [(ngModel)]="cashTo" (change)="loadSummary()"></div>
        </div>
        @if (summary(); as s) {
          <div class="grid grid-3">
            @for (k of summaryRows(s); track k.label) {
              <div class="card kpi"><span class="kpi-label">{{ k.label }}</span><span class="kpi-value" [style.color]="k.color">{{ k.value | brl }}</span><span class="kpi-foot">{{ k.help }}</span></div>
            }
          </div>
          <div class="alert" style="margin-top:16px"><app-icon name="info" /> Faturamento não é saldo disponível nem lucro. O resultado estimado é uma estimativa gerencial a partir dos custos e despesas registrados, sem valor contábil ou fiscal.</div>
        } @else { <div class="grid grid-3">@for (i of [1,2,3,4,5,6]; track i) { <div class="card kpi"><span class="skeleton line"></span><span class="skeleton title"></span></div> }</div> }
      }
      @case ('categories') {
        <div class="grid grid-2">
          @for (type of ['INCOME', 'EXPENSE']; track type) {
            <section class="card">
              <h2>{{ type === 'INCOME' ? 'Receitas' : 'Despesas' }}</h2>
              <ul class="cats">
                @for (c of categoriesOf(type); track c.id) {
                  <li><span class="spacer">{{ c.name }} @if (c.systemKey) { <span class="badge info" title="Usada pelas integrações">automática</span> } @if (!c.active) { <span class="badge">inativa</span> }</span>
                    @if (!c.systemKey) { <button class="btn btn-sm btn-ghost" type="button" (click)="toggleCategory(c)">{{ c.active ? 'Desativar' : 'Ativar' }}</button> }</li>
                }
              </ul>
              <form class="row" (ngSubmit)="addCategory(type)">
                <input class="input spacer" [name]="'nc' + type" [(ngModel)]="newCat[type]" placeholder="Nova categoria" aria-label="Nome da nova categoria" maxlength="80">
                <button class="btn" type="submit">Adicionar</button>
              </form>
            </section>
          }
        </div>
      }
    }

    <app-modal title="Novo lançamento" [open]="newOpen()" (closed)="newOpen.set(false)" [busy]="busy()">
      <form id="entryForm" class="stack" (ngSubmit)="create()">
        <div class="row" role="radiogroup" aria-label="Tipo">
          <label class="check"><input type="radio" name="tp" value="EXPENSE" [(ngModel)]="entry.type" (ngModelChange)="entry.categoryId = null"> Despesa / conta a pagar</label>
          <label class="check"><input type="radio" name="tp" value="INCOME" [(ngModel)]="entry.type" (ngModelChange)="entry.categoryId = null"> Receita / conta a receber</label>
        </div>
        <div class="field"><label for="ec">Categoria *</label><select id="ec" class="input" name="cat" [(ngModel)]="entry.categoryId">
          <option [ngValue]="null">Selecione</option>@for (c of manualCategories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select></div>
        <div class="field"><label for="ed">Descrição *</label><input id="ed" class="input" name="desc" [(ngModel)]="entry.description" maxlength="200"></div>
        <div class="grid grid-3">
          <div class="field"><label for="ea">Valor total *</label><input id="ea" class="input" type="number" step="0.01" min="0.01" name="amt" [(ngModel)]="entry.totalAmount" (ngModelChange)="updatePreview()"></div>
          <div class="field"><label for="ei">Parcelas</label><input id="ei" class="input" type="number" min="1" max="60" name="inst" [(ngModel)]="entry.installments" (ngModelChange)="updatePreview()"></div>
          <div class="field"><label for="edu">{{ entry.installments > 1 ? '1º vencimento' : 'Vencimento' }} *</label><input id="edu" class="input" type="date" name="due" [(ngModel)]="entry.firstDueDate"></div>
        </div>
        @if (installmentPreview().length > 1) { <p class="hint">Parcelas mensais: {{ installmentPreview().join(' + ') }} — centavos restantes vão para as primeiras.</p> }
        @if (entry.installments <= 1) {
          <label class="check"><input type="checkbox" name="paid" [(ngModel)]="entry.paid"> Já {{ entry.type === 'INCOME' ? 'recebido' : 'pago' }}</label>
          @if (entry.paid) { <div class="field" style="max-width:200px"><label for="ep">Data do pagamento</label><input id="ep" class="input" type="date" name="pd" [(ngModel)]="entry.paidDate"></div> }
        }
        <div class="field"><label for="eo">{{ entry.type === 'INCOME' ? 'Pagador' : 'Favorecido' }}</label><input id="eo" class="input" name="cp" [(ngModel)]="entry.counterparty" maxlength="160"></div>
        <div class="field"><label for="en">Observações</label><input id="en" class="input" name="nt" [(ngModel)]="entry.notes" maxlength="500"></div>
        @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
      </form>
      <div footer><button class="btn" type="button" (click)="newOpen.set(false)">Cancelar</button>
        <button class="btn btn-primary" type="submit" form="entryForm" [disabled]="busy()">{{ busy() ? 'Salvando...' : 'Salvar' }}</button></div>
    </app-modal>

    <app-modal [title]="actionTitle()" [open]="!!acting()" (closed)="acting.set(null)" [busy]="busy()">
      @if (acting(); as a) {
        <p><b>{{ a.entry.description }}</b> — {{ a.entry.amount | brl }}</p>
        <form id="actForm" class="stack" (ngSubmit)="confirmAct()">
          @if (a.kind === 'settle') { <div class="field" style="max-width:200px"><label for="sd">Data do pagamento</label><input id="sd" class="input" type="date" name="sd" [(ngModel)]="actDate" [max]="today"></div> }
          @else {
            <p class="hint">@if (a.kind === 'reverse') { Será criado um lançamento de estorno vinculado; o original continua no histórico. } @else { O lançamento pendente será cancelado (fica registrado). }</p>
            <div class="field"><label for="ar">Motivo *</label><input id="ar" class="input" name="ar" [(ngModel)]="actReason" maxlength="300"></div>
          }
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
        </form>
      }
      <div footer><button class="btn" type="button" (click)="acting.set(null)">Voltar</button>
        <button class="btn btn-primary" type="submit" form="actForm" [disabled]="busy()">{{ busy() ? 'Processando...' : 'Confirmar' }}</button></div>
    </app-modal>`,
  styles: [`
    tr.reversed td { opacity: .6; }
    .cats { list-style: none; padding: 0; margin: 0 0 12px; }
    .cats li { display: flex; align-items: center; gap: 6px; padding: 8px 0; border-bottom: 1px solid var(--border); }
  `],
})
export class FinancePage {
  private api = inject(AdminApi);
  private toast = inject(ToastService);
  readonly tabs: [Tab, string][] = [['entries', 'Lançamentos'], ['cash', 'Fluxo de caixa'], ['summary', 'Resumo do período'], ['categories', 'Categorias']];
  readonly tab = signal<Tab>('entries');
  readonly categories = signal<FinCategory[]>([]);
  readonly cash = signal<CashFlow | null>(null);
  readonly summary = signal<FinSummary | null>(null);
  readonly busy = signal(false);
  readonly error = signal('');
  readonly newOpen = signal(false);
  readonly acting = signal<{ entry: Entry; kind: 'settle' | 'cancel' | 'reverse' } | null>(null);
  readonly today = isoDate(new Date());
  f = { q: '', type: '', status: '', categoryId: null as number | null, dateField: 'due', from: '', to: '', overdueOnly: false };
  cashFrom = daysAgo(29); cashTo = daysAgo(-30);
  actDate = this.today; actReason = '';
  newCat: Record<string, string> = { INCOME: '', EXPENSE: '' };
  entry = this.blankEntry('EXPENSE');

  readonly list = new PagedList<Entry>(page => this.api.entries({ ...this.f, page, size: 30 }));

  readonly cashRows = computed(() => (this.cash()?.points ?? []).filter(p => p.realizedIn || p.realizedOut || p.forecastIn || p.forecastOut));
  readonly cashLabels = computed(() => (this.cash()?.points ?? []).map(p => chartDay(p.date)));
  readonly cashSeries = computed(() => {
    const pts = this.cash()?.points ?? [];
    return [
      { name: 'Saldo realizado', color: 'var(--series-1)', values: pts.map(p => p.realizedBalance) },
      { name: 'Saldo projetado', color: 'var(--series-2)', dashed: true, values: pts.map(p => p.projectedBalance) },
    ];
  });
  readonly actionTitle = computed(() => ({ settle: 'Dar baixa', cancel: 'Cancelar lançamento', reverse: 'Estornar lançamento' })[this.acting()?.kind ?? 'settle']);
  readonly installmentPreview = signal<string[]>([]);

  constructor() {
    this.list.search();
    this.loadCategories();
  }

  manualCategories() { return this.categories().filter(c => c.type === this.entry.type && !c.systemKey && c.active); }
  categoriesOf(type: string) { return this.categories().filter(c => c.type === type); }

  go(t: Tab) {
    this.tab.set(t);
    if (t === 'cash' && !this.cash()) this.loadCash();
    if (t === 'summary') this.loadSummary();
  }

  loadCategories() { this.api.finCategories().subscribe(c => this.categories.set(c)); }
  loadCash() { if (this.cashFrom && this.cashTo) this.api.cashFlow(this.cashFrom, this.cashTo).subscribe({ next: c => this.cash.set(c), error: e => this.toast.error(e) }); }
  loadSummary() { this.summary.set(null); this.api.finSummary(this.cashFrom, this.cashTo).subscribe({ next: s => this.summary.set(s), error: e => this.toast.error(e) }); }

  summaryRows(s: FinSummary) {
    return [
      { label: 'Faturamento por vendas', value: s.salesRevenue, help: `${s.salesCount} pedidos confirmados no período` },
      { label: 'Recebimentos efetivos', value: s.receipts, help: 'Entradas realizadas no período' },
      { label: 'A receber (em aberto)', value: s.receivablesOpen, help: `${this.fmt(s.receivablesOverdue)} vencidos` },
      { label: 'A pagar (em aberto)', value: s.payablesOpen, help: `${this.fmt(s.payablesOverdue)} vencidos` },
      { label: 'Despesas operacionais', value: s.operatingExpenses, help: 'Por vencimento no período (competência)' },
      { label: 'Despesas pagas', value: s.expensesPaid, help: 'Saídas manuais realizadas no período' },
      { label: 'Custo das mercadorias vendidas', value: s.costOfGoodsSold, help: 'Custo registrado nos itens vendidos' },
      { label: 'Taxas e reembolsos', value: s.fees + s.refunds, help: `Taxas ${this.fmt(s.fees)} · reembolsos ${this.fmt(s.refunds)}` },
      { label: 'Resultado gerencial estimado', value: s.estimatedResult, help: 'Faturamento − CMV − taxas − reembolsos − despesas', color: s.estimatedResult < 0 ? 'var(--danger)' : 'var(--success)' },
    ];
  }

  private fmt(v: number) { return v.toLocaleString('pt-BR', { style: 'currency', currency: 'BRL' }); }

  private blankEntry(type: EntryType) {
    return { type, categoryId: null as number | null, description: '', totalAmount: null as number | null, installments: 1, firstDueDate: isoDate(new Date()), paid: false, paidDate: isoDate(new Date()), counterparty: '', notes: '' };
  }

  openNew(type: EntryType) { this.entry = this.blankEntry(type); this.error.set(''); this.installmentPreview.set([]); this.newOpen.set(true); }

  /** Prévia das parcelas (o cálculo oficial é feito no servidor com BigDecimal, com a mesma regra). */
  updatePreview() {
    const total = Math.round((this.entry.totalAmount ?? 0) * 100);
    const n = Math.max(1, Math.min(60, Math.floor(this.entry.installments || 1)));
    if (total <= 0 || n <= 1) { this.installmentPreview.set([]); return; }
    const base = Math.floor(total / n), rem = total % n;
    const parts = Array.from({ length: n }, (_, i) => ((base + (i < rem ? 1 : 0)) / 100).toLocaleString('pt-BR', { minimumFractionDigits: 2 }));
    this.installmentPreview.set(n > 4 ? [...parts.slice(0, 3), `… (${n}x)`] : parts);
  }

  create() {
    const e = this.entry;
    if (!e.categoryId || !e.description.trim() || !e.totalAmount || e.totalAmount <= 0 || !e.firstDueDate) { this.error.set('Preencha categoria, descrição, valor e vencimento.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.api.createEntry({ ...e, paid: e.installments <= 1 && e.paid, paidDate: e.paid ? e.paidDate : null }).subscribe({
      next: r => { this.busy.set(false); this.newOpen.set(false); this.toast.success(r.length > 1 ? `${r.length} parcelas criadas.` : 'Lançamento criado.'); this.list.refresh(); },
      error: err => { this.busy.set(false); this.error.set(errorMessage(err)); },
    });
  }

  act(entry: Entry, kind: 'settle' | 'cancel' | 'reverse') { this.actDate = this.today; this.actReason = ''; this.error.set(''); this.acting.set({ entry, kind }); }

  confirmAct() {
    const a = this.acting();
    if (!a || this.busy()) return;
    if (a.kind !== 'settle' && !this.actReason.trim()) { this.error.set('Informe o motivo.'); return; }
    this.busy.set(true);
    const body = a.kind === 'settle' ? { paidDate: this.actDate } : { reason: this.actReason };
    this.api.entryAction(a.entry.id, a.kind, body).subscribe({
      next: () => { this.busy.set(false); this.acting.set(null); this.toast.success('Lançamento atualizado.'); this.list.refresh(); },
      error: err => { this.busy.set(false); this.error.set(errorMessage(err)); },
    });
  }

  addCategory(type: string) {
    const name = this.newCat[type]?.trim();
    if (!name) return;
    this.api.saveFinCategory(null, { name, type, active: true }).subscribe({
      next: () => { this.newCat[type] = ''; this.loadCategories(); this.toast.success('Categoria criada.'); }, error: e => this.toast.error(e),
    });
  }

  toggleCategory(c: FinCategory) {
    this.api.saveFinCategory(c.id, { name: c.name, type: c.type, active: !c.active }).subscribe({ next: () => this.loadCategories(), error: e => this.toast.error(e) });
  }
}
