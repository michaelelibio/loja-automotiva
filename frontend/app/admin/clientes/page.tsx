'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AdminCustomerApiError, getAdminCustomers } from '@/lib/api/admin/customers';
import { adminCustomerAuthProviderLabels, type AdminCustomerAuthProvider, type AdminCustomerPage } from '@/lib/types/admin-customer';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
type Filters = { search?: string; hasOrders?: boolean; authProvider?: AdminCustomerAuthProvider };

function CustomerListSkeleton() {
  return <div className="admin-customer-skeleton" aria-label="Carregando clientes">{Array.from({ length: 5 }, (_, index) => <span key={index} />)}</div>;
}

export default function AdminCustomersPage() {
  const router = useRouter();
  const { logout } = useAuth();
  const [result, setResult] = useState<AdminCustomerPage | null>(null);
  const [filters, setFilters] = useState<Filters>({});
  const [draftSearch, setDraftSearch] = useState('');
  const [draftOrders, setDraftOrders] = useState<'all' | 'true' | 'false'>('all');
  const [draftProvider, setDraftProvider] = useState<AdminCustomerAuthProvider | ''>('');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);
  const [requestKey, setRequestKey] = useState(0);

  useEffect(() => {
    let current = true;
    getAdminCustomers({ ...filters, page, size: 20 }).then((data) => { if (current) setResult(data); }).catch((cause: unknown) => {
      if (!current) return;
      if (cause instanceof AdminCustomerApiError && cause.status === 401) { logout(); router.replace('/login'); }
      else if (cause instanceof AdminCustomerApiError && cause.status === 403) setDenied(true);
      else setError('Não foi possível carregar os clientes. Tente novamente.');
    }).finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [filters, logout, page, requestKey, router]);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setLoading(true); setError(null); setDenied(false); setPage(0);
    setFilters({ search: draftSearch.trim() || undefined, hasOrders: draftOrders === 'all' ? undefined : draftOrders === 'true', authProvider: draftProvider || undefined });
  }

  function clearFilters() {
    setDraftSearch(''); setDraftOrders('all'); setDraftProvider(''); setPage(0); setLoading(true); setError(null); setDenied(false); setFilters({});
  }

  return <>
    <header className="admin-page-heading"><p className="eyebrow">OPERAÇÃO</p><h1>Clientes</h1><p>Base de clientes e histórico de relacionamento.</p></header>
    {denied ? <div className="admin-feedback error" role="alert">Acesso negado. Sua conta não possui permissão administrativa.</div> : <>
      <section className="admin-customer-indicators" aria-label="Indicadores da listagem">
        <article><span>Clientes encontrados</span><strong>{result?.totalElements.toLocaleString('pt-BR') ?? '—'}</strong></article>
        <article><span>Exibidos nesta página</span><strong>{result?.content.length.toLocaleString('pt-BR') ?? '—'}</strong></article>
        <article><span>Página</span><strong>{result ? `${result.page + 1} / ${Math.max(result.totalPages, 1)}` : '—'}</strong></article>
      </section>
      <section className="admin-customers-panel">
        <form className="admin-customer-filters" onSubmit={applyFilters}>
          <label>Nome ou e-mail<input type="search" value={draftSearch} onChange={(event) => setDraftSearch(event.target.value)} placeholder="Buscar cliente" /></label>
          <label>Pedidos<select value={draftOrders} onChange={(event) => setDraftOrders(event.target.value as typeof draftOrders)}><option value="all">Todos</option><option value="true">Com pedidos</option><option value="false">Sem pedidos</option></select></label>
          <label>Login<select value={draftProvider} onChange={(event) => setDraftProvider(event.target.value as AdminCustomerAuthProvider | '')}><option value="">Todos</option><option value="LOCAL">E-mail e senha</option><option value="GOOGLE">Google</option></select></label>
          <button type="submit">Aplicar filtros</button><button className="clear" type="button" onClick={clearFilters}>Limpar</button>
        </form>
        {loading && <CustomerListSkeleton />}
        {error && !loading && <div className="admin-feedback error" role="alert"><p>{error}</p><button type="button" onClick={() => { setLoading(true); setError(null); setRequestKey((value) => value + 1); }}>Tentar novamente</button></div>}
        {!loading && !error && result && (result.content.length ? <>
          <div className="admin-customer-list"><div className="admin-customer-list-head"><span>Cliente</span><span>E-mail</span><span>Login</span><span>Pedidos</span><span>Compras confirmadas</span><span>Total gasto</span><span>Ticket médio</span><span>Último pedido</span><span>Status</span><span className="sr-only">Ação</span></div>
            {result.content.map((customer) => <article key={customer.id}>
              <div className="admin-customer-name" data-label="Cliente"><strong>{customer.name}</strong><small>Desde {dateTime.format(new Date(customer.createdAt))}</small></div>
              <div className="admin-customer-email" data-label="E-mail"><span>{customer.email}</span>{customer.emailVerified && <small>Verificado</small>}</div>
              <div data-label="Login"><span className={`admin-auth-provider ${customer.authProvider.toLowerCase()}`}>{adminCustomerAuthProviderLabels[customer.authProvider]}</span></div>
              <strong data-label="Pedidos">{customer.totalOrders.toLocaleString('pt-BR')}</strong>
              <strong data-label="Confirmadas">{customer.confirmedOrders.toLocaleString('pt-BR')}</strong>
              <strong data-label="Total gasto">{currency.format(customer.totalSpent)}</strong>
              <strong data-label="Ticket médio">{currency.format(customer.averageTicket)}</strong>
              <time data-label="Último pedido" dateTime={customer.lastOrderAt ?? undefined}>{customer.lastOrderAt ? dateTime.format(new Date(customer.lastOrderAt)) : 'Nenhum pedido'}</time>
              <div data-label="Status"><span className={`admin-customer-status ${customer.active ? 'active' : 'inactive'}`}>{customer.active ? 'Ativo' : 'Inativo'}</span></div>
              <Link href={`/admin/clientes/${customer.id}`}>Ver cliente</Link>
            </article>)}
          </div>
          <nav className="admin-pagination" aria-label="Paginação de clientes"><button type="button" disabled={result.page === 0} onClick={() => { setLoading(true); setPage((value) => value - 1); }}>← Anterior</button><span>Página {result.page + 1} de {Math.max(result.totalPages, 1)}</span><button type="button" disabled={result.page + 1 >= result.totalPages} onClick={() => { setLoading(true); setPage((value) => value + 1); }}>Próxima →</button></nav>
        </> : <div className="admin-feedback"><p>{Object.keys(filters).length ? 'Nenhum cliente encontrado com estes filtros.' : 'Nenhum cliente cadastrado.'}</p></div>)}
      </section>
    </>}
  </>;
}
