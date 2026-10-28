export type Role = 'OWNER' | 'MANAGER' | 'OPERATOR';
export type OrderStatus = 'OPEN' | 'CONFIRMED' | 'CANCELLED' | 'COMPLETED';
export type PaymentStatus = 'PENDING' | 'APPROVED' | 'DECLINED' | 'EXPIRED' | 'PARTIALLY_REFUNDED' | 'REFUNDED';
export type FulfillmentStatus = 'AWAITING' | 'PICKING' | 'READY_FOR_PICKUP' | 'SHIPPED' | 'DELIVERED' | 'RETURNED';
export type DeliveryMethod = 'PICKUP' | 'LOCAL_DELIVERY';
export type EntryType = 'INCOME' | 'EXPENSE';
export type EntryStatus = 'PENDING' | 'PAID' | 'CANCELLED';

export interface Page<T> { content: T[]; page: number; size: number; totalElements: number; totalPages: number; }

export interface UserView { id: number; fullName: string; email: string; phone?: string; role: Role; active: boolean; }
export interface Me { user: UserView; store: { id: number; name: string; slug: string; demo: boolean }; demoMode: boolean; }

export interface StoreSettings {
  id: number; slug: string; name: string; description?: string; logoUrl?: string; bannerUrl?: string;
  primaryColor: string; secondaryColor: string; onPrimaryColor: string; onSecondaryColor: string;
  contactEmail?: string; contactPhone?: string; whatsapp?: string; instagram?: string; facebook?: string;
  addressLine?: string; addressCity?: string; addressState?: string; addressZip?: string; serviceHours?: string;
  pickupEnabled: boolean; localDeliveryEnabled: boolean; localDeliveryFee: number; reservationMinutes: number; demo: boolean;
}

export interface PublicStore {
  slug: string; name: string; description?: string; logoUrl?: string; bannerUrl?: string;
  primaryColor: string; secondaryColor: string; onPrimaryColor: string; onSecondaryColor: string;
  contactEmail?: string; contactPhone?: string; whatsapp?: string; instagram?: string; facebook?: string;
  address?: string; serviceHours?: string; pickupEnabled: boolean; localDeliveryEnabled: boolean;
  localDeliveryFee: number; demo: boolean;
}

export interface Category { id: number; name: string; slug: string; active: boolean; }

export interface Variant {
  id: number; sku: string; barcode?: string; option1Value?: string; option2Value?: string; name?: string;
  priceOverride?: number; finalPrice: number; physicalQty: number; reservedQty: number; availableQty: number;
  active: boolean; lowStock: boolean;
}
export interface ProductListItem {
  id: number; name: string; sku: string; categoryName?: string; salePrice: number; promoPrice?: number; costPrice: number;
  imageUrl?: string; active: boolean; visible: boolean; featured: boolean; archived: boolean; hasVariants: boolean;
  variantCount: number; physicalQty: number; reservedQty: number; availableQty: number; lowStock: boolean;
}
export interface ProductDetail {
  id: number; name: string; slug: string; description?: string; categoryId?: number; sku: string; barcode?: string;
  costPrice: number; salePrice: number; promoPrice?: number; unit: string; minStock: number; active: boolean;
  visible: boolean; featured: boolean; hasVariants: boolean; option1Name?: string; option2Name?: string;
  archivedAt?: string; variants: Variant[]; images: { id: number; url: string; position: number }[]; usedInOrders: boolean;
}

export interface PublicProductCard {
  id: number; slug: string; name: string; imageUrl?: string; price: number; finalPrice: number; onSale: boolean;
  hasVariants: boolean; available: boolean; categorySlug?: string;
}
export interface PublicVariant { id: number; option1Value?: string; option2Value?: string; name?: string; price: number; finalPrice: number; available: boolean; lastUnits: boolean; }
export interface PublicProduct {
  id: number; slug: string; name: string; description?: string; unit: string; images: string[]; price: number;
  finalPrice: number; onSale: boolean; hasVariants: boolean; option1Name?: string; option2Name?: string;
  variants: PublicVariant[]; category?: Category;
}

export interface StockView {
  variantId: number; productId: number; productName: string; sku: string; variantName?: string;
  physicalQty: number; reservedQty: number; availableQty: number; minStock: number; lowStock: boolean;
}
export interface MovementView {
  id: number; variantId: number; productName: string; sku: string; variantName?: string;
  type: 'ENTRY' | 'EXIT' | 'ADJUSTMENT' | 'SALE' | 'RETURN'; quantity: number; balanceAfter: number;
  reason: string; orderId?: number; userName: string; createdAt: string;
}

export interface Address { label?: string; street: string; number: string; complement?: string; district: string; city: string; state: string; zip: string; }
export interface CustomerListItem { id: number; name: string; email: string; phone?: string; createdAt: string; }
export interface CustomerDetail {
  id: number; name: string; email: string; phone?: string; notes?: string; createdAt: string; addresses: Address[];
  recentOrders: { id: number; number: number; createdAt: string; total: number; status: OrderStatus; paymentStatus: PaymentStatus; fulfillmentStatus: FulfillmentStatus }[];
  orderCount: number; totalSpent: number;
}
export interface Supplier { id: number; name: string; contactName?: string; phone?: string; email?: string; address?: string; notes?: string; active: boolean; }

