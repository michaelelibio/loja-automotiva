'use client';

import { FormEvent, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AdminProductImage } from '@/components/admin/AdminProductImage';
import { AliExpressImportError, AliExpressPreviewError, importAliExpressProduct, previewAliExpressProduct } from '@/lib/api/admin/aliexpress';
import { ALIEXPRESS_CATEGORIES, prepareAliExpressImport } from '@/lib/admin/aliexpress-import';
import type { AliExpressImportResult, AliExpressPreview, AliExpressVariant } from '@/lib/types/admin-aliexpress';

function priceLabel(price: number | null, currency: string) {
  if (price == null || !Number.isFinite(price)) return 'Preço não informado';
  try { return new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(price); }
  catch { return `${price.toLocaleString('pt-BR')} ${currency}`; }
}

function Images({ images, name }: { images: string[]; name: string }) {
  const [selected, setSelected] = useState(0);
  const safeImages = [...new Set(images)].filter((src) => {
    try { return new URL(src).protocol === 'https:'; } catch { return false; }
  });
  if (!safeImages.length) return <p>Imagem indisponível.</p>;
  return <div className="admin-aliexpress-gallery">
    <AdminProductImage src={safeImages[selected] ?? safeImages[0]} alt={`${name} — imagem principal`} size={300} />
    {safeImages.length > 1 && <div className="admin-aliexpress-images" aria-label="Galeria do produto">{safeImages.map((src, index) => <button key={src} type="button" aria-label={`Ver imagem ${index + 1}`} aria-pressed={selected === index} onClick={() => setSelected(index)}><AdminProductImage src={src} alt="" size={56} /></button>)}</div>}
  </div>;
}

function commercialOptions(variant: AliExpressVariant) {
  const attributes = variant.attributes ?? {};
  return Object.entries(attributes).flatMap(([key, value]) => {
    if (key.endsWith(':name') || key.endsWith(':valueId') || key.startsWith('source:')) return [];
    const name = attributes[`${key}:name`] || (/^\d+$/.test(key) ? '' : key);
    return [name ? `${name}: ${value}` : value];
  }).join(' · ') || 'Opções não informadas';
}

function shoppingWarnings(warnings: string[]) {
  return [...new Set(warnings.flatMap((warning) => {
    if (/^Categoria comercial Nacionais|^Peso e dimensoes preservados/.test(warning)) return [];
    if (/^Titulo resumido/.test(warning)) return ['O nome será resumido no cadastro.'];
    if (/^Descricao resumida/.test(warning)) return ['A descrição será resumida no cadastro.'];
    if (/^Atributos do SKU .* inconsistentes/.test(warning)) return ['Confira as opções das variantes antes de cadastrar.'];
    if (/^Imagem invalida ou fora dos CDNs/.test(warning)) return ['Algumas imagens não estão disponíveis.'];
    return [warning];
  }))];
}

type ImportEditor = {
  name: string; category: string; selected: string[]; prices: Record<string, string>; disabled: boolean;
  setName: (value: string) => void; setCategory: (value: string) => void;
  toggle: (id: string) => void; setPrice: (id: string, value: string) => void;
};

export function AliExpressPreviewDetails({ product, editor }: { product: AliExpressPreview; editor?: ImportEditor }) {
  const warnings = shoppingWarnings(product.warnings);
  return <div className="admin-aliexpress-details">
    <p className="admin-action-feedback" role="status">Pré-visualização carregada.</p>
    <div className="admin-aliexpress-overview">
      <Images images={[...product.images, ...product.variants.flatMap((variant) => variant.images ?? [])]} name={product.name} />
      <div className="admin-aliexpress-copy">
        <label htmlFor="aliexpress-product-name">Nome do produto{editor ? <input id="aliexpress-product-name" value={editor.name} maxLength={150} disabled={editor.disabled} onChange={(event) => editor.setName(event.target.value)} /> : <input id="aliexpress-product-name" defaultValue={product.name} maxLength={150} aria-describedby="aliexpress-name-note" />}</label>
        {editor ? <label className="admin-aliexpress-category">Categoria da loja<select value={editor.category} disabled={editor.disabled} onChange={(event) => editor.setCategory(event.target.value)}><option value="">Selecione uma categoria</option>{ALIEXPRESS_CATEGORIES.map((category) => <option key={category} value={category}>{category}</option>)}</select></label> : <><small id="aliexpress-name-note">Edição somente nesta prévia, sem salvar no catálogo.</small><dl className="admin-aliexpress-summary"><div><dt>Categoria da loja</dt><dd>{product.commercialCategory || 'Não informada'}</dd></div><div><dt>Preço de venda</dt><dd>Não definido</dd></div></dl></>}
        <h3>Descrição</h3><p className="admin-aliexpress-description" tabIndex={0} aria-label="Descrição do produto">{product.description || 'Descrição não informada.'}</p>
        <p>{product.domesticShippingConfirmed ? 'Envio nacional confirmado.' : 'Frete e prazo não confirmados.'}</p>
      </div>
    </div>
    <h3>Variantes</h3>
    {editor && <p className="admin-aliexpress-price-note">Informe o preço em BRL de cada variante selecionada. Todas devem ter o mesmo preço de venda, conforme o catálogo atual.</p>}
    {product.variants.length ? <div className="admin-aliexpress-table-wrap"><table className="admin-aliexpress-table"><caption className="sr-only">Opções, SKU, custo e estoque no fornecedor</caption><thead><tr><th scope="col">Opções</th><th scope="col">SKU</th><th scope="col">Custo do fornecedor</th><th scope="col">Estoque disponível</th>{editor && <th scope="col">Venda (BRL)</th>}</tr></thead><tbody>{product.variants.map((variant, index) => <tr key={`${variant.skuId}-${index}`}>
      <td data-label="Opções">{editor ? <label className="admin-aliexpress-selection"><input type="checkbox" checked={editor.selected.includes(variant.skuId)} disabled={editor.disabled} onChange={() => editor.toggle(variant.skuId)} aria-label={`Importar variante ${variant.skuCode || variant.skuId}`} />{commercialOptions(variant)}</label> : commercialOptions(variant)}</td>
      <td data-label="SKU">{variant.skuCode || variant.skuId || 'Não informado'}</td>
      <td data-label="Custo do fornecedor">{(variant.currency || product.currency) === 'BRL' ? priceLabel(variant.price, 'BRL') : <>Custo em BRL não informado<small>{priceLabel(variant.price, variant.currency || product.currency)}</small></>}</td>
      <td data-label="Estoque disponível">{variant.availableStock ?? 'Não informado'}</td>
      {editor && <td data-label="Venda (BRL)"><input className="admin-aliexpress-price" aria-label={`Preço de venda da variante ${variant.skuCode || variant.skuId}`} inputMode="decimal" placeholder="0,00" value={editor.prices[variant.skuId] ?? ''} disabled={editor.disabled || !editor.selected.includes(variant.skuId)} onChange={(event) => editor.setPrice(variant.skuId, event.target.value)} /></td>}
    </tr>)}</tbody></table></div> : <p>Nenhuma variante informada.</p>}
    {warnings.length > 0 && <aside className="admin-aliexpress-warnings" aria-label="Avisos importantes"><h3>Atenção antes de cadastrar</h3><ul>{warnings.map((warning) => <li key={warning}>{warning}</li>)}</ul></aside>}
  </div>;
}

