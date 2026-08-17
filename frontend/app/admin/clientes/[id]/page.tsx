'use client';

import Link from 'next/link';
import { useParams, useRouter } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AdminVehicleImage } from '@/components/admin/AdminVehicleImage';
import { useAuth } from '@/context/AuthContext';
import { AdminCustomerApiError, getAdminCustomer } from '@/lib/api/admin/customers';
import { adminCustomerAuthProviderLabels, type AdminCustomerDetail } from '@/lib/types/admin-customer';
import { adminOrderStatusLabels, type AdminPaymentStatus } from '@/lib/types/admin-order';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const paymentLabels: Record<AdminPaymentStatus, string> = { PENDING: 'Aguardando pagamento', PAID: 'Pago', EXPIRED: 'Expirado', CANCELED: 'Cancelado', FAILED: 'Falhou' };
function formattedDate(value: string | null) { return value ? dateTime.format(new Date(value)) : 'Nenhum pedido'; }

function CustomerDetailSkeleton() {
  return <div className="admin-customer-detail-skeleton" aria-label="Carregando cliente"><span /><span /><span /><span /></div>;
}

export default function AdminCustomerDetailPage() {
  const params = useParams<{ id: string }>();
  const router = useRouter();
  const { logout } = useAuth();
  const id = Number(params.id);
  const [detail, setDetail] = useState<AdminCustomerDetail | null>(null);
  const [orderPage, setOrderPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [ordersLoading, setOrdersLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);

  useEffect(() => {
    if (!Number.isSafeInteger(id) || id <= 0) return;
    let current = true;
    getAdminCustomer(id, orderPage).then((data) => { if (current) setDetail(data); }).catch((cause: unknown) => {
      if (!current) return;
      if (cause instanceof AdminCustomerApiError && cause.status === 401) { logout(); router.replace('/login'); }
      else if (cause instanceof AdminCustomerApiError && cause.status === 403) setDenied(true);
      else setError(cause instanceof AdminCustomerApiError && cause.status === 404 ? 'Cliente não encontrado.' : 'Não foi possível carregar este cliente.');
    }).finally(() => { if (current) { setLoading(false); setOrdersLoading(false); } });
    return () => { current = false; };
  }, [id, logout, orderPage, router]);

  if (!Number.isSafeInteger(id) || id <= 0) return <div className="admin-feedback error"><p>Cliente inválido.</p><Link href="/admin/clientes">Voltar aos clientes</Link></div>;
  if (loading) return <CustomerDetailSkeleton />;
  if (denied) return <div className="admin-feedback error" role="alert">Acesso negado. Sua conta não possui permissão administrativa.</div>;
  if (error || !detail) return <div className="admin-feedback error"><p>{error ?? 'Cliente não encontrado.'}</p><Link href="/admin/clientes">Voltar aos clientes</Link></div>;
  const { customer, purchaseSummary, addresses, vehicles, orders } = detail;

  return <>
    <header className="admin-page-heading admin-customer-detail-heading"><div><Link href="/admin/clientes">← Clientes</Link><p className="eyebrow">CLIENTE #{customer.id}</p><h1>{customer.name}</h1><p>{customer.email} · Cliente desde {dateTime.format(new Date(customer.createdAt))}</p></div><div className="admin-customer-heading-badges"><span className={`admin-auth-provider ${customer.authProvider.toLowerCase()}`}>{adminCustomerAuthProviderLabels[customer.authProvider]}</span><span className={`admin-customer-status ${customer.active ? 'active' : 'inactive'}`}>{customer.active ? 'Ativo' : 'Inativo'}</span></div></header>

    <section className="admin-customer-metrics" aria-label="Resumo do relacionamento">
      <article><span>Pedidos totais</span><strong>{purchaseSummary.totalOrders.toLocaleString('pt-BR')}</strong></article>
      <article><span>Compras confirmadas</span><strong>{purchaseSummary.confirmedOrders.toLocaleString('pt-BR')}</strong></article>
      <article><span>Total gasto</span><strong>{currency.format(purchaseSummary.totalSpent)}</strong></article>
      <article><span>Ticket médio</span><strong>{currency.format(purchaseSummary.averageTicket)}</strong></article>
      <article><span>Último pedido</span><strong>{formattedDate(purchaseSummary.lastOrderAt)}</strong></article>
    </section>

    <section className="admin-card admin-customer-data"><div className="admin-section-title"><p className="eyebrow">CADASTRO</p><h2>Dados do cliente</h2></div><dl><div><dt>Nome</dt><dd>{customer.name}</dd></div><div><dt>E-mail</dt><dd><a href={`mailto:${customer.email}`}>{customer.email}</a></dd></div><div><dt>Tipo de login</dt><dd>{adminCustomerAuthProviderLabels[customer.authProvider]}</dd></div><div><dt>E-mail verificado</dt><dd>{customer.emailVerified ? 'Sim' : 'Não'}</dd></div><div><dt>Status</dt><dd>{customer.active ? 'Ativo' : 'Inativo'}</dd></div><div><dt>Cadastro</dt><dd>{dateTime.format(new Date(customer.createdAt))}</dd></div></dl></section>

    <div className="admin-customer-columns">
      <section className="admin-card"><div className="admin-section-title"><p className="eyebrow">ENTREGA</p><h2>Endereços</h2></div>{addresses.length ? <div className="admin-customer-addresses">{addresses.map((address) => <address key={address.id} className={address.primary ? 'primary' : ''}><div><strong>{address.label || 'Endereço'}</strong>{address.primary && <span>Principal</span>}</div><b>{address.recipientName}</b><p>{address.street}, {address.number}</p>{address.complement && <p>{address.complement}</p>}<p>{address.neighborhood}</p><p>{address.city} — {address.state}</p><small>CEP {address.zipCode}</small></address>)}</div> : <div className="admin-customer-empty"><p>Nenhum endereço cadastrado.</p></div>}</section>
      <section className="admin-card"><div className="admin-section-title"><p className="eyebrow">GARAGEM</p><h2>Veículos</h2></div>{vehicles.length ? <div className="admin-customer-vehicles">{vehicles.map((vehicle) => <article key={vehicle.id} className={vehicle.primary ? 'primary' : ''}><AdminVehicleImage src={vehicle.imageUrl} alt={`${vehicle.brand} ${vehicle.model}`} /><div><div><strong>{vehicle.brand} {vehicle.model}</strong>{vehicle.primary && <span>Principal</span>}</div><p>{vehicle.year}{vehicle.version ? ` · ${vehicle.version}` : ''}</p><small>{vehicle.licensePlate || 'Placa não informada'}</small></div></article>)}</div> : <div className="admin-customer-empty"><p>Nenhum veículo cadastrado.</p></div>}</section>
    </div>

    <section className="admin-customer-orders">
      <div className="admin-section-title"><p className="eyebrow">HISTÓRICO</p><h2>Pedidos</h2></div>
      {ordersLoading ? <div className="admin-feedback" role="status">Carregando pedidos…</div> : orders.content.length ? <>
        <div className="admin-customer-order-list"><div className="admin-customer-order-head"><span>Pedido</span><span>Data</span><span>Status</span><span>Total</span><span>Pagamento</span><span className="sr-only">Ação</span></div>{orders.content.map((order) => <article key={order.id}>
          <strong data-label="Pedido">#{order.id}</strong><time data-label="Data" dateTime={order.createdAt}>{dateTime.format(new Date(order.createdAt))}</time><div data-label="Status"><span className={`admin-status ${order.status.toLowerCase()}`}>{adminOrderStatusLabels[order.status]}</span></div><strong data-label="Total">{currency.format(order.total)}</strong><div className="admin-customer-payment" data-label="Pagamento">{order.payment ? <><strong>{order.payment.method} · {paymentLabels[order.payment.status]}</strong>{order.payment.paidAt && <small>Pago em {dateTime.format(new Date(order.payment.paidAt))}</small>}</> : <span>Sem pagamento registrado</span>}</div><Link href={`/admin/pedidos/${order.id}`}>Ver pedido</Link>
        </article>)}</div>
        <nav className="admin-pagination" aria-label="Paginação dos pedidos do cliente"><button type="button" disabled={orders.page === 0} onClick={() => { setOrdersLoading(true); setOrderPage((value) => value - 1); }}>← Anterior</button><span>Página {orders.page + 1} de {Math.max(orders.totalPages, 1)}</span><button type="button" disabled={orders.page + 1 >= orders.totalPages} onClick={() => { setOrdersLoading(true); setOrderPage((value) => value + 1); }}>Próxima →</button></nav>
      </> : <div className="admin-customer-empty"><p>Este cliente ainda não possui pedidos.</p></div>}
    </section>
  </>;
}
