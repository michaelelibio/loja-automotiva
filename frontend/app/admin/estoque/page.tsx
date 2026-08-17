'use client';

import Link from 'next/link';
import { FormEvent, useCallback, useEffect, useState } from 'react';
import { useRouter } from 'next/navigation';
import { AdminProductPicker } from '@/components/admin/AdminProductPicker';
import { StockMovementDialog } from '@/components/admin/StockMovementDialog';
import { useAuth } from '@/context/AuthContext';
import { AdminStockApiError, getAdminStockMovements, getAdminStockSummary } from '@/lib/api/admin/stock';
import type { AdminProduct } from '@/lib/types/admin-product';
import { adminStockMovementLabels, type AdminStockMovement, type AdminStockMovementPage, type AdminStockMovementType, type AdminStockSummary } from '@/lib/types/admin-stock';

const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });
const movementTypes = Object.keys(adminStockMovementLabels) as AdminStockMovementType[];

type Filters = { productId?: number; type?: AdminStockMovementType; dateFrom?: string; dateTo?: string };
function startOfDay(value: string) { return value ? new Date(`${value}T00:00:00`).toISOString() : undefined; }
function endOfDay(value: string) { return value ? new Date(`${value}T23:59:59.999`).toISOString() : undefined; }

export default function AdminStockPage() {
  const router = useRouter();
  const { logout } = useAuth();
  const [summary, setSummary] = useState<AdminStockSummary | null>(null);
  const [movements, setMovements] = useState<AdminStockMovementPage | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);
  const [historyError, setHistoryError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);
  const [page, setPage] = useState(0);
  const [filters, setFilters] = useState<Filters>({});
  const [draftProduct, setDraftProduct] = useState<AdminProduct | null>(null);
  const [draftType, setDraftType] = useState<AdminStockMovementType | ''>('');
  const [draftFrom, setDraftFrom] = useState('');
  const [draftTo, setDraftTo] = useState('');
  const [filterError, setFilterError] = useState<string | null>(null);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [feedback, setFeedback] = useState<string | null>(null);
  const [refreshKey, setRefreshKey] = useState(0);

  const handleAuthorizationError = useCallback((status: 401 | 403) => {
    if (status === 401) { logout(); router.replace('/login'); }
    else setDenied(true);
  }, [logout, router]);

  useEffect(() => {
    let current = true;
    getAdminStockSummary().then((data) => { if (current) setSummary(data); }).catch((cause: unknown) => {
      if (!current) return;
      if (cause instanceof AdminStockApiError && (cause.status === 401 || cause.status === 403)) handleAuthorizationError(cause.status);
      else setSummaryError('Não foi possível carregar o resumo do estoque.');
    }).finally(() => { if (current) setSummaryLoading(false); });
    return () => { current = false; };
  }, [handleAuthorizationError, refreshKey]);

  useEffect(() => {
    let current = true;
    getAdminStockMovements({ ...filters, page, size: 20 }).then((data) => { if (current) setMovements(data); }).catch((cause: unknown) => {
      if (!current) return;
      if (cause instanceof AdminStockApiError && (cause.status === 401 || cause.status === 403)) handleAuthorizationError(cause.status);
      else setHistoryError('Não foi possível carregar o histórico de movimentações.');
    }).finally(() => { if (current) setHistoryLoading(false); });
    return () => { current = false; };
  }, [filters, handleAuthorizationError, page, refreshKey]);

  function applyFilters(event: FormEvent) {
    event.preventDefault();
    if (draftFrom && draftTo && draftFrom > draftTo) { setFilterError('A data inicial deve ser anterior ou igual à data final.'); return; }
    setFilterError(null); setHistoryError(null); setHistoryLoading(true); setPage(0);
    setFilters({ productId: draftProduct?.id, type: draftType || undefined, dateFrom: startOfDay(draftFrom), dateTo: endOfDay(draftTo) });
  }

  function movementCreated(movement: AdminStockMovement) {
    setDialogOpen(false); setFeedback(`${adminStockMovementLabels[movement.type]} registrada: ${movement.previousStock} → ${movement.newStock}.`);
    setPage(0); setSummaryLoading(true); setHistoryLoading(true); setSummaryError(null); setHistoryError(null); setRefreshKey((value) => value + 1);
  }

  return <>
    <header className="admin-page-heading admin-stock-heading"><div><p className="eyebrow">OPERAÇÃO</p><h1>Estoque</h1><p>Saldo atual e histórico oficial de movimentações.</p></div><button type="button" onClick={() => { setFeedback(null); setDialogOpen(true); }}>Nova movimentação</button></header>
    {feedback && <p className="admin-action-feedback" role="status">{feedback}</p>}
    {denied && <div className="admin-feedback error" role="alert">Acesso negado. Sua conta não possui permissão administrativa.</div>}
    {!denied && <>
      <section className="admin-stock-summary" aria-label="Resumo do estoque">
        {summaryLoading ? <div className="admin-feedback" role="status">Carregando resumo…</div> : summaryError || !summary ? <div className="admin-feedback error"><p>{summaryError ?? 'Resumo indisponível.'}</p><button type="button" onClick={() => { setSummaryLoading(true); setSummaryError(null); setRefreshKey((value) => value + 1); }}>Tentar novamente</button></div> : <>
          <article><span>01</span><p>Total de produtos</p><strong>{summary.totalProducts.toLocaleString('pt-BR')}</strong></article>
          <article><span>02</span><p>Unidades em estoque</p><strong>{summary.totalUnits.toLocaleString('pt-BR')}</strong></article>
          <article><span>03</span><p>Produtos sem estoque</p><strong>{summary.outOfStockProducts.toLocaleString('pt-BR')}</strong></article>
        </>}
      </section>

      <section className="admin-stock-history">
        <div className="admin-section-title"><p className="eyebrow">RASTREABILIDADE</p><h2>Histórico de movimentações</h2></div>
        <form className="admin-stock-filters" onSubmit={applyFilters}>
          <label>Produto<AdminProductPicker id="stock-filter-product" value={draftProduct} onChange={setDraftProduct} onAuthorizationError={handleAuthorizationError} allowClear /></label>
          <label>Tipo<select value={draftType} onChange={(event) => setDraftType(event.target.value as AdminStockMovementType | '')}><option value="">Todos</option>{movementTypes.map((type) => <option key={type} value={type}>{adminStockMovementLabels[type]}</option>)}</select></label>
          <label>De<input type="date" value={draftFrom} onChange={(event) => setDraftFrom(event.target.value)} /></label>
          <label>Até<input type="date" value={draftTo} onChange={(event) => setDraftTo(event.target.value)} /></label>
          <button type="submit">Aplicar filtros</button>
        </form>
        {filterError && <p className="admin-stock-filter-error" role="alert">{filterError}</p>}
        {historyLoading && <div className="admin-feedback" role="status">Carregando movimentações…</div>}
        {historyError && !historyLoading && <div className="admin-feedback error"><p>{historyError}</p><button type="button" onClick={() => { setHistoryLoading(true); setHistoryError(null); setRefreshKey((value) => value + 1); }}>Tentar novamente</button></div>}
        {!historyLoading && !historyError && movements && (movements.content.length ? <>
          <div className="admin-stock-list"><div className="admin-stock-list-head"><span>Data</span><span>Produto</span><span>Tipo</span><span>Movimento</span><span>Motivo / referência</span><span>Responsável</span></div>
            {movements.content.map((movement) => <article key={movement.id}>
              <time dateTime={movement.createdAt} data-label="Data">{dateTime.format(new Date(movement.createdAt))}</time>
              <div className="admin-stock-product" data-label="Produto"><strong>{movement.productName}</strong><span>SKU {movement.sku}</span></div>
              <div data-label="Tipo"><span className={`admin-stock-type ${movement.type.toLowerCase()}`}>{adminStockMovementLabels[movement.type]}</span></div>
              <div className="admin-stock-balance" data-label="Estoque"><strong>{movement.previousStock} <span>→</span> {movement.newStock}</strong><small>{movement.quantity} {movement.quantity === 1 ? 'unidade' : 'unidades'}</small></div>
              <div className="admin-stock-reason" data-label="Motivo"><span>{movement.reason}</span>{movement.type === 'SALE' && movement.referenceType === 'ORDER' && movement.referenceId ? <Link href={`/admin/pedidos/${movement.referenceId}`}>Pedido #{movement.referenceId} →</Link> : movement.referenceType && <small>{movement.referenceType} #{movement.referenceId ?? '—'}</small>}</div>
              <div className="admin-stock-performer" data-label="Responsável"><strong>{movement.performedBy?.name ?? 'Sistema'}</strong>{movement.performedBy?.email && <span>{movement.performedBy.email}</span>}</div>
            </article>)}
          </div>
          <nav className="admin-pagination" aria-label="Paginação do histórico"><button type="button" disabled={movements.page === 0} onClick={() => { setHistoryLoading(true); setPage((value) => value - 1); }}>← Anterior</button><span>Página {movements.page + 1} de {Math.max(movements.totalPages, 1)}</span><button type="button" disabled={movements.page + 1 >= movements.totalPages} onClick={() => { setHistoryLoading(true); setPage((value) => value + 1); }}>Próxima →</button></nav>
        </> : <div className="admin-feedback">Nenhuma movimentação encontrada com estes filtros.</div>)}
      </section>
    </>}
    {dialogOpen && <StockMovementDialog onCancel={() => setDialogOpen(false)} onCreated={movementCreated} onAuthorizationError={handleAuthorizationError} />}
  </>;
}
