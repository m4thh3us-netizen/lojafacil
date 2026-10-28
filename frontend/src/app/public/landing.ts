import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterLink } from '@angular/router';

import { AuthService } from '../core/auth.service';
import { Icon } from '../shared/icon';
import { Brand, ThemeToggle } from '../shared/theme-toggle';

@Component({
  selector: 'app-landing',
  imports: [RouterLink, Icon, Brand, ThemeToggle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <header class="top glass">
      <div class="container row-between">
        <a routerLink="/" aria-label="LojaFácil - início"><app-brand /></a>
        <nav class="row">
          <app-theme-toggle />
          @if (auth.me()) {
            <a class="btn btn-primary" routerLink="/painel">Ir para o painel</a>
          } @else {
            <a class="btn btn-ghost" routerLink="/login">Entrar</a>
            <a class="btn btn-primary" routerLink="/cadastro">Criar minha loja</a>
          }
        </nav>
      </div>
    </header>
    <main id="conteudo" class="container">
      <section class="hero fade-in">
        <span class="badge primary">Gestão completa para pequenas lojas</span>
        <h1>Catálogo, estoque, pedidos e financeiro<br>num só lugar — com loja virtual inclusa.</h1>
        <p class="lead">Cadastre produtos com variações, venda pela sua vitrine, receba pagamentos online
          e acompanhe o resultado com números claros. Sem planilhas soltas.</p>
        <div class="row">
          <a class="btn btn-primary" routerLink="/cadastro">Começar agora <app-icon name="arrowRight" /></a>
          <a class="btn" routerLink="/loja/demo">Ver loja demonstrativa</a>
          <a class="btn btn-ghost" routerLink="/login">Acessar painel demo</a>
        </div>
      </section>
      <section class="grid grid-3 features">
        @for (f of features; track f.title) {
          <article class="card glass">
            <div class="ic"><app-icon [name]="f.icon" [size]="20" /></div>
            <h3>{{ f.title }}</h3>
            <p class="text-2">{{ f.text }}</p>
          </article>
        }
      </section>
    </main>
    <footer class="container foot muted">LojaFácil · projeto de portfólio · sem emissão fiscal</footer>`,
  styles: [`
    .top { position: sticky; top: 0; z-index: 20; border-width: 0 0 1px; }
    .top .container { min-height: 64px; }
    .hero { padding: 72px 0 40px; max-width: 820px; }
    .hero h1 { font-size: clamp(1.8rem, 4vw, 2.8rem); margin: 14px 0; letter-spacing: -0.03em; }
    .lead { font-size: 1.1rem; color: var(--text-2); max-width: 640px; margin-bottom: 24px; }
    .features { padding-bottom: 48px; }
    .ic { width: 40px; height: 40px; border-radius: 12px; display: grid; place-items: center; background: var(--primary-soft); color: var(--primary); margin-bottom: 12px; }
    .foot { padding: 24px 16px 40px; font-size: .85rem; }
  `],
})
export class Landing {
  readonly auth = inject(AuthService);
  readonly features = [
    { icon: 'layers', title: 'Estoque confiável', text: 'Saldo físico, reservado e disponível. Reserva ao iniciar o pagamento e proteção contra venda dupla da última unidade.' },
    { icon: 'store', title: 'Loja virtual própria', text: 'Vitrine personalizável com suas cores, carrinho, checkout como visitante e acompanhamento por link seguro.' },
    { icon: 'card', title: 'Pagamentos online', text: 'Checkout hospedado do Mercado Pago (ambiente de teste) ou simulador local, com confirmação pelo servidor.' },
    { icon: 'receipt', title: 'Pedidos organizados', text: 'Estados de pedido, pagamento e atendimento separados, com histórico, devoluções e reembolsos.' },
    { icon: 'wallet', title: 'Financeiro claro', text: 'Contas a pagar e receber, parcelas, baixas, estornos e fluxo de caixa realizado e previsto.' },
    { icon: 'shield', title: 'Equipe com perfis', text: 'Proprietário, gerente e operador, com permissões verificadas no servidor e isolamento entre lojas.' },
  ];
}

@Component({
  selector: 'app-not-found',
  imports: [RouterLink, Icon],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <main id="conteudo" class="container" style="padding: 96px 16px; text-align: center">
      <div class="empty">
        <div class="icon-wrap"><app-icon name="search" [size]="24" /></div>
        <h1>Página não encontrada</h1>
        <p>O endereço pode estar incorreto ou a página foi removida.</p>
        <a class="btn btn-primary" routerLink="/">Voltar ao início</a>
      </div>
    </main>`,
})
export class NotFound {}
