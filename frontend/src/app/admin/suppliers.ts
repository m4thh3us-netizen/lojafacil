import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdminApi } from '../core/api';
import { Supplier } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { PagedList } from '../shared/paged-list';
import { Empty, Modal, Pager, TableSkeleton } from '../shared/ui';

@Component({
  selector: 'app-suppliers',
  imports: [FormsModule, Icon, Empty, Modal, Pager, TableSkeleton],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div><h1>Fornecedores</h1><p>Contatos usados nas reposições de estoque e contas a pagar.</p></div>
      <button class="btn btn-primary" type="button" (click)="edit(null)"><app-icon name="plus" /> Novo fornecedor</button>
    </div>
    <div class="toolbar" role="search">
      <div class="field grow"><label for="q">Buscar</label><input id="q" class="input" type="search" [(ngModel)]="q" (ngModelChange)="list.search(true)" placeholder="Nome, contato ou e-mail"></div>
    </div>
    @if (list.firstLoad) { <app-table-skeleton [cols]="5" /> }
    @else if (!list.items.length) { <div class="card"><app-empty icon="truck" title="Nenhum fornecedor" text="Cadastre seus fornecedores para registrar reposições."><button class="btn btn-primary" (click)="edit(null)">Cadastrar</button></app-empty></div> }
    @else {
      <div class="table-wrap" [style.opacity]="list.loading() ? .6 : 1">
        <table class="table cards">
          <thead><tr><th>Nome / razão social</th><th>Contato</th><th>Telefone</th><th>E-mail</th><th></th></tr></thead>
          <tbody>
            @for (s of list.items; track s.id) {
              <tr>
                <td data-label="Nome"><b>{{ s.name }}</b> @if (!s.active) { <span class="badge">inativo</span> }</td>
                <td data-label="Contato">{{ s.contactName || '—' }}</td>
                <td data-label="Telefone">{{ s.phone || '—' }}</td>
                <td data-label="E-mail">{{ s.email || '—' }}</td>
                <td class="num"><button class="btn btn-sm" type="button" (click)="edit(s)"><app-icon name="edit" [size]="15" /> Editar</button></td>
              </tr>
            }
          </tbody>
        </table>
      </div>
      <app-pager [page]="list.page" [pages]="list.data()?.totalPages ?? 0" [total]="list.data()?.totalElements ?? 0" (change)="list.goTo($event)" />
    }

    <app-modal [title]="editingId ? 'Editar fornecedor' : 'Novo fornecedor'" [open]="open()" (closed)="open.set(false)" [busy]="busy()">
      <form id="supForm" class="stack" (ngSubmit)="save()">
        <div class="field"><label for="sn">Nome ou razão social *</label><input id="sn" class="input" name="name" [(ngModel)]="form.name" required maxlength="160"></div>
        <div class="grid grid-2">
          <div class="field"><label for="sc">Contato</label><input id="sc" class="input" name="contact" [(ngModel)]="form.contactName" maxlength="120"></div>
          <div class="field"><label for="sp">Telefone</label><input id="sp" class="input" name="phone" [(ngModel)]="form.phone" maxlength="30"></div>
        </div>
        <div class="field"><label for="se">E-mail</label><input id="se" class="input" type="email" name="email" [(ngModel)]="form.email" maxlength="160"></div>
        <div class="field"><label for="sa">Endereço</label><input id="sa" class="input" name="address" [(ngModel)]="form.address" maxlength="300"></div>
        <div class="field"><label for="so">Observações</label><textarea id="so" class="input" name="notes" [(ngModel)]="form.notes" maxlength="2000"></textarea></div>
        <label class="check"><input type="checkbox" name="active" [(ngModel)]="form.active"> Ativo</label>
        @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
      </form>
      <div footer><button class="btn" type="button" (click)="open.set(false)">Cancelar</button>
        <button class="btn btn-primary" type="submit" form="supForm" [disabled]="busy()">{{ busy() ? 'Salvando...' : 'Salvar' }}</button></div>
    </app-modal>`,
})
export class SuppliersPage {
  private api = inject(AdminApi);
  private toast = inject(ToastService);
  q = '';
  readonly list = new PagedList<Supplier>(page => this.api.suppliers({ q: this.q, page, size: 20 }));
  readonly open = signal(false);
  readonly busy = signal(false);
  readonly error = signal('');
  editingId: number | null = null;
  form = this.blank();

  constructor() { this.list.search(); }

  private blank() { return { name: '', contactName: '', phone: '', email: '', address: '', notes: '', active: true }; }

  edit(s: Supplier | null) {
    this.editingId = s?.id ?? null;
    this.form = s ? { name: s.name, contactName: s.contactName ?? '', phone: s.phone ?? '', email: s.email ?? '', address: s.address ?? '', notes: s.notes ?? '', active: s.active } : this.blank();
    this.error.set('');
    this.open.set(true);
  }

  save() {
    if (!this.form.name.trim()) { this.error.set('Informe o nome.'); return; }
    if (this.busy()) return;
    this.busy.set(true);
    this.api.saveSupplier(this.editingId, { ...this.form, email: this.form.email || null }).subscribe({
      next: () => { this.busy.set(false); this.open.set(false); this.toast.success('Fornecedor salvo.'); this.list.refresh(); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }
}
