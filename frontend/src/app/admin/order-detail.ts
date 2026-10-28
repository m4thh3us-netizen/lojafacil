import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { Observable } from 'rxjs';

import { AdminApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { BrlPipe, DatePipeBr, LABELS, LabelPipe, TonePipe } from '../core/format';
import { FulfillmentStatus, OrderDetail } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { Modal } from '../shared/ui';

type ActionKind = 'cancel' | 'refund' | 'return' | 'manualPayment' | 'note' | 'receive' | 'resolve' | null;

@Component({
  selector: 'app-order-detail',
  imports: [FormsModule, RouterLink, BrlPipe, DatePipeBr, LabelPipe, TonePipe, Icon, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/painel/pedidos" class="back"><app-icon name="arrowLeft" [size]="14" /> Pedidos</a>
    @if (notFound()) {
      <div class="card" style="margin-top:12px"><h2>Pedido não encontrado</h2><p class="text-2">Ele pode não existir ou pertencer a outra loja.</p></div>
    } @else if (!o()) {
      <div class="card" style="margin-top:12px"><span class="skeleton title"></span>@for (i of [1,2,3,4,5,6]; track i) { <span class="skeleton line"></span> }</div>
    } @else {
      @let order = o()!;
      <div class="page-header" style="margin-top:6px">
        <div>
          <h1>Pedido #{{ order.number }}</h1>
          <div class="row">
            <span [class]="'badge ' + (order.status | tone)">Pedido: {{ order.status | label }}</span>
            <span [class]="'badge ' + (order.paymentStatus | tone)">Pagamento: {{ order.paymentStatus | label }}</span>
            <span [class]="'badge ' + (order.fulfillmentStatus | tone)">Atendimento: {{ order.fulfillmentStatus | label }}</span>
            @if (simulated()) { <span class="badge sim">PAGAMENTO SIMULADO</span> }
          </div>
        </div>
        <div class="row">
          @for (f of order.allowedFulfillment; track f) {
            <button class="btn btn-primary" type="button" (click)="advance(f)" [disabled]="busy()">{{ busy() === 'f-' + f ? 'Salvando...' : nextLabel(f) }}</button>
          }
          @if (canManualPay()) { <button class="btn btn-primary" type="button" (click)="open('manualPayment')"><app-icon name="wallet" /> Registrar recebimento</button> }
          @if (onlinePending()) { <button class="btn" type="button" (click)="sync()" [disabled]="!!busy()"><app-icon name="refresh" /> {{ busy() === 'sync' ? 'Consultando...' : 'Consultar pagamento' }}</button> }
          <button class="btn" type="button" (click)="open('note')"><app-icon name="edit" /> Observação</button>
          @if (canReturn()) { <button class="btn" type="button" (click)="open('return')"><app-icon name="undo" /> Devolução</button> }
          @if (auth.isManagement() && canRefund()) { <button class="btn" type="button" (click)="open('refund')">Reembolsar</button> }
          @if (auth.isManagement() && canCancel()) { <button class="btn btn-ghost" type="button" (click)="open('cancel')" style="color:var(--danger)">Cancelar pedido</button> }
        </div>
      </div>

      @if (order.requiresAttention) {
        <div class="alert danger" role="alert" style="margin-bottom:16px">
          <app-icon name="alert" /><div class="spacer"><b>Exige atenção:</b> {{ order.attentionReason }}</div>
          <button class="btn btn-sm" type="button" (click)="open('resolve')">Marcar como resolvido</button>
        </div>
      }
      @if (order.status === 'OPEN' && order.reservationExpiresAt && !paid()) {
        <div class="alert warning" style="margin-bottom:16px"><app-icon name="clock" /> Estoque reservado até {{ order.reservationExpiresAt | dt: true }}. Sem pagamento até lá, o pedido é cancelado e o estoque liberado.</div>
      }

      <div class="layout">
        <div class="stack">
          <section class="card">
            <h2>Itens</h2>
            <div class="table-wrap">
              <table class="table cards">
                <thead><tr><th>Produto</th><th class="num">Qtd.</th><th class="num">Unitário</th><th class="num">Desconto</th><th class="num">Custo</th><th class="num">Total</th></tr></thead>
                <tbody>
                  @for (i of order.items; track i.id) {
                    <tr>
                      <td data-label="Produto"><a [routerLink]="['/painel/produtos', i.productId]">{{ i.productName }}</a>@if (i.variantName) { <small> · {{ i.variantName }}</small> }<br><small>{{ i.sku }}</small></td>
                      <td data-label="Qtd." class="num">{{ i.quantity }}</td>
                      <td data-label="Unitário" class="num">{{ i.unitPrice | brl }}</td>
                      <td data-label="Desconto" class="num">{{ i.unitDiscount > 0 ? '−' : '' }}{{ i.unitDiscount | brl }}</td>
                      <td data-label="Custo" class="num muted">{{ i.unitCost | brl }}</td>
                      <td data-label="Total" class="num"><b>{{ i.lineTotal | brl }}</b></td>
                    </tr>
                  }
                </tbody>
              </table>
            </div>
            <dl class="totals">
              <dt>Subtotal</dt><dd>{{ order.subtotal | brl }}</dd>
              <dt>Descontos</dt><dd>−{{ order.discountTotal | brl }}</dd>
              <dt>Entrega</dt><dd>{{ order.deliveryFee | brl }}</dd>
              <dt class="t">Total</dt><dd class="t">{{ order.total | brl }}</dd>
            </dl>
            <p class="hint">Preços, descontos e custos são uma cópia do momento da venda: alterar o produto depois não muda este pedido.</p>
          </section>

          <section class="card">
            <h2>Pagamentos</h2>
            @for (p of order.payments; track p.id) {
              <div class="pay">
                <div><b>{{ p.provider | label }}</b> @if (p.simulated) { <span class="badge sim">SIMULADO</span> } <span [class]="'badge ' + (p.status | tone)">{{ p.status | label }}</span>
                  <br><small>{{ p.method || 'forma não informada' }} · atualizado {{ p.updatedAt | dt: true }}</small></div>
                <div class="num-col"><b>{{ p.amount | brl }}</b>
                  @if (p.refundedAmount > 0) { <small>reembolsado {{ p.refundedAmount | brl }}</small> }
                  @if (p.feeAmount > 0) { <small>taxa {{ p.feeAmount | brl }}</small> }</div>
              </div>
            } @empty { <p class="muted">Nenhum pagamento registrado.</p> }
          </section>

          @if (order.returns.length) {
            <section class="card">
              <h2>Devoluções</h2>
              @for (r of order.returns; track r.id) {
                <div class="pay">
                  <div><span [class]="'badge ' + (r.status | tone)">{{ r.status | label }}</span> {{ r.reason }}
                    <br><small>{{ r.createdAt | dt: true }} · {{ itemsOf(r.items) }}
                      @if (r.status === 'RECEIVED') { · {{ r.restocked ? 'devolvido ao estoque' : 'sem condição de revenda' }} }</small></div>
                  @if (r.status === 'AWAITING_RECEIPT') { <button class="btn btn-sm" type="button" (click)="openReceive(r.id)">Confirmar recebimento</button> }
                </div>
              }
            </section>
          }
        </div>

        <aside class="stack">
          <section class="card">
            <h2>Cliente</h2>
            <p>@if (order.customerId) { <a [routerLink]="['/painel/clientes', order.customerId]">{{ order.customerName }}</a> } @else { {{ order.customerName }} }<br>
              <small>{{ order.customerEmail }}<br>{{ order.customerPhone }}</small></p>
            <h3>{{ order.deliveryMethod | label }}</h3>
            @if (order.deliveryAddress) { <p class="text-2">{{ order.deliveryAddress }}</p> }
            @if (order.notes) { <p class="hint">Obs. do pedido: {{ order.notes }}</p> }
            <small>{{ order.channel | label }} · criado em {{ order.createdAt | dt: true }}</small>
          </section>
          <section class="card">
            <h2>Histórico</h2>
            <ol class="timeline">
              @for (h of order.history; track h.id) {
                <li>
                  <b>@if (h.toValue) { {{ fieldLabel(h.field) }}: {{ h.toValue | label }} } @else { {{ fieldLabel(h.field) }} }</b>
                  @if (h.note) { <span>{{ h.note }}</span> }
                  <small>{{ h.createdAt | dt: true }} · {{ h.userName }}</small>
                </li>
              }
            </ol>
          </section>
        </aside>
      </div>
    }

    <app-modal [title]="modalTitle()" [open]="!!action()" (closed)="action.set(null)" [busy]="!!busy()">
      <form id="actForm" class="stack" (ngSubmit)="submitAction()">
        @switch (action()) {
          @case ('cancel') {
            <p class="text-2">@if (paid()) { O valor pago ({{ refundable() | brl }}) será reembolsado e os itens voltarão ao estoque. } @else { A reserva de estoque será liberada. }</p>
            <div class="field"><label for="r1">Motivo *</label><input id="r1" class="input" name="reason" [(ngModel)]="reason" required maxlength="300"></div>
          }
          @case ('refund') {
            <div class="field"><label for="am">Valor (máx. {{ refundable() | brl }}) *</label><input id="am" class="input" type="number" step="0.01" min="0.01" [max]="refundable()" name="amount" [(ngModel)]="amount" required></div>
            <div class="field"><label for="r2">Motivo *</label><input id="r2" class="input" name="reason" [(ngModel)]="reason" required maxlength="300"></div>
            <p class="hint">O reembolso não devolve itens ao estoque. Para isso, registre a devolução.</p>
          }
          @case ('manualPayment') {
            <div class="field"><label for="mm">Forma de pagamento *</label>
              <select id="mm" class="input" name="method" [(ngModel)]="method"><option>Dinheiro</option><option>Pix</option><option>Cartão (maquininha)</option><option>Transferência</option></select></div>
            <p class="hint">Confirma o pedido, baixa o estoque e lança a receita no financeiro.</p>
          }
          @case ('note') { <div class="field"><label for="nt">Observação interna *</label><textarea id="nt" class="input" name="note" [(ngModel)]="note" maxlength="500" required></textarea></div> }
          @case ('resolve') { <div class="field"><label for="rv">Como foi resolvido? *</label><input id="rv" class="input" name="note" [(ngModel)]="note" maxlength="300" required></div> }
          @case ('return') {
            <p class="hint">Informe as quantidades devolvidas. O estoque só é reposto quando o recebimento é confirmado e o produto está em condições de revenda.</p>
            @for (i of o()?.items ?? []; track i.id) {
              <div class="row-between"><span>{{ i.productName }}@if (i.variantName) { · {{ i.variantName }} } <small>(vendido: {{ i.quantity }})</small></span>
                <input class="input" style="width:90px" type="number" min="0" [max]="i.quantity" [name]="'q' + i.id" [(ngModel)]="returnQty[i.id]" [attr.aria-label]="'Quantidade devolvida de ' + i.productName"></div>
            }
            <div class="field"><label for="rr">Motivo *</label><input id="rr" class="input" name="reason" [(ngModel)]="reason" required maxlength="500"></div>
            <label class="check"><input type="checkbox" name="received" [(ngModel)]="received"> Itens já recebidos na loja</label>
            @if (received) {
              <label class="check"><input type="checkbox" name="cond" [(ngModel)]="conditionOk"> Em condições de revenda (repor estoque)</label>
              <div class="field"><label for="ins">Observação da conferência</label><input id="ins" class="input" name="ins" [(ngModel)]="note" maxlength="500"></div>
            }
          }
          @case ('receive') {
            <label class="check"><input type="checkbox" name="cond2" [(ngModel)]="conditionOk"> Itens em condições de revenda (repor estoque)</label>
            <div class="field"><label for="ins2">Observação da conferência</label><input id="ins2" class="input" name="ins2" [(ngModel)]="note" maxlength="500"></div>
          }
        }
        @if (actionError()) { <div class="alert danger" role="alert">{{ actionError() }}</div> }
      </form>
      <div footer>
        <button class="btn" type="button" (click)="action.set(null)" [disabled]="!!busy()">Voltar</button>
        <button class="btn" [class.btn-danger]="action() === 'cancel'" [class.btn-primary]="action() !== 'cancel'" type="submit" form="actForm" [disabled]="!!busy()">
          {{ busy() ? 'Processando...' : 'Confirmar' }}</button>
      </div>
    </app-modal>`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
    @media (max-width: 960px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .totals { display: grid; grid-template-columns: 1fr auto; gap: 4px 16px; margin: 14px 0 8px auto; max-width: 280px; font-variant-numeric: tabular-nums; }
    .totals dd { margin: 0; text-align: right; }
    .totals .t { font-weight: 700; font-size: 1.05rem; padding-top: 6px; border-top: 1px solid var(--border); }
    .pay { display: flex; justify-content: space-between; gap: 12px; padding: 10px 0; border-bottom: 1px solid var(--border); align-items: center; }
    .pay:last-child { border-bottom: 0; }
    .num-col { display: flex; flex-direction: column; text-align: right; }
    .timeline { list-style: none; padding: 0; margin: 0; position: relative; }
    .timeline li { display: flex; flex-direction: column; padding: 0 0 12px 16px; border-left: 2px solid var(--border); position: relative; font-size: .88rem; }
    .timeline li::before { content: ''; position: absolute; left: -5px; top: 5px; width: 8px; height: 8px; border-radius: 50%; background: var(--primary); }
  `],
})
export class OrderDetailPage implements OnInit {
  private api = inject(AdminApi);
  private toast = inject(ToastService);
  readonly auth = inject(AuthService);
  readonly id = input.required<string>();
  readonly o = signal<OrderDetail | null>(null);
  readonly notFound = signal(false);
  readonly busy = signal<string | false>(false);
  readonly action = signal<ActionKind>(null);
  readonly actionError = signal('');
  reason = ''; note = ''; method = 'Dinheiro'; amount: number | null = null;
  received = true; conditionOk = true; returnQty: Record<number, number> = {}; private receiveId = 0;

  readonly paid = computed(() => ['APPROVED', 'PARTIALLY_REFUNDED'].includes(this.o()?.paymentStatus ?? ''));
  readonly lastPayment = computed(() => this.o()?.payments.at(-1));
  readonly simulated = computed(() => !!this.lastPayment()?.simulated);
  readonly refundable = computed(() => { const p = this.lastPayment(); return p ? +(p.amount - p.refundedAmount).toFixed(2) : 0; });
  readonly canManualPay = computed(() => this.lastPayment()?.provider === 'MANUAL' && !this.paid() && this.o()?.status === 'OPEN');
  readonly onlinePending = computed(() => { const p = this.lastPayment(); return !!p && p.provider !== 'MANUAL' && ['PENDING', 'DECLINED', 'EXPIRED'].includes(p.status); });
  readonly canRefund = computed(() => this.paid() && this.refundable() > 0);
  readonly canCancel = computed(() => {
    const o = this.o();
    return !!o && (o.status === 'OPEN' || o.status === 'CONFIRMED') && !['SHIPPED', 'DELIVERED', 'RETURNED'].includes(o.fulfillmentStatus);
  });
  readonly canReturn = computed(() => !!this.o()?.stockCommitted && ['SHIPPED', 'DELIVERED', 'RETURNED'].includes(this.o()!.fulfillmentStatus));
  readonly modalTitle = computed(() => ({
    cancel: 'Cancelar pedido', refund: 'Reembolsar', return: 'Registrar devolução', manualPayment: 'Registrar recebimento',
    note: 'Adicionar observação', receive: 'Confirmar recebimento da devolução', resolve: 'Resolver pendência',
  } as Record<string, string>)[this.action() ?? ''] ?? '');

  ngOnInit() { this.load(); }

  load() { this.api.order(Number(this.id())).subscribe({ next: o => this.o.set(o), error: () => this.notFound.set(true) }); }

  nextLabel(f: FulfillmentStatus) {
    return ({ PICKING: 'Iniciar separação', READY_FOR_PICKUP: 'Pronto para retirada', SHIPPED: 'Marcar como enviado', DELIVERED: 'Marcar como entregue', AWAITING: 'Voltar para aguardando' } as Record<string, string>)[f] ?? LABELS[f];
  }

  fieldLabel(f: string) { return ({ ORDER: 'Pedido', PAYMENT: 'Pagamento', FULFILLMENT: 'Atendimento', NOTE: 'Observação', RETURN: 'Devolução' } as Record<string, string>)[f]; }

  itemsOf(items: { orderItemId: number; quantity: number }[]) {
    return items.map(i => `${i.quantity}x ${this.o()?.items.find(x => x.id === i.orderItemId)?.productName ?? ''}`).join(', ');
  }

  open(kind: ActionKind) {
    this.reason = ''; this.note = ''; this.actionError.set(''); this.received = true; this.conditionOk = true; this.returnQty = {};
    if (kind === 'refund') this.amount = this.refundable();
    this.action.set(kind);
  }

  openReceive(id: number) { this.receiveId = id; this.open('receive'); }

  advance(f: FulfillmentStatus) { this.run(`f-${f}`, this.api.orderAction(this.o()!.id, 'fulfillment', { status: f }), 'Atendimento atualizado.'); }

  sync() { this.run('sync', this.api.orderAction(this.o()!.id, 'payments/sync'), 'Situação do pagamento conferida no provedor.'); }

  submitAction() {
    const id = this.o()!.id;
    const kind = this.action();
    let req: Observable<OrderDetail> | null = null;
    if ((kind === 'cancel' || kind === 'refund' || kind === 'return') && !this.reason.trim()) { this.actionError.set('Informe o motivo.'); return; }
    switch (kind) {
      case 'cancel': req = this.api.orderAction(id, 'cancel', { reason: this.reason }); break;
      case 'refund': req = this.api.orderAction(id, 'refunds', { amount: this.amount, reason: this.reason }); break;
      case 'manualPayment': req = this.api.orderAction(id, 'payments/manual', { method: this.method }); break;
      case 'note': if (!this.note.trim()) { this.actionError.set('Escreva a observação.'); return; } req = this.api.orderAction(id, 'notes', { note: this.note }); break;
      case 'resolve': if (!this.note.trim()) { this.actionError.set('Descreva a solução.'); return; } req = this.api.orderAction(id, 'attention/resolve', { note: this.note }); break;
      case 'receive': req = this.api.orderAction(id, `returns/${this.receiveId}/receive`, { conditionOk: this.conditionOk, inspectionNote: this.note || null }); break;
      case 'return': {
        const items = Object.entries(this.returnQty).filter(([, q]) => q > 0).map(([k, q]) => ({ orderItemId: Number(k), quantity: q }));
        if (!items.length) { this.actionError.set('Informe a quantidade de ao menos um item.'); return; }
        req = this.api.orderAction(id, 'returns', { items, reason: this.reason, received: this.received, conditionOk: this.received && this.conditionOk, inspectionNote: this.note || null });
        break;
      }
    }
    if (req) this.run('action', req, 'Operação concluída.', true);
  }

  private run(key: string, req: Observable<OrderDetail>, ok: string, closeModal = false) {
    if (this.busy()) return;
    this.busy.set(key);
    req.subscribe({
      next: o => { this.o.set(o); this.busy.set(false); if (closeModal) this.action.set(null); this.toast.success(ok); },
      error: e => { this.busy.set(false); if (closeModal) this.actionError.set(errorMessage(e)); else this.toast.error(e); },
    });
  }
}
