import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { AdminApi } from '../core/api';
import { AuthService } from '../core/auth.service';
import { DatePipeBr, LabelPipe } from '../core/format';
import { AuditItem, PaymentConfig, Role, StoreSettings, TeamView } from '../core/models';
import { ToastService, errorMessage } from '../core/ui.services';
import { bestText, contrast } from '../shared/contrast';
import { Icon } from '../shared/icon';
import { Pager } from '../shared/ui';
import { AppearanceSettings } from './appearance-settings';

type Tab = 'store' | 'appearance' | 'payments' | 'team' | 'audit';

@Component({
  selector: 'app-settings',
  imports: [FormsModule, DatePipeBr, LabelPipe, Icon, Pager, AppearanceSettings],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header"><div><h1>Configurações</h1><p>Loja virtual, aparência do painel, pagamentos, equipe e auditoria.</p></div></div>
    <div class="tabs" role="tablist">
      @for (t of tabs(); track t[0]) { <button role="tab" [class.active]="tab() === t[0]" [attr.aria-selected]="tab() === t[0]" (click)="go(t[0])">{{ t[1] }}</button> }
    </div>

    @switch (tab()) {
      @case ('store') {
        @if (!s()) { <div class="card"><span class="skeleton title"></span>@for (i of [1,2,3,4,5]; track i) { <span class="skeleton line"></span> }</div> }
        @else {
          @if (!auth.isManagement()) { <div class="alert warning" style="margin-bottom:12px">Somente proprietário ou gerente podem alterar estas configurações.</div> }
          <div class="layout">
            <form class="stack" (ngSubmit)="saveStore()">
              <fieldset [disabled]="!auth.isManagement()" class="stack">
                <section class="card">
                  <h2>Identidade</h2>
                  <div class="grid grid-2">
                    <div class="field span-2"><label for="nm">Nome da loja *</label><input id="nm" class="input" name="nm" [(ngModel)]="form.name" maxlength="120"></div>
                    <div class="field span-2"><label for="ds">Descrição</label><textarea id="ds" class="input" name="ds" [(ngModel)]="form.description" maxlength="1000"></textarea></div>
                  </div>
                  <div class="grid grid-2" style="margin-top:12px">
                    @for (k of imageKinds; track k[0]) {
                      <div class="field">
                        <span class="label">{{ k[1] }}</span>
                        <div class="img-slot" [class.banner]="k[0] === 'banner'">
                          @if (k[0] === 'logo' ? s()!.logoUrl : s()!.bannerUrl; as url) { <img [src]="url" alt=""> } @else { <app-icon name="image" [size]="22" /> }
                        </div>
                        <div class="row">
                          <label class="btn btn-sm" [class.disabled]="uploading() === k[0]"><app-icon name="upload" [size]="14" /> {{ uploading() === k[0] ? 'Enviando...' : 'Enviar' }}
                            <input type="file" class="sr-only" accept="image/jpeg,image/png" (change)="upload(k[0], $event)"></label>
                          @if (k[0] === 'logo' ? s()!.logoUrl : s()!.bannerUrl) { <button type="button" class="btn btn-sm btn-ghost" (click)="removeImage(k[0])">Remover</button> }
                        </div>
                        <span class="hint">{{ k[0] === 'logo' ? 'Quadrado, até 3 MB.' : 'Horizontal (ex.: 1600x500), até 3 MB.' }}</span>
                      </div>
                    }
                  </div>
                </section>

                <section class="card">
                  <div class="row-between"><h2>Cores da vitrine</h2><button type="button" class="btn btn-sm" (click)="resetTheme()"><app-icon name="refresh" [size]="14" /> Restaurar padrão</button></div>
                  <p class="hint">Independentes do tema claro/escuro do painel. O texto sobre cada cor é escolhido automaticamente para manter contraste.</p>
                  <div class="grid grid-2">
                    @for (c of colorFields; track c[0]) {
                      <div class="field"><label [for]="c[0]">{{ c[1] }}</label>
                        <div class="row"><input type="color" [id]="c[0]" [name]="c[0]" [(ngModel)]="form[c[0]]" class="swatch">
                          <input class="input" style="max-width:120px" [name]="c[0] + 't'" [(ngModel)]="form[c[0]]" maxlength="7" [attr.aria-label]="c[1] + ' em hexadecimal'">
                          <span class="badge" [class.danger]="ratio(form[c[0]]) < 4.5" [class.success]="ratio(form[c[0]]) >= 4.5">contraste {{ ratio(form[c[0]]).toFixed(1) }}:1</span></div>
                      </div>
                    }
                  </div>
                </section>

                <section class="card">
                  <h2>Contatos e atendimento</h2>
                  <div class="grid grid-3">
                    <div class="field"><label for="ce">E-mail</label><input id="ce" class="input" type="email" name="ce" [(ngModel)]="form.contactEmail"></div>
                    <div class="field"><label for="cp">Telefone</label><input id="cp" class="input" name="cp" [(ngModel)]="form.contactPhone"></div>
                    <div class="field"><label for="wa">WhatsApp</label><input id="wa" class="input" name="wa" [(ngModel)]="form.whatsapp"></div>
                    <div class="field"><label for="ig">Instagram</label><input id="ig" class="input" name="ig" [(ngModel)]="form.instagram" placeholder="@sualoja"></div>
                    <div class="field"><label for="fb">Facebook</label><input id="fb" class="input" name="fb" [(ngModel)]="form.facebook"></div>
                    <div class="field"><label for="sh">Horário de atendimento</label><input id="sh" class="input" name="sh" [(ngModel)]="form.serviceHours"></div>
                    <div class="field span-2"><label for="al">Endereço</label><input id="al" class="input" name="al" [(ngModel)]="form.addressLine"></div>
                    <div class="field"><label for="zp">CEP</label><input id="zp" class="input" name="zp" [(ngModel)]="form.addressZip" maxlength="9"></div>
                    <div class="field span-2"><label for="ci">Cidade</label><input id="ci" class="input" name="ci" [(ngModel)]="form.addressCity"></div>
                    <div class="field"><label for="uf">UF</label><input id="uf" class="input" name="uf" [(ngModel)]="form.addressState" maxlength="2"></div>
                  </div>
                </section>

                <section class="card">
                  <h2>Entrega e reservas</h2>
                  <div class="stack-sm">
                    <label class="check"><input type="checkbox" name="pk" [(ngModel)]="form.pickupEnabled"> Retirada no local</label>
                    <label class="check"><input type="checkbox" name="ld" [(ngModel)]="form.localDeliveryEnabled"> Entrega local</label>
                  </div>
                  <div class="grid grid-2" style="margin-top:12px">
                    <div class="field"><label for="fee">Taxa de entrega local (R$)</label><input id="fee" class="input" type="number" step="0.01" min="0" name="fee" [(ngModel)]="form.localDeliveryFee"></div>
                    <div class="field"><label for="rm">Prazo de reserva do estoque (minutos)</label><input id="rm" class="input" type="number" min="5" max="1440" name="rm" [(ngModel)]="form.reservationMinutes">
                      <span class="hint">Tempo para o cliente pagar antes de o estoque ser liberado.</span></div>
                  </div>
                </section>
                @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
                <div><button class="btn btn-primary" [disabled]="busy()">{{ busy() ? 'Salvando...' : 'Salvar configurações' }}</button></div>
              </fieldset>
            </form>

            <aside class="preview-wrap card">
              <span class="label">Prévia da vitrine</span>
              <div class="preview" [style.--p]="form.primaryColor" [style.--op]="onPrimary()" [style.--s]="form.secondaryColor" [style.--os]="onSecondary()">
                <div class="pv-head">@if (s()!.logoUrl) { <img [src]="s()!.logoUrl" alt="" class="pv-logo"> } <b>{{ form.name || 'Sua loja' }}</b></div>
                <div class="pv-banner" [style.background-image]="s()!.bannerUrl ? 'url(' + s()!.bannerUrl + ')' : null">
                  <span>{{ form.description || 'Descrição da loja' }}</span></div>
                <div class="pv-card"><div class="pv-img"></div><span>Produto exemplo</span><b>R$ 89,90</b><button type="button" class="pv-btn" tabindex="-1">Adicionar ao carrinho</button></div>
              </div>
              <a class="btn btn-sm" [href]="'/loja/' + s()!.slug" target="_blank" rel="noopener" style="margin-top:8px"><app-icon name="external" [size]="14" /> Abrir loja</a>
            </aside>
          </div>
        }
      }

      @case ('appearance') { <app-appearance-settings /> }

      @case ('payments') {
        @if (!pay()) { <div class="card"><span class="skeleton title"></span><span class="skeleton line"></span><span class="skeleton line"></span></div> }
        @else {
          <div class="layout">
            <form class="card stack" (ngSubmit)="savePayments()">
              <h2>Integração de pagamentos</h2>
              @if (pay()!.demoMode) {
                <div class="alert warning"><app-icon name="shield" /> Ambiente demonstrativo: credenciais reais estão bloqueadas e todas as cobranças usam o <b>simulador</b>.</div>
              }
              <div class="row">
                <span class="label">Em uso agora:</span>
                @if (pay()!.simulated) { <span class="badge sim">SIMULADOR LOCAL</span> } @else { <span class="badge success">Mercado Pago (teste)</span> }
              </div>
              <fieldset [disabled]="pay()!.demoMode" class="stack">
                <div class="row" role="radiogroup" aria-label="Provedor">
                  <label class="check"><input type="radio" name="pv" value="SIMULATOR" [(ngModel)]="payForm.provider"> Simulador local</label>
                  <label class="check"><input type="radio" name="pv" value="MERCADO_PAGO" [(ngModel)]="payForm.provider"> Mercado Pago (Checkout Pro)</label>
                </div>
                @if (payForm.provider === 'MERCADO_PAGO') {
                  <div class="field"><label for="at">Access token de teste</label>
                    <input id="at" class="input" name="at" type="password" autocomplete="off" [(ngModel)]="payForm.accessToken" [placeholder]="pay()!.accessTokenMasked || 'TEST-... ou APP_USR-... (conta de teste)'">
                    <span class="hint">Salvo cifrado no servidor e nunca exibido novamente. Deixe em branco para manter o atual.</span></div>
                  <div class="field"><label for="pk">Public key</label><input id="pk" class="input" name="pk" [(ngModel)]="payForm.publicKey"></div>
                  <div class="field"><label for="ws">Assinatura secreta do webhook</label>
                    <input id="ws" class="input" name="ws" type="password" autocomplete="off" [(ngModel)]="payForm.webhookSecret" [placeholder]="pay()!.webhookSecretMasked || 'Suas integrações > Webhooks'"></div>
                  <div class="field"><label>URL de notificação (webhook)</label>
                    <div class="row"><input class="input spacer" [value]="pay()!.webhookUrl" readonly aria-label="URL do webhook"><button type="button" class="btn" (click)="copy(pay()!.webhookUrl)"><app-icon name="copy" /></button></div>
                    <span class="hint">Precisa ser HTTPS público (ex.: túnel ngrok em desenvolvimento). Sem ela, a confirmação vem pela consulta ao voltar do checkout e pela conciliação periódica.</span></div>
                }
              </fieldset>
              @if (payError()) { <div class="alert danger" role="alert">{{ payError() }}</div> }
              <div class="row">
                <button class="btn btn-primary" [disabled]="busy() || pay()!.demoMode">{{ busy() ? 'Salvando...' : 'Salvar' }}</button>
                <button class="btn" type="button" (click)="testPayments()" [disabled]="testing()">{{ testing() ? 'Testando...' : 'Testar conexão' }}</button>
              </div>
              @if (testResult(); as t) { <div class="alert" [class.success]="t.ok" [class.danger]="!t.ok" role="status">{{ t.message }}</div> }
            </form>
            <aside class="card">
              <h3>Como funciona</h3>
              <ol class="text-2" style="padding-left:18px">
                <li>O cliente finaliza a compra e o estoque é reservado.</li>
                <li>É redirecionado ao checkout hospedado (o cartão nunca passa pelo nosso servidor).</li>
                <li>O provedor notifica o webhook; o servidor valida a assinatura e <b>consulta a situação real</b>.</li>
                <li>Aprovado: estoque baixado e receita lançada uma única vez.</li>
              </ol>
              <p class="hint">Transações de produção (live_mode) são bloqueadas enquanto a configuração do servidor não permitir.</p>
            </aside>
          </div>
        }
      }

      @case ('team') {
        @if (!team()) { <div class="card"><span class="skeleton line"></span><span class="skeleton line"></span></div> }
        @else {
          <div class="layout">
            <section class="card">
              <h2>Equipe</h2>
              <div class="table-wrap">
                <table class="table cards">
                  <thead><tr><th>Nome</th><th>Perfil</th><th>Situação</th><th></th></tr></thead>
                  <tbody>
                    @for (m of team()!.members; track m.id) {
                      <tr>
                        <td data-label="Nome"><b>{{ m.fullName }}</b><br><small>{{ m.email }}</small></td>
                        <td data-label="Perfil">
                          @if (m.role === 'OWNER') { <span class="badge primary">Proprietário</span> }
                          @else { <select class="input" [ngModel]="m.role" (ngModelChange)="updateMember(m.id, $event, m.active)" [attr.aria-label]="'Perfil de ' + m.fullName">
                            <option value="MANAGER">Gerente</option><option value="OPERATOR">Operador</option></select> }
                        </td>
                        <td data-label="Situação"><span class="badge" [class.success]="m.active">{{ m.active ? 'Ativo' : 'Desativado' }}</span></td>
                        <td class="num">@if (m.role !== 'OWNER') { <button class="btn btn-sm" type="button" (click)="updateMember(m.id, m.role, !m.active)">{{ m.active ? 'Desativar' : 'Reativar' }}</button> }</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              @if (team()!.invitations.length) {
                <h3 style="margin-top:16px">Convites pendentes</h3>
                @for (i of team()!.invitations; track i.id) {
                  <div class="row-between inv"><span>{{ i.email }} · {{ i.role | label }} <small>expira {{ i.expiresAt | dt }}</small></span>
                    <button class="btn btn-sm btn-ghost" type="button" (click)="revoke(i.id)">Revogar</button></div>
                }
              }
            </section>
            <form class="card stack" (ngSubmit)="invite()">
              <h2>Convidar colaborador</h2>
              <div class="field"><label for="ie">E-mail</label><input id="ie" class="input" type="email" name="ie" [(ngModel)]="inviteEmail"></div>
              <div class="field"><label for="ir">Perfil</label><select id="ir" class="input" name="ir" [(ngModel)]="inviteRole">
                <option value="MANAGER">Gerente — comercial, estoque e financeiro</option><option value="OPERATOR">Operador — produtos, estoque e pedidos</option></select></div>
              <button class="btn btn-primary" [disabled]="busy()">{{ busy() ? 'Enviando...' : 'Enviar convite' }}</button>
              @if (lastInvite()) {
                <div class="alert success">Convite enviado por e-mail. Você também pode compartilhar este link (válido por 7 dias, exibido uma única vez):
                  <div class="row" style="margin-top:6px"><input class="input spacer" [value]="lastInvite()" readonly aria-label="Link do convite"><button class="btn btn-sm" type="button" (click)="copy(lastInvite()!)"><app-icon name="copy" /></button></div></div>
              }
            </form>
          </div>
        }
      }

      @case ('audit') {
        <div class="table-wrap">
          <table class="table cards">
            <thead><tr><th>Data</th><th>Área</th><th>Ação</th><th>Detalhes</th></tr></thead>
            <tbody>
              @for (a of audit(); track a.id) {
                <tr><td data-label="Data">{{ a.createdAt | dt: true }}</td><td data-label="Área">{{ a.entityType }}</td><td data-label="Ação"><code>{{ a.action }}</code></td><td data-label="Detalhes">{{ a.details || '—' }}</td></tr>
              } @empty { <tr><td colspan="4" class="muted">Carregando...</td></tr> }
            </tbody>
          </table>
        </div>
        <app-pager [page]="auditPage" [pages]="auditPages()" [total]="auditTotal()" (change)="loadAudit($event)" />
      }
    }`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 16px; align-items: start; }
    @media (max-width: 960px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .img-slot { height: 96px; border-radius: 12px; border: 1px dashed var(--border-strong); display: grid; place-items: center; color: var(--text-3); overflow: hidden; background: var(--surface-2); aspect-ratio: 1; }
    .img-slot.banner { aspect-ratio: auto; width: 100%; }
    .img-slot img { width: 100%; height: 100%; object-fit: cover; }
    label.btn.disabled { opacity: .6; pointer-events: none; }
    .swatch { width: 44px; height: 40px; border: 1px solid var(--border-strong); border-radius: 10px; padding: 2px; background: var(--surface); cursor: pointer; }
    .preview-wrap { position: sticky; top: 84px; display: flex; flex-direction: column; gap: 6px; }
    .preview { border-radius: 16px; overflow: hidden; border: 1px solid var(--border); background: #fff; color: #111827; box-shadow: var(--shadow); }
    .pv-head { background: var(--s); color: var(--os); padding: 12px 14px; display: flex; align-items: center; gap: 8px; }
    .pv-logo { width: 26px; height: 26px; border-radius: 6px; object-fit: cover; }
    .pv-banner { height: 90px; background-color: var(--s); background-size: cover; background-position: center; display: flex; align-items: flex-end; padding: 10px 14px; color: #fff; font-size: .82rem;  }
    .pv-card { margin: 14px; padding: 10px; border: 1px solid #e5e7eb; border-radius: 12px; display: flex; flex-direction: column; gap: 4px; font-size: .85rem; }
    .pv-img { height: 70px; border-radius: 8px; background: #f1f5f9; }
    .pv-btn { background: var(--p); color: var(--op); border: 0; border-radius: 8px; padding: 8px; font: inherit; font-weight: 600; }
    .inv { padding: 8px 0; border-bottom: 1px solid var(--border); }
  `],
})
export class SettingsPage {
  private api = inject(AdminApi);
  private toast = inject(ToastService);
  readonly auth = inject(AuthService);
  readonly tab = signal<Tab>('store');
  readonly tabs = computed(() => {
    const t: [Tab, string][] = [['store', 'Loja virtual'], ['appearance', 'Aparência do painel']];
    if (this.auth.isOwner()) t.push(['payments', 'Pagamentos'], ['team', 'Equipe']);
    if (this.auth.isManagement()) t.push(['audit', 'Auditoria']);
    return t;
  });
  readonly imageKinds: ['logo' | 'banner', string][] = [['logo', 'Logo'], ['banner', 'Banner']];
  readonly colorFields: ['primaryColor' | 'secondaryColor', string][] = [['primaryColor', 'Cor principal (botões e destaques)'], ['secondaryColor', 'Cor secundária (cabeçalho e rodapé)']];
  readonly s = signal<StoreSettings | null>(null);
  readonly busy = signal(false);
  readonly uploading = signal<string | null>(null);
  readonly error = signal('');
  form = {} as StoreSettings;

