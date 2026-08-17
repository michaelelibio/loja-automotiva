'use client';

import Link from 'next/link';
import { useParams, useRouter, useSearchParams } from 'next/navigation';
import { useEffect, useState } from 'react';
import { AdminProductForm } from '@/components/admin/AdminProductForm';
import { AdminProductImage } from '@/components/admin/AdminProductImage';
import { ProductActiveDialog } from '@/components/admin/ProductActiveDialog';
import { useAuth } from '@/context/AuthContext';
import { productMargin } from '@/lib/admin/product-finance';
import { AdminProductApiError, getAdminProduct, setAdminProductActive, updateAdminProduct } from '@/lib/api/admin/products';
import type { AdminProduct, AdminProductPayload } from '@/lib/types/admin-product';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const dateTime = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

export default function AdminProductDetailPage() {
  const params = useParams<{ id: string }>();
  const searchParams = useSearchParams();
  const router = useRouter();
  const { logout } = useAuth();
  const id = Number(params.id);
  const [product, setProduct] = useState<AdminProduct | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [fields, setFields] = useState<Record<string, string>>({});
  const [denied, setDenied] = useState(false);
  const [feedback, setFeedback] = useState(searchParams.get('created') === '1' ? 'Produto criado com sucesso.' : null);
  const [confirming, setConfirming] = useState(false);
  const [changingActive, setChangingActive] = useState(false);

  useEffect(() => {
    if (!Number.isSafeInteger(id) || id <= 0) return;
    let current = true;
    getAdminProduct(id).then((data) => { if (current) setProduct(data); }).catch((cause: unknown) => {
      if (!current) return;
      if (cause instanceof AdminProductApiError && cause.status === 401) { logout(); router.replace('/login'); }
      else if (cause instanceof AdminProductApiError && cause.status === 403) setDenied(true);
      else setError(cause instanceof AdminProductApiError && cause.status === 404 ? 'Produto não encontrado.' : 'Não foi possível carregar este produto.');
    }).finally(() => { if (current) setLoading(false); });
    return () => { current = false; };
  }, [id, logout, router]);

  async function save(payload: AdminProductPayload) {
    if (!product) return;
    setSubmitting(true); setError(null); setFields({}); setFeedback(null);
    try {
      const updated = await updateAdminProduct(product.id, { ...payload, active: product.active });
      setProduct(updated); setFeedback('Produto atualizado com sucesso.');
    } catch (cause) {
      if (cause instanceof AdminProductApiError && cause.status === 401) { logout(); router.replace('/login'); return; }
      if (cause instanceof AdminProductApiError && cause.status === 403) { setDenied(true); return; }
      if (cause instanceof AdminProductApiError && cause.status === 404) { setError('Produto não encontrado.'); return; }
      if (cause instanceof AdminProductApiError && cause.status === 409) { const sku = cause.message.toLowerCase().includes('sku'); const message = sku ? 'Já existe um produto com este SKU.' : 'Já existe um produto com este slug.'; setError(message); setFields(sku ? { sku: message } : { slug: message }); return; }
      if (cause instanceof AdminProductApiError && cause.status === 400) { setError('Revise os campos destacados.'); setFields(cause.fields); return; }
      setError('Não foi possível atualizar o produto. Tente novamente.');
    } finally { setSubmitting(false); }
  }

  async function changeActive(active: boolean) {
    if (!product || changingActive) return;
    setChangingActive(true); setError(null); setFeedback(null);
    try { const updated = await setAdminProductActive(product.id, active); setProduct(updated); setConfirming(false); setFeedback(`Produto ${active ? 'reativado' : 'desativado'} com sucesso.`); }
    catch (cause) {
      if (cause instanceof AdminProductApiError && cause.status === 401) { logout(); router.replace('/login'); return; }
      if (cause instanceof AdminProductApiError && cause.status === 403) { setDenied(true); return; }
      if (cause instanceof AdminProductApiError && cause.status === 404) setError('Produto não encontrado.'); else setError('Não foi possível alterar a disponibilidade do produto.');
    } finally { setChangingActive(false); }
  }

  if (!Number.isSafeInteger(id) || id <= 0) return <div className="admin-feedback error"><p>Produto inválido.</p><Link href="/admin/produtos">Voltar aos produtos</Link></div>;
  if (loading) return <div className="admin-feedback" role="status">Carregando produto…</div>;
  if (denied) return <div className="admin-feedback error" role="alert">Acesso negado. Sua conta não possui permissão administrativa.</div>;
  if (!product) return <div className="admin-feedback error"><p>{error ?? 'Produto não encontrado.'}</p><Link href="/admin/produtos">Voltar aos produtos</Link></div>;
  const margin = productMargin(product.price, product.costPrice);

  return <>
    <header className="admin-page-heading admin-product-detail-heading"><div><Link href="/admin/produtos">← Produtos</Link><p className="eyebrow">SKU {product.sku}</p><h1>{product.name}</h1><p>Criado em {dateTime.format(new Date(product.createdAt))} · Atualizado em {dateTime.format(new Date(product.updatedAt))}</p></div><AdminProductImage src={product.imageUrl} alt={product.name} size={92} /></header>
    {feedback && <p className="admin-action-feedback" role="status">{feedback}</p>}
    <section className="admin-product-overview"><div><span>Status</span><strong className={`admin-product-status ${product.active ? 'active' : 'inactive'}`}>{product.active ? 'Ativo' : 'Inativo'}</strong></div><div><span>Margem bruta estimada</span><strong>{currency.format(margin.marginCents / 100)}</strong></div><div><span>Margem percentual</span><strong>{margin.percentage.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}%</strong></div><div className="admin-product-availability"><span>Disponibilidade</span>{product.active ? <button type="button" onClick={() => setConfirming(true)}>Desativar produto</button> : <button type="button" disabled={changingActive} onClick={() => void changeActive(true)}>{changingActive ? 'Reativando…' : 'Reativar produto'}</button>}</div></section>
    <AdminProductForm key={`${product.id}-${product.updatedAt}`} product={product} submitting={submitting} apiError={error} apiFields={fields} onSubmit={save} />
    {confirming && <ProductActiveDialog productName={product.name} busy={changingActive} onCancel={() => setConfirming(false)} onConfirm={() => void changeActive(false)} />}
  </>;
}
