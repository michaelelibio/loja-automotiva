'use client';

import Link from 'next/link';
import { FormEvent, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminProductImage } from '@/components/admin/AdminProductImage';
import { useAuth } from '@/context/AuthContext';
import { AdminCjApiError, getCjProduct, importCjProduct, searchCjProducts } from '@/lib/api/admin/cj-products';
import type { AdminCjImportedProduct, AdminCjProduct, AdminCjProductPage } from '@/lib/types/admin-cj-product';

const usd = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'USD' });

function errorMessage(error: unknown, operation: 'search' | 'details' | 'import') {
  if (!(error instanceof AdminCjApiError)) return 'Ocorreu um erro inesperado. Tente novamente.';
  if (error.status === 0) return 'Não foi possível conectar ao backend. Verifique sua conexão.';
  if (error.status === 403) return 'Sua conta não possui permissão para acessar a integração CJ.';
  if (error.status === 409) return 'Este produto já foi importado.';
  if (error.status === 429) return 'A CJ limitou temporariamente as consultas. Aguarde um momento e tente novamente.';
  if (error.status >= 500) return 'A integração com a CJ está temporariamente indisponível.';
  if (operation === 'details') return 'Não foi possível carregar os detalhes deste produto.';
  if (operation === 'import') return 'Não foi possível importar este produto.';
  return 'Não foi possível pesquisar produtos na CJ.';
}