  readonly pay = signal<PaymentConfig | null>(null);
  readonly payError = signal('');
  readonly testing = signal(false);
  readonly testResult = signal<{ ok: boolean; message: string } | null>(null);
  payForm = { provider: 'SIMULATOR', accessToken: '', publicKey: '', webhookSecret: '' };

  readonly team = signal<TeamView | null>(null);
  readonly lastInvite = signal<string | null>(null);
  inviteEmail = ''; inviteRole: Role = 'OPERATOR';

  readonly audit = signal<AuditItem[]>([]);
  readonly auditPages = signal(0);
  readonly auditTotal = signal(0);
  auditPage = 0;

  onPrimary() { return bestText(this.form.primaryColor ?? '#1F5EFF'); }
  onSecondary() { return bestText(this.form.secondaryColor ?? '#0F172A'); }

  constructor() { this.loadStore(); }

  ratio(color: string) { return /^#[0-9a-fA-F]{6}$/.test(color ?? '') ? contrast(color, bestText(color)) : 0; }

  go(t: Tab) {
    this.tab.set(t);
    if (t === 'payments' && !this.pay()) this.loadPayments();
    if (t === 'team') this.loadTeam();
    if (t === 'audit') this.loadAudit(0);
  }

  loadStore() { this.api.store().subscribe({ next: s => this.fillStore(s), error: e => this.toast.error(e) }); }

  private fillStore(s: StoreSettings) {
    this.s.set(s);
    this.form = { ...s, description: s.description ?? '', addressState: s.addressState ?? '', addressZip: s.addressZip ?? '' };
  }

  saveStore() {
    this.error.set('');
    if (this.ratio(this.form.primaryColor) < 4.5 || this.ratio(this.form.secondaryColor) < 4.5) {
      this.error.set('Uma das cores não tem contraste suficiente com texto claro ou escuro. Escolha um tom mais claro ou mais escuro.'); return;
    }
    if (this.busy()) return;
    this.busy.set(true);
    this.api.saveStore(this.form).subscribe({
      next: s => { this.busy.set(false); this.fillStore(s); this.toast.success('Configurações salvas.'); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  resetTheme() {
    this.api.resetTheme().subscribe({ next: s => { this.fillStore(s); this.toast.success('Tema padrão restaurado.'); }, error: e => this.toast.error(e) });
  }

  upload(kind: 'logo' | 'banner', ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 3 * 1024 * 1024) { this.toast.error('A imagem deve ter no máximo 3 MB.'); return; }
    this.uploading.set(kind);
    this.api.uploadStoreImage(kind, file).subscribe({
      next: s => { this.uploading.set(null); this.s.set(s); this.toast.success('Imagem atualizada.'); },
      error: e => { this.uploading.set(null); this.toast.error(e); },
    });
  }

  removeImage(kind: 'logo' | 'banner') { this.api.removeStoreImage(kind).subscribe({ next: s => this.s.set(s), error: e => this.toast.error(e) }); }

  loadPayments() {
    this.api.paymentConfig().subscribe({
      next: p => { this.pay.set(p); this.payForm = { provider: p.simulated ? 'SIMULATOR' : 'MERCADO_PAGO', accessToken: '', publicKey: p.publicKey ?? '', webhookSecret: '' }; },
      error: e => this.toast.error(e),
    });
  }

  savePayments() {
    if (this.busy()) return;
    this.busy.set(true);
    this.payError.set('');
    this.api.savePaymentConfig(this.payForm).subscribe({
      next: p => { this.busy.set(false); this.pay.set(p); this.payForm.accessToken = ''; this.payForm.webhookSecret = ''; this.toast.success('Integração salva.'); },
      error: e => { this.busy.set(false); this.payError.set(errorMessage(e)); },
    });
  }

  testPayments() {
    this.testing.set(true);
    this.api.testPayment().subscribe({
      next: r => { this.testing.set(false); this.testResult.set(r); },
      error: e => { this.testing.set(false); this.testResult.set({ ok: false, message: errorMessage(e) }); },
    });
  }

  loadTeam() { this.api.team().subscribe({ next: t => this.team.set(t), error: e => this.toast.error(e) }); }

  invite() {
    if (!this.inviteEmail.includes('@') || this.busy()) { if (!this.busy()) this.toast.error('Informe um e-mail válido.'); return; }
    this.busy.set(true);
    this.api.invite({ email: this.inviteEmail, role: this.inviteRole }).subscribe({
      next: i => { this.busy.set(false); this.lastInvite.set(i.inviteLink ?? null); this.inviteEmail = ''; this.loadTeam(); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }

  revoke(id: number) { this.api.revokeInvite(id).subscribe({ next: () => { this.toast.success('Convite revogado.'); this.loadTeam(); }, error: e => this.toast.error(e) }); }

  updateMember(id: number, role: Role, active: boolean) {
    this.api.updateMember(id, { role, active }).subscribe({ next: () => { this.toast.success('Acesso atualizado.'); this.loadTeam(); }, error: e => this.toast.error(e) });
  }

  loadAudit(page: number) {
    this.auditPage = page;
    this.api.audit(page).subscribe({ next: p => { this.audit.set(p.content); this.auditPages.set(p.totalPages); this.auditTotal.set(p.totalElements); }, error: e => this.toast.error(e) });
  }

  copy(text: string) { navigator.clipboard?.writeText(text).then(() => this.toast.success('Copiado.')); }
}
