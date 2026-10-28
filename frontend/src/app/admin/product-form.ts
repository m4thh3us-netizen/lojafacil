import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { FormArray, FormGroup, NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { AdminApi } from '../core/api';

import { Category, ProductDetail } from '../core/models';
import { ToastService, errorMessage, fieldErrors } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { Modal } from '../shared/ui';

@Component({
  selector: 'app-product-form',
  imports: [ReactiveFormsModule, RouterLink, Icon, Modal],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="page-header">
      <div>
        <a routerLink="/painel/produtos" class="back"><app-icon name="arrowLeft" [size]="14" /> Produtos</a>
        <h1>{{ isNew() ? 'Novo produto' : (product()?.name ?? 'Produto') }}</h1>
        @if (product()?.archivedAt) { <span class="badge">Arquivado</span> }
      </div>
      @if (!isNew() && product(); as p) {
        <div class="row">
          <button class="btn" type="button" (click)="archive(!p.archivedAt)" [disabled]="busy()"><app-icon name="archive" /> {{ p.archivedAt ? 'Desarquivar' : 'Arquivar' }}</button>
          @if (!p.usedInOrders) { <button class="btn btn-ghost" type="button" (click)="confirmDelete.set(true)"><app-icon name="trash" /> Excluir</button> }
        </div>
      }
    </div>

    @if (loading()) {
      <div class="card"><span class="skeleton title"></span>@for (i of [1,2,3,4,5]; track i) { <span class="skeleton line"></span> }</div>
    } @else {
      <form [formGroup]="form" (ngSubmit)="save()" novalidate class="layout">
        <div class="stack">
          <section class="card">
            <h2>Informações</h2>
            <div class="grid grid-2">
              <div class="field span-2"><label for="name">Nome *</label><input id="name" class="input" formControlName="name" maxlength="160">
                @if (err('name')) { <span class="error-text">{{ err('name') }}</span> }</div>
              <div class="field span-2"><label for="desc">Descrição</label><textarea id="desc" class="input" formControlName="description" rows="4" maxlength="4000"></textarea></div>
              <div class="field"><label for="cat">Categoria</label>
                <select id="cat" class="input" formControlName="categoryId"><option [ngValue]="null">Sem categoria</option>
                  @for (c of categories(); track c.id) { <option [ngValue]="c.id">{{ c.name }}</option> }</select></div>
              <div class="field"><label for="unit">Unidade de venda</label>
                <select id="unit" class="input" formControlName="unit">
                  @for (u of units; track u[0]) { <option [value]="u[0]">{{ u[1] }}</option> }</select></div>
              <div class="field"><label for="sku">SKU *</label><input id="sku" class="input" formControlName="sku" maxlength="60" autocapitalize="characters">
                @if (err('sku')) { <span class="error-text">{{ err('sku') }}</span> }</div>
              <div class="field"><label for="bc">Código de barras</label><input id="bc" class="input" formControlName="barcode" inputmode="numeric" maxlength="60"></div>
            </div>
          </section>

          <section class="card">
            <h2>Preços</h2>
            <div class="grid grid-3">
              <div class="field"><label for="cost">Preço de custo *</label><input id="cost" class="input" type="number" step="0.01" min="0" formControlName="costPrice"></div>
              <div class="field"><label for="sale">Preço de venda *</label><input id="sale" class="input" type="number" step="0.01" min="0.01" formControlName="salePrice"></div>
              <div class="field"><label for="promo">Preço promocional</label><input id="promo" class="input" type="number" step="0.01" min="0.01" formControlName="promoPrice">
                <span class="hint">Deve ser menor que o preço de venda.</span></div>
            </div>
            @if (margin() !== null) { <p class="hint" style="margin-top:8px">Margem bruta estimada: <b>{{ margin() }}%</b> sobre o preço praticado.</p> }
          </section>

          <section class="card">
            <div class="row-between">
              <h2>Variações e estoque</h2>
              <label class="check"><input type="checkbox" formControlName="hasVariants" [attr.disabled]="!isNew() ? true : null"> Produto com variações (ex.: tamanho e cor)</label>
            </div>
            @if (!isNew()) { <p class="hint">O tipo do produto (simples ou com variações) não muda após o cadastro. O estoque é movimentado na tela <a routerLink="/painel/estoque">Estoque</a>.</p> }

            @if (!form.controls.hasVariants.value) {
              @if (isNew()) {
                <div class="field" style="max-width:220px"><label for="init">Estoque inicial</label><input id="init" class="input" type="number" min="0" formControlName="initialStock"></div>
              } @else if (product()?.variants?.[0]; as v) {
                <p>Físico <b>{{ v.physicalQty }}</b> · Reservado <b>{{ v.reservedQty }}</b> · Disponível <b>{{ v.availableQty }}</b></p>
              }
            } @else {
              <div class="grid grid-2">
                <div class="field"><label for="o1">Nome da variação 1 *</label><input id="o1" class="input" formControlName="option1Name" placeholder="Tamanho"></div>
                <div class="field"><label for="o2">Nome da variação 2</label><input id="o2" class="input" formControlName="option2Name" placeholder="Cor"></div>
              </div>
              <div class="table-wrap" style="margin-top:12px">
                <table class="table cards" formArrayName="variants">
                  <thead><tr><th>{{ form.controls.option1Name.value || 'Variação 1' }}</th>@if (form.controls.option2Name.value) { <th>{{ form.controls.option2Name.value }}</th> }
                    <th>SKU</th><th>Preço próprio</th><th>{{ isNew() ? 'Estoque inicial' : 'Disponível' }}</th><th>Ativa</th><th></th></tr></thead>
                  <tbody>
                    @for (g of variantGroups(); track $index; let i = $index) {
                      <tr [formGroupName]="i">
                        <td [attr.data-label]="form.controls.option1Name.value || 'Variação 1'"><input class="input" formControlName="option1Value" aria-label="Valor da variação 1"></td>
                        @if (form.controls.option2Name.value) { <td [attr.data-label]="form.controls.option2Name.value"><input class="input" formControlName="option2Value" aria-label="Valor da variação 2"></td> }
                        <td data-label="SKU"><input class="input" formControlName="sku" aria-label="SKU da variação"></td>
                        <td data-label="Preço próprio"><input class="input" type="number" step="0.01" formControlName="priceOverride" placeholder="padrão" aria-label="Preço próprio"></td>
                        <td data-label="Estoque">
                          @if (g.value.id) { {{ availableOf(g.value.id) }} } @else { <input class="input" type="number" min="0" formControlName="initialStock" aria-label="Estoque inicial"> }
                        </td>
                        <td data-label="Ativa"><input type="checkbox" formControlName="active" aria-label="Variação ativa"></td>
                        <td>@if (!g.value.id) { <button type="button" class="btn btn-ghost btn-sm btn-icon" (click)="removeVariant(i)" aria-label="Remover variação"><app-icon name="trash" [size]="15" /></button> }</td>
                      </tr>
                    }
                  </tbody>
                </table>
              </div>
              <button type="button" class="btn btn-sm" style="margin-top:10px" (click)="addVariant()"><app-icon name="plus" /> Adicionar variação</button>
              <p class="hint">Variações já usadas não são apagadas: desmarque "Ativa" para retirá-las de venda.</p>
            }
          </section>
        </div>

        <aside class="stack">
          <section class="card">
            <h2>Publicação</h2>
            <div class="stack-sm">
              <label class="check"><input type="checkbox" formControlName="active"> Ativo (pode ser vendido)</label>
              <label class="check"><input type="checkbox" formControlName="visible"> Visível na loja virtual</label>
              <label class="check"><input type="checkbox" formControlName="featured"> Destaque na página inicial</label>
            </div>
            <div class="field" style="margin-top:12px"><label for="min">Estoque mínimo (alerta)</label><input id="min" class="input" type="number" min="0" formControlName="minStock"></div>
          </section>

          <section class="card">
            <h2>Fotos</h2>
            @if (isNew()) { <p class="hint">Salve o produto para adicionar fotos.</p> }
            @else {
              <div class="photos">
                @for (img of product()?.images ?? []; track img.id; let first = $first) {
                  <figure>
                    <img [src]="img.url" alt="" loading="lazy" width="96" height="96">
                    @if (first) { <span class="badge primary main">Principal</span> }
                    <div class="row" style="gap:2px">
                      @if (!first) { <button type="button" class="btn btn-ghost btn-sm btn-icon" (click)="mainImage(img.id)" aria-label="Definir como principal"><app-icon name="star" [size]="14" /></button> }
                      <button type="button" class="btn btn-ghost btn-sm btn-icon" (click)="removeImage(img.id)" aria-label="Remover foto"><app-icon name="trash" [size]="14" /></button>
                    </div>
                  </figure>
                }
              </div>
              <label class="btn btn-sm upload" [class.disabled]="uploading()">
                <app-icon name="upload" /> {{ uploading() ? 'Enviando...' : 'Enviar foto' }}
                <input type="file" accept="image/jpeg,image/png" class="sr-only" (change)="upload($event)" [disabled]="uploading()">
              </label>
              <p class="hint">JPG ou PNG, até 3 MB. Imagens grandes são reduzidas automaticamente.</p>
            }
          </section>

          @if (formError()) { <div class="alert danger" role="alert">{{ formError() }}</div> }
          <button class="btn btn-primary btn-block" type="submit" [disabled]="busy()">{{ busy() ? 'Salvando...' : 'Salvar produto' }}</button>
        </aside>
      </form>
    }

    <app-modal title="Excluir produto?" [open]="confirmDelete()" (closed)="confirmDelete.set(false)" [busy]="busy()">
      <p>A exclusão é definitiva. Ela só é permitida para produtos sem pedidos e sem movimentações de estoque; nos demais casos, arquive.</p>
      <div footer>
        <button class="btn" type="button" (click)="confirmDelete.set(false)">Cancelar</button>
        <button class="btn btn-danger" type="button" (click)="remove()" [disabled]="busy()">{{ busy() ? 'Excluindo...' : 'Excluir' }}</button>
      </div>
    </app-modal>`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) 320px; gap: 16px; align-items: start; }
    aside { position: sticky; top: 84px; }
    @media (max-width: 960px) { .layout { grid-template-columns: minmax(0, 1fr); } aside { position: static; } }
    .photos { display: grid; grid-template-columns: repeat(auto-fill, minmax(96px, 1fr)); gap: 8px; margin-bottom: 10px; }
    .photos figure { margin: 0; position: relative; }
    .photos img { width: 100%; aspect-ratio: 1; object-fit: cover; border-radius: 10px; background: var(--surface-3); }
    .main { position: absolute; top: 4px; left: 4px; }
    .upload { cursor: pointer; }
    .upload.disabled { opacity: .6; pointer-events: none; }
  `],
})
export class ProductFormPage implements OnInit {
  private api = inject(AdminApi);
  private fb = inject(NonNullableFormBuilder);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly id = input<string>();
  readonly isNew = computed(() => !this.id() || this.id() === 'novo');
  readonly product = signal<ProductDetail | null>(null);
  readonly categories = signal<Category[]>([]);
  readonly loading = signal(false);
  readonly busy = signal(false);
  readonly uploading = signal(false);
  readonly formError = signal('');
  readonly errors = signal<Record<string, string>>({});
  readonly confirmDelete = signal(false);
  readonly units = [['UN', 'Unidade'], ['KG', 'Quilograma'], ['G', 'Grama'], ['L', 'Litro'], ['ML', 'Mililitro'], ['M', 'Metro'], ['CX', 'Caixa'], ['PC', 'Pacote'], ['PAR', 'Par'], ['KIT', 'Kit']];

