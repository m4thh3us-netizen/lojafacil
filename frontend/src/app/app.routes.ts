import { Routes } from '@angular/router';

import { authGuard, guestGuard, roleGuard } from './core/http';

/** Todas as telas são carregadas sob demanda (lazy loading), reduzindo o pacote inicial. */
export const routes: Routes = [
  { path: '', loadComponent: () => import('./public/landing').then(m => m.Landing), title: 'LojaFácil' },
  { path: 'login', canActivate: [guestGuard], loadComponent: () => import('./public/auth-pages').then(m => m.LoginPage), title: 'Entrar · LojaFácil' },
  { path: 'cadastro', canActivate: [guestGuard], loadComponent: () => import('./public/register').then(m => m.RegisterPage), title: 'Criar loja · LojaFácil' },
  { path: 'recuperar-senha', loadComponent: () => import('./public/auth-pages').then(m => m.ForgotPage), title: 'Recuperar senha · LojaFácil' },
  { path: 'redefinir-senha', loadComponent: () => import('./public/auth-pages').then(m => m.ResetPage), title: 'Nova senha · LojaFácil' },
  { path: 'convite', loadComponent: () => import('./public/auth-pages').then(m => m.InvitePage), title: 'Convite · LojaFácil' },
  { path: 'pagamento-simulado/:id', loadComponent: () => import('./storefront/simulated-payment').then(m => m.SimulatedPayment), title: 'Pagamento simulado' },
  {
    path: 'painel',
    canActivate: [authGuard],
    loadComponent: () => import('./admin/shell').then(m => m.AdminShell),
    children: [
      { path: '', loadComponent: () => import('./admin/dashboard').then(m => m.DashboardPage), title: 'Visão geral · LojaFácil' },
      { path: 'produtos', loadComponent: () => import('./admin/products').then(m => m.ProductsPage), title: 'Produtos · LojaFácil' },
      { path: 'produtos/novo', loadComponent: () => import('./admin/product-form').then(m => m.ProductFormPage), title: 'Novo produto · LojaFácil' },
      { path: 'produtos/:id', loadComponent: () => import('./admin/product-form').then(m => m.ProductFormPage), title: 'Produto · LojaFácil' },
      { path: 'estoque', loadComponent: () => import('./admin/inventory').then(m => m.InventoryPage), title: 'Estoque · LojaFácil' },
      { path: 'pedidos', loadComponent: () => import('./admin/orders').then(m => m.OrdersPage), title: 'Pedidos · LojaFácil' },
      { path: 'pedidos/novo', loadComponent: () => import('./admin/order-new').then(m => m.OrderNewPage), title: 'Novo pedido · LojaFácil' },
      { path: 'pedidos/:id', loadComponent: () => import('./admin/order-detail').then(m => m.OrderDetailPage), title: 'Pedido · LojaFácil' },
      { path: 'clientes', loadComponent: () => import('./admin/customers').then(m => m.CustomersPage), title: 'Clientes · LojaFácil' },
      { path: 'clientes/:id', loadComponent: () => import('./admin/customer-detail').then(m => m.CustomerDetailPage), title: 'Cliente · LojaFácil' },
      { path: 'fornecedores', loadComponent: () => import('./admin/suppliers').then(m => m.SuppliersPage), title: 'Fornecedores · LojaFácil' },
      {
        path: 'financeiro', canActivate: [roleGuard('OWNER', 'MANAGER')],
        loadComponent: () => import('./admin/finance').then(m => m.FinancePage), title: 'Financeiro · LojaFácil',
      },
      { path: 'configuracoes', loadComponent: () => import('./admin/settings').then(m => m.SettingsPage), title: 'Configurações · LojaFácil' },
    ],
  },
  {
    path: 'loja/:slug',
    loadComponent: () => import('./storefront/shell').then(m => m.StoreShell),
    children: [
      { path: '', loadComponent: () => import('./storefront/home').then(m => m.StoreHome) },
      { path: 'produtos', loadComponent: () => import('./storefront/catalog').then(m => m.StoreCatalog) },
      { path: 'produto/:productSlug', loadComponent: () => import('./storefront/product').then(m => m.StoreProduct) },
      { path: 'carrinho', loadComponent: () => import('./storefront/cart').then(m => m.StoreCart) },
      { path: 'checkout', loadComponent: () => import('./storefront/checkout').then(m => m.StoreCheckout) },
      { path: 'pedido/:token', loadComponent: () => import('./storefront/tracking').then(m => m.StoreTracking) },
    ],
  },
  { path: '**', loadComponent: () => import('./public/landing').then(m => m.NotFound), title: 'Página não encontrada' },
];
