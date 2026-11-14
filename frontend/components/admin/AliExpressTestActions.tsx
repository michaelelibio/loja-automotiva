'use client';
import { useEffect, useRef, useState } from 'react';
import { AdminOrderApiError, getAdminAliExpressTest, getAdminAliExpressTestPayload, preflightAdminAliExpressTest, runAdminAliExpressTest, type AdminAliExpressTest, type AdminAliExpressReview } from '@/lib/api/admin/orders';
const confirmationPhrase = 'CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS';
const date = (value: string | null) => value ? new Date(value).toLocaleString('pt-BR') : '—';
const money = (value: number | null, currency: string) => value === null || !Number.isFinite(value) ? 'Desconhecido' : new Intl.NumberFormat('pt-BR', { style: 'currency', currency }).format(value);
export function AliExpressRealReview({ review }: { review: AdminAliExpressReview }) {
  const costs=review.costs;
  const address=review.payload.logistics_address as Record<string,string> | undefined;
  const birthday=(review.payload.ds_extend_request as { trade_extra_param?: { birthday?: string } } | undefined)?.trade_extra_param?.birthday;
  return <div>
    <p>Conta compradora: <strong>{review.buyerAccount || 'Conta vinculada ao OAuth existente'}</strong></p>
    {review.buyerState === 'UNKNOWN' && <p>A identificação da conta não foi retornada pelo OAuth armazenado. A compra será criada na conta vinculada a esse OAuth.</p>}
    <p>Produto {costs.productId} / SKU {costs.skuId} / Quantidade {costs.quantity}</p>
    <p>Modalidade de entrega: <strong>{costs.deliveryService || 'Desconhecida — será decidida ou rejeitada pelo AliExpress'}</strong></p>
    <address>{address?.full_name}<br />{address?.address}<br />{address?.address2}<br />{address?.city} — {address?.province}<br />CEP {address?.zip}, {address?.country}</address>
    <p>CPF: {address?.cpf} / Telefone: {address?.phone_country} {address?.mobile_no} / Nascimento: {birthday?.replace(/^(..)(..)(....)$/, '$1/$2/$3')}</p>
    <dl>
      <dt>Produto na inGarage (quantidade total)</dt><dd>{money(review.inGarageProductTotal,costs.currency)}</dd>
      <dt>Produto no fornecedor (quantidade total)</dt><dd>{money(costs.productCost,costs.currency)}{review.productCostSource === 'IMPORTED_VARIANT' ? ' — valor da importação, pode estar desatualizado' : ''}</dd>
      <dt>Frete do fornecedor</dt><dd>{money(costs.shippingCost,costs.currency)}</dd>
      <dt>Impostos</dt><dd>{money(costs.taxCost,costs.currency)}</dd>
      <dt>Total do fornecedor</dt><dd><strong>{money(costs.totalCost,costs.currency)}</strong></dd>
    </dl>
    <p>O custo pode superar o valor da inGarage. A criação não limita o preço nem efetua pagamento. Confira o total definitivo e possíveis encargos no AliExpress antes de pagar manualmente.</p>
    {review.warnings?.length > 0 && <ul>{review.warnings.map(value=><li key={value}>{value}</li>)}</ul>}
    <p>Conferido em {date(review.checkedAt)}. Confirmação válida até {date(review.validUntil)}.</p>
  </div>;
}
export function AliExpressTestActions({ orderId }: { orderId:number }) {
  const [state,setState]=useState<AdminAliExpressTest | null>(null);
  const [error,setError]=useState<string | null>(null);
  const [busy,setBusy]=useState(false);
  const [uncertain,setUncertain]=useState(false);
  const [confirming,setConfirming]=useState(false);
  const [phrase,setPhrase]=useState('');
  const [review,setReview]=useState<AdminAliExpressReview | null>(null);
  const [acceptCosts,setAcceptCosts]=useState(false);
  const inFlight=useRef(false);
  const dialog=useRef<HTMLDialogElement>(null);
  useEffect(()=>{let active=true;getAdminAliExpressTest(orderId).then(value=>{if(active)setState(value);}).catch(()=>{if(active)setError('Não foi possível consultar a operação administrativa.');});return()=>{active=false;};},[orderId]);
  useEffect(()=>{if(confirming)dialog.current?.showModal();else dialog.current?.close();},[confirming]);
  useEffect(()=>{if(!confirming || !review?.validUntil)return;const timer=setTimeout(()=>{setConfirming(false);setReview(null);setError('Revisão expirada. Confira novamente antes de criar.');},Math.max(0,new Date(review.validUntil).getTime()-Date.now()));return()=>clearTimeout(timer);},[confirming,review]);
  async function reviewPayload(create=false){
    if(inFlight.current || orderId!==19 || (create && (!state?.canCreateReal || uncertain)))return;
    inFlight.current=true;setBusy(true);setError(null);setReview(null);setPhrase('');setAcceptCosts(false);
    try{const value=create?await preflightAdminAliExpressTest(orderId):await getAdminAliExpressTestPayload(orderId);
      if(value.orderId!==orderId || !value.costs || !value.digest || (create && (!value.preflightId || !value.validUntil || new Date(value.validUntil).getTime()<=Date.now())))throw new Error();
      setReview(value);if(create)setConfirming(true);
    }catch(cause){setError(cause instanceof AdminOrderApiError?cause.message:'Não foi possível preparar a confirmação. Nenhum pedido foi criado.');}
    finally{inFlight.current=false;setBusy(false);}
  }
  async function run(action:'simulate-payment'|'simulate-supplier'|'create-real'|'sync'){
    if(!state?.testOrder || inFlight.current)return;
    if(action==='create-real' && (orderId!==19 || !state.canCreateReal || !review?.preflightId || review.orderId!==orderId || !review.validUntil || new Date(review.validUntil).getTime()<=Date.now() || !acceptCosts || uncertain || !confirming || phrase!==confirmationPhrase))return;
    inFlight.current=true;setBusy(true);setError(null);
    try{setState(await runAdminAliExpressTest(orderId,action,action==='create-real' && review?.preflightId?{phrase,idempotencyKey:review.costs.id,fixtureDigest:review.digest,preflightId:review.preflightId,acceptKnownAndUnknownCosts:true}:undefined));}
    catch(cause){if(action==='create-real')setUncertain(true);setError(action==='create-real'?'Resultado não confirmado. Reenvio bloqueado. Consulte o registro e reconcilie no AliExpress.':cause instanceof AdminOrderApiError?cause.message:'Não foi possível concluir a ação.');try{setState(await getAdminAliExpressTest(orderId));}catch{}}
    finally{inFlight.current=false;setBusy(false);setConfirming(false);setPhrase('');setAcceptCosts(false);}
  }
  if(!state)return error?<p role="alert">{error}</p>:null;
  if(!state.testOrder && orderId!==19)return null;
  return <section className="admin-card"><h2>Operação administrativa AliExpress</h2>
    {error && <p role="alert">{error}</p>}
    {orderId===19 && <>
      <p>Pedido próprio #19. Não depende de Mercado Pago. O fornecedor será pago somente por você, manualmente.</p>
      {state.realBlockers.length>0 && <ul>{state.realBlockers.map(value=><li key={value}>{value}</li>)}</ul>}
      <button type="button" disabled={busy || !state.fixture} onClick={()=>void reviewPayload()}>Revisar dados autorizados</button>
      {review && !confirming && <details open><summary>Dados do pedido #19</summary><AliExpressRealReview review={review}/></details>}
      <button type="button" disabled={busy || uncertain || !state.canCreateReal || !!state.realStatus} onClick={()=>void reviewPayload(true)}>Criar pedido real no AliExpress</button>
      {state.realStatus && <div role="status">
        <p>Estado da tentativa: {state.realStatus}. Referência: {state.idempotencyKey}</p>
        <p>Enviado em {date(state.submittedAt)} por {state.submittedBy}.</p>
        {state.providerError && <div role="alert"><p>Resposta oficial AliExpress: <strong>{state.providerError.code}</strong>{state.providerError.subCode?` / ${state.providerError.subCode}`:''}{state.providerError.httpStatus?` (HTTP ${state.providerError.httpStatus})`:''}</p><p>{state.providerError.message}</p></div>}
        <p>IDs reais AliExpress: {state.supplierOrderIds.join(', ') || 'Sem IDs confirmados. Reconcilie em Meus pedidos; não reenvie.'}</p>
        <p>Última consulta real: {date(state.supplierSync?.checkedAt??null)}. {state.supplierSync?.inProgress?'Consulta em andamento; resultados anteriores podem estar desatualizados.':''}</p>
        <ul>{state.supplierOrderIds.map(id=>{const part=state.supplierSync?.parts.find(p=>p.orderId===id);return <li key={id}>AliExpress #{id}: {part?.verified && part.state==='AWAITING_MANUAL_PAYMENT'?'Aguardando pagamento manual confirmado por consulta':part?.verified?`Estado do fornecedor: ${part.state}`:'Pagamento ao fornecedor desconhecido — sem confirmação atual'} — {date(part?.checkedAt??null)}</li>;})}</ul>
        <button type="button" disabled={busy || !state.supplierOrderIds.length} onClick={()=>void run('sync')}>Consultar pedido real de teste</button>
        <p>Na conta vinculada ao OAuth, abra Meus pedidos → Aguardando pagamento e confira cada ID, endereço e total antes de pagar.</p>
      </div>}
      <dialog style={{width:'min(44rem, calc(100vw - 2rem))',maxHeight:'85vh',overflowY:'auto',padding:'1.5rem'}} ref={dialog} onCancel={event=>{if(busy)event.preventDefault();else setConfirming(false);}} aria-labelledby="real-test-title">
        <h3 id="real-test-title">Confirmar pedido real no AliExpress</h3><p>Esta confirmação envia uma requisição real de criação. Não haverá pagamento automático.</p>
        {review && <AliExpressRealReview review={review}/>}
        <label><input type="checkbox" checked={acceptCosts} onChange={event=>setAcceptCosts(event.target.checked)}/>Aceito criar este pedido com os custos conhecidos, com frete, impostos ou total ainda desconhecidos e com possível custo superior ao da inGarage. Pagarei somente após conferir no AliExpress.</label>
        <label>Digite {confirmationPhrase}<input type="text" value={phrase} onChange={event=>setPhrase(event.target.value)} autoComplete="off"/></label>
        <button type="button" disabled={busy} onClick={()=>setConfirming(false)}>Cancelar</button>
        <button type="button" disabled={busy || !acceptCosts || phrase!==confirmationPhrase || uncertain || !state.canCreateReal} onClick={()=>void run('create-real')}>Confirmar criação real não paga</button>
      </dialog>
    </>}
    {state.testOrder && <details><summary>Simulações administrativas existentes</summary><p>Registros de teste, sem confirmação financeira.</p>
      <p>Pagamento simulado: {state.simulatedPaymentAt?`SIMULADO em ${date(state.simulatedPaymentAt)}`:'Não executado'}</p>
      <button type="button" disabled={busy || !!state.simulatedPaymentAt} onClick={()=>void run('simulate-payment')}>Simular pagamento</button>
      <p>AliExpress simulado: {state.simulatedSupplierId || 'Não executado'}</p><button type="button" disabled={busy || !state.simulatedPaymentAt || !!state.simulatedSupplierAt} onClick={()=>void run('simulate-supplier')}>Executar AliExpress simulado</button>
    </details>}
  </section>;
}