  readonly form = this.fb.group({
    name: ['', Validators.required], description: [''], categoryId: this.fb.control<number | null>(null),
    sku: ['', [Validators.required, Validators.pattern(/^[A-Za-z0-9._-]+$/)]], barcode: [''],
    costPrice: this.fb.control<number | null>(null, Validators.required),
    salePrice: this.fb.control<number | null>(null, Validators.required),
    promoPrice: this.fb.control<number | null>(null),
    unit: ['UN'], minStock: [0], active: [true], visible: [true], featured: [false],
    hasVariants: [false], option1Name: [''], option2Name: [''], initialStock: [0],
    variants: this.fb.array<FormGroup>([]),
  });

  readonly variantGroups = signal<FormGroup[]>([]);
  readonly margin = signal<string | null>(null);

  ngOnInit() {
    this.api.categories().subscribe(c => this.categories.set(c));
    this.form.valueChanges.subscribe(() => this.updateMargin());
    if (!this.isNew()) {
      this.loading.set(true);
      this.api.product(Number(this.id())).subscribe({
        next: p => { this.fill(p); this.loading.set(false); },
        error: e => { this.loading.set(false); this.toast.error(e); this.router.navigateByUrl('/painel/produtos'); },
      });
    }
  }

  get variantsArray() { return this.form.controls.variants as FormArray<FormGroup>; }

