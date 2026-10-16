'use client';

import { useEffect, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AdminOrderApiError, getAdminAliExpressOrder, sendAdminAliExpressOrder } from '@/lib/api/admin/orders';
import type { AdminAliExpressOrder } from '@/lib/types/admin-order';

export function AliExpressOrderState({ state }: { state: AdminAliExpressOrder }) {
  if (state.status === 'CREATED') return <div role="status"><strong>Enviado ao fornecedor</strong><small>Aguardando pagamento manual ao fornecedor</small></div>;
  if (state.status === 'VERIFICATION_REQUIRED' || state.status === 'PROCESSING') return <p role="status">Verificação necessária. Reenvio bloqueado até reconciliação.</p>;
  if (state.status === 'FAILED') return <p role="alert">{state.lastError || 'O AliExpress recusou a criação do pedido.'}</p>;
  return null;
}

export function AliExpressOrderAction({ orderId, onUpdated }: { orderId: number; onUpdated: () => void }) {
  const router = useRouter(); const { logout } = useAuth();
  const [state, setState] = useState<AdminAliExpressOrder | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [confirming, setConfirming] = useState(false);
  const [sending, setSending] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const busy = useRef(false); const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    let active = true;
    getAdminAliExpressOrder(orderId).then((value) => { if (active) setState(value); }).catch((cause) => {
      if (!active) return;
      setError('Não foi possível verificar o envio ao AliExpress. Atualize a listagem.');
      if (cause instanceof AdminOrderApiError && cause.status === 401) { logout(); router.replace('/login'); }
    });
    return () => { active = false; };
  }, [orderId, logout, router]);
  useEffect(() => { if (confirming) dialog.current?.showModal(); else dialog.current?.close(); }, [confirming]);

  async function send() {
    if (busy.current || !state?.canSend || !state.paymentConfirmed || uncertain || !confirming) return;
    busy.current = true; setSending(true); setError(null);
    try { setState(await sendAdminAliExpressOrder(orderId)); onUpdated(); }
    catch (cause) {
      // A browser failure is not proof of rejection. Never enable a second creation call locally.
      setUncertain(true); setError('Resultado não confirmado. Atualize a listagem. Não reenvie antes de verificar o estado do pedido.');
      if (cause instanceof AdminOrderApiError && cause.status === 401) { logout(); router.replace('/login'); }
      try { setState(await getAdminAliExpressOrder(orderId)); onUpdated(); } catch { /* Keep the local uncertainty block. */ }
    } finally { busy.current = false; setSending(false); setConfirming(false); }
  }
  if (error && !state) return <small role="alert">{error}</small>;
  if (!state?.applicable || !state.paymentConfirmed) return null;
  const finished = state.status === 'CREATED' || state.status === 'VERIFICATION_REQUIRED' || state.status === 'PROCESSING';
  return <div className="admin-aliexpress-order-action">
    <AliExpressOrderState state={state} />
    {!finished && <><button type="button" disabled={!state.canSend || sending || uncertain} onClick={() => setConfirming(true)}>Enviar ao AliExpress</button>{state.blockReason && <small>{state.blockReason}</small>}</>}
    {error && <small role="alert">{error}</small>}
    <dialog ref={dialog} className="admin-aliexpress-order-dialog" aria-labelledby={`ali-order-title-${orderId}`} onCancel={(event) => { if (busy.current) event.preventDefault(); else setConfirming(false); }}>
      <h2 id={`ali-order-title-${orderId}`}>Enviar pedido #{orderId} ao AliExpress?</h2>
      <p>Será criada uma ordem no fornecedor. O pagamento ao AliExpress será manual. Isso não marca o pedido como despachado ou entregue.</p>
      <div><button type="button" disabled={sending} onClick={() => setConfirming(false)}>Cancelar</button><button type="button" disabled={sending} onClick={() => void send()}>{sending ? 'Enviando…' : 'Confirmar envio'}</button></div>
    </dialog>
  </div>;
}
