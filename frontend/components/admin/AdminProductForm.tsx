'use client';

import { FormEvent, useRef, useState } from 'react';
import { apiMoneyToCents, centsToInput, moneyToCents, productMargin } from '@/lib/admin/product-finance';
import type { AdminProduct, AdminProductPayload, AdminProductType } from '@/lib/types/admin-product';

const brl = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const usd = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'USD' });
const supplierDate = new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' });

type FormValues = {
  name: string; slug: string; description: string; longDescription: string; price: string; oldPrice: string;
  costPrice: string; stock: string; active: boolean; category: string; sku: string; imageUrl: string; productType: AdminProductType;
};

function initialValues(product?: AdminProduct): FormValues {
  return {
    name: product?.name ?? '', slug: product?.slug ?? '', description: product?.description ?? '', longDescription: product?.longDescription ?? '',
    price: product ? centsToInput(apiMoneyToCents(product.price)) : '', oldPrice: product?.oldPrice == null ? '' : centsToInput(apiMoneyToCents(product.oldPrice)),
    costPrice: product ? centsToInput(apiMoneyToCents(product.costPrice)) : '', stock: String(product?.stock ?? 0), active: product?.active ?? true,
    category: product?.category ?? '', sku: product?.sku ?? '', imageUrl: product?.imageUrl ?? '', productType: product?.productType ?? 'SINGLE',
  };
}

function slugify(value: string) {
  return value.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase().replace(/[^a-z0-9]+/g, '-').replace(/^-|-$/g, '');
}

