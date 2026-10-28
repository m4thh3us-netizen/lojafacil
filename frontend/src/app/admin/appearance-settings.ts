import { ChangeDetectionStrategy, Component, OnDestroy, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';

import { Appearance, AppearanceService } from '../core/appearance.service';
import { AuthService } from '../core/auth.service';
import { ThemeService, ToastService, errorMessage } from '../core/ui.services';
import { Icon } from '../shared/icon';

@Component({
  selector: 'app-appearance-settings',
  imports: [FormsModule, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    @let a = form();
    <div class="layout">
      <form class="card stack" (ngSubmit)="save()">
        <div class="row-between">
          <h2 style="margin:0">Fundo do painel</h2>
          @if (dirty()) { <span class="badge warning">Alterações não salvas</span> }
        </div>
        @if (!canEdit()) { <div class="alert warning">Somente proprietário ou gerente podem alterar a aparência.</div> }

        <fieldset class="stack" [disabled]="!canEdit()">
          <div class="seg" role="radiogroup" aria-label="Tipo de fundo">
            <label [class.on]="a.mode === 'PHOTO'"><input type="radio" name="mode" value="PHOTO" [ngModel]="a.mode" (ngModelChange)="set({ mode: $event })"> <app-icon name="image" [size]="15" /> Fotografia</label>
            <label [class.on]="a.mode === 'COLOR'"><input type="radio" name="mode" value="COLOR" [ngModel]="a.mode" (ngModelChange)="set({ mode: $event })"> <app-icon name="layers" [size]="15" /> Cor sólida</label>
          </div>

          @if (a.mode === 'PHOTO') {
            <div class="field">
              <span class="label">Fotografia</span>
              @if (a.imageUrl) {
                <button type="button" class="focal" (click)="pickFocus($event)" title="Clique no ponto que deve ficar sempre visível"
                        aria-label="Definir o ponto de foco clicando na imagem">
                  <img [src]="a.imageUrl" alt="" draggable="false">
                  <span class="dot" [style.left.%]="a.posX" [style.top.%]="a.posY"></span>
                </button>
                <span class="hint">Clique na imagem para escolher o ponto que deve permanecer visível em qualquer tela.</span>
              } @else {
                <div class="empty-photo"><app-icon name="image" [size]="22" /><span>Nenhuma fotografia enviada</span></div>
              }
              <div class="row">
                <label class="btn btn-sm" [class.disabled]="uploading()">
                  <app-icon name="upload" [size]="14" /> {{ uploading() ? 'Otimizando imagem...' : (a.imageUrl ? 'Trocar fotografia' : 'Enviar fotografia') }}
                  <input type="file" class="sr-only" accept="image/jpeg,image/png" (change)="upload($event)" [disabled]="uploading()">
                </label>
              </div>
              <span class="hint">JPG ou PNG, até 10 MB e no mínimo 640×360. O servidor reduz para até 2400 px e grava em JPEG progressivo.</span>
            </div>
            <div class="grid grid-2">
              <div class="field"><label for="px">Posição horizontal: {{ a.posX }}%</label>
                <input id="px" type="range" min="0" max="100" step="1" name="px" [ngModel]="a.posX" (ngModelChange)="set({ posX: +$event })"></div>
              <div class="field"><label for="py">Posição vertical: {{ a.posY }}%</label>
                <input id="py" type="range" min="0" max="100" step="1" name="py" [ngModel]="a.posY" (ngModelChange)="set({ posY: +$event })"></div>
            </div>
            <div class="field"><label for="dim">Escurecimento da fotografia: {{ a.dim }}%</label>
              <input id="dim" type="range" min="0" max="70" step="1" name="dim" [ngModel]="a.dim" (ngModelChange)="set({ dim: +$event })">
              <span class="hint">Camada escura uniforme sobre a foto. Aumente para fotografias claras ou com muitos detalhes.</span></div>
          }

          <div class="field">
            <label for="bgc">{{ a.mode === 'PHOTO' ? 'Cor de fundo (exibida enquanto a foto carrega)' : 'Cor de fundo' }}</label>
            <div class="row"><input id="bgc" type="color" class="swatch" name="bgc" [ngModel]="a.color" (ngModelChange)="set({ color: $event })">
              <input class="input" style="max-width:120px" name="bgct" [ngModel]="a.color" (ngModelChange)="setHex($event)" maxlength="7" aria-label="Cor de fundo em hexadecimal"></div>
          </div>

          <div class="field"><label for="gl">Opacidade dos painéis de vidro: {{ a.glass }}%</label>
            <input id="gl" type="range" min="55" max="95" step="1" name="gl" [ngModel]="a.glass" (ngModelChange)="set({ glass: +$event })">
            <span class="hint">Entre 55% (mais transparente) e 95% (quase sólido). Tabelas, formulários e menus ficam sempre com no mínimo 93%.</span>
            @if (autoRaised()) {
              <div class="alert warning" role="status"><app-icon name="info" [size]="15" />
                <span>Neste modo ({{ darkTheme() ? 'escuro' : 'claro' }}) a opacidade está sendo elevada automaticamente para {{ effective() }}% para manter o contraste do texto.
                  @if (a.mode === 'PHOTO') { {{ darkTheme() ? 'Aumentar o escurecimento' : 'Usar uma foto mais clara' }} permite painéis mais transparentes. }</span></div>
            }</div>
        </fieldset>

        @if (error()) { <div class="alert danger" role="alert">{{ error() }}</div> }
        @if (canEdit()) {
          <div class="row">
            <button class="btn btn-primary" type="submit" [disabled]="busy() || !dirty()">{{ busy() === 'save' ? 'Salvando...' : 'Salvar aparência' }}</button>
            <button class="btn" type="button" (click)="discard()" [disabled]="!!busy() || !dirty()">Descartar alterações</button>
            <button class="btn btn-ghost" type="button" (click)="resetDefault()" [disabled]="!!busy()"><app-icon name="refresh" [size]="14" /> {{ busy() === 'reset' ? 'Restaurando...' : 'Restaurar padrão' }}</button>
          </div>
        }
      </form>

      <aside class="stack">
        <div class="card">
          <h3>Pré-visualização</h3>
          <div class="mini" [style.background-color]="a.color"
               [style.background-image]="a.mode === 'PHOTO' && a.imageUrl ? 'url(&quot;' + a.imageUrl + '&quot;)' : 'none'"
               [style.background-position]="a.posX + '% ' + a.posY + '%'">
            @if (a.mode === 'PHOTO' && a.imageUrl) { <div class="mini-dim" [style.background-color]="'rgb(0 0 0 / ' + a.dim / 100 + ')'"></div> }
            <div class="mini-nav"><span></span><span class="on"></span><span></span><span></span></div>
            <div class="mini-panel" [style.background-color]="'rgb(var(--surface-rgb) / ' + effective() / 100 + ')'">
              <small>Faturamento por vendas</small><b>R$ 1.267,10</b><small>8 pedidos confirmados</small>
            </div>
          </div>
          <p class="hint" style="margin-top:8px">O painel inteiro também já mostra a pré-visualização. Teste nos modos claro e escuro (ícone do monitor no topo).</p>
        </div>
        <div class="card">
          <h3>Dicas de legibilidade</h3>
          <ul class="tips text-2">
            <li>Fotografias claras ou cheias de detalhes: escurecimento entre 30% e 50%.</li>
            <li>Prefira imagens sem texto e com áreas calmas.</li>
            <li>Se o navegador não suportar desfoque, os painéis ficam sólidos automaticamente.</li>
          </ul>
        </div>
      </aside>
    </div>`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 340px; gap: 14px; align-items: start; }
    @media (max-width: 960px) { .layout { grid-template-columns: minmax(0, 1fr); } }
    .seg { display: inline-flex; gap: 4px; padding: 4px; border-radius: 999px; background: var(--surface-3); align-self: flex-start; }
    .seg label { display: inline-flex; align-items: center; gap: 6px; padding: 7px 14px; border-radius: 999px; cursor: pointer; font-weight: 550; font-size: .88rem; color: var(--text-2); }
    .seg label.on { background: var(--primary); color: var(--on-primary); }
    .seg input { position: absolute; opacity: 0; pointer-events: none; }
    .seg label:has(input:focus-visible) { box-shadow: var(--focus); }
    .focal { position: relative; display: block; width: 100%; max-width: 560px; padding: 0; border: 1px solid var(--border); border-radius: 12px; overflow: hidden; cursor: crosshair; background: none; aspect-ratio: 16 / 9; }
    .focal img { width: 100%; height: 100%; object-fit: cover; }
    .dot { position: absolute; width: 18px; height: 18px; border-radius: 50%; border: 3px solid #fff; background: var(--primary); transform: translate(-50%, -50%); box-shadow: 0 0 0 1px rgb(0 0 0 / .4); pointer-events: none; }
    .empty-photo { max-width: 560px; display: flex; align-items: center; justify-content: center; gap: 8px; aspect-ratio: 16 / 9; border: 1px dashed var(--border-strong); border-radius: 12px; color: var(--text-3); }
    label.btn.disabled { opacity: .6; pointer-events: none; }
    .swatch { width: 44px; height: 40px; border: 1px solid var(--border-strong); border-radius: 10px; padding: 2px; background: var(--surface); cursor: pointer; }
    .mini { position: relative; aspect-ratio: 16 / 10; border-radius: 12px; overflow: hidden; background-size: cover; border: 1px solid var(--border); display: flex; flex-direction: column; align-items: center; gap: 10%; padding: 6% 8%; }
    .mini-dim { position: absolute; inset: 0; }
    .mini-nav { position: relative; display: flex; gap: 4px; padding: 3px; border-radius: 999px; background: rgb(var(--surface-rgb) / .93); }
    .mini-nav span { width: 26px; height: 10px; border-radius: 999px; background: var(--surface-3); }
    .mini-nav span.on { background: var(--primary); }
    .mini-panel { position: relative; align-self: stretch; display: flex; flex-direction: column; gap: 2px; padding: 10px 12px; border-radius: 10px; border: 1px solid var(--glass-border);
      -webkit-backdrop-filter: blur(var(--blur)); backdrop-filter: blur(var(--blur)); color: var(--text); }
    .mini-panel b { font-size: 1.05rem; }
    .tips { padding-left: 18px; margin: 0; display: flex; flex-direction: column; gap: 6px; font-size: .88rem; }
  `],
})
export class AppearanceSettings implements OnDestroy {
  private service = inject(AppearanceService);
  private toast = inject(ToastService);
  private auth = inject(AuthService);
  readonly canEdit = this.auth.isManagement;
  readonly form = computed(() => this.service.current());
  readonly dirty = computed(() => this.service.previewing());
  readonly autoRaised = this.service.autoRaised;
  readonly effective = computed(() => Math.round(this.service.effectiveGlass() * 100));
  readonly darkTheme = inject(ThemeService).isDark;
  readonly uploading = signal(false);
  readonly busy = signal<'save' | 'reset' | false>(false);
  readonly error = signal('');

  set(patch: Partial<Appearance>) {
    this.error.set('');
    this.service.draft.set({ ...this.service.current(), ...patch });
  }

  setHex(v: string) {
    if (/^#[0-9a-fA-F]{6}$/.test(v)) this.set({ color: v });
  }

  pickFocus(ev: MouseEvent) {
    if (!this.canEdit()) return;
    const r = (ev.currentTarget as HTMLElement).getBoundingClientRect();
    const x = Math.round(((ev.clientX - r.left) / r.width) * 100);
    const y = Math.round(((ev.clientY - r.top) / r.height) * 100);
    this.set({ posX: Math.min(100, Math.max(0, x)), posY: Math.min(100, Math.max(0, y)) });
  }

  upload(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (!/^image\/(jpeg|png)$/.test(file.type)) { this.error.set('Envie uma fotografia JPG ou PNG.'); return; }
    if (file.size > 10 * 1024 * 1024) { this.error.set('A fotografia deve ter no máximo 10 MB.'); return; }
    this.uploading.set(true);
    this.service.uploadPhoto(file).subscribe({
      next: r => { this.uploading.set(false); this.set({ mode: 'PHOTO', imagePath: r.path, imageUrl: r.url }); this.toast.info('Fotografia pronta. Ajuste e clique em "Salvar aparência".'); },
      error: e => { this.uploading.set(false); this.error.set(errorMessage(e)); },
    });
  }

  save() {
    const a = this.form();
    if (a.mode === 'PHOTO' && !a.imagePath) { this.error.set('Envie uma fotografia ou escolha o fundo de cor sólida.'); return; }
    if (this.busy()) return;
    this.busy.set('save');
    this.service.save(a).subscribe({
      next: () => { this.busy.set(false); this.toast.success('Aparência salva para toda a equipe da loja.'); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  discard() {
    this.service.draft.set(null);
    this.error.set('');
  }

  resetDefault() {
    if (this.busy()) return;
    this.busy.set('reset');
    this.service.reset().subscribe({
      next: () => { this.busy.set(false); this.toast.success('Aparência padrão restaurada.'); },
      error: e => { this.busy.set(false); this.error.set(errorMessage(e)); },
    });
  }

  ngOnDestroy() {
    this.service.draft.set(null);
  }
}
