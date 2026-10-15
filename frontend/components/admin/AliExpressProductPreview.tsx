'use client';

import { FormEvent, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AdminProductImage } from '@/components/admin/AdminProductImage';
import { AliExpressPreviewError, previewAliExpressProduct } from '@/lib/api/admin/aliexpress';
import type { AliExpressPreview } from '@/lib/types/admin-aliexpress';

function priceLabel(price: number | null, currency: string) {
  if (price == null || !Number.isFinite(price)) return 'Preço não informado';
  try { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(price); }
  catch { return `${price.toLocaleString('pt-BR')} ${currency}`; }
}

function Images({ images, name }: { images: string[]; name: string }) {
  const safeImages = images.filter((src) => {
    try { return new URL(src).protocol === 'https:'; } catch { return false; }
  });
  return <div className="admin-aliexpress-images">{safeImages.length ? safeImages.map((src, index) => <AdminProductImage key={`${src}-${index}`} src={src} alt={`${name} — imagem ${index + 1}`} size={100} />) : <p>Imagem indisponível.</p>}</div>;
}

export function AliExpressPreviewDetails({ product }: { product: AliExpressPreview }) {
  return <div className="admin-aliexpress-details">
    <p className="admin-action-feedback" role="status">Pré-visualização carregada. Nenhum produto foi importado ou alterado.</p>
    <h3>{product.name || 'Produto sem nome'}</h3>
    <p>ID AliExpress: {product.productId} · Moeda: {product.currency || 'Não informada'}</p>
    <Images images={product.images} name={product.name} />
    <h4>Atributos do produto</h4>
    {Object.keys(product.attributes).length ? <dl className="admin-aliexpress-attributes">{Object.entries(product.attributes).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl> : <p>Nenhum atributo informado.</p>}
    <h4>Variantes, preços e estoque</h4>
    {product.variants.length ? <ul className="admin-aliexpress-variants">{product.variants.map((variant, index) => <li key={`${variant.skuId}-${index}`}>
      <strong>{variant.skuCode || variant.skuId || `Variante ${index + 1}`}</strong>
      <p>{priceLabel(variant.price, variant.currency || product.currency)} · Moeda: {variant.currency || product.currency || 'Não informada'}</p>
      {variant.regularPrice != null && <p>Preço original: {priceLabel(variant.regularPrice, variant.currency || product.currency)}</p>}
      <p>Estoque disponível no fornecedor: {variant.availableStock ?? 'Não informado'}</p>
      <dl className="admin-aliexpress-attributes">{Object.entries(variant.attributes ?? {}).map(([key, value]) => <div key={key}><dt>{key}</dt><dd>{value}</dd></div>)}</dl>
      <Images images={variant.images ?? []} name={variant.skuCode || variant.skuId || product.name} />
    </li>)}</ul> : <p>Nenhuma variante, preço ou estoque informado.</p>}
    <h4>Avisos</h4>
    {product.warnings.length ? <ul>{product.warnings.map((warning, index) => <li key={index}>{warning}</li>)}</ul> : <p>Nenhum aviso retornado.</p>}
  </div>;
}

export function AliExpressProductPreview() {
  const router = useRouter();
  const { logout } = useAuth();
  const [input, setInput] = useState('');
  const [product, setProduct] = useState<AliExpressPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const busy = useRef(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy.current) return;
    setProduct(null); setError(null);
    if (!input.trim()) { setError('Informe o ID ou a URL do produto AliExpress.'); return; }
    busy.current = true; setLoading(true);
    try { setProduct(await previewAliExpressProduct(input)); }
    catch (cause) {
      setError(cause instanceof AliExpressPreviewError ? cause.message : 'Não foi possível carregar a pré-visualização. Tente novamente.');
      if (cause instanceof AliExpressPreviewError && cause.status === 401) { logout(); router.replace('/login'); }
    } finally { busy.current = false; setLoading(false); }
  }

  return <section className="admin-products-panel admin-aliexpress-panel" aria-labelledby="aliexpress-title">
    <div className="admin-aliexpress-heading"><h2 id="aliexpress-title">Buscar produto no AliExpress</h2><p>Consulte por ID ou URL. Pré-visualização para Brasil, em BRL e português.</p></div>
    <form className="admin-cj-search" onSubmit={submit} aria-busy={loading}>
      <label htmlFor="aliexpress-input">ID ou URL do produto AliExpress<input id="aliexpress-input" value={input} maxLength={2048} disabled={loading} onChange={(event) => { setInput(event.target.value); setProduct(null); setError(null); }} placeholder="Ex.: 1005001234567890 ou https://pt.aliexpress.com/item/..." aria-describedby="aliexpress-preview-note" /></label>
      <button type="submit" disabled={loading}>{loading ? 'Buscando…' : 'Buscar pré-visualização'}</button>
    </form>
    {loading && <p className="admin-feedback" role="status">Consultando produto no AliExpress…</p>}
    {error && <p className="admin-feedback error" role="alert">{error}</p>}
    {product && <AliExpressPreviewDetails product={product} />}
    <div className="admin-aliexpress-footer"><button type="button" disabled aria-describedby="aliexpress-preview-note">Importar produto</button><p id="aliexpress-preview-note">Importação desabilitada até a validação do fluxo. Esta consulta não importa, publica nem altera produtos.</p></div>
  </section>;
}