export default function ImportCjProductsPage() {
  const router = useRouter();
  const { logout } = useAuth();
  const [draftKeyword, setDraftKeyword] = useState('');
  const [keyword, setKeyword] = useState('');
  const [result, setResult] = useState<AdminCjProductPage | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [detail, setDetail] = useState<AdminCjProduct | null>(null);
  const [detailLoadingId, setDetailLoadingId] = useState<string | null>(null);
  const [importingId, setImportingId] = useState<string | null>(null);
  const [imported, setImported] = useState<Record<string, AdminCjImportedProduct>>({});
  const [itemErrors, setItemErrors] = useState<Record<string, string>>({});

  function handleAuth(cause: unknown) {
    if (cause instanceof AdminCjApiError && cause.status === 401) {
      logout(); router.replace('/login'); return true;
    }
    return false;
  }

  async function search(nextKeyword: string, page: number) {
    setLoading(true); setError(null); setDetail(null);
    try { setResult(await searchCjProducts(nextKeyword, page)); }
    catch (cause) { if (!handleAuth(cause)) setError(errorMessage(cause, 'search')); }
    finally { setLoading(false); }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    const cleanKeyword = draftKeyword.trim();
    if (!cleanKeyword) { setError('Digite um termo para pesquisar na CJ.'); return; }
    setKeyword(cleanKeyword); void search(cleanKeyword, 1);
  }

  async function showDetails(productId: string) {
    setDetailLoadingId(productId); setError(null);
    try { setDetail(await getCjProduct(productId)); }
    catch (cause) { if (!handleAuth(cause)) setError(errorMessage(cause, 'details')); }
    finally { setDetailLoadingId(null); }
  }

  async function importProduct(productId: string) {
    if (importingId) return;
    setImportingId(productId); setItemErrors((current) => ({ ...current, [productId]: '' }));
    try {
      const local = await importCjProduct(productId);
      setImported((current) => ({ ...current, [productId]: local }));
    } catch (cause) {
      if (!handleAuth(cause)) setItemErrors((current) => ({ ...current, [productId]: errorMessage(cause, 'import') }));
    } finally { setImportingId(null); }
  }

  return <>
    <header className="admin-page-heading admin-cj-heading"><div><Link href="/admin/produtos">← Produtos</Link><p className="eyebrow">GARAGE / CATÁLOGO</p><h1>Importar da CJ</h1><p>Pesquise o catálogo da CJ. Produtos importados ficam inativos para revisão de preço, categoria e publicação.</p></div></header>
    <section className="admin-cj-panel">
      <form className="admin-cj-search" onSubmit={submit}><label>Buscar produtos na CJ<input type="search" value={draftKeyword} onChange={(event) => setDraftKeyword(event.target.value)} placeholder="Ex.: car light, cleaning, phone holder..." /></label><button type="submit" disabled={loading}>{loading ? 'Buscando…' : 'Buscar'}</button></form>
      {error && <div className="admin-feedback error" role="alert"><p>{error}</p></div>}
      {loading && <div className="admin-feedback" role="status">Consultando produtos na CJ…</div>}
      {!loading && !error && !result && <div className="admin-feedback"><p>Use a busca acima para consultar produtos do fornecedor.</p></div>}
      {!loading && result && (result.products.length ? <>
        <div className="admin-cj-results">{result.products.map((product) => {
          const local = imported[product.cjProductId];
          return <article key={product.cjProductId} className={local ? 'imported' : ''}><AdminProductImage src={product.imageUrl} alt={product.name ?? 'Produto CJ'} size={84} /><div className="admin-cj-product-copy"><span className="admin-supplier-badge">CJ</span><h2>{product.name || 'Produto sem nome'}</h2><p>{product.categoryName || 'Categoria não informada'}</p><dl><div><dt>SKU</dt><dd>{product.sku || 'Não informado'}</dd></div><div><dt>Custo original</dt><dd>{product.priceUsd == null ? 'Não informado' : usd.format(product.priceUsd)}</dd></div><div><dt>ID CJ</dt><dd>{product.cjProductId}</dd></div></dl></div><div className="admin-cj-actions">{local ? <><span className="admin-product-status active">Importado</span><Link href={`/admin/produtos/${local.id}`}>Ver produto</Link></> : <><button className="secondary" type="button" disabled={detailLoadingId !== null || importingId !== null} onClick={() => void showDetails(product.cjProductId)}>{detailLoadingId === product.cjProductId ? 'Carregando…' : 'Ver detalhes'}</button><button type="button" disabled={importingId !== null} onClick={() => void importProduct(product.cjProductId)}>{importingId === product.cjProductId ? 'Importando…' : 'Importar'}</button></>}{itemErrors[product.cjProductId] && <small role="alert">{itemErrors[product.cjProductId]}</small>}</div></article>;
        })}</div>
        <nav className="admin-pagination" aria-label="Paginação de produtos CJ"><button type="button" disabled={result.page <= 1 || loading} onClick={() => void search(keyword, result.page - 1)}>← Anterior</button><span>Página {result.page} de {Math.max(result.totalPages, 1)} · {result.totalRecords} resultados</span><button type="button" disabled={result.page >= result.totalPages || loading} onClick={() => void search(keyword, result.page + 1)}>Próxima →</button></nav>
      </> : <div className="admin-feedback"><p>Nenhum produto encontrado para “{keyword}”.</p></div>)}
    </section>
    {detail && <div className="admin-cj-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget) setDetail(null); }}><section className="admin-cj-dialog" role="dialog" aria-modal="true" aria-labelledby="cj-detail-title"><button className="admin-cj-dialog-close" type="button" aria-label="Fechar detalhes" onClick={() => setDetail(null)}>×</button><AdminProductImage src={detail.imageUrl} alt={detail.name ?? 'Produto CJ'} size={150} /><div><p className="eyebrow">PRODUTO CJ</p><h2 id="cj-detail-title">{detail.name || 'Produto sem nome'}</h2><dl><div><dt>SKU</dt><dd>{detail.sku || 'Não informado'}</dd></div><div><dt>Categoria CJ</dt><dd>{detail.categoryName || 'Não informada'}</dd></div><div><dt>Custo original</dt><dd>{detail.priceUsd == null ? 'Não informado' : usd.format(detail.priceUsd)}</dd></div><div><dt>ID CJ</dt><dd>{detail.cjProductId}</dd></div></dl><div className="admin-cj-dialog-actions"><button type="button" onClick={() => setDetail(null)}>Voltar</button><button type="button" disabled={importingId !== null || Boolean(imported[detail.cjProductId])} onClick={() => void importProduct(detail.cjProductId)}>{imported[detail.cjProductId] ? 'Importado' : importingId === detail.cjProductId ? 'Importando…' : 'Importar produto'}</button>{imported[detail.cjProductId] && <Link href={`/admin/produtos/${imported[detail.cjProductId].id}`}>Ver produto</Link>}</div>{itemErrors[detail.cjProductId] && <p className="admin-action-feedback error" role="alert">{itemErrors[detail.cjProductId]}</p>}</div></section></div>}
  </>;
}