export interface CartLine { variantId: number; quantity: number; }
export interface QuoteLine {
  variantId: number; productName: string; variantName?: string; sku?: string; imageUrl?: string; quantity: number;
  unitPrice: number; unitDiscount: number; lineTotal: number; available: boolean; availableQty: number; problem?: string;
}
export interface Quote { items: QuoteLine[]; subtotal: number; discountTotal: number; deliveryFee: number; total: number; valid: boolean; }
export interface CheckoutResponse { orderNumber: number; trackingToken: string; trackingUrl: string; checkoutUrl?: string; simulated: boolean; total: number; paymentError?: string; }

export interface OrderItem { id: number; productId: number; variantId: number; productName: string; variantName?: string; sku: string; quantity: number; unitPrice: number; unitDiscount: number; lineTotal: number; unitCost?: number; }
export interface HistoryItem { id: number; field: 'ORDER' | 'PAYMENT' | 'FULFILLMENT' | 'NOTE' | 'RETURN'; fromValue?: string; toValue?: string; note?: string; userName?: string; createdAt: string; }
export interface PaymentSummary { id: number; provider: string; simulated: boolean; status: PaymentStatus; amount: number; refundedAmount: number; feeAmount: number; method?: string; checkoutUrl?: string; createdAt: string; updatedAt: string; }
export interface ReturnView { id: number; status: 'AWAITING_RECEIPT' | 'RECEIVED'; reason: string; restocked: boolean; conditionOk?: boolean; inspectionNote?: string; createdAt: string; receivedAt?: string; items: { orderItemId: number; quantity: number }[]; }
export interface OrderListItem {
  id: number; number: number; createdAt: string; customerName: string; channel: 'ONLINE' | 'MANUAL'; deliveryMethod: DeliveryMethod;
  total: number; status: OrderStatus; paymentStatus: PaymentStatus; fulfillmentStatus: FulfillmentStatus; requiresAttention: boolean; itemCount: number;
}
export interface OrderDetail {
  id: number; number: number; createdAt: string; channel: 'ONLINE' | 'MANUAL'; customerId?: number; customerName: string;
  customerEmail: string; customerPhone?: string; deliveryMethod: DeliveryMethod; deliveryAddress?: string; notes?: string;
  subtotal: number; discountTotal: number; deliveryFee: number; total: number; status: OrderStatus; paymentStatus: PaymentStatus;
  fulfillmentStatus: FulfillmentStatus; stockCommitted: boolean; requiresAttention: boolean; attentionReason?: string;
  reservationExpiresAt?: string; confirmedAt?: string; cancelledAt?: string; completedAt?: string;
  items: OrderItem[]; payments: PaymentSummary[]; returns: ReturnView[]; history: HistoryItem[]; allowedFulfillment: FulfillmentStatus[];
}
export interface PublicOrder {
  number: number; createdAt: string; customerName: string; deliveryMethod: DeliveryMethod; deliveryAddress?: string;
  subtotal: number; discountTotal: number; deliveryFee: number; total: number; status: OrderStatus; paymentStatus: PaymentStatus;
  fulfillmentStatus: FulfillmentStatus; reservationExpiresAt?: string; checkoutUrl?: string; simulated: boolean;
  items: OrderItem[]; timeline: HistoryItem[];
}

export interface FinCategory { id: number; name: string; type: EntryType; systemKey?: string; active: boolean; }
export interface Entry {
  id: number; type: EntryType; categoryId: number; categoryName: string; description: string; amount: number; dueDate: string;
  paidDate?: string; status: EntryStatus; overdue: boolean; installmentGroup?: string; installmentNumber?: number;
  installmentCount?: number; counterparty?: string; orderId?: number; paymentId?: number;
  sourceType: 'MANUAL' | 'ORDER_PAYMENT' | 'PAYMENT_FEE' | 'REFUND' | 'REVERSAL'; reversalOfId?: number; reversed: boolean; notes?: string; createdAt: string;
}
export interface CashFlowPoint { date: string; realizedIn: number; realizedOut: number; forecastIn: number; forecastOut: number; realizedBalance: number; projectedBalance: number; }
export interface CashFlow { from: string; to: string; granularity: 'day' | 'month'; openingBalance: number; points: CashFlowPoint[]; }
export interface FinSummary {
  from: string; to: string; salesRevenue: number; salesCount: number; receipts: number; receivablesOpen: number; receivablesOverdue: number;
  payablesOpen: number; payablesOverdue: number; expensesPaid: number; operatingExpenses: number; fees: number; refunds: number;
  costOfGoodsSold: number; estimatedResult: number;
}
export interface Dashboard {
  from: string; to: string; financialAccess: boolean; summary?: FinSummary; salesCount: number; pendingOrders: number; attentionOrders: number;
  lowStock: StockView[]; topProducts: { productId: number; name: string; quantity: number; revenue?: number }[];
  sales: { date: string; orders: number; revenue?: number }[]; cashFlow?: CashFlow; definitions: Record<string, string>; demo: boolean;
}

export interface PaymentConfig {
  provider: 'SIMULATOR' | 'MERCADO_PAGO'; simulated: boolean; publicKey?: string; accessTokenMasked?: string; webhookSecretMasked?: string;
  webhookUrl: string; lastTestAt?: string; lastTestOk?: boolean; demoMode: boolean; liveTransactionsAllowed: boolean;
}
export interface TeamView { members: UserView[]; invitations: { id: number; email: string; role: Role; expiresAt: string; inviteLink?: string }[]; }
export interface AuditItem { id: number; userId?: number; entityType: string; entityId?: number; action: string; details?: string; createdAt: string; }
