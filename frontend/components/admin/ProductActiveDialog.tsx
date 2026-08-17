'use client';

import { useEffect, useRef } from 'react';

export function ProductActiveDialog({ productName, busy, onCancel, onConfirm }: {
  productName: string; busy: boolean; onCancel: () => void; onConfirm: () => void;
}) {
  const dialogRef = useRef<HTMLDivElement>(null);
  useEffect(() => {
    dialogRef.current?.focus();
    function escape(event: KeyboardEvent) { if (event.key === 'Escape' && !busy) onCancel(); }
    document.addEventListener('keydown', escape);
    return () => document.removeEventListener('keydown', escape);
  }, [busy, onCancel]);

  return <div className="admin-product-dialog-backdrop" role="presentation" onMouseDown={(event) => { if (event.target === event.currentTarget && !busy) onCancel(); }}>
    <div ref={dialogRef} className="admin-product-dialog" role="dialog" aria-modal="true" aria-labelledby="deactivate-product-title" aria-describedby="deactivate-product-description" tabIndex={-1}>
      <span className="logout-dialog-mark" aria-hidden="true">G</span>
      <h2 id="deactivate-product-title">Desativar produto?</h2>
      <p id="deactivate-product-description"><strong>{productName}</strong> deixará de aparecer no catálogo público. O registro e o histórico serão preservados.</p>
      <div className="logout-dialog-actions"><button type="button" disabled={busy} onClick={onCancel}>Cancelar</button><button className="confirm" type="button" disabled={busy} onClick={onConfirm}>{busy ? 'Desativando…' : 'Desativar'}</button></div>
    </div>
  </div>;
}
