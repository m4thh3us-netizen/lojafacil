import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';
import { BrlPipe, DatePipeBr, LabelPipe, TonePipe } from '../core/format';
import { Address, CustomerDetail } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';
import { AddressFields, addressComplete, emptyAddress } from '../shared/address-fields';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-customer-detail',
  imports: [FormsModule, RouterLink, BrlPipe, DatePipeBr, LabelPipe, TonePipe, Icon, AddressFields],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a routerLink="/painel/clientes" class="back"><app-icon name="arrowLeft" [size]="14" /> Clientes</a>
    <div class="page-header" style="margin-top:6px"><h1>{{ isNew() ? 'Novo cliente' : (c()?.name ?? 'Cliente') }}</h1></div>
    @if (loading()) {
      <div class="card"><span class="skeleton title"></span>@for (i of [1,2,3,4]; track i) { <span class="skeleton line"></span> }</div>
    } @else {
      <div class="layout">
        <form class="card stack" (ngSubmit)="save()">
          <h2>Dados</h2>
          <div class="grid grid-3">
            <div class="field"><label for="n">Nome *</label><input id="n" class="input" name="n" [(ngModel)]="form.name" required maxlength="120"></div>
            <div class="field"><label for="e">E-mail *</label><input id="e" class="input" type="email" name="e" [(ngModel)]="form.email" required maxlength="160"></div>
            <div class="field"><label for="p">Telefone</label><input id="p" class="input" name="p" [(ngModel)]="form.phone" maxlength="30"></div>
          </div>
          <div class="field"><label for="obs">Observações internas</label>
            <textarea id="obs" class="input" name="obs" [(ngModel)]="form.notes" maxlength="2000"></textarea>
            <span class="hint">Visíveis apenas para a equipe; nunca aparecem na loja virtual.</span></div>
          <div class="row-between"><h3 style="margin:0">Endereços</h3><button type="button" class="btn btn-sm" (click)="addAddress()"><app-icon name="plus" /> Endereço</button></div>
          @for (a of addresses(); track $index; let i = $index) {
            <div class="addr">
              <div class="row-between"><input class="input" style="max-width:200px" placeholder="Rótulo (ex.: Casa)" [ngModel]="a.label" [name]="'lb' + i" (ngModelChange)="patch(i, { label: $event })" aria-label="Rótulo do endereço">
                <button type="button" class="btn btn-ghost btn-sm" (click)="removeAddress(i)">Remover</button></div>
              <app-address-fields [address]="a" (addressChange)="setAddress(i, $event)" />
            </div>
          } @empty { <p class="muted">Nenhum endereço cadastrado.</p> }
          @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
          <div><button class="btn btn-primary" [disabled]="busy()">{{ busy() ? 'Salvando...' : 'Salvar' }}</button></div>
        </form>

        @if (c(); as c) {
          <aside class="card">
            <h2>Histórico de pedidos</h2>
            <div class="row" style="margin-bottom:10px"><span class="badge">{{ c.orderCount }} pedido(s)</span><span class="badge success">{{ c.totalSpent | brl }} confirmados</span></div>
            @for (o of c.recentOrders; track o.id) {
              <a class="ord" [routerLink]="['/painel/pedidos', o.id]">
                <span><b>#{{ o.number }}</b> <small>{{ o.createdAt | dt }}</small></span>
                <span class="row" style="gap:4px"><span [class]="'badge ' + (o.status | tone)">{{ o.status | label }}</span><b>{{ o.total | brl }}</b></span>
              </a>
            } @empty { <p class="muted">Ainda sem pedidos.</p> }
          </aside>
        }
      </div>
    }`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
    @media (max-width: 960px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .addr { padding: 12px; border: 1px solid var(--border); border-radius: 12px; background: var(--surface-2); }
    .ord { display: flex; justify-content: space-between; gap: 8px; padding: 10px 0; border-bottom: 1px solid var(--border); color: var(--text); text-decoration: none; flex-wrap: wrap; }
  `],
})
export class CustomerDetailPage implements OnInit {
  private api = inject(AdminApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly id = input.required<string>();
  readonly isNew = computed(() => this.id() === 'novo');
  readonly c = signal<CustomerDetail | null>(null);
  readonly addresses = signal<Address[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  form = { name: '', email: '', phone: '', notes: '' };

  ngOnInit() {
    if (this.isNew()) return;
    this.loading.set(true);
    this.api.customer(Number(this.id())).subscribe({
      next: c => { this.fill(c); this.loading.set(false); },
      error: e => { this.loading.set(false); this.toast.error(e); },
    });
  }

  private fill(c: CustomerDetail) {
    this.c.set(c);
    this.form = { name: c.name, email: c.email, phone: c.phone ?? '', notes: c.notes ?? '' };
    this.addresses.set(c.addresses.map(a => ({ ...emptyAddress(), ...a })));
  }

  addAddress() { this.addresses.update(l => [...l, emptyAddress()]); }
  removeAddress(i: number) { this.addresses.update(l => l.filter((_, j) => j !== i)); }
  setAddress(i: number, a: Address) { this.addresses.update(l => l.map((x, j) => j === i ? a : x)); }
  patch(i: number, p: Partial<Address>) { this.addresses.update(l => l.map((x, j) => j === i ? { ...x, ...p } : x)); }

  save() {
    this.error.set('');
    if (!this.form.name.trim() || !this.form.email.trim()) { this.error.set('Informe nome e e-mail.'); return; }
    if (this.addresses().some(a => !addressComplete(a))) { this.error.set('Complete os endereços (rua, número, bairro, cidade, UF e CEP) ou remova-os.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    const id = this.isNew() ? null : Number(this.id());
    this.api.saveCustomer(id, { ...this.form, addresses: this.addresses() }).subscribe({
      next: c => {
        this.busy.set(false); this.toast.success('Cliente salvo.');
        if (!id) this.router.navigate(['/painel/clientes', c.id]); else this.fill(c);
      },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }
}
