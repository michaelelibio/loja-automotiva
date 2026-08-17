'use client';

import { FormEvent, useEffect, useRef, useState } from 'react';
import { AdminProductPicker } from '@/components/admin/AdminProductPicker';
import { AdminStockApiError, createAdminStockMovement } from '@/lib/api/admin/stock';
import type { AdminProduct } from '@/lib/types/admin-product';
import type { AdminManualStockMovementType, AdminStockMovement } from '@/lib/types/admin-stock';

const manualLabels: Record<AdminManualStockMovementType, string> = {
  PURCHASE_ENTRY: 'Entrada de compra', MANUAL_ADJUSTMENT_IN: 'Ajuste de entrada', MANUAL_ADJUSTMENT_OUT: 'Ajuste de saída',
};

export function StockMovementDialog({ onCancel, onCreated, onAuthorizationError }: {
  onCancel: () => void;
  onCreated: (movement: AdminStockMovement) => void;
  onAuthorizationError: (status: 401 | 403) => void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);
  const [product, setProduct] = useState<AdminProduct | null>(null);
  const [type, setType] = useState<AdminManualStockMovementType>('PURCHASE_ENTRY');
  const [quantity, setQuantity] = useState('');
  const [reason, setReason] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    dialogRef.current?.focus();
    function escape(event: KeyboardEvent) { if (event.key === 'Escape' && !submitting) onCancel(); }
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [onCancel, submitting]);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (submitting) return;
    const parsedQuantity = Number(quantity);
    const nextErrors: Record<string, string> = {};
    if (!product) nextErrors.productId = 'Selecione um produto.';
    if (!Number.isSafeInteger(parsedQuantity) || parsedQuantity <= 0) nextErrors.quantity = 'Informe uma quantidade inteira maior que zero.';
    if (!reason.trim()) nextErrors.reason = 'Informe o motivo da movimentação.';
    if (Object.keys(nextErrors).length) { setErrors(nextErrors); return; }
    setSubmitting(true); setError(null); setErrors({});
    try {
      const movement = await createAdminStockMovement({ productId: product!.id, type, quantity: parsedQuantity, reason: reason.trim() });
      onCreated(movement);
    } catch (cause) {
      if (cause instanceof AdminStockApiError && (cause.status === 401 || cause.status === 403)) { onAuthorizationError(cause.status); return; }
      if (cause instanceof AdminStockApiError && cause.status === 409) setError(cause.message.toLowerCase().includes('dropshipping') ? 'Movimentações de estoque local não se aplicam a produtos dropshipping.' : 'Estoque insuficiente para realizar esta saída. Confira o saldo atual.');
      else if (cause instanceof AdminStockApiError && cause.status === 404) setError('O produto selecionado não foi encontrado.');
      else if (cause instanceof AdminStockApiError && cause.status === 400) { setError('Revise os campos destacados.'); setErrors(cause.fields); }
      else setError('Não foi possível registrar a movimentação. Tente novamente.');
    } finally { setSubmitting(false); }
  }

  return <div className="admin-product-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !submitting) onCancel(); }}>
    <div ref={dialogRef} className="admin-product-dialog admin-stock-dialog" role="dialog" aria-modal="true" aria-labelledby="stock-dialog-title" aria-describedby="stock-dialog-description" tabIndex={-1}>
      <div className="admin-stock-dialog-heading"><div><p className="eyebrow">ESTOQUE</p><h2 id="stock-dialog-title">Nova movimentação</h2></div><button type="button" aria-label="Fechar" disabled={submitting} onClick={onCancel}>×</button></div>
      <p id="stock-dialog-description">Registre uma entrada ou ajuste manual. O saldo definitivo será calculado pelo backend.</p>
      {error && <p className="admin-action-feedback error" role="alert">{error}</p>}
      <form className="admin-stock-form" onSubmit={(event) => void submit(event)} noValidate>
        <label>Produto<AdminProductPicker id="stock-product" value={product} onChange={(value) => { setProduct(value); setErrors((current) => ({ ...current, productId: '' })); }} onAuthorizationError={onAuthorizationError} localStockOnly />{errors.productId && <small>{errors.productId}</small>}</label>
        <label>Tipo<select value={type} onChange={(event) => setType(event.target.value as AdminManualStockMovementType)}>{Object.entries(manualLabels).map(([value, label]) => <option value={value} key={value}>{label}</option>)}</select></label>
        <label>Quantidade<input type="number" min="1" step="1" value={quantity} onChange={(event) => { setQuantity(event.target.value); setErrors((current) => ({ ...current, quantity: '' })); }} />{errors.quantity && <small>{errors.quantity}</small>}</label>
        <label>Motivo<textarea maxLength={500} rows={4} value={reason} onChange={(event) => { setReason(event.target.value); setErrors((current) => ({ ...current, reason: '' })); }} placeholder="Ex.: Reposição do fornecedor" />{errors.reason && <small>{errors.reason}</small>}</label>
        <div className="logout-dialog-actions"><button type="button" disabled={submitting} onClick={onCancel}>Cancelar</button><button className="confirm" type="submit" disabled={submitting}>{submitting ? 'Registrando…' : 'Registrar movimentação'}</button></div>
      </form>
    </div>
  </div>;
}