  private variantGroup(v?: Partial<{ id: number; option1Value: string; option2Value: string; sku: string; priceOverride: number; active: boolean }>) {
    return this.fb.group({
      id: this.fb.control<number | null>(v?.id ?? null), option1Value: [v?.option1Value ?? ''], option2Value: [v?.option2Value ?? ''],
      sku: [v?.sku ?? ''], priceOverride: this.fb.control<number | null>(v?.priceOverride ?? null), initialStock: [0], active: [v?.active ?? true],
    });
  }

  addVariant() {
    const base = this.form.controls.sku.value.toUpperCase();
    this.variantsArray.push(this.variantGroup({ sku: base ? `${base}-${this.variantsArray.length + 1}` : '' }));
    this.variantGroups.set([...this.variantsArray.controls]);
  }

  removeVariant(i: number) {
    this.variantsArray.removeAt(i);
    this.variantGroups.set([...this.variantsArray.controls]);
  }

  availableOf(id: number) { return this.product()?.variants.find(v => v.id === id)?.availableQty ?? 0; }

  err(field: string) { return this.errors()[field]; }

  private fill(p: ProductDetail) {
    this.product.set(p);
    this.form.patchValue({ ...p, promoPrice: p.promoPrice ?? null, categoryId: p.categoryId ?? null, barcode: p.barcode ?? '', description: p.description ?? '', option1Name: p.option1Name ?? '', option2Name: p.option2Name ?? '' });
    this.variantsArray.clear();
    if (p.hasVariants) {
      p.variants.forEach(v => this.variantsArray.push(this.variantGroup({ ...v, option1Value: v.option1Value ?? '', option2Value: v.option2Value ?? '', priceOverride: v.priceOverride })));
    }
    this.variantGroups.set([...this.variantsArray.controls]);
    this.updateMargin();
  }

