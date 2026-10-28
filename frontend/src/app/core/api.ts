import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import * as M from './models';

export function params(obj: Record<string, unknown>): HttpParams {
  let p = new HttpParams();
  for (const [k, v] of Object.entries(obj)) {
    if (v !== null && v !== undefined && v !== '') {
      p = p.set(k, String(v));
    }
  }
  return p;
}

@Injectable({ providedIn: 'root' })
export class AdminApi {
  private http = inject(HttpClient);
  private base = '/api/admin';

  dashboard(from: string, to: string) { return this.http.get<M.Dashboard>(`${this.base}/dashboard`, { params: params({ from, to }) }); }

  store() { return this.http.get<M.StoreSettings>(`${this.base}/store`); }
  saveStore(body: unknown) { return this.http.put<M.StoreSettings>(`${this.base}/store`, body); }
  resetTheme() { return this.http.post<M.StoreSettings>(`${this.base}/store/theme/reset`, {}); }
  uploadStoreImage(kind: 'logo' | 'banner', file: File) {
    const fd = new FormData(); fd.append('file', file);
    return this.http.post<M.StoreSettings>(`${this.base}/store/images/${kind}`, fd);
  }
  removeStoreImage(kind: 'logo' | 'banner') { return this.http.delete<M.StoreSettings>(`${this.base}/store/images/${kind}`); }
  audit(page: number) { return this.http.get<M.Page<M.AuditItem>>(`${this.base}/audit`, { params: params({ page, size: 30 }) }); }

  team() { return this.http.get<M.TeamView>(`${this.base}/team`); }
  invite(body: { email: string; role: M.Role }) { return this.http.post<M.TeamView['invitations'][0]>(`${this.base}/team/invitations`, body); }
  revokeInvite(id: number) { return this.http.delete<void>(`${this.base}/team/invitations/${id}`); }
  updateMember(id: number, body: { role: M.Role; active: boolean }) { return this.http.put<M.UserView>(`${this.base}/team/members/${id}`, body); }
  paymentConfig() { return this.http.get<M.PaymentConfig>(`${this.base}/payments/integration`); }
  savePaymentConfig(body: unknown) { return this.http.put<M.PaymentConfig>(`${this.base}/payments/integration`, body); }
  testPayment() { return this.http.post<{ ok: boolean; message: string }>(`${this.base}/payments/integration/test`, {}); }

  categories() { return this.http.get<M.Category[]>(`${this.base}/categories`); }
  saveCategory(id: number | null, body: { name: string; active: boolean }) {
    return id ? this.http.put<M.Category>(`${this.base}/categories/${id}`, body) : this.http.post<M.Category>(`${this.base}/categories`, body);
  }
  products(f: Record<string, unknown>) { return this.http.get<M.Page<M.ProductListItem>>(`${this.base}/products`, { params: params(f) }); }
  product(id: number) { return this.http.get<M.ProductDetail>(`${this.base}/products/${id}`); }
  saveProduct(id: number | null, body: unknown) {
    return id ? this.http.put<M.ProductDetail>(`${this.base}/products/${id}`, body) : this.http.post<M.ProductDetail>(`${this.base}/products`, body);
  }
  archiveProduct(id: number, archived: boolean) { return this.http.post<M.ProductDetail>(`${this.base}/products/${id}/${archived ? 'archive' : 'unarchive'}`, {}); }
  deleteProduct(id: number) { return this.http.delete<void>(`${this.base}/products/${id}`); }
  addProductImage(id: number, file: File) { const fd = new FormData(); fd.append('file', file); return this.http.post<M.ProductDetail>(`${this.base}/products/${id}/images`, fd); }
  removeProductImage(id: number, imageId: number) { return this.http.delete<M.ProductDetail>(`${this.base}/products/${id}/images/${imageId}`); }
  mainProductImage(id: number, imageId: number) { return this.http.post<M.ProductDetail>(`${this.base}/products/${id}/images/${imageId}/main`, {}); }

  stock(f: Record<string, unknown>) { return this.http.get<M.Page<M.StockView>>(`${this.base}/inventory/stock`, { params: params(f) }); }
  movements(f: Record<string, unknown>) { return this.http.get<M.Page<M.MovementView>>(`${this.base}/inventory/movements`, { params: params(f) }); }
  move(body: unknown) { return this.http.post<M.StockView>(`${this.base}/inventory/movements`, body); }

