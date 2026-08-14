'use client';

import Link from 'next/link';
import { useCallback, useEffect, useState } from 'react';
import { getOrders } from '@/lib/api/orders';
import type { Order, OrderStatus } from '@/lib/types/order';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
export const statusLabels: Record<OrderStatus, string> = { PENDING_PAYMENT: 'Aguardando pagamento', PAID: 'Pago', PROCESSING: 'Em preparação', SHIPPED: 'Enviado', DELIVERED: 'Entregue', CANCELED: 'Cancelado' };
export const formatOrderDate = (value: string) => new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium', timeStyle: 'short' }).format(new Date(value));

export function OrderStatusBadge({ status }: { status: OrderStatus }) { return <span className={`order-status ${status.toLowerCase()}`}>{statusLabels[status]}</span>; }

export function OrdersPanel({ compact = false }: { compact?: boolean }) {
  const [orders, setOrders] = useState<Order[]>([]); const [loading, setLoading] = useState(true); const [error, setError] = useState(false);
  const load = useCallback(async () => { setLoading(true); setError(false); try { setOrders(await getOrders()); } catch { setError(true); } finally { setLoading(false); } }, []);
  useEffect(() => { void load(); }, [load]);
  return <div className={`orders-panel ${compact ? 'compact' : ''}`}>
    {loading && <p className="account-status">Carregando seus pedidos...</p>}
    {error && <div className="orders-error"><p>Não foi possível carregar seus pedidos.</p><button type="button" onClick={() => void load()}>Tentar novamente</button></div>}
    {!loading && !error && orders.length === 0 && <div className="orders-empty"><h2>Você ainda não possui pedidos.</h2><p>Quando finalizar uma compra, ela aparecerá aqui.</p><Link href="/produtos">Ver produtos</Link></div>}
    {!loading && !error && orders.length > 0 && <div className="orders-list">{orders.map((order) => <article key={order.id}><div><p className="eyebrow">PEDIDO #{order.id}</p><h3>{formatOrderDate(order.createdAt)}</h3><span>{order.items.length} {order.items.length === 1 ? 'item' : 'itens'}</span></div><OrderStatusBadge status={order.status} /><strong>{currency.format(order.total)}</strong><Link href={`/conta/pedidos/${order.id}`}>Ver detalhes</Link></article>)}</div>}
  </div>;
}
