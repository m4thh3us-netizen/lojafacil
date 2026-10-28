import { ChangeDetectionStrategy, Component, OnInit, computed, inject, input, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { PublicApi } from '../core/api';
import { BrlPipe } from '../core/format';
import { PublicProduct, PublicVariant } from '../core/models';
import { ToastService } from '../core/ui.services';
import { Icon } from '../shared/icon';
import { StoreContext } from './store-context';

@Component({
  selector: 'app-store-product',
  imports: [RouterLink, BrlPipe, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <a [routerLink]="['/loja', ctx.slug(), 'produtos']" class="back"><app-icon name="arrowLeft" [size]="14" /> Produtos</a>
    @if (notFound()) {
      <div class="card" style="margin-top:12px"><h2>Produto indisponível</h2><p class="text-2">Ele pode ter sido removido da loja.</p></div>
    } @else if (!p()) {
      <div class="layout" style="margin-top:12px"><span class="skeleton" style="aspect-ratio:1;border-radius:18px"></span>
        <div><span class="skeleton title"></span><span class="skeleton line"></span><span class="skeleton line" style="width:60%"></span></div></div>
    } @else {
      @let prod = p()!;
      <div class="layout fade-in" style="margin-top:12px">
        <div class="gallery">
          <div class="main">@if (prod.images.length) { <img [src]="prod.images[imageIndex()]" [alt]="prod.name" width="800" height="800"> } @else { <app-icon name="image" [size]="40" /> }</div>
          @if (prod.images.length > 1) {
            <div class="thumbs" role="list">
              @for (img of prod.images; track img; let i = $index) {
                <button type="button" role="listitem" [class.on]="i === imageIndex()" (click)="imageIndex.set(i)" [attr.aria-label]="'Foto ' + (i + 1)"><img [src]="img" alt="" loading="lazy"></button>
              }
            </div>
          }
        </div>
        <div class="buy">
          @if (prod.category) { <a class="hint" [routerLink]="['/loja', ctx.slug(), 'produtos']" [queryParams]="{ categoria: prod.category.slug }">{{ prod.category.name }}</a> }
          <h1>{{ prod.name }}</h1>
          <div class="price">
            @if (variant(); as v) { @if (v.finalPrice < v.price) { <s>{{ v.price | brl }}</s> } <b>{{ v.finalPrice | brl }}</b> }
            @else { @if (prod.onSale) { <s>{{ prod.price | brl }}</s> } <b>{{ prod.finalPrice | brl }}</b> }
            <small> / {{ prod.unit === 'UN' ? 'unidade' : prod.unit }}</small>
          </div>

          @if (prod.hasVariants) {
            <fieldset class="opt"><legend>{{ prod.option1Name }}</legend>
              <div class="chips">@for (v of option1Values(); track v) { <button type="button" class="chip" [class.on]="opt1() === v" [attr.aria-pressed]="opt1() === v" (click)="pick1(v)">{{ v }}</button> }</div>
            </fieldset>
            @if (prod.option2Name) {
              <fieldset class="opt"><legend>{{ prod.option2Name }}</legend>
                <div class="chips">@for (v of option2Values(); track v) {
                  <button type="button" class="chip" [class.on]="opt2() === v" [attr.aria-pressed]="opt2() === v" [disabled]="!combinationExists(v)" (click)="opt2.set(v)">{{ v }}</button> }</div>
              </fieldset>
            }
          }

          @if (variant(); as v) {
            @if (!v.available) { <p class="stock out">Esgotado nesta opção.</p> }
            @else if (v.lastUnits) { <p class="stock low">Últimas unidades!</p> }
          } @else if (prod.hasVariants) { <p class="hint">Escolha as opções para ver a disponibilidade.</p> }

          <div class="row">
            <div class="qty" role="group" aria-label="Quantidade">
              <button type="button" class="btn btn-icon" (click)="qty.set(Math.max(1, qty() - 1))" aria-label="Diminuir"><app-icon name="minus" /></button>
              <span aria-live="polite">{{ qty() }}</span>
              <button type="button" class="btn btn-icon" (click)="qty.set(Math.min(99, qty() + 1))" aria-label="Aumentar"><app-icon name="plus" /></button>
            </div>
            <button class="btn btn-primary spacer" type="button" (click)="add(false)" [disabled]="!variant()?.available"><app-icon name="cart" /> Adicionar ao carrinho</button>
          </div>
          <button class="btn btn-block" type="button" style="margin-top:8px" (click)="add(true)" [disabled]="!variant()?.available">Comprar agora</button>
          @if (prod.description) { <div class="desc"><h2>Descrição</h2><p>{{ prod.description }}</p></div> }
        </div>
      </div>
    }`,
  styles: [`
    .layout { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 32px; align-items: start; }
    @media (max-width: 800px) { .layout { grid-template-columns: minmax(0, 1fr); gap: 18px; } }
    .main { aspect-ratio: 1; border-radius: 20px; overflow: hidden; background: var(--surface-3); display: grid; place-items: center; color: var(--text-3); }
    .main img { width: 100%; height: 100%; object-fit: cover; }
    .thumbs { display: flex; gap: 8px; margin-top: 10px; overflow-x: auto; }
    .thumbs button { width: 68px; height: 68px; border-radius: 12px; overflow: hidden; border: 2px solid transparent; padding: 0; cursor: pointer; flex-shrink: 0; background: none; }
    .thumbs button.on { border-color: var(--brand); }
    .thumbs img { width: 100%; height: 100%; object-fit: cover; }
    .buy h1 { margin-top: 6px; }
    .price { font-size: 1.6rem; margin: 6px 0 16px; }
    .price s { font-size: 1rem; color: var(--text-3); margin-right: 6px; }
    .opt { margin-bottom: 14px; }
    .chips { display: flex; flex-wrap: wrap; gap: 8px; }
    .chip { min-width: 48px; padding: 8px 14px; border-radius: 10px; border: 1px solid var(--border-strong); background: var(--surface); color: var(--text); font: inherit; cursor: pointer; transition: border-color .12s, background .12s; }
    .chip.on { border-color: var(--brand); background: var(--primary-soft); color: var(--text); font-weight: 600; }
    .chip:disabled { opacity: .4; cursor: not-allowed; text-decoration: line-through; }
    .stock { font-weight: 600; }
    .stock.out { color: var(--danger); }
    .stock.low { color: var(--warning); }
    .qty { display: inline-flex; align-items: center; gap: 4px; border: 1px solid var(--border-strong); border-radius: 12px; padding: 2px; }
    .qty .btn { border: 0; min-height: 36px; width: 36px; }
    .qty span { min-width: 28px; text-align: center; font-weight: 600; }
    .desc { margin-top: 28px; white-space: pre-line; color: var(--text-2); }
  `],
})
export class StoreProduct implements OnInit {
  private api = inject(PublicApi);
  private router = inject(Router);
  private toast = inject(ToastService);
  readonly ctx = inject(StoreContext);
  readonly productSlug = input.required<string>();
  readonly p = signal<PublicProduct | null>(null);
  readonly notFound = signal(false);
  readonly imageIndex = signal(0);
  readonly qty = signal(1);
  readonly opt1 = signal<string | null>(null);
  readonly opt2 = signal<string | null>(null);
  readonly Math = Math;

  readonly option1Values = computed(() => [...new Set((this.p()?.variants ?? []).map(v => v.option1Value!).filter(Boolean))]);
  readonly option2Values = computed(() => [...new Set((this.p()?.variants ?? []).map(v => v.option2Value!).filter(Boolean))]);
  readonly variant = computed<PublicVariant | null>(() => {
    const p = this.p();
    if (!p) return null;
    if (!p.hasVariants) return p.variants[0] ?? null;
    return p.variants.find(v => v.option1Value === this.opt1() && (!p.option2Name || v.option2Value === this.opt2())) ?? null;
  });

  ngOnInit() {
    this.api.product(this.ctx.slug(), this.productSlug()).subscribe({
      next: p => {
        this.p.set(p);
        // pré-seleciona a primeira combinação disponível
        const first = p.variants.find(v => v.available) ?? p.variants[0];
        if (first && p.hasVariants) { this.opt1.set(first.option1Value ?? null); this.opt2.set(first.option2Value ?? null); }
      },
      error: () => this.notFound.set(true),
    });
  }

  combinationExists(o2: string) { return !!this.p()?.variants.some(v => v.option1Value === this.opt1() && v.option2Value === o2); }

  pick1(v: string) {
    this.opt1.set(v);
    if (this.opt2() && !this.combinationExists(this.opt2()!)) {
      this.opt2.set(this.p()!.variants.find(x => x.option1Value === v)?.option2Value ?? null);
    }
  }

  add(goToCart: boolean) {
    const p = this.p();
    const v = this.variant();
    if (!p || !v) return;
    this.ctx.add({ variantId: v.id, quantity: this.qty(), productSlug: p.slug, name: p.name, variantName: v.name, imageUrl: p.images[0], priceHint: v.finalPrice });
    if (goToCart) {
      this.router.navigate(['/loja', this.ctx.slug(), 'carrinho']);
    } else {
      this.toast.success('Produto adicionado ao carrinho.');
    }
  }
}
