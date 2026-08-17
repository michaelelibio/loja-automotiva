'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { CartesianGrid, Cell, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useAuth } from '@/context/AuthContext';
import { AdminDashboardApiError, getAdminDashboard } from '@/lib/api/admin/dashboard';
import { adminOrderStatusLabels, type AdminOrderStatus } from '@/lib/types/admin-order';
import type { AdminDashboard as AdminDashboardData } from '@/lib/types/admin-dashboard';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const shortDate = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });
const STATUS_COLORS: Record<AdminOrderStatus, string> = {
  PENDING_PAYMENT: '#aeb4ad', PAID: '#111316', PROCESSING: '#c8ff00', SHIPPED: '#778f16',
  DELIVERED: '#3f6b45', CANCELED: '#a94438', EXPIRED: '#d6b3aa',
};

function formatDay(value: string) { return shortDate.format(new Date(`${value}T12:00:00`)); }
function orderNumber(id: number) { return `#${String(id).padStart(6, '0')}`; }

export default function AdminDashboard() {
  const router = useRouter();
  const { logout } = useAuth();
  const [dashboard, setDashboard] = useState<AdminDashboardData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [requestKey, setRequestKey] = useState(0);

  useEffect(() => {
    let active = true;
    getAdminDashboard().then((data) => {
      if (active) setDashboard(data);
    }).catch((cause: unknown) => {
      if (!active) return;
      if (cause instanceof AdminDashboardApiError && cause.status === 401) {
        logout();
        router.replace('/login');
      } else if (cause instanceof AdminDashboardApiError && cause.status === 403) {
        setForbidden(true);
      } else {
        setError('Não foi possível carregar o dashboard. Tente novamente.');
      }
    }).finally(() => {
      if (active) setLoading(false);
    });
    return () => { active = false; };
  }, [logout, requestKey, router]);

  function retry() {
    setLoading(true);
    setError(null);
    setForbidden(false);
    setRequestKey((current) => current + 1);
  }
  const activeStatuses = useMemo(() => dashboard?.ordersByStatus.filter((item) => item.quantity > 0) ?? [], [dashboard]);

  if (loading) return <div className="admin-feedback" role="status">Carregando dashboard…</div>;
  if (forbidden) return <div className="admin-feedback error"><h2>Acesso negado</h2><p>Sua conta não possui permissão para visualizar este dashboard.</p></div>;
  if (error || !dashboard) return <div className="admin-feedback error" role="alert"><p>{error ?? 'Dashboard indisponível.'}</p><button type="button" onClick={retry}>Tentar novamente</button></div>;

  const cards = [
    { label: 'Faturamento hoje', value: money.format(dashboard.summary.revenueToday), money: true },
    { label: 'Pedidos hoje', value: dashboard.summary.ordersToday.toLocaleString('pt-BR') },
    { label: 'Ticket médio hoje', value: money.format(dashboard.summary.averageTicketToday), money: true },
    { label: 'Aguardando pagamento', value: dashboard.summary.pendingPayment.toLocaleString('pt-BR') },
    { label: 'Em preparação', value: dashboard.summary.processing.toLocaleString('pt-BR') },
    { label: 'Enviados', value: dashboard.summary.shipped.toLocaleString('pt-BR') },
  ];

  return <>
    <header className="admin-page-heading admin-dashboard-heading">
      <div><p className="eyebrow">PAINEL ADMINISTRATIVO</p><h1>Visão geral</h1><p>O ritmo da operação da inGarage, direto dos pedidos.</p></div>
      <Link href="/admin/pedidos">Gerenciar pedidos →</Link>
    </header>

    <section className="admin-metrics" aria-label="Resumo de hoje">
      {cards.map((card, index) => <article className="admin-metric-card" key={card.label}>
        <span>{String(index + 1).padStart(2, '0')}</span><p>{card.label}</p><strong className={card.money ? 'money' : undefined}>{card.value}</strong>
      </article>)}
    </section>

    <section className="admin-charts" aria-label="Indicadores do dashboard">
      <article className="admin-chart-card">
        <div className="admin-section-title"><p className="eyebrow">ÚLTIMOS 7 DIAS</p><h2>Faturamento</h2></div>
        {dashboard.revenueLast7Days.length === 0 ? <div className="admin-chart-empty">Nenhum dado de faturamento disponível.</div> :
          <div className="admin-line-chart" aria-label="Gráfico de faturamento dos últimos 7 dias">
            <ResponsiveContainer width="100%" height="100%"><LineChart data={dashboard.revenueLast7Days} margin={{ top: 12, right: 12, left: 0, bottom: 0 }}>
              <CartesianGrid stroke="#e0e2dc" strokeDasharray="3 3" vertical={false} />
              <XAxis dataKey="date" tickFormatter={formatDay} tick={{ fontSize: 10 }} axisLine={false} tickLine={false} />
              <YAxis tickFormatter={(value) => `R$ ${Number(value).toLocaleString('pt-BR')}`} tick={{ fontSize: 9 }} axisLine={false} tickLine={false} width={72} />
              <Tooltip formatter={(value) => [money.format(Number(value)), 'Faturamento']} labelFormatter={(label) => formatDay(String(label))} contentStyle={{ border: '1px solid #111316', borderRadius: 0, fontSize: 11 }} />
              <Line type="monotone" dataKey="revenue" stroke="#111316" strokeWidth={3} dot={{ fill: '#c8ff00', stroke: '#111316', strokeWidth: 2, r: 4 }} activeDot={{ r: 6 }} />
            </LineChart></ResponsiveContainer>
          </div>}
      </article>

      <article className="admin-chart-card">
        <div className="admin-section-title"><p className="eyebrow">OPERAÇÃO</p><h2>Pedidos por status</h2></div>
        {activeStatuses.length === 0 ? <div className="admin-chart-empty">Ainda não há pedidos para distribuir por status.</div> : <div className="admin-status-chart">
          <div className="admin-donut" aria-label="Gráfico de pedidos por status"><ResponsiveContainer width="100%" height="100%"><PieChart>
            <Tooltip formatter={(value) => [Number(value).toLocaleString('pt-BR'), 'Pedidos']} contentStyle={{ border: '1px solid #111316', borderRadius: 0, fontSize: 11 }} />
            <Pie data={activeStatuses} dataKey="quantity" nameKey="status" innerRadius="58%" outerRadius="86%" paddingAngle={2} stroke="none">{activeStatuses.map((item) => <Cell key={item.status} fill={STATUS_COLORS[item.status]} />)}</Pie>
          </PieChart></ResponsiveContainer></div>
          <ul className="admin-chart-legend">{activeStatuses.map((item) => <li key={item.status}><i style={{ background: STATUS_COLORS[item.status] }} /><span>{adminOrderStatusLabels[item.status]}</span><strong>{item.quantity.toLocaleString('pt-BR')}</strong></li>)}</ul>
        </div>}
      </article>
    </section>

    <section className="admin-recent-card">
      <div className="admin-section-title"><p className="eyebrow">MOVIMENTAÇÃO</p><h2>Pedidos recentes</h2></div>
      {dashboard.recentOrders.length === 0 ? <div className="admin-recent-empty"><p>Nenhum pedido recente por enquanto.</p><span>Novos pedidos aparecerão aqui assim que forem criados.</span></div> : <div className="admin-recent-orders">
        {dashboard.recentOrders.map((order) => <article key={order.orderId}>
          <div><span className="admin-recent-label">Pedido</span><strong>{orderNumber(order.orderId)}</strong></div>
          <div className="admin-recent-customer"><span className="admin-recent-label">Cliente</span><strong>{order.customer.name}</strong><small>{order.customer.email}</small></div>
          <div><span className="admin-recent-label">Total</span><strong>{money.format(order.total)}</strong></div>
          <div><span className={`admin-status ${order.status.toLowerCase()}`}>{adminOrderStatusLabels[order.status]}</span></div>
          <time dateTime={order.createdAt}>{dateTime.format(new Date(order.createdAt))}</time>
          <Link href={`/admin/pedidos/${order.orderId}`}>Ver pedido</Link>
        </article>)}
      </div>}
    </section>
  </>;
}
