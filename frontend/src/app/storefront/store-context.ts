import { Injectable, computed, signal } from '@angular/core';

import { CartLine, PublicStore } from '../core/models';

export interface CartItem extends CartLine {
  productSlug: string;
  name: string;
  variantName?: string;
  imageUrl?: string;

  priceHint: number;
}

@Injectable()
export class StoreContext {
  readonly store = signal<PublicStore | null>(null);
  readonly slug = signal('');
  readonly cart = signal<CartItem[]>([]);
  readonly cartCount = computed(() => this.cart().reduce((s, i) => s + i.quantity, 0));

  init(slug: string) {
    this.slug.set(slug);
    this.cart.set(this.read());
  }

  add(item: CartItem) {
    this.cart.update(list => {
      const found = list.find(i => i.variantId === item.variantId);
      return found
        ? list.map(i => i.variantId === item.variantId ? { ...i, quantity: Math.min(999, i.quantity + item.quantity) } : i)
        : [...list, item];
    });
    this.save();
  }

  setQuantity(variantId: number, quantity: number) {
    this.cart.update(list => list.map(i => i.variantId === variantId ? { ...i, quantity: Math.max(1, Math.min(999, quantity)) } : i));
    this.save();
  }

  remove(variantId: number) {
    this.cart.update(list => list.filter(i => i.variantId !== variantId));
    this.save();
  }

  clear() {
    this.cart.set([]);
    this.save();
  }

  lines(): CartLine[] {
    return this.cart().map(i => ({ variantId: i.variantId, quantity: i.quantity }));
  }

  rememberOrder(number: number, token: string) {
    try {
      const key = `lf-orders-${this.slug()}`;
      const list = JSON.parse(localStorage.getItem(key) ?? '[]') as { number: number; token: string }[];
      localStorage.setItem(key, JSON.stringify([{ number, token }, ...list].slice(0, 5)));
    } catch {}
  }

  recentOrders(): { number: number; token: string }[] {
    try { return JSON.parse(localStorage.getItem(`lf-orders-${this.slug()}`) ?? '[]'); } catch { return []; }
  }

  private read(): CartItem[] {
    try {
      const raw = localStorage.getItem(`lf-cart-${this.slug()}`);
      const parsed = raw ? JSON.parse(raw) : [];
      return Array.isArray(parsed) ? parsed.filter(i => typeof i.variantId === 'number' && i.quantity > 0) : [];
    } catch {
      return [];
    }
  }

  private save() {
    try { localStorage.setItem(`lf-cart-${this.slug()}`, JSON.stringify(this.cart())); } catch {}
  }
}
