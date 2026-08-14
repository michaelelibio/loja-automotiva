'use client';

import Link from 'next/link';
import { useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AdminOrderApiError, getAdminOrder, getAdminOrders } from '@/lib/api/admin/orders';
import { adminOrderStatusLabels, type AdminOrderPage, type AdminOrderStatus } from '@/lib/types/admin-order';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const date = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const statuses = Object.keys(adminOrderStatusLabels) as AdminOrderStatus[];

export default function AdminOrdersPage() {
  const router = useRouter(); const { logout } = useAuth();
  const [result, setResult] = useState<AdminOrderPage | null>(null); const [customers, setCustomers] = useState<Record<number, string>>({});
  const [page, setPage] = useState(0); const [status, setStatus] = useState<AdminOrderStatus | ''>('');
  const [loading, setLoading] = useState(true); const [error, setError] = useState<string | null>(null); const [denied, setDenied] = useState(false);

  useEffect(() => { let active = true;
    getAdminOrders(page, 20, status || undefined).then(async (data) => {
      if (!active) return; setResult(data);
      const details = await Promise.allSettled(data.content.map((order) => getAdminOrder(order.id)));
      const authorizationFailure = details.find((entry) => entry.status === 'rejected' && entry.reason instanceof AdminOrderApiError && (entry.reason.status === 401 || entry.reason.status === 403));
      if (authorizationFailure?.status === 'rejected') throw authorizationFailure.reason;
      if (!active) return; setCustomers(Object.fromEntries(details.flatMap((entry) => entry.status === 'fulfilled' ? [[entry.value.id, entry.value.customer.name]] : [])));
    }).catch((cause) => { if (!active) return; if (cause instanceof AdminOrderApiError && cause.status === 401) { logout(); router.replace('/login'); return; } if (cause instanceof AdminOrderApiError && cause.status === 403) { setDenied(true); return; } setError('Não foi possível carregar os pedidos. Tente novamente.'); }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [page, status, logout, router]);

  return <><header className="admin-page-heading"><p className="eyebrow">OPERAÇÃO</p><h1>Pedidos</h1><p>{result ? `${result.totalElements} pedido${result.totalElements === 1 ? '' : 's'} encontrado${result.totalElements === 1 ? '' : 's'}` : 'Acompanhe os pedidos da loja.'}</p></header>
    <section className="admin-orders-panel"><div className="admin-orders-toolbar"><label htmlFor="status-filter">Filtrar por status</label><select id="status-filter" value={status} onChange={(event) => { setLoading(true); setError(null); setDenied(false); setStatus(event.target.value as AdminOrderStatus | ''); setPage(0); }}><option value="">Todos os status</option>{statuses.map((value) => <option key={value} value={value}>{adminOrderStatusLabels[value]}</option>)}</select></div>
    {loading && <div className="admin-feedback">Carregando pedidos...</div>}{denied && <div className="admin-feedback error" role="alert">Acesso negado. Sua conta não possui permissão administrativa.</div>}{error && <div className="admin-feedback error" role="alert">{error}</div>}
    {!loading && !error && !denied && result && (result.content.length ? <><div className="admin-orders-table-wrap"><table className="admin-orders-table"><thead><tr><th>Pedido</th><th>Cliente</th><th>Total</th><th>Status</th><th>Data</th><th><span className="sr-only">Ação</span></th></tr></thead><tbody>{result.content.map((order) => <tr key={order.id}><td><strong>#{order.id}</strong></td><td>{customers[order.id] ?? '—'}</td><td>{currency.format(order.total)}</td><td><span className={`admin-status ${order.status.toLowerCase()}`}>{adminOrderStatusLabels[order.status]}</span></td><td>{date.format(new Date(order.createdAt))}</td><td><Link href={`/admin/pedidos/${order.id}`}>Ver pedido →</Link></td></tr>)}</tbody></table></div><nav className="admin-pagination" aria-label="Paginação"><button type="button" disabled={result.page === 0} onClick={() => { setLoading(true); setPage((value) => value - 1); }}>← Anterior</button><span>Página {result.page + 1} de {Math.max(result.totalPages, 1)}</span><button type="button" disabled={result.page + 1 >= result.totalPages} onClick={() => { setLoading(true); setPage((value) => value + 1); }}>Próxima →</button></nav></> : <div className="admin-feedback">Nenhum pedido encontrado com este filtro.</div>)}</section></>;
}
