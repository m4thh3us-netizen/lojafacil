import { Pipe, PipeTransform } from '@angular/core';

const BRL = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const DATE = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric' });
const DATETIME = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit' });

export function brl(v: number | null | undefined): string {
  return v === null || v === undefined ? '—' : BRL.format(Number(v));
}

/** Datas "YYYY-MM-DD" (sem fuso) são exibidas como estão; instantes ISO são convertidos para o horário local. */
export function date(v: string | null | undefined): string {
  if (!v) return '—';
  if (/^\d{4}-\d{2}-\d{2}$/.test(v)) {
    const [y, m, d] = v.split('-');
    return `${d}/${m}/${y}`;
  }
  return DATE.format(new Date(v));
}

export function dateTime(v: string | null | undefined): string {
  return v ? DATETIME.format(new Date(v)) : '—';
}

export function isoDate(d: Date): string {
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}`;
}

export function daysAgo(n: number): string {
  const d = new Date();
  d.setDate(d.getDate() - n);
  return isoDate(d);
}

@Pipe({ name: 'brl' })
export class BrlPipe implements PipeTransform {
  transform(v: number | null | undefined): string { return brl(v); }
}

@Pipe({ name: 'dt' })
export class DatePipeBr implements PipeTransform {
  transform(v: string | null | undefined, withTime = false): string { return withTime ? dateTime(v) : date(v); }
}

// ------------------------------------------------------------------ rótulos de estados

export const LABELS: Record<string, string> = {
  OPEN: 'Aberto', CONFIRMED: 'Confirmado', CANCELLED: 'Cancelado', COMPLETED: 'Concluído',
  PENDING: 'Pendente', APPROVED: 'Aprovado', DECLINED: 'Recusado', EXPIRED: 'Expirado',
  PARTIALLY_REFUNDED: 'Parc. reembolsado', REFUNDED: 'Reembolsado',
  AWAITING: 'Aguardando', PICKING: 'Em separação', READY_FOR_PICKUP: 'Pronto p/ retirada', SHIPPED: 'Enviado',
  DELIVERED: 'Entregue', RETURNED: 'Devolvido',
  PICKUP: 'Retirada no local', LOCAL_DELIVERY: 'Entrega local', ONLINE: 'Loja virtual', MANUAL: 'Manual',
  OWNER: 'Proprietário', MANAGER: 'Gerente', OPERATOR: 'Operador',
  ENTRY: 'Entrada', EXIT: 'Saída', ADJUSTMENT: 'Ajuste', SALE: 'Venda', RETURN: 'Devolução',
  INCOME: 'Receita', EXPENSE: 'Despesa', PAID: 'Realizado',
  ORDER_PAYMENT: 'Venda', PAYMENT_FEE: 'Taxa', REVERSAL: 'Estorno',
  AWAITING_RECEIPT: 'Aguardando recebimento', RECEIVED: 'Recebida',
  SIMULATOR: 'Simulador', MERCADO_PAGO: 'Mercado Pago',
};

const TONE: Record<string, string> = {
  CONFIRMED: 'success', COMPLETED: 'success', APPROVED: 'success', DELIVERED: 'success', PAID: 'success', RECEIVED: 'success',
  OPEN: 'info', PENDING: 'warning', PICKING: 'info', READY_FOR_PICKUP: 'primary', SHIPPED: 'primary', AWAITING: '',
  CANCELLED: 'danger', DECLINED: 'danger', EXPIRED: '', REFUNDED: '', PARTIALLY_REFUNDED: 'warning', RETURNED: 'warning',
  AWAITING_RECEIPT: 'warning',
};

@Pipe({ name: 'label' })
export class LabelPipe implements PipeTransform {
  transform(v: string | null | undefined): string { return v ? (LABELS[v] ?? v) : '—'; }
}

@Pipe({ name: 'tone' })
export class TonePipe implements PipeTransform {
  transform(v: string | null | undefined): string { return v ? (TONE[v] ?? '') : ''; }
}
