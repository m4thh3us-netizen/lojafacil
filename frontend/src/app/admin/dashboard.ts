import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { BrlPipe, daysAgo, isoDate } from '../core/format';
import { Dashboard } from '../core/models';
import { ToastService } from '../core/ui.services';
import { BarChart, LineChart, chartDay } from '../shared/charts';
import { Icon } from '../shared/icon';
import { Empty } from '../shared/ui';

@Component({
  selector: 'app-dashboard',
  imports: [FormsModule, RouterLink, BrlPipe, BarChart, LineChart, Icon, Empty],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div>
        <h1>Olá, {{ firstName() }}</h1>
        <p>Indicadores calculados a partir dos dados registrados no período escolhido.</p>
      </div>
      <div class="row" role="group" aria-label="Período">
        @for (p of presets; track p.days) {
          <button type="button" class="btn btn-sm" [class.btn-primary]="preset() === p.days" (click)="usePreset(p.days)">{{ p.label }}</button>
        }
        <input type="date" class="input period" [(ngModel)]="from" aria-label="Data inicial" (change)="custom()">
        <input type="date" class="input period" [(ngModel)]="to" aria-label="Data final" (change)="custom()">
      </div>
    </div>

    @if (d()?.demo) {
      <div class="alert warning" style="margin-bottom:16px"><app-icon name="info" /> Exemplo: estes números vêm dos dados fictícios da loja demonstrativa.</div>
    }

    <section class="grid kpis" aria-label="Indicadores">
      @if (loading() && !d()) {
        @for (i of [1,2,3,4]; track i) { <div class="card kpi"><span class="skeleton line" style="width:50%"></span><span class="skeleton title"></span><span class="skeleton line" style="width:70%"></span></div> }
      } @else if (d(); as d) {
        @if (d.financialAccess && d.summary; as s) {
          <div class="card kpi" [title]="d.definitions['salesRevenue']">
            <span class="kpi-label"><app-icon name="chart" [size]="15" /> Faturamento por vendas</span>
            <span class="kpi-value">{{ s.salesRevenue | brl }}</span>
            <span class="kpi-foot">{{ s.salesCount }} pedido(s) confirmados · não é saldo nem lucro</span>
          </div>
          <div class="card kpi" [title]="d.definitions['receipts']">
            <span class="kpi-label"><app-icon name="wallet" [size]="15" /> Recebimentos efetivos</span>
            <span class="kpi-value">{{ s.receipts | brl }}</span>
            <span class="kpi-foot">entradas realizadas no período</span>
          </div>
          <div class="card kpi" [title]="d.definitions['receivablesOpen']">
            <span class="kpi-label"><app-icon name="clock" [size]="15" /> A receber</span>
            <span class="kpi-value">{{ s.receivablesOpen | brl }}</span>
            <span class="kpi-foot">@if (s.receivablesOverdue > 0) { <span style="color:var(--danger)">{{ s.receivablesOverdue | brl }} em atraso</span> } @else { contas pendentes, todos os vencimentos }</span>
          </div>
          <div class="card kpi" [title]="d.definitions['estimatedResult']">
            <span class="kpi-label"><app-icon name="info" [size]="15" /> Resultado estimado</span>
            <span class="kpi-value" [style.color]="s.estimatedResult < 0 ? 'var(--danger)' : 'var(--success)'">{{ s.estimatedResult | brl }}</span>
            <span class="kpi-foot">estimativa gerencial · despesas {{ s.operatingExpenses | brl }}</span>
          </div>
        } @else {
          <div class="card kpi"><span class="kpi-label">Pedidos confirmados</span><span class="kpi-value">{{ d.salesCount }}</span><span class="kpi-foot">no período</span></div>
        }
        <div class="card kpi" [title]="d.definitions['pendingOrders']">
          <span class="kpi-label"><app-icon name="receipt" [size]="15" /> Pedidos pendentes</span>
          <span class="kpi-value">{{ d.pendingOrders }}</span>
          <span class="kpi-foot"><a routerLink="/painel/pedidos">ver pedidos</a>@if (d.attentionOrders) { · <span style="color:var(--danger)">{{ d.attentionOrders }} exigem atenção</span> }</span>
        </div>
        @if (!d.financialAccess) {
          <div class="card kpi"><span class="kpi-label">Estoque baixo</span><span class="kpi-value">{{ d.lowStock.length }}</span><span class="kpi-foot"><a routerLink="/painel/estoque">ver estoque</a></span></div>
        }
      }
    </section>

    @if (d()?.financialAccess && d()?.summary; as s) {
      <section class="grid grid-4 secondary" aria-label="Detalhes financeiros">
        <div class="card card-tight"><small>Despesas pagas</small><b>{{ s.expensesPaid | brl }}</b></div>
        <div class="card card-tight"><small>Custo das mercadorias vendidas</small><b>{{ s.costOfGoodsSold | brl }}</b></div>
        <div class="card card-tight"><small>Taxas de pagamento</small><b>{{ s.fees | brl }}</b></div>
        <div class="card card-tight"><small>Reembolsos</small><b>{{ s.refunds | brl }}</b></div>
      </section>
    }

    <section class="grid grid-2" style="margin-top:16px">
      <div class="card">
        <h2>Vendas por dia</h2>
        @if (loading() && !d()) { <span class="skeleton block" style="height:220px"></span> }
        @else if (salesBars().length) {
          <app-bar-chart [data]="salesBars()" [money]="!!d()?.financialAccess" [ariaLabel]="d()?.financialAccess ? 'Faturamento diário' : 'Pedidos por dia'" />
        }
      </div>
      <div class="card">
        <h2>Fluxo de caixa</h2>
        @if (loading() && !d()) { <span class="skeleton block" style="height:220px"></span> }
        @else if (d()?.cashFlow; as cf) {
          <app-line-chart [labelsIn]="cashLabels()" [series]="cashSeries()" ariaLabel="Saldo realizado e projetado" />
          <p class="hint">Projetado = realizado + contas pendentes até a data. Saldo inicial: {{ cf.openingBalance | brl }}.</p>
        } @else {
          <app-empty icon="wallet" title="Disponível para proprietário e gerente" />
        }
      </div>
    </section>

    <section class="grid grid-2" style="margin-top:16px">
      <div class="card">
        <div class="row-between"><h2>Mais vendidos</h2><small>quantidade no período</small></div>
        @if (loading() && !d()) { @for (i of [1,2,3]; track i) { <span class="skeleton line"></span> } }
        @else if (d()?.topProducts?.length) {
          <ol class="rank">
            @for (t of d()!.topProducts; track t.productId; let i = $index) {
              <li><span class="pos">{{ i + 1 }}</span><span class="spacer">{{ t.name }}</span><b>{{ t.quantity }} un.</b>
                @if (t.revenue !== null && t.revenue !== undefined) { <small>{{ t.revenue | brl }}</small> }</li>
            }
          </ol>
        } @else { <app-empty icon="chart" title="Sem vendas no período" text="Os produtos mais vendidos aparecem aqui." /> }
      </div>
      <div class="card">
        <div class="row-between"><h2>Estoque baixo</h2><a routerLink="/painel/estoque" class="hint">ver tudo</a></div>
        @if (loading() && !d()) { @for (i of [1,2,3]; track i) { <span class="skeleton line"></span> } }
        @else if (d()?.lowStock?.length) {
          <ul class="rank">
            @for (s of d()!.lowStock; track s.variantId) {
              <li><app-icon name="alert" [size]="15" /><span class="spacer">{{ s.productName }}@if (s.variantName) { <small> · {{ s.variantName }}</small> }</span>
                <span class="badge" [class.danger]="s.availableQty <= 0" [class.warning]="s.availableQty > 0">{{ s.availableQty }} disp. / mín. {{ s.minStock }}</span></li>
            }
          </ul>
        } @else { <app-empty icon="check" title="Estoque em dia" text="Nenhuma variante abaixo do mínimo." /> }
      </div>
    </section>`,
  styles: [`
    .period { width: 150px; min-height: 32px; padding: 4px 8px; font-size: .85rem; }
    .secondary { margin-top: 12px; }
    .secondary .card { display: flex; flex-direction: column; }
    .secondary b { font-variant-numeric: tabular-nums; }
    .rank { list-style: none; padding: 0; margin: 0; }
    .rank li { display: flex; align-items: center; gap: 10px; padding: 9px 0; border-bottom: 1px solid var(--border); }
    .rank li:last-child { border-bottom: 0; }
    .pos { width: 24px; height: 24px; border-radius: 8px; background: var(--primary-soft); color: var(--primary); display: grid; place-items: center; font-size: .78rem; font-weight: 700; }
  `],
})
export class DashboardPage {
  private api = inject(AdminApi);
  private auth = inject(AuthService);
  private toast = inject(ToastService);
  readonly presets = [{ days: 7, label: '7 dias' }, { days: 30, label: '30 dias' }, { days: 90, label: '90 dias' }];
  readonly preset = signal<number | null>(30);
  readonly d = signal<Dashboard | null>(null);
  readonly loading = signal(false);
  from = daysAgo(29);
  to = isoDate(new Date());

  readonly firstName = computed(() => this.auth.me()?.user.fullName.split(' ')[0] ?? '');
  readonly salesBars = computed(() => (this.d()?.sales ?? []).map(s => ({
    label: chartDay(s.date), value: s.revenue ?? s.orders, extra: `${s.orders} pedido(s)`,
  })));
  readonly cashLabels = computed(() => (this.d()?.cashFlow?.points ?? []).map(p => chartDay(p.date)));
  readonly cashSeries = computed(() => {
    const pts = this.d()?.cashFlow?.points ?? [];
    return [
      { name: 'Saldo realizado', color: 'var(--series-1)', values: pts.map(p => p.realizedBalance) },
      { name: 'Saldo projetado', color: 'var(--series-2)', dashed: true, values: pts.map(p => p.projectedBalance) },
    ];
  });

  constructor() { this.load(); }

  usePreset(days: number) {
    this.preset.set(days);
    this.from = daysAgo(days - 1);
    this.to = isoDate(new Date());
    this.load();
  }

  custom() {
    if (this.from && this.to && this.from <= this.to) { this.preset.set(null); this.load(); }
  }

  load() {
    this.loading.set(true);
    this.api.dashboard(this.from, this.to).subscribe({
      next: d => { this.d.set(d); this.loading.set(false); },
      error: e => { this.loading.set(false); this.toast.error(e); },
    });
  }
}