  private updateMargin() {
    const v = this.form.getRawValue();
    const price = v.promoPrice || v.salePrice;
    if (!price || v.costPrice === null) { this.margin.set(null); return; }
    this.margin.set((((price - v.costPrice) / price) * 100).toLocaleString('pt-BR', { maximumFractionDigits: 1 }));
  }

  save() {
    this.formError.set('');
    this.errors.set({});
    if (this.form.invalid) { this.form.markAllAsTouched(); this.formError.set('Preencha nome, SKU e preços.'); return; }
    const v = this.form.getRawValue();
    if (v.hasVariants && this.variantsArray.length === 0) { this.formError.set('Adicione ao menos uma variação.'); return; }
    if (this.busy()) return;
    const body = {
      ...v,
      variants: v.hasVariants
        ? this.variantsArray.getRawValue().map(x => ({ ...x, priceOverride: x['priceOverride'] || null }))
        : [{ id: null, sku: null, initialStock: v.initialStock || 0, active: true }],
    };
    this.busy.set(true);
    const id = this.isNew() ? null : Number(this.id());
    this.api.saveProduct(id, body).subscribe({
      next: p => {
        this.busy.set(false);
        this.toast.success('Produto salvo.');
        if (!id) this.router.navigate(['/painel/produtos', p.id]); else this.fill(p);
      },
      error: e => { this.busy.set(false); this.errors.set(fieldErrors(e)); this.formError.set(errorMessage(e)); },
    });
  }

  archive(archived: boolean) {
    this.busy.set(true);
    this.api.archiveProduct(Number(this.id()), archived).subscribe({
      next: p => { this.busy.set(false); this.fill(p); this.toast.success(archived ? 'Produto arquivado.' : 'Produto restaurado.'); },
      error: e => { this.busy.set(false); this.toast.error(e); },
    });
  }

  remove() {
    this.busy.set(true);
    this.api.deleteProduct(Number(this.id())).subscribe({
      next: () => { this.toast.success('Produto excluído.'); this.router.navigateByUrl('/painel/produtos'); },
      error: e => { this.busy.set(false); this.confirmDelete.set(false); this.toast.error(e); },
    });
  }

  upload(ev: Event) {
    const input = ev.target as HTMLInputElement;
    const file = input.files?.[0];
    input.value = '';
    if (!file) return;
    if (file.size > 3 * 1024 * 1024) { this.toast.error('A imagem deve ter no máximo 3 MB.'); return; }
    this.uploading.set(true);
    this.api.addProductImage(Number(this.id()), file).subscribe({
      next: p => { this.uploading.set(false); this.product.set(p); this.toast.success('Foto adicionada.'); },
      error: e => { this.uploading.set(false); this.toast.error(e); },
    });
  }

  mainImage(imageId: number) {
    this.api.mainProductImage(Number(this.id()), imageId).subscribe({ next: p => this.product.set(p), error: e => this.toast.error(e) });
  }

  removeImage(imageId: number) {
    this.api.removeProductImage(Number(this.id()), imageId).subscribe({
      next: p => { this.product.set(p); this.toast.success('Foto removida.'); }, error: e => this.toast.error(e),
    });
  }
}
