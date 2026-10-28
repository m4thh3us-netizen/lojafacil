import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter } from 'rxjs';

import { AppearanceService } from '../core/appearance.service';
import { AuthService } from '../core/auth.service';
import { LABELS } from '../core/format';
import { Icon } from '../shared/icon';
import { Brand, ThemeToggle } from '../shared/theme-toggle';

interface NavItem { path: string; label: string; icon: string; exact?: boolean; management?: boolean; }

/**
 * Estrutura do painel: fundo (fotografia ou cor da loja) fixo atrás de tudo e cabeçalho em
 * grupos de vidro — marca à esquerda, navegação em cápsulas ao centro, ações e perfil à direita.
 * Em telas menores a navegação vira um menu recolhível.
 */
@Component({
  selector: 'app-admin-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Icon, Brand, ThemeToggle],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <div class="backdrop" aria-hidden="true" [style.background-color]="bg().color"
         [style.background-image]="bg().image" [style.background-position]="bg().position">
      <div class="dim" [style.background-color]="bg().dim"></div>
    </div>

    <header class="top">
      <div class="container bar">
        <a routerLink="/painel" class="pill logo" aria-label="Painel - visão geral"><app-brand /></a>

        <nav id="main-nav" class="nav" [class.open]="menuOpen()" aria-label="Navegação principal">
          @for (item of items(); track item.path) {
            <a [routerLink]="item.path" routerLinkActive="active" [routerLinkActiveOptions]="{ exact: !!item.exact }"
               #rla="routerLinkActive" [attr.aria-current]="rla.isActive ? 'page' : null">
              <app-icon [name]="item.icon" [size]="15" /> <span>{{ item.label }}</span>
            </a>
          }
          <div class="nav-extra">
            <a [routerLink]="['/loja', me()?.store?.slug]" target="_blank" rel="noopener"><app-icon name="store" [size]="15" /> <span>Ver loja virtual</span></a>
            <app-theme-toggle />
          </div>
        </nav>

        <div class="actions">
          @if (me()?.store?.demo) { <span class="pill demo" title="Ambiente demonstrativo: dados fictícios e pagamentos simulados">DEMO</span> }
          <span class="pill icons">
            <a class="btn btn-ghost btn-icon btn-sm" [routerLink]="['/loja', me()?.store?.slug]" target="_blank" rel="noopener" aria-label="Abrir loja virtual em nova aba" title="Ver loja virtual">
              <app-icon name="store" [size]="16" />
            </a>
            <app-theme-toggle />
          </span>
          <div class="user">
            <button class="pill user-btn" type="button" (click)="userOpen.update(v => !v)" [attr.aria-expanded]="userOpen()" aria-haspopup="menu" aria-controls="user-menu">
              <span class="avatar" aria-hidden="true">{{ initials() }}</span>
              <span class="who"><b>{{ me()?.user?.fullName }}</b><small>{{ roleLabel() }}</small></span>
              <app-icon name="chevronDown" [size]="14" />
            </button>
            @if (userOpen()) {
              <div id="user-menu" class="menu fade-in" role="menu">
                <div class="menu-head"><b>{{ me()?.store?.name }}</b><small>{{ me()?.user?.email }}</small></div>
                <a role="menuitem" routerLink="/painel/configuracoes" (click)="userOpen.set(false)"><app-icon name="settings" /> Configurações</a>
                <button role="menuitem" type="button" (click)="logout()"><app-icon name="logout" /> Sair</button>
              </div>
            }
          </div>
          <button class="pill burger" type="button" (click)="menuOpen.update(v => !v)"
                  [attr.aria-expanded]="menuOpen()" aria-controls="main-nav" [attr.aria-label]="menuOpen() ? 'Fechar menu' : 'Abrir menu'">
            <app-icon [name]="menuOpen() ? 'close' : 'menu'" />
          </button>
        </div>
      </div>
    </header>

    @if (appearance.previewing()) {
      <div class="container"><div class="preview-note" role="status"><app-icon name="eye" [size]="15" /> Pré-visualização da aparência — ainda não salva.</div></div>
    }
    <main id="conteudo" class="container page"><router-outlet /></main>`,
  styles: [`
    :host { display: block; min-height: 100vh; position: relative; isolation: isolate; }
    .backdrop { position: fixed; inset: 0; z-index: -1; background-size: cover; background-repeat: no-repeat; transition: background-color .2s; }
    .dim { position: absolute; inset: 0; }

    .top { position: sticky; top: 0; z-index: 30; padding: 10px 0; }
    .bar { display: flex; align-items: center; gap: 10px; max-width: 1600px; }
    .pill {
      display: inline-flex; align-items: center; gap: 6px; min-height: 44px; padding: 0 12px; border-radius: 999px;
      background: var(--panel-strong); border: 1px solid var(--glass-border); color: var(--text);
      -webkit-backdrop-filter: blur(var(--blur)); backdrop-filter: blur(var(--blur)); box-shadow: var(--shadow);
      font: inherit; text-decoration: none; cursor: pointer;
    }
    .pill:hover { text-decoration: none; }
    .logo { flex-shrink: 0; padding: 0 14px 0 8px; }
    .icons { padding: 0 4px; gap: 2px; cursor: default; }

    .nav {
      display: flex; gap: 4px; padding: 4px; border-radius: 999px; margin: 0 auto; min-width: 0;
      background: var(--panel-strong); border: 1px solid var(--glass-border);
      -webkit-backdrop-filter: blur(var(--blur)); backdrop-filter: blur(var(--blur)); box-shadow: var(--shadow);
    }
    .nav a {
      display: inline-flex; align-items: center; gap: 6px; padding: 7px 12px; border-radius: 999px;
      color: var(--text-2); font-weight: 550; font-size: .86rem; white-space: nowrap; text-decoration: none;
      transition: background-color .15s, color .15s, transform .1s;
    }
    .nav a:hover { background: var(--surface-3); color: var(--text); }
    .nav a:active { transform: scale(.96); }
    .nav a.active { background: var(--primary); color: var(--on-primary); }

    .actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
    .demo { min-height: 30px; font-size: .72rem; font-weight: 700; letter-spacing: .05em; color: var(--warning); background: var(--warning-soft); border-style: dashed; border-color: var(--warning); box-shadow: none; cursor: default; }
    .user { position: relative; }
    .user-btn { padding: 0 10px 0 4px; gap: 8px; }
    .avatar { width: 34px; height: 34px; border-radius: 50%; display: grid; place-items: center; background: var(--primary); color: var(--on-primary); font-size: .75rem; font-weight: 700; }
    .who { display: flex; flex-direction: column; text-align: left; line-height: 1.15; }
    .who b { font-size: .84rem; font-weight: 600; max-width: 150px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
    .who small { font-size: .72rem; }
    .menu {
      position: absolute; right: 0; top: calc(100% + 8px); min-width: 230px; border-radius: 14px; padding: 6px;
      display: flex; flex-direction: column; background: var(--surface); border: 1px solid var(--border); box-shadow: var(--shadow-lg);
    }
    .menu-head { display: flex; flex-direction: column; padding: 8px 10px 10px; border-bottom: 1px solid var(--border); margin-bottom: 4px; }
    .menu-head small { overflow: hidden; text-overflow: ellipsis; }
    .menu a, .menu button { display: flex; align-items: center; gap: 8px; padding: 9px 10px; border-radius: 9px; color: var(--text); background: none; border: 0; font: inherit; cursor: pointer; text-decoration: none; text-align: left; }
    .menu a:hover, .menu button:hover { background: var(--surface-3); }
    .burger { display: none; width: 44px; justify-content: center; padding: 0; }
    .nav-extra { display: none; }
    .preview-note { display: inline-flex; align-items: center; gap: 8px; margin-top: 2px; padding: 6px 14px; border-radius: 999px; background: var(--warning-soft); color: var(--warning); font-size: .82rem; font-weight: 600; border: 1px solid var(--warning); }

    /* Pontos de quebra calculados para a navegação nunca encostar nas ações */
    @media (max-width: 1700px) { .nav a app-icon { display: none !important; } }
    @media (max-width: 1380px) { .who { display: none; } .user-btn { padding: 0 8px 0 4px; } }
    /* Abaixo de 1240px a navegação não cabe numa linha: vira menu recolhível, com fundo sólido */
    @media (max-width: 1240px) {
      .nav a app-icon { display: inline-flex !important; }
      .burger { display: inline-flex; }
      .actions { margin-left: auto; }
      .nav {
        position: absolute; left: 16px; right: 16px; top: 64px; flex-direction: column; border-radius: 16px; padding: 8px;
        background: var(--surface); box-shadow: var(--shadow-lg); display: none;
      }
      .nav.open { display: flex; animation: fadeIn .15s var(--ease); }
      .nav a { padding: 11px 12px; border-radius: 10px; font-size: .95rem; }
    }
    @media (max-width: 1240px) {
      .nav-extra { display: flex; align-items: center; justify-content: space-between; border-top: 1px solid var(--border); margin-top: 4px; padding-top: 4px; }
    }
    @media (max-width: 640px) {
      .who, .demo { display: none; }
      .user-btn { padding: 0 6px 0 4px; }
      .logo { padding: 0 8px; }
      .logo ::ng-deep .brand > span { display: none; }
    }
    @media (max-width: 560px) {
      .icons, .user-btn app-icon { display: none !important; }
      .bar, .actions { gap: 6px; }
    }
  `],
})
export class AdminShell {
  readonly auth = inject(AuthService);
  readonly appearance = inject(AppearanceService);
  readonly me = this.auth.me;
  readonly menuOpen = signal(false);
  readonly userOpen = signal(false);

  private all: NavItem[] = [
    { path: '/painel', label: 'Visão geral', icon: 'dashboard', exact: true },
    { path: '/painel/pedidos', label: 'Pedidos', icon: 'receipt' },
    { path: '/painel/produtos', label: 'Produtos', icon: 'box' },
    { path: '/painel/estoque', label: 'Estoque', icon: 'layers' },
    { path: '/painel/clientes', label: 'Clientes', icon: 'users' },
    { path: '/painel/fornecedores', label: 'Fornecedores', icon: 'truck' },
    { path: '/painel/financeiro', label: 'Financeiro', icon: 'wallet', management: true },
    { path: '/painel/configuracoes', label: 'Configurações', icon: 'settings' },
  ];
  readonly items = computed(() => this.all.filter(i => !i.management || this.auth.isManagement()));
  readonly initials = computed(() => (this.me()?.user.fullName ?? '').split(' ').filter(Boolean).slice(0, 2).map(p => p[0]).join('').toUpperCase());
  readonly roleLabel = computed(() => LABELS[this.me()?.user.role ?? ''] ?? '');

  /** Fundo do painel: fotografia com posição escolhida + camada escura uniforme, ou cor sólida. */
  readonly bg = computed(() => {
    const a = this.appearance.current();
    const photo = a.mode === 'PHOTO' && a.imageUrl;
    return {
      color: a.color,
      image: photo ? `url("${a.imageUrl}")` : 'none',
      position: `${a.posX}% ${a.posY}%`,
      dim: photo ? `rgb(0 0 0 / ${a.dim / 100})` : 'transparent',
    };
  });

  constructor() {
    this.appearance.load();
    inject(Router).events.pipe(filter(e => e instanceof NavigationEnd), takeUntilDestroyed())
      .subscribe(() => { this.menuOpen.set(false); this.userOpen.set(false); });
  }

  logout() {
    this.appearance.clear();
    this.auth.logout();
  }
}