  customers(f: Record<string, unknown>) { return this.http.get<M.Page<M.CustomerListItem>>(`${this.base}/customers`, { params: params(f) }); }
  customer(id: number) { return this.http.get<M.CustomerDetail>(`${this.base}/customers/${id}`); }
  saveCustomer(id: number | null, body: unknown) {
    return id ? this.http.put<M.CustomerDetail>(`${this.base}/customers/${id}`, body) : this.http.post<M.CustomerDetail>(`${this.base}/customers`, body);
  }
  suppliers(f: Record<string, unknown>) { return this.http.get<M.Page<M.Supplier>>(`${this.base}/suppliers`, { params: params(f) }); }
  activeSuppliers() { return this.http.get<M.Supplier[]>(`${this.base}/suppliers/active`); }
  saveSupplier(id: number | null, body: unknown) {
    return id ? this.http.put<M.Supplier>(`${this.base}/suppliers/${id}`, body) : this.http.post<M.Supplier>(`${this.base}/suppliers`, body);
  }

  orders(f: Record<string, unknown>) { return this.http.get<M.Page<M.OrderListItem>>(`${this.base}/orders`, { params: params(f) }); }
  order(id: number) { return this.http.get<M.OrderDetail>(`${this.base}/orders/${id}`); }
  quote(body: unknown) { return this.http.post<M.Quote>(`${this.base}/orders/quote`, body); }
  createOrder(body: unknown) { return this.http.post<M.CheckoutResponse>(`${this.base}/orders`, body); }
  orderAction(id: number, action: string, body: unknown = {}): Observable<M.OrderDetail> {
    return this.http.post<M.OrderDetail>(`${this.base}/orders/${id}/${action}`, body);
  }

  finCategories() { return this.http.get<M.FinCategory[]>(`${this.base}/finance/categories`); }
  saveFinCategory(id: number | null, body: unknown) {
    return id ? this.http.put<M.FinCategory>(`${this.base}/finance/categories/${id}`, body) : this.http.post<M.FinCategory>(`${this.base}/finance/categories`, body);
  }
  entries(f: Record<string, unknown>) { return this.http.get<M.Page<M.Entry>>(`${this.base}/finance/entries`, { params: params(f) }); }
  createEntry(body: unknown) { return this.http.post<M.Entry[]>(`${this.base}/finance/entries`, body); }
  entryAction(id: number, action: 'settle' | 'cancel' | 'reverse', body: unknown) { return this.http.post<M.Entry>(`${this.base}/finance/entries/${id}/${action}`, body); }
  cashFlow(from: string, to: string) { return this.http.get<M.CashFlow>(`${this.base}/finance/cash-flow`, { params: params({ from, to }) }); }
  finSummary(from: string, to: string) { return this.http.get<M.FinSummary>(`${this.base}/finance/summary`, { params: params({ from, to }) }); }
}

@Injectable({ providedIn: 'root' })
export class PublicApi {
  private http = inject(HttpClient);
  private base = (slug: string) => `/api/public/stores/${encodeURIComponent(slug)}`;

  store(slug: string) { return this.http.get<M.PublicStore>(this.base(slug)); }
  categories(slug: string) { return this.http.get<M.Category[]>(`${this.base(slug)}/categories`); }
  products(slug: string, f: Record<string, unknown>) { return this.http.get<M.Page<M.PublicProductCard>>(`${this.base(slug)}/products`, { params: params(f) }); }
  product(slug: string, productSlug: string) { return this.http.get<M.PublicProduct>(`${this.base(slug)}/products/${encodeURIComponent(productSlug)}`); }
  quote(slug: string, body: unknown) { return this.http.post<M.Quote>(`${this.base(slug)}/cart/quote`, body); }
  checkout(slug: string, body: unknown) { return this.http.post<M.CheckoutResponse>(`${this.base(slug)}/checkout`, body); }
  order(slug: string, token: string) { return this.http.get<M.PublicOrder>(`${this.base(slug)}/orders/${encodeURIComponent(token)}`); }
  syncOrder(slug: string, token: string) { return this.http.post<M.PublicOrder>(`${this.base(slug)}/orders/${encodeURIComponent(token)}/sync`, {}); }
  pay(slug: string, token: string) { return this.http.post<{ checkoutUrl: string }>(`${this.base(slug)}/orders/${encodeURIComponent(token)}/pay`, {}); }

  simulatedCharge(id: string) { return this.http.get<SimulatedCharge>(`/api/public/simulator/charges/${encodeURIComponent(id)}`); }
  decideSimulated(id: string, decision: 'approve' | 'decline') { return this.http.post<SimulatedCharge>(`/api/public/simulator/charges/${encodeURIComponent(id)}/${decision}`, {}); }
}

export interface SimulatedCharge { id: string; storeName: string; description: string; amount: number; status: string; returnUrl: string; simulated: boolean; }