export function AdminProductForm({ product, submitting, apiError, apiFields = {}, onSubmit }: {
  product?: AdminProduct; submitting: boolean; apiError: string | null; apiFields?: Record<string, string>;
  onSubmit: (payload: AdminProductPayload) => Promise<void>;
}) {
  const [values, setValues] = useState<FormValues>(() => initialValues(product));
  const [errors, setErrors] = useState<Record<string, string>>({});
  const slugEdited = useRef(Boolean(product));

  function field<K extends keyof FormValues>(key: K, value: FormValues[K]) {
    setValues((current) => ({ ...current, [key]: value }));
    setErrors((current) => ({ ...current, [key]: '' }));
  }

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (submitting) return;
    const nextErrors: Record<string, string> = {};
    const priceCents = moneyToCents(values.price);
    const oldPriceCents = values.oldPrice.trim() ? moneyToCents(values.oldPrice) : null;
    const costCents = moneyToCents(values.costPrice);
    const stock = Number(values.stock);
    if (!values.name.trim()) nextErrors.name = 'Informe o nome.';
    if (!/^[a-z0-9]+(?:-[a-z0-9]+)*$/.test(values.slug)) nextErrors.slug = 'Use letras minúsculas, números e hífens.';
    if (priceCents === null || priceCents < 1) nextErrors.price = 'Informe um preço maior que zero.';
    if (values.oldPrice.trim() && (oldPriceCents === null || oldPriceCents < 1)) nextErrors.oldPrice = 'Informe um valor maior que zero.';
    if (costCents === null || costCents < 0) nextErrors.costPrice = 'Informe um custo igual ou maior que zero.';
    if (!Number.isSafeInteger(stock) || stock < 0) nextErrors.stock = 'Informe um estoque inteiro igual ou maior que zero.';
    if (!values.category.trim()) nextErrors.category = 'Informe a categoria.';
    if (!/^[A-Za-z0-9][A-Za-z0-9._-]*$/.test(values.sku)) nextErrors.sku = 'Use letras, números, ponto, sublinhado ou hífen.';
    if (Object.keys(nextErrors).length) { setErrors(nextErrors); return; }
    await onSubmit({
      name: values.name.trim(), slug: values.slug, description: values.description.trim() || null, longDescription: values.longDescription.trim() || null,
      price: priceCents! / 100, oldPrice: oldPriceCents === null ? null : oldPriceCents / 100, costPrice: costCents! / 100,
      stock, active: values.active, category: values.category.trim(), sku: values.sku.trim(), imageUrl: values.imageUrl.trim() || null, productType: values.productType,
    });
  }

  const visibleErrors = { ...apiFields, ...errors };
  const previewPrice = moneyToCents(values.price);
  const previewCost = moneyToCents(values.costPrice);
  const margin = previewPrice !== null && previewCost !== null ? productMargin(previewPrice / 100, previewCost / 100) : null;
  const dropshipping = product?.fulfillmentType === 'DROPSHIPPING';

  return <form className="admin-product-form" onSubmit={(event) => void submit(event)} noValidate>
    {apiError && <p className="admin-action-feedback error" role="alert">{apiError}</p>}
    <section className="admin-card"><div className="admin-form-section-heading"><div><p className="eyebrow">IDENTIFICAÇÃO</p><h2>Informações do produto</h2></div>{product ? <span className={`admin-product-status ${product.active ? 'active' : 'inactive'}`}>{product.active ? 'Ativo' : 'Inativo'}</span> : <label className="admin-switch"><input type="checkbox" checked={values.active} onChange={(event) => field('active', event.target.checked)} /><span>Produto ativo</span></label>}</div>
      <div className="admin-product-form-grid">
        <label className="wide">Nome<input maxLength={150} value={values.name} onChange={(event) => { const name = event.target.value; field('name', name); if (!slugEdited.current) field('slug', slugify(name)); }} aria-invalid={Boolean(visibleErrors.name)} />{visibleErrors.name && <small>{visibleErrors.name}</small>}</label>
        <label>SKU<input maxLength={100} value={values.sku} onChange={(event) => field('sku', event.target.value)} aria-invalid={Boolean(visibleErrors.sku)} />{visibleErrors.sku && <small>{visibleErrors.sku}</small>}</label>
        <label>Slug<input maxLength={180} value={values.slug} onChange={(event) => { slugEdited.current = true; field('slug', event.target.value); }} aria-invalid={Boolean(visibleErrors.slug)} />{visibleErrors.slug && <small>{visibleErrors.slug}</small>}</label>
        <label>Categoria<input maxLength={100} value={values.category} onChange={(event) => field('category', event.target.value)} aria-invalid={Boolean(visibleErrors.category)} />{visibleErrors.category && <small>{visibleErrors.category}</small>}</label>
        <label>Tipo<select value={values.productType} onChange={(event) => field('productType', event.target.value as AdminProductType)}><option value="SINGLE">Produto individual</option><option value="KIT">Kit</option></select></label>
        <label className="wide">Descrição<textarea maxLength={1000} rows={3} value={values.description} onChange={(event) => field('description', event.target.value)} />{visibleErrors.description && <small>{visibleErrors.description}</small>}</label>
        <label className="wide">Descrição detalhada<textarea maxLength={5000} rows={7} value={values.longDescription} onChange={(event) => field('longDescription', event.target.value)} />{visibleErrors.longDescription && <small>{visibleErrors.longDescription}</small>}</label>
      </div>
    </section>

    {product?.supplier && <section className="admin-card admin-supplier-card">
      <div className="admin-form-section-heading"><div><p className="eyebrow">INTEGRAÇÃO</p><h2>Fornecedor</h2></div></div>
      <dl className="admin-supplier-grid">
        <div><dt>Fornecedor</dt><dd>{product.supplier === 'CJ' ? 'CJ Dropshipping' : product.supplier}</dd></div>
        <div><dt>ID no fornecedor</dt><dd>{product.supplierProductId ?? 'Não informado'}</dd></div>
        <div><dt>Custo original</dt><dd>{product.supplierCostUsd == null ? 'Não registrado' : usd.format(product.supplierCostUsd)}</dd></div>
        <div><dt>Cotação utilizada</dt><dd>{product.supplierExchangeRate == null ? 'Não registrada' : `US$ 1 = ${brl.format(product.supplierExchangeRate)}`}</dd></div>
        <div><dt>Custo convertido</dt><dd>{brl.format(product.costPrice)}</dd></div>
        <div><dt>Atualizado em</dt><dd>{product.supplierCostUpdatedAt ? supplierDate.format(new Date(product.supplierCostUpdatedAt)) : 'Não registrado'}</dd></div>
        <div><dt>Modelo de estoque</dt><dd>{dropshipping ? 'Dropshipping' : 'Estoque local'}</dd></div>
      </dl>
      {product.supplierCostUsd == null && <p className="admin-supplier-legacy-note">Produto importado antes do registro da cotação. Os dados históricos não foram recalculados automaticamente.</p>}
    </section>}

    <section className="admin-card"><div className="admin-form-section-heading"><div><p className="eyebrow">COMERCIAL</p><h2>Valores e estoque</h2></div></div>
      <div className="admin-product-form-grid commercial">
        <label>Preço<input inputMode="decimal" placeholder="0,00" value={values.price} onChange={(event) => field('price', event.target.value)} aria-invalid={Boolean(visibleErrors.price)} />{visibleErrors.price && <small>{visibleErrors.price}</small>}</label>
        <label>Preço anterior <span>(opcional)</span><input inputMode="decimal" placeholder="0,00" value={values.oldPrice} onChange={(event) => field('oldPrice', event.target.value)} aria-invalid={Boolean(visibleErrors.oldPrice)} />{visibleErrors.oldPrice && <small>{visibleErrors.oldPrice}</small>}</label>
        <label>Preço de custo<input inputMode="decimal" placeholder="0,00" value={values.costPrice} onChange={(event) => field('costPrice', event.target.value)} aria-invalid={Boolean(visibleErrors.costPrice)} />{visibleErrors.costPrice && <small>{visibleErrors.costPrice}</small>}</label>
        {dropshipping ? <div className="admin-fulfillment-note"><span>Estoque local</span><strong>Não aplicável</strong><small>A disponibilidade não depende de entrada manual de estoque.</small></div> : <label>Estoque<input type="number" min="0" step="1" value={values.stock} onChange={(event) => field('stock', event.target.value)} aria-invalid={Boolean(visibleErrors.stock)} />{visibleErrors.stock && <small>{visibleErrors.stock}</small>}</label>}
      </div>
      {margin && <div className="admin-margin-preview"><span>Margem bruta estimada</span><strong>{new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' }).format(margin.marginCents / 100)}</strong><small>{margin.percentage.toLocaleString('pt-BR', { maximumFractionDigits: 1 })}% sobre o preço</small></div>}
    </section>

    <section className="admin-card"><div className="admin-form-section-heading"><div><p className="eyebrow">CATÁLOGO</p><h2>Imagem</h2></div></div><div className="admin-product-form-grid"><label className="wide">URL da imagem<input type="url" maxLength={500} placeholder="https://…" value={values.imageUrl} onChange={(event) => field('imageUrl', event.target.value)} />{visibleErrors.imageUrl && <small>{visibleErrors.imageUrl}</small>}</label></div></section>
    <div className="admin-form-actions"><button type="submit" disabled={submitting}>{submitting ? 'Salvando…' : product ? 'Salvar alterações' : 'Criar produto'}</button></div>
  </form>;
}
