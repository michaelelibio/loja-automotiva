'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState } from 'react';
import { Bar, BarChart, CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { useAuth } from '@/context/AuthContext';
import { AdminFinanceApiError, getAdminFinance } from '@/lib/api/admin/finance';
import type { AdminFinance as AdminFinanceData, AdminFinanceProductSales } from '@/lib/types/admin-finance';
import { adminOrderStatusLabels, type AdminOrderStatus, type AdminPaymentStatus } from '@/lib/types/admin-order';

const money = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const calendarDate = new Intl.DateTimeFormat('pt-BR');
const shortDate = new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const paymentLabels: Record<AdminPaymentStatus, string> = { PENDING: 'Aguardando pagamento', PAID: 'Pago', EXPIRED: 'Expirado', CANCELED: 'Cancelado', FAILED: 'Falhou' };
const financeStatusLabels = { ...adminOrderStatusLabels, PROCESSING: 'Em processamento' };
const STATUS_COLORS: Record<AdminOrderStatus, string> = { PENDING_PAYMENT: '#aeb4ad', PAID: '#111316', PROCESSING: '#c8ff00', SHIPPED: '#778f16', DELIVERED: '#3f6b45', CANCELED: '#a94438', EXPIRED: '#d6b3aa' };
const CHART_COLORS = ['#c8ff00', '#111316', '#778f16', '#aeb4ad', '#3f6b45', '#d6b3aa'];
type Preset = 'today' | '7days' | '30days' | 'month' | 'custom';

function isoDate(date: Date) { return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`; }
function presetPeriod(preset: Preset) {
  const today = new Date();
  const from = new Date(today.getFullYear(), today.getMonth(), today.getDate());
  if (preset === '7days') from.setDate(from.getDate() - 6);
  if (preset === '30days') from.setDate(from.getDate() - 29);
  if (preset === 'month') from.setDate(1);
  return { dateFrom: isoDate(from), dateTo: isoDate(today) };
}
function formatDay(value: string) { return shortDate.format(new Date(`${value}T12:00:00`)); }
function formatFullDate(value: string) { return calendarDate.format(new Date(`${value}T12:00:00`)); }

function RankingChart({ data, empty }: { data: AdminFinanceProductSales[]; empty: string }) {
  if (!data.length) return <div className="admin-finance-empty">{empty}</div>;
  return <div className="admin-finance-ranking-chart" style={{ height: Math.max(230, data.length * 42) }}><ResponsiveContainer width="100%" height="100%"><BarChart data={data} layout="vertical" margin={{ top: 5, right: 20, bottom: 5, left: 8 }}>
    <CartesianGrid stroke="#e0e2dc" strokeDasharray="3 3" horizontal={false} /><XAxis type="number" allowDecimals={false} tick={{ fontSize: 9 }} axisLine={false} tickLine={false} /><YAxis type="category" dataKey="name" width={115} tick={{ fontSize: 9 }} axisLine={false} tickLine={false} />
    <Tooltip content={({ active, payload }) => active && payload?.[0] ? <div className="admin-finance-tooltip"><strong>{String(payload[0].payload.name)}</strong><span>{Number(payload[0].payload.quantitySold).toLocaleString('pt-BR')} vendidos</span><span>{money.format(Number(payload[0].payload.revenue))}</span></div> : null} />
    <Bar dataKey="quantitySold" name="Quantidade vendida" fill="#111316" radius={0} />
  </BarChart></ResponsiveContainer></div>;
}

export default function AdminFinance() {
  const router = useRouter();
  const { logout } = useAuth();
  const initial = useMemo(() => presetPeriod('30days'), []);
  const [preset, setPreset] = useState<Preset>('30days');
  const [draftFrom, setDraftFrom] = useState(initial.dateFrom);
  const [draftTo, setDraftTo] = useState(initial.dateTo);
  const [period, setPeriod] = useState(initial);
  const [finance, setFinance] = useState<AdminFinanceData | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [periodError, setPeriodError] = useState<string | null>(null);
  const [forbidden, setForbidden] = useState(false);
  const [requestKey, setRequestKey] = useState(0);

  useEffect(() => {
    let active = true;
    getAdminFinance(period.dateFrom, period.dateTo).then((data) => { if (active) setFinance(data); }).catch((cause: unknown) => {
      if (!active) return;
      if (cause instanceof AdminFinanceApiError && cause.status === 401) { logout(); router.replace('/login'); }
      else if (cause instanceof AdminFinanceApiError && cause.status === 403) setForbidden(true);
      else if (cause instanceof AdminFinanceApiError && cause.status === 400) setError('O período informado não é válido para o relatório financeiro.');
      else setError('Não foi possível carregar os dados financeiros. Tente novamente.');
    }).finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [logout, period, requestKey, router]);

  function choosePreset(value: Preset) {
    setPreset(value); setPeriodError(null);
    if (value !== 'custom') { const next = presetPeriod(value); setDraftFrom(next.dateFrom); setDraftTo(next.dateTo); }
  }
  function applyPeriod() {
    if (!draftFrom || !draftTo) { setPeriodError('Informe as duas datas do período.'); return; }
    if (draftFrom > draftTo) { setPeriodError('A data inicial deve ser anterior ou igual à data final.'); return; }
    setPeriodError(null); setError(null); setForbidden(false); setLoading(true); setPeriod({ dateFrom: draftFrom, dateTo: draftTo });
  }
  function retry() { setError(null); setLoading(true); setRequestKey((value) => value + 1); }

  if (forbidden) return <div className="admin-feedback error" role="alert"><h2>Acesso negado</h2><p>Sua conta não possui permissão para visualizar o financeiro.</p></div>;

  const applied = finance?.period ?? period;
  const activeStatuses = finance?.ordersByStatus.filter((item) => item.quantity > 0) ?? [];
  const cards = finance ? [
    ['Faturamento', money.format(finance.summary.revenue)], ['Pedidos confirmados', finance.summary.confirmedOrders.toLocaleString('pt-BR')], ['Ticket médio', money.format(finance.summary.averageTicket)],
    ['Custo conhecido', money.format(finance.summary.knownProductCost)], ['Lucro bruto estimado', money.format(finance.summary.grossProfit)], ['Margem bruta estimada', `${finance.summary.grossMargin.toLocaleString('pt-BR', { maximumFractionDigits: 2 })}%`],
  ] : [];

  return <>
    <header className="admin-page-heading admin-finance-heading"><div><p className="eyebrow">FINANCEIRO</p><h1>Financeiro</h1><p>Receita, custos e desempenho comercial.</p></div><span>{formatFullDate(applied.dateFrom)} — {formatFullDate(applied.dateTo)}</span></header>
    <section className="admin-finance-period" aria-label="Selecionar período"><div className="admin-finance-presets">{([['today', 'Hoje'], ['7days', 'Últimos 7 dias'], ['30days', 'Últimos 30 dias'], ['month', 'Este mês'], ['custom', 'Personalizado']] as Array<[Preset, string]>).map(([value, label]) => <button className={preset === value ? 'active' : ''} type="button" key={value} onClick={() => choosePreset(value)}>{label}</button>)}</div><div className="admin-finance-dates"><label>Data inicial<input type="date" value={draftFrom} onChange={(event) => { setPreset('custom'); setDraftFrom(event.target.value); }} /></label><label>Data final<input type="date" value={draftTo} onChange={(event) => { setPreset('custom'); setDraftTo(event.target.value); }} /></label><button type="button" onClick={applyPeriod}>Aplicar período</button></div>{periodError && <p role="alert">{periodError}</p>}</section>

    {loading && <div className="admin-finance-skeleton" aria-label="Carregando financeiro">{Array.from({ length: 9 }, (_, index) => <span key={index} />)}</div>}
    {error && !loading && <div className="admin-feedback error" role="alert"><p>{error}</p><button type="button" onClick={retry}>Tentar novamente</button></div>}
    {!loading && !error && finance && <>
      <section className="admin-finance-metrics" aria-label="Resumo financeiro">{cards.map(([label, value], index) => <article key={label}><span>{String(index + 1).padStart(2, '0')}</span><p>{label}</p><strong>{value}</strong></article>)}</section>
      <div className={`admin-cost-coverage ${finance.costCoverage.complete ? 'complete' : 'partial'}`} role="status"><strong>{finance.costCoverage.complete ? 'Custos históricos completos no período.' : `Existem ${finance.costCoverage.ordersWithUnknownCost} pedido${finance.costCoverage.ordersWithUnknownCost === 1 ? '' : 's'} com custo histórico desconhecido.`}</strong>{!finance.costCoverage.complete && <span>Lucro e margem são estimativas parciais.</span>}</div>

      <section className="admin-finance-card admin-finance-main-chart"><div className="admin-section-title"><p className="eyebrow">PERÍODO</p><h2>Desempenho financeiro</h2><span>Faturamento, custo histórico conhecido e lucro bruto estimado.</span></div>{finance.daily.length ? <div className="admin-finance-line"><ResponsiveContainer width="100%" height="100%"><LineChart data={finance.daily} margin={{ top: 15, right: 20, left: 5, bottom: 5 }}><CartesianGrid stroke="#e0e2dc" strokeDasharray="3 3" vertical={false} /><XAxis dataKey="date" tickFormatter={formatDay} minTickGap={28} tick={{ fontSize: 9 }} axisLine={false} tickLine={false} /><YAxis tickFormatter={(value) => `R$ ${Number(value).toLocaleString('pt-BR')}`} tick={{ fontSize: 9 }} width={76} axisLine={false} tickLine={false} /><Tooltip formatter={(value, name) => [money.format(Number(value)), String(name)]} labelFormatter={(label) => formatFullDate(String(label))} contentStyle={{ border: '1px solid #111316', borderRadius: 0, fontSize: 10 }} /><Legend wrapperStyle={{ fontSize: 10 }} /><Line type="monotone" dataKey="revenue" name="Faturamento" stroke="#111316" strokeWidth={3} dot={false} /><Line type="monotone" dataKey="knownProductCost" name="Custo conhecido" stroke="#aeb4ad" strokeWidth={2} dot={false} /><Line type="monotone" dataKey="grossProfit" name="Lucro bruto estimado" stroke="#778f16" strokeWidth={3} dot={false} /></LineChart></ResponsiveContainer></div> : <div className="admin-finance-empty">Nenhum dado diário disponível no período.</div>}</section>

      <section className="admin-finance-secondary-charts">
        <article className="admin-finance-card"><div className="admin-section-title"><p className="eyebrow">PAGAMENTOS</p><h2>Vendas por pagamento</h2></div>{finance.paymentMethods.length ? <div className="admin-finance-donut-layout"><div className="admin-finance-donut"><ResponsiveContainer width="100%" height="100%"><PieChart><Tooltip content={({ active, payload }) => active && payload?.[0] ? <div className="admin-finance-tooltip"><strong>{String(payload[0].payload.method)}</strong><span>{Number(payload[0].payload.orders).toLocaleString('pt-BR')} pedidos</span><span>{money.format(Number(payload[0].payload.revenue))}</span></div> : null} /><Pie data={finance.paymentMethods} dataKey="revenue" nameKey="method" innerRadius="58%" outerRadius="86%" stroke="none">{finance.paymentMethods.map((item, index) => <Cell key={item.method} fill={CHART_COLORS[index % CHART_COLORS.length]} />)}</Pie></PieChart></ResponsiveContainer></div><ul>{finance.paymentMethods.map((item, index) => <li key={item.method}><i style={{ background: CHART_COLORS[index % CHART_COLORS.length] }} /><span>{item.method}</span><strong>{money.format(item.revenue)}</strong></li>)}</ul></div> : <div className="admin-finance-empty">Nenhuma venda com pagamento identificado no período.</div>}</article>
        <article className="admin-finance-card"><div className="admin-section-title"><p className="eyebrow">PEDIDOS</p><h2>Pedidos por status</h2></div>{activeStatuses.length ? <div className="admin-finance-donut-layout"><div className="admin-finance-donut"><ResponsiveContainer width="100%" height="100%"><PieChart><Tooltip formatter={(value) => [Number(value).toLocaleString('pt-BR'), 'Pedidos']} /><Pie data={activeStatuses} dataKey="quantity" nameKey="status" innerRadius="58%" outerRadius="86%" stroke="none">{activeStatuses.map((item) => <Cell key={item.status} fill={STATUS_COLORS[item.status]} />)}</Pie></PieChart></ResponsiveContainer></div><ul>{activeStatuses.map((item) => <li key={item.status}><i style={{ background: STATUS_COLORS[item.status] }} /><span>{financeStatusLabels[item.status]}</span><strong>{item.quantity}</strong></li>)}</ul></div> : <div className="admin-finance-empty">Nenhum pedido no período.</div>}</article>
      </section>

      <section className="admin-finance-rankings"><article className="admin-finance-card"><div className="admin-section-title"><p className="eyebrow">DESTAQUES</p><h2>Produtos mais vendidos</h2></div><RankingChart data={finance.topSellingProducts} empty="Nenhum produto vendido no período." /></article><article className="admin-finance-card"><div className="admin-section-title"><p className="eyebrow">MENOR SAÍDA</p><h2>Produtos com menor saída</h2></div><RankingChart data={finance.lowestSellingProducts} empty="Nenhum produto vendido no período." /></article></section>

      <section className="admin-finance-card admin-finance-transactions"><div className="admin-section-title"><p className="eyebrow">MOVIMENTAÇÃO</p><h2>Transações recentes</h2></div>{finance.recentTransactions.length ? <div className="admin-finance-transaction-list"><div className="admin-finance-transaction-head"><span>Pedido</span><span>Cliente</span><span>Data</span><span>Status</span><span>Pagamento</span><span>Faturamento</span><span>Custo conhecido</span><span>Lucro bruto</span><span className="sr-only">Ação</span></div>{finance.recentTransactions.map((transaction) => <article key={transaction.orderId}><strong data-label="Pedido">#{transaction.orderId}</strong><div data-label="Cliente"><strong>{transaction.customer.name}</strong><small>{transaction.customer.email}</small></div><time data-label="Data" dateTime={transaction.createdAt}>{dateTime.format(new Date(transaction.createdAt))}</time><div data-label="Status"><span className={`admin-status ${transaction.status.toLowerCase()}`}>{financeStatusLabels[transaction.status]}</span></div><div data-label="Pagamento"><strong>{transaction.paymentMethod ? `${transaction.paymentMethod} · ${transaction.paymentStatus ? paymentLabels[transaction.paymentStatus] : 'Não identificado'}` : 'Não identificado'}</strong>{transaction.paidAt && <small>Pago em {dateTime.format(new Date(transaction.paidAt))}</small>}</div><strong data-label="Faturamento">{money.format(transaction.total)}</strong><div data-label="Custo conhecido"><strong>{money.format(transaction.knownProductCost)}</strong>{!transaction.costComplete && <small className="partial">Parcial</small>}</div><strong data-label="Lucro bruto">{money.format(transaction.grossProfit)}</strong><Link href={`/admin/pedidos/${transaction.orderId}`}>Ver pedido</Link></article>)}</div> : <div className="admin-finance-empty">Nenhuma transação no período.</div>}</section>
    </>}
  </>;
}
