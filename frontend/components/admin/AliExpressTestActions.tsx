'use client';

import { useEffect, useRef, useState } from 'react';
import { AdminOrderApiError, getAdminAliExpressTest, runAdminAliExpressTest, type AdminAliExpressTest } from '@/lib/api/admin/orders';

const confirmationPhrase = 'CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS';
const date = (value: string | null) => value ? new Date(value).toLocaleString('pt-BR') : '—';

export function AliExpressTestActions({ orderId }: { orderId: number }) {
  const [state, setState] = useState<AdminAliExpressTest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [uncertain, setUncertain] = useState(false);
  const [confirming, setConfirming] = useState(false);
  const [phrase, setPhrase] = useState('');
  const inFlight = useRef(false);
  const dialog = useRef<HTMLDialogElement>(null);
  useEffect(() => {
    let active = true;
    getAdminAliExpressTest(orderId).then(value => { if (active) setState(value); })
      .catch(() => { if (active) setError('Não foi possível consultar os testes administrativos.'); });
    return () => { active = false; };
  }, [orderId]);
  useEffect(() => { if (confirming) dialog.current?.showModal(); else dialog.current?.close(); }, [confirming]);

  async function run(action: 'simulate-payment' | 'simulate-supplier' | 'create-real') {
    if (!state?.testOrder || inFlight.current) return;
    if (action === 'create-real' && (!state.canCreateReal || !state.fixture || uncertain || !confirming || phrase !== confirmationPhrase)) return;
    inFlight.current = true; setBusy(true); setError(null);
    try {
      setState(await runAdminAliExpressTest(orderId, action, action === 'create-real' && state.fixture ? {
        phrase, idempotencyKey: state.fixture.id, fixtureDigest: state.fixture.digest,
      } : undefined));
    } catch (cause) {
      if (action === 'create-real') setUncertain(true);
      setError(action === 'create-real' ? 'Resultado não confirmado. Reenvio bloqueado. Consulte o registro e reconcilie manualmente no AliExpress.'
        : cause instanceof AdminOrderApiError ? cause.message : 'Não foi possível registrar a simulação.');
      try { setState(await getAdminAliExpressTest(orderId)); } catch { /* Keep uncertainty blocked. */ }
    } finally { inFlight.current = false; setBusy(false); setConfirming(false); setPhrase(''); }
  }
  if (!state) return error ? <p role="alert">{error}</p> : null;
  if (!state.testOrder) return null;
  return <section className="admin-card"><h2>Testes administrativos AliExpress</h2>
    <p>Pedido identificado como teste. Estes registros não confirmam pagamento financeiro e não autorizam compras de clientes.</p>
    {error && <p role="alert">{error}</p>}
    <p>Pagamento simulado: {state.simulatedPaymentAt ? `SIMULADO em ${date(state.simulatedPaymentAt)} por ${state.simulatedPaymentBy}` : 'Não executado'}</p>
    <button type="button" disabled={busy || !!state.simulatedPaymentAt} onClick={() => void run('simulate-payment')}>Simular pagamento</button>
    <p>AliExpress simulado: {state.simulatedSupplierId ? `${state.simulatedSupplierId} — CRIADO SIMULADO, NÃO PAGO — ${date(state.simulatedSupplierAt)} por ${state.simulatedSupplierBy}` : 'Não executado'}</p>
    <button type="button" disabled={busy || !state.simulatedPaymentAt || !!state.simulatedSupplierAt} onClick={() => void run('simulate-supplier')}>Executar AliExpress simulado</button>
    <hr /><h3>Operação real de teste separada</h3>
    <p>Usa exclusivamente a configuração de teste autorizada. Pagamento ao fornecedor exclusivamente manual.</p>
    {state.fixture && <dl>
      <dt>Autorização dos dados próprios</dt><dd>{state.fixture.authorizationReference}</dd>
      <dt>Identificador idempotente</dt><dd>{state.fixture.id}</dd>
      <dt>Produto / SKU / quantidade</dt><dd>{state.fixture.productId} / {state.fixture.skuId} / {state.fixture.quantity}</dd>
      <dt>Entrega / cidade de teste</dt><dd>{state.fixture.deliveryService} / {state.fixture.city}</dd>
      <dt>Produto / frete / impostos / total</dt><dd>{state.fixture.currency} {state.fixture.productCost} / {state.fixture.shippingCost} / {state.fixture.taxCost} / {state.fixture.totalCost}</dd>
    </dl>}
    {state.realBlockers.length > 0 && <ul>{state.realBlockers.map(reason => <li key={reason}>{reason}</li>)}</ul>}
    <button type="button" disabled={busy || uncertain || !state.canCreateReal} onClick={() => setConfirming(true)}>Criar pedido real de teste no AliExpress</button>
    {state.realStatus && <div role="status"><p>Estado real de teste: {state.realStatus}. Identificador: {state.idempotencyKey}</p>
      <p>Enviado em {date(state.submittedAt)} por {state.submittedBy}. Criação registrada em {date(state.createdAt)}.</p>
      <p>Digest da configuração utilizada: {state.submittedFixtureDigest}</p>
      <p>IDs AliExpress: {state.supplierOrderIds.join(', ') || 'Sem IDs confirmados'}</p>
      <p>Confira manualmente em Minha conta → Meus pedidos → Aguardando pagamento. A criação não comprova o status atual no fornecedor.</p>
    </div>}
    <dialog ref={dialog} onCancel={() => { if (!busy) setConfirming(false); }} aria-labelledby="real-test-title">
      <h3 id="real-test-title">Confirmar pedido real não pago</h3>
      <p>Esta ação cria um pedido REAL no AliExpress com a configuração de teste exibida. Não efetua pagamento. Confira a autorização do endereço e os custos antes de continuar.</p>
      <label>Digite {confirmationPhrase}<input value={phrase} onChange={event => setPhrase(event.target.value)} autoComplete="off" /></label>
      <button type="button" disabled={busy} onClick={() => setConfirming(false)}>Cancelar</button>
      <button type="button" disabled={busy || phrase !== confirmationPhrase || uncertain || !state.canCreateReal} onClick={() => void run('create-real')}>Confirmar criação real não paga</button>
    </dialog>
  </section>;
}
