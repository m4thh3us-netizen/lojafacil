import { ChangeDetectionStrategy, Component, OnInit, inject, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink, RouterOutlet } from '@angular/router';
import { Title } from '@angular/platform-browser';

import { PublicApi } from '../core/api';
import { Icon } from '../shared/icon';
import { StoreContext } from './store-context';

/**
 * Moldura da vitrine. As cores do lojista viram variáveis CSS locais (--brand, --brand-ink...),
 * sem afetar o tema pessoal do painel. A vitrine usa sempre superfícies claras próprias.
 */
@Component({
  selector: 'app-store-shell',
  imports: [RouterOutlet, RouterLink, FormsModule, Icon],
  providers: [StoreContext],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="storefront" [style.--brand]="ctx.store()?.primaryColor" [style.--brand-ink]="ctx.store()?.onPrimaryColor"
         [style.--brand-2]="ctx.store()?.secondaryColor" [style.--brand-2-ink]="ctx.store()?.onSecondaryColor">
      @if (notFound()) {
        <main id="conteudo" class="container" style="padding:96px 16px;text-align:center">
          <h1>Loja não encontrada</h1><p class="text-2">Confira o endereço digitado.</p><a routerLink="/" class="btn">Voltar</a>
        </main>
      } @else {
        @if (ctx.store()?.demo) { <div class="demo">Loja demonstrativa — produtos e pedidos fictícios, pagamentos simulados.</div> }
        <header class="s-head">
          <div class="container row-between bar">
            <a [routerLink]="['/loja', slug()]" class="s-brand">
              @if (ctx.store()?.logoUrl) { <img [src]="ctx.store()!.logoUrl" alt="" width="36" height="36"> }
              @if (ctx.store()) { <span>{{ ctx.store()!.name }}</span> } @else { <span class="skeleton" style="width:140px;height:20px"></span> }
            </a>
            <form class="s-search" role="search" (ngSubmit)="search()">
              <input class="input" type="search" name="q" [(ngModel)]="q" placeholder="Buscar produtos" aria-label="Buscar produtos">
              <button class="btn btn-ghost btn-icon btn-sm" aria-label="Buscar"><app-icon name="search" /></button>
            </form>
            <nav class="row" aria-label="Loja">
              <a [routerLink]="['/loja', slug(), 'produtos']" class="btn btn-ghost">Produtos</a>
              <a [routerLink]="['/loja', slug(), 'carrinho']" class="btn btn-ghost cart" [attr.aria-label]="'Carrinho com ' + ctx.cartCount() + ' itens'">
                <app-icon name="cart" /> @if (ctx.cartCount()) { <span class="count">{{ ctx.cartCount() }}</span> }
              </a>
            </nav>
          </div>
        </header>
        <main id="conteudo" class="container s-main"><router-outlet /></main>
        <footer class="s-foot">
          @if (ctx.store(); as s) {
            <div class="container grid grid-3">
              <div><b>{{ s.name }}</b><p>{{ s.description }}</p></div>
              <div class="stack-sm">
                @if (s.address) { <span><app-icon name="map" [size]="14" /> {{ s.address }}</span> }
                @if (s.serviceHours) { <span><app-icon name="clock" [size]="14" /> {{ s.serviceHours }}</span> }
              </div>
              <div class="stack-sm">
                @if (s.contactPhone) { <span><app-icon name="phone" [size]="14" /> {{ s.contactPhone }}</span> }
                @if (s.whatsapp) { <span>WhatsApp: {{ s.whatsapp }}</span> }
                @if (s.contactEmail) { <span><app-icon name="mail" [size]="14" /> {{ s.contactEmail }}</span> }
                @if (s.instagram) { <span>Instagram: {{ s.instagram }}</span> }
              </div>
            </div>
          }
          <div class="container powered">Loja criada com LojaFácil</div>
        </footer>
      }
    </div>`,
  styleUrl: './storefront.scss',
})
export class StoreShell implements OnInit {
  private api = inject(PublicApi);
  private router = inject(Router);
  private title = inject(Title);
  readonly ctx = inject(StoreContext);
  readonly slug = input.required<string>();
  readonly notFound = signal(false);
  q = '';

  ngOnInit() {
    this.ctx.init(this.slug());
    this.api.store(this.slug()).subscribe({
      next: s => { this.ctx.store.set(s); this.title.setTitle(s.name); },
      error: () => this.notFound.set(true),
    });
  }

  search() {
    this.router.navigate(['/loja', this.slug(), 'produtos'], { queryParams: { q: this.q || null } });
  }
}
