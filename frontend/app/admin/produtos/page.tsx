'use client';

import Link from 'next/link';
import { FormEvent, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminProductImage } from '@/components/admin/AdminProductImage';
import { ProductActiveDialog } from '@/components/admin/ProductActiveDialog';
import { useAuth } from '@/context/AuthContext';
import { productMargin } from '@/lib/admin/product-finance';
import { AdminProductApiError, getAdminProducts, setAdminProductActive } from '@/lib/api/admin/products';
import type { AdminProduct, AdminProductPage } from '@/lib/types/admin-product';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

export default function AdminProductsPage() {
  const router = useRouter();
  const { logout } = useAuth();
  const [result, setResult] = useState<AdminProductPage | null>(null);
  const [draftSearch, setDraftSearch] = useState('');
  const [draftCategory, setDraftCategory] = useState('');
  const [search, setSearch] = useState('');
  const [category, setCategory] = useState('');
  const [activeFilter, setActiveFilter] = useState<'all' | 'true' | 'false'>('all');
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [deactivating, setDeactivating] = useState<AdminProduct | null>(null);
  const [updatingId, setUpdatingId] = useState<number | null>(null);
  const [requestKey, setRequestKey] = useState(0);

  useEffect(() => {
    let current = true;
    getAdminProducts({ search: search || undefined, category: category || undefined, active: activeFilter === 'all' ? undefined : activeFilter === 'true', page, size: 20 })
      .then((data) => { if (current) setResult(data); })
      .catch((cause: unknown) => {
        if (!current) return;
        if (cause instanceof AdminProductApiError && cause.status === 401) { logout(); router.replace('/login'); }
        else if (cause instanceof AdminProductApiError && cause.status === 403) setDenied(true);
        else setError('Não foi possível carregar os produtos. Tente novamente.');
      }).finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [activeFilter, category, logout, page, requestKey, router, search]);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    setLoading(true); setError(null); setDenied(false); setFeedback(null); setPage(0);
    setSearch(draftSearch.trim()); setCategory(draftCategory.trim());
  }

  async function changeActive(product: AdminProduct, active: boolean) {
    if (updatingId !== null) return;
    setUpdatingId(product.id); setFeedback(null); setError(null);
    try {
      const updated = await setAdminProductActive(product.id, active);
      setResult((current) => current ? { ...current, content: current.content.map((item) => item.id === updated.id ? updated : item) } : current);
      setDeactivating(null);
      setFeedback(`${updated.name} foi ${updated.active ? 'reativado' : 'desativado'} com sucesso.`);
    } catch (cause) {
      if (cause instanceof AdminProductApiError && cause.status === 401) { logout(); router.replace('/login'); return; }
      if (cause instanceof AdminProductApiError && cause.status === 403) { setDenied(true); return; }
      if (cause instanceof AdminProductApiError && cause.status === 404) setError('Produto não encontrado. Atualize a listagem.');
      else setError('Não foi possível alterar a disponibilidade do produto.');
    } finally { setUpdatingId(null); }
  }

  return <>
    <header className="admin-page-heading admin-products-heading"><div><p className="eyebrow">CATÁLOGO</p><h1>Produtos</h1><p>{result ? `${result.totalElements} produto${result.totalElements === 1 ? '' : 's'} encontrado${result.totalElements === 1 ? '' : 's'}` : 'Gerencie o catálogo da inGarage.'}</p></div><div className="admin-products-heading-actions"><Link className="secondary" href="/admin/produtos/importar">Importar da CJ</Link><Link href="/admin/produtos/novo">Novo produto</Link></div></header>
    {feedback && <p className="admin-action-feedback" role="status">{feedback}</p>}
    <section className="admin-products-panel">
      <form className="admin-products-toolbar" onSubmit={applyFilters}>
        <label>Buscar por nome ou SKU<input type="search" value={draftSearch} onChange={(event) => setDraftSearch(event.target.value)} placeholder="Ex.: V-Floc ou SKU-001" /></label>
        <label>Status<select value={activeFilter} onChange={(event) => { setLoading(true); setPage(0); setActiveFilter(event.target.value as typeof activeFilter); }}><option value="all">Todos</option><option value="true">Ativos</option><option value="false">Inativos</option></select></label>
        <label>Categoria<input value={draftCategory} onChange={(event) => setDraftCategory(event.target.value)} placeholder="Todas" /></label>
        <button type="submit">Aplicar filtros</button>
      </form>
      {loading && <div className="admin-feedback" role="status">Carregando produtos…</div>}
      {denied && <div className="admin-feedback error" role="alert">Acesso negado. Sua conta não possui permissão administrativa.</div>}
      {error && <div className="admin-feedback error" role="alert"><p>{error}</p><button type="button" onClick={() => { setLoading(true); setError(null); setRequestKey((value) => value + 1); }}>Tentar novamente</button></div>}
      {!loading && !error && !denied && result && (result.content.length ? <>
        <div className="admin-products-list"><div className="admin-products-list-head"><span>Produto</span><span>Categoria</span><span>Preço / custo</span><span>Estoque</span><span>Margem estimada</span><span>Status</span><span className="sr-only">Ações</span></div>
          {result.content.map((product) => { const margin = productMargin(product.price, product.costPrice); return <article key={product.id}>
            <div className="admin-product-identity"><AdminProductImage src={product.imageUrl} alt={product.name} /><div><strong>{product.name}</strong><span>SKU {product.sku}</span><small>{product.productType === 'KIT' ? 'Kit' : 'Produto individual'}{product.supplier ? <b className="admin-supplier-badge">{product.supplier}</b> : null}</small></div></div>
            <div data-label="Categoria">{product.category}</div>
            <div data-label="Preço / custo"><strong>{currency.format(product.price)}</strong><span>Custo {currency.format(product.costPrice)}</span></div>
            <div data-label="Estoque">{product.fulfillmentType === 'DROPSHIPPING' ? <><strong>Não aplicável</strong><span>Dropshipping</span></> : <><strong>{product.stock}</strong><span>{product.stock === 1 ? 'unidade' : 'unidades'}</span></>}</div>
            <div data-label="Margem"><strong>{currency.format(margin.marginCents / 100)}</strong><span>{margin.percentage.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</span></div>
            <div data-label="Status"><span className={`admin-product-status ${product.active ? 'active' : 'inactive'}`}>{product.active ? 'Ativo' : 'Inativo'}</span></div>
            <div className="admin-product-actions"><Link href={`/admin/produtos/${product.id}`}>Ver / editar</Link>{product.active ? <button type="button" disabled={updatingId !== null} onClick={() => setDeactivating(product)}>Desativar</button> : <button type="button" disabled={updatingId !== null} onClick={() => void changeActive(product, true)}>{updatingId === product.id ? 'Ativando…' : 'Ativar'}</button>}</div>
          </article>; })}
        </div>
        <nav className="admin-pagination" aria-label="Paginação de produtos"><button type="button" disabled={result.first} onClick={() => { setLoading(true); setPage((value) => value - 1); }}>← Anterior</button><span>Página {result.page + 1} de {Math.max(result.totalPages, 1)}</span><button type="button" disabled={result.last} onClick={() => { setLoading(true); setPage((value) => value + 1); }}>Próxima →</button></nav>
      </> : <div className="admin-feedback"><p>Nenhum produto encontrado com estes filtros.</p></div>)}
    </section>
    {deactivating && <ProductActiveDialog productName={deactivating.name} busy={updatingId === deactivating.id} onCancel={() => setDeactivating(null)} onConfirm={() => void changeActive(deactivating, false)} />}
  </>;
}
