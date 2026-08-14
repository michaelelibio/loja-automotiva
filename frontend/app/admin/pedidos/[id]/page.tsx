'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { useAuth } from '@/context/AuthContext';
import { AdminOrderApiError, getAdminOrder, updateAdminOrderStatus } from '@/lib/api/admin/orders';
import { adminOrderStatusLabels, type AdminOrder, type AdminOrderStatus } from '@/lib/types/admin-order';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const paymentLabels = { PENDING: 'Aguardando pagamento', PAID: 'Pago', EXPIRED: 'Expirado', CANCELED: 'Cancelado', FAILED: 'Falhou' } as const;
const actions: Partial<Record<AdminOrderStatus, { target: 'PROCESSING' | 'SHIPPED' | 'DELIVERED'; label: string; confirmation: string }>> = {
  PAID: { target: 'PROCESSING', label: 'Iniciar preparação', confirmation: 'Confirmar o início da preparação deste pedido?' },
  PROCESSING: { target: 'SHIPPED', label: 'Marcar como enviado', confirmation: 'Confirmar que este pedido foi enviado?' },
  SHIPPED: { target: 'DELIVERED', label: 'Marcar como entregue', confirmation: 'Confirmar que este pedido foi entregue?' },
};

function timestamp(value: string | null) { return value ? dateTime.format(new Date(value)) : '—'; }

export default function AdminOrderDetailPage() {
  const params = useParams<{ id: string }>(); const router = useRouter(); const { logout } = useAuth();
  const [order, setOrder] = useState<AdminOrder | null>(null); const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null); const [denied, setDenied] = useState(false);
  const [confirming, setConfirming] = useState(false); const [updating, setUpdating] = useState(false); const [feedback, setFeedback] = useState<{ type: 'success' | 'error'; message: string } | null>(null);
  const id = Number(params.id);

  useEffect(() => { if (!Number.isSafeInteger(id) || id <= 0) return; let active = true;
    getAdminOrder(id).then((data) => { if (active) setOrder(data); }).catch((cause) => { if (!active) return; if (cause instanceof AdminOrderApiError && cause.status === 401) { logout(); router.replace('/login'); return; } if (cause instanceof AdminOrderApiError && cause.status === 403) { setDenied(true); return; } setError(cause instanceof AdminOrderApiError && cause.status === 404 ? 'Pedido não encontrado.' : 'Não foi possível carregar este pedido.'); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [id, logout, router]);

  async function transition() { const action = order ? actions[order.status] : undefined; if (!order || !action || updating) return; setUpdating(true); setFeedback(null);
    try { const updated = await updateAdminOrderStatus(order.id, action.target); setOrder(updated); setConfirming(false); setFeedback({ type: 'success', message: `Pedido atualizado para “${adminOrderStatusLabels[updated.status]}”.` }); }
    catch (cause) { if (cause instanceof AdminOrderApiError && cause.status === 401) { logout(); router.replace('/login'); return; } if (cause instanceof AdminOrderApiError && cause.status === 403) { setDenied(true); return; } setConfirming(false); setFeedback({ type: 'error', message: cause instanceof AdminOrderApiError && cause.status === 409 ? 'O pedido mudou de status ou esta transição não é mais permitida. Atualize a página e confira o estado atual.' : 'Não foi possível atualizar o pedido. Tente novamente.' }); }
    finally { setUpdating(false); }
  }

  if (!Number.isSafeInteger(id) || id <= 0) return <div className="admin-feedback error"><p>Pedido inválido.</p><Link href="/admin/pedidos">Voltar aos pedidos</Link></div>;
  if (loading) return <div className="admin-feedback">Carregando pedido...</div>;
  if (denied) return <div className="admin-feedback error" role="alert">Acesso negado. Sua conta não possui permissão administrativa.</div>;
  if (error || !order) return <div className="admin-feedback error"><p>{error}</p><Link href="/admin/pedidos">Voltar aos pedidos</Link></div>;
  const address = order.shippingAddress; const action = actions[order.status];

  return <><header className="admin-page-heading admin-detail-heading"><div><Link href="/admin/pedidos">← Pedidos</Link><p className="eyebrow">PEDIDO #{order.id}</p><h1>Detalhes do pedido</h1><p>Criado em {timestamp(order.createdAt)}</p></div><span className={`admin-status ${order.status.toLowerCase()}`}>{adminOrderStatusLabels[order.status]}</span></header>
    {feedback && <p className={`admin-action-feedback ${feedback.type}`} role={feedback.type === 'error' ? 'alert' : 'status'}>{feedback.message}</p>}
    <div className="admin-order-detail"><div className="admin-order-main">
      <section className="admin-card"><h2>Cliente e entrega</h2><div className="admin-customer"><div><span>Cliente</span><strong>{order.customer.name}</strong><a href={`mailto:${order.customer.email}`}>{order.customer.email}</a></div><address><strong>{address.recipientName}</strong><span>{address.street}, {address.number}</span>{address.complement && <span>{address.complement}</span>}<span>{address.neighborhood}</span><span>{address.city} — {address.state}</span><span>CEP {address.zipCode}</span></address></div></section>
      <section className="admin-card"><h2>Itens</h2><div className="admin-detail-items">{order.items.map((item) => <article key={item.productId}><div><strong>{item.productName}</strong><span>{item.quantity} × {currency.format(item.unitPrice)}</span></div><strong>{currency.format(item.subtotal)}</strong></article>)}</div></section>
      <section className="admin-card"><h2>Pagamento</h2>{order.payment ? <div className="admin-payment"><div><span>Método</span><strong>{order.payment.method}</strong></div><div><span>Status</span><strong>{paymentLabels[order.payment.status]}</strong></div><div><span>Pago em</span><strong>{timestamp(order.payment.paidAt)}</strong></div></div> : <p className="admin-muted">Nenhuma tentativa de pagamento registrada.</p>}</section>
      <section className="admin-card"><h2>Linha do tempo</h2><dl className="admin-timestamps"><div><dt>Criado</dt><dd>{timestamp(order.createdAt)}</dd></div><div><dt>Expira</dt><dd>{timestamp(order.expiresAt)}</dd></div><div><dt>Atualizado</dt><dd>{timestamp(order.updatedAt)}</dd></div><div><dt>Em preparação</dt><dd>{timestamp(order.processingAt)}</dd></div><div><dt>Enviado</dt><dd>{timestamp(order.shippedAt)}</dd></div><div><dt>Entregue</dt><dd>{timestamp(order.deliveredAt)}</dd></div></dl></section>
    </div><aside className="admin-order-summary"><h2>Resumo</h2><div><span>Subtotal</span><strong>{currency.format(order.subtotal)}</strong></div><div><span>Frete</span><strong>{currency.format(order.shippingCost)}</strong></div><div className="total"><span>Total</span><strong>{currency.format(order.total)}</strong></div>{action && <div className="admin-transition">{!confirming ? <button type="button" onClick={() => { setFeedback(null); setConfirming(true); }}>{action.label}</button> : <div className="admin-confirm"><p>{action.confirmation}</p><div><button type="button" disabled={updating} onClick={() => setConfirming(false)}>Voltar</button><button type="button" disabled={updating} onClick={() => void transition()}>{updating ? 'Atualizando...' : 'Confirmar'}</button></div></div>}</div>}</aside></div></>;
}