export function AliExpressImportForm({ product, onBusyChange, onImported }: {
  product: AliExpressPreview; onBusyChange: (busy: boolean) => void; onImported?: () => void;
}) {
  const router = useRouter();
  const { logout } = useAuth();
  const [name, setName] = useState(product.name.slice(0, 150));
  const [category, setCategory] = useState('');
  const [selected, setSelected] = useState(product.variants.map((variant) => variant.skuId));
  const [prices, setPrices] = useState<Record<string, string>>({});
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<AliExpressImportResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [needsPreview, setNeedsPreview] = useState(false);
  const busy = useRef(false);
  const prepared = prepareAliExpressImport(product, name, category, selected, prices);
  const disabled = loading || result !== null || needsPreview;

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy.current || disabled) return;
    if (!prepared.payload) { setError(prepared.error); return; }
    busy.current = true; setLoading(true); setError(null); onBusyChange(true);
    try {
      const imported = await importAliExpressProduct(prepared.payload);
      setResult(imported); onImported?.();
    } catch (cause) {
      setError(cause instanceof AliExpressImportError ? cause.message : 'Não foi possível confirmar a importação. Busque o produto novamente.');
      if (!(cause instanceof AliExpressImportError) || cause.status === 0 || cause.status >= 500 || cause.status === 409) setNeedsPreview(true);
      if (cause instanceof AliExpressImportError && cause.status === 401) { logout(); router.replace('/login'); }
    } finally { busy.current = false; setLoading(false); onBusyChange(false); }
  }

  return <form onSubmit={submit} aria-label="Preparar importação AliExpress" aria-busy={loading}>
    <AliExpressPreviewDetails product={product} editor={{ name, category, selected, prices, disabled, setName, setCategory,
      toggle: (id) => setSelected((current) => current.includes(id) ? current.filter((item) => item !== id) : [...current, id]),
      setPrice: (id, value) => setPrices((current) => ({ ...current, [id]: value })),
    }} />
    <div className="admin-aliexpress-footer">
      {result ? <p className="admin-action-feedback" role="status">Produto importado como rascunho/inativo. <Link href={`/admin/produtos/${result.productId}`}>Editar produto cadastrado</Link></p> : <>
        <button type="submit" disabled={disabled || !prepared.payload}>{loading ? 'Importando…' : 'Importar produto'}</button>
        <p>{loading ? 'Validando os dados do fornecedor e criando o rascunho…' : needsPreview ? 'Busque o produto novamente para conferir o cadastro e atualizar a prévia.' : prepared.error || 'Pronto para importar como rascunho.'}</p>
      </>}
      {error && <p className="admin-feedback error" role="alert">{error}</p>}
      <p>A importação cria um produto inativo. A publicação exige ativação manual por um administrador.</p>
    </div>
  </form>;
}

export function AliExpressProductPreview({ onImported }: { onImported?: () => void } = {}) {
  const router = useRouter();
  const { logout } = useAuth();
  const [input, setInput] = useState('');
  const [product, setProduct] = useState<AliExpressPreview | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [importing, setImporting] = useState(false);
  const busy = useRef(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (busy.current || importing) return;
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
      <label htmlFor="aliexpress-input">ID ou URL do produto AliExpress<input id="aliexpress-input" value={input} maxLength={2048} disabled={loading || importing} onChange={(event) => { setInput(event.target.value); setProduct(null); setError(null); }} placeholder="Ex.: 1005001234567890 ou https://pt.aliexpress.com/item/..." /></label>
      <button type="submit" disabled={loading || importing}>{loading ? 'Buscando…' : 'Buscar pré-visualização'}</button>
    </form>
    {loading && <p className="admin-feedback" role="status">Consultando produto no AliExpress…</p>}
    {error && <p className="admin-feedback error" role="alert">{error}</p>}
    {product ? <AliExpressImportForm product={product} onBusyChange={setImporting} onImported={onImported} /> : <div className="admin-aliexpress-footer"><button type="button" disabled>Importar produto</button><p>Busque um produto para preparar a importação como rascunho.</p></div>}
  </section>;
}
