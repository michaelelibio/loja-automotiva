'use client';

import { useEffect, useState, type FormEvent } from 'react';
import { assistedQuoteAction, getFulfillmentRecipient, getAssistedFreightSuggestions } from '@/lib/api/admin/orders';
import { prefillObservedLeg, prefillUnambiguousLegs, type AssistedFreightPreview } from '@/lib/admin/assisted-freight';
import type { AdminOrder, AdminOrderFulfillment } from '@/lib/types/admin-order';

type LegInput={code:string;name:string;originCountry:string;shippingBrl:string;estimatedDays:string;skuIds:string[]};
export function AssistedQuotePanel({order,fulfillment,onUpdated}:{order:AdminOrder;fulfillment:AdminOrderFulfillment|null;onUpdated:()=>Promise<void>}) {
  const [legs,setLegs]=useState<LegInput[]>(order.items.map(item=>({code:'',name:'',originCountry:'',shippingBrl:'',estimatedDays:'',skuIds:[item.supplierVariantId!]})));
  const [supplierTotal,setSupplierTotal]=useState('');const [hours,setHours]=useState('2');const [reference,setReference]=useState('');
  const [checked,setChecked]=useState(false);const [busy,setBusy]=useState(false);const [message,setMessage]=useState<string|null>(null);
  const [ids,setIds]=useState('');const [recipient,setRecipient]=useState<{cpf:string;birthDate:string;phone:string}|null>(null);
  const [preview,setPreview]=useState<AssistedFreightPreview|null>(null);
  const [previewLoading,setPreviewLoading]=useState(false);const [previewError,setPreviewError]=useState<string|null>(null);
  const [refresh,setRefresh]=useState(0);
  useEffect(()=>{
    if(order.quote?.state!=='WAITING') return;
    let cancelled=false;
    async function load() {
      setPreviewLoading(true);setPreviewError(null);setPreview(null);
      try {
        const result=await getAssistedFreightSuggestions(order.id);
        if(cancelled) return;
        if(result.orderId!==order.id) throw new Error('A consulta não corresponde ao pedido atual.');
        setPreview(result);setLegs(current=>prefillUnambiguousLegs(current,result));setChecked(false);
      } catch(cause) {if(!cancelled)setPreviewError(cause instanceof Error?cause.message:'Consulta indisponível. Continue a conferência manual.');}
      finally {if(!cancelled)setPreviewLoading(false);}
    }
    void load();return()=>{cancelled=true;};
  },[order.id,order.quote?.state,refresh]);
  if(!order.quote) return null;
  const state=order.quote.state;
  const finalTotal=legs.every(leg=>leg.shippingBrl!=='' && Number.isFinite(Number(leg.shippingBrl)) && Number(leg.shippingBrl)>=0)
    ? order.subtotal+legs.reduce((sum,leg)=>sum+Number(leg.shippingBrl),0) : null;
  async function action(name:string,data?:unknown) {
    setBusy(true);setMessage(null);
    try {await assistedQuoteAction(order.id,name,data);await onUpdated();setMessage('Registro salvo. Confira o estado atualizado.');setChecked(false);}
    catch(cause){setMessage(cause instanceof Error ? cause.message : 'Não foi possível salvar. Atualize antes de tentar novamente.');}
    finally{setBusy(false);}
  }
  function confirm(event:FormEvent) {event.preventDefault();if(finalTotal===null){setMessage('Confira o frete de todas as remessas.');return;}void action('',{supplierTotalBrl:Number(supplierTotal),expectedTotal:Math.round(finalTotal*100)/100,
    validUntil:new Date(Date.now()+Number(hours)*3600000).toISOString(),evidenceReference:reference,fullAddressAndTaxesChecked:checked,
    legs:legs.map(leg=>({...leg,shippingBrl:Number(leg.shippingBrl),estimatedDays:Number(leg.estimatedDays)}))});}
  return <section className="admin-card assisted-quote-panel"><h2>Cotação assistida AliExpress</h2><p>Estado: {state}. O registro usa os itens, quantidades e endereço gravados neste pedido.</p>
    {order.quote.validUntil && <p>Validade: {new Date(order.quote.validUntil).toLocaleString('pt-BR')}</p>}
    {message && <p role="status">{message}</p>}
    {state==='WAITING' && <form onSubmit={confirm}>
      <section aria-label="Consulta automática AliExpress">
        <h3>Dados observados no AliExpress</h3>
        <p>A consulta usa SKU, quantidade e país/província do pedido. Não confirma preço por CEP, endereço completo ou impostos. Campos já preenchidos manualmente são preservados.</p>
        <button type="button" disabled={previewLoading||busy} onClick={()=>setRefresh(value=>value+1)}>Atualizar dados do AliExpress</button>
        {previewLoading && <p role="status">Consultando produto e modalidades, sem criar pedido ou pagamento…</p>}
        {previewError && <p role="alert">{previewError}</p>}
        {preview && <>
          <p>Consulta: {new Date(preview.observedAt).toLocaleString('pt-BR')}. Revisar até {new Date(preview.reviewBefore).toLocaleString('pt-BR')}; este prazo é local e não reserva preço no fornecedor.</p>
          <p>Subtotal observado dos produtos: {preview.costs.observedProductsBrl===null?'não confirmado':preview.costs.observedProductsBrl.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}. Custo completo, impostos e margem líquida: pendentes de revisão.</p>
          {preview.lines.map((line,lineIndex)=><div key={`${line.productId}:${line.skuId}:${lineIndex}`}>
            <h4>SKU {line.skuId} · quantidade {line.quantity}</h4><p>Resultado: {line.status}</p>
            {line.tax && <p>Indicadores do produto: imposto incluído {line.tax.priceIncludesTax===null?'não informado':line.tax.priceIncludesTax?'sim':'não'}; valor recebido {line.tax.receivedTaxAmount??'não informado'} {line.tax.currency??''}. Não comprova todos os tributos brasileiros.</p>}
            {line.options.length===0 && <p>Nenhuma modalidade observada. Preencha somente após conferência manual.</p>}
            {line.options.map(option=><div key={option.optionIndex}>
              <p>{option.name??'Nome não informado'} · código {option.code??'não informado'} · origem {option.originCountry??'não informada'} · prazo máximo {option.estimatedDays??'não informado'} dias.</p>
              <p>Frete recebido: {option.receivedAmount??'não informado'} {option.currency??''}; formatado: {option.formattedAmount??'não informado'}; consistência: {option.monetaryConsistency}. {option.shippingBrl===null?'Escala não comprovada: frete permanece para revisão manual.':'Valor normalizado observado; destino completo ainda requer revisão.'}</p>
              <p>DDP/VAT incluído: {option.ddpIncludesVatTax===null?'não informado':option.ddpIncludesVatTax?'sim':'não'}. Não substitui a conferência dos tributos.</p>
              <button type="button" disabled={busy||previewLoading||Date.now()>=Date.parse(preview.reviewBefore)} onClick={()=>{
                if(!Number.isFinite(Date.parse(preview.reviewBefore)) || Date.now()>=Date.parse(preview.reviewBefore)) {
                  setPreviewError('Consulta expirada. Atualize os dados antes de preencher os campos.');return;
                }
                setLegs(current=>current.map(leg=>leg.skuIds.length===1&&leg.skuIds[0]===line.skuId?prefillObservedLeg(leg,option):leg));setChecked(false);
              }}>Preencher campos vazios desta modalidade</button>
            </div>)}
          </div>)}
        </>}
      </section>
      <p>Confira no AliExpress o custo completo para o endereço do pedido, sem criar pedido ao fornecedor. Informe o código da modalidade, não o nome comercial. Agrupe SKUs na mesma remessa somente se a conferência no fornecedor comprovar esse agrupamento.</p>
      {legs.map((leg,index)=><fieldset key={index}><legend>Remessa {index+1} — SKU {leg.skuIds.join(', ')}</legend>
        <button type="button" disabled={busy} onClick={()=>{
          setLegs(current=>current.map((entry,i)=>i===index?{...entry,code:'',name:'',originCountry:'',shippingBrl:'',estimatedDays:''}:entry));
          setSupplierTotal('');setChecked(false);
        }}>Limpar dados desta remessa para escolher outra modalidade</button>
        {(['code','name','originCountry','shippingBrl','estimatedDays'] as const).map(field=><label key={field}>{({code:'Código logístico',name:'Nome da modalidade',originCountry:'País de origem (ex.: CN)',shippingBrl:'Frete cobrado ao cliente (BRL)',estimatedDays:'Prazo máximo em dias'})[field]}
          <input required value={leg[field]} type={field==='shippingBrl'||field==='estimatedDays'?'number':'text'} min={field==='estimatedDays'?1:0} step={field==='shippingBrl'?'0.01':undefined}
            onChange={event=>setLegs(legs.map((entry,i)=>i===index?{...entry,[field]:event.target.value}:entry))}/></label>)}
        {index>0 && <button type="button" onClick={()=>setLegs(legs.filter((_,i)=>i!==index).map((entry,i)=>i===0?{...entry,skuIds:[...entry.skuIds,...leg.skuIds]}:entry))}>Agrupar na primeira remessa</button>}
      </fieldset>)}
      <label>Custo total do fornecedor, incluindo produtos, frete e impostos (BRL)<input required type="number" min="0.01" step="0.01" value={supplierTotal} onChange={e=>setSupplierTotal(e.target.value)}/></label>
      <label>Validade em horas (máximo 24)<input required type="number" min="0.1" max="24" step="0.1" value={hours} onChange={e=>setHours(e.target.value)}/></label>
      <label>Referência da evidência interna, sem dados pessoais ou credenciais<input required pattern="[A-Za-z0-9_.:/-]{1,150}" value={reference} onChange={e=>setReference(e.target.value)}/></label>
      <label><input required type="checkbox" checked={checked} onChange={e=>setChecked(e.target.checked)}/>Conferi produtos, SKUs, quantidades, endereço completo, impostos, modalidades e margem. Não criei pedido ao fornecedor.</label>
      <p>Total da venda: {finalTotal===null?'a confirmar':finalTotal.toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}. O cliente precisará aceitar antes de pagar.</p>
      {finalTotal!==null && supplierTotal!=='' && <p>Margem bruta informada: {(finalTotal-Number(supplierTotal)).toLocaleString('pt-BR',{style:'currency',currency:'BRL'})}, antes das taxas de pagamento e demais custos da loja. Não representa margem líquida confirmada pela API.</p>}
      <button disabled={busy||previewLoading||!checked||finalTotal===null}>Confirmar cotação</button>
    </form>}
    {(state==='CONFIRMED'||(state==='ACCEPTED'&&order.status==='PENDING_PAYMENT')) && <p>Aguardando aceite e pagamento do cliente. Não crie pedido ao fornecedor.</p>}
    {state==='ACCEPTED' && (order.status==='PAID'||order.status==='PROCESSING') && <div>
      <p>Antes de encaminhar, confira novamente o custo total, endereço, quantidades e modalidades. Qualquer mudança de custo bloqueia o envio e exige cancelamento/reembolso integral; não há cobrança adicional.</p>
      <label>Custo total atual no AliExpress (BRL)<input type="number" min="0.01" step="0.01" value={supplierTotal} onChange={e=>setSupplierTotal(e.target.value)}/></label>
      <label><input type="checkbox" checked={checked} onChange={e=>setChecked(e.target.checked)}/>Conferi o mesmo endereço, itens, quantidades e modalidades.</label>
      <button disabled={busy||!checked||!supplierTotal} onClick={()=>void action('recheck',{supplierTotalBrl:Number(supplierTotal),sameAddressItemsAndMethods:checked})}>Registrar reconferência de custo</button>
      <button disabled={busy} onClick={()=>void action('recheck',{supplierTotalBrl:Number(supplierTotal)||order.total,sameAddressItemsAndMethods:false})}>Bloquear por indisponibilidade ou mudança na entrega</button>
      {fulfillment?.status==='PENDING' && <button disabled={busy} onClick={()=>void action('manual-claim')}>Reservar encaminhamento manual único</button>}
      {fulfillment?.status==='VERIFICATION_REQUIRED' && <div><p>Encaminhamento reservado. Verifique primeiro se já existe pedido no AliExpress. Crie uma única compra manualmente e mantenha o pagamento ao fornecedor manual. Registre todos os IDs abaixo. Em caso de dúvida, não repita a compra.</p>
        <button disabled={busy} onClick={async()=>{try{setRecipient(await getFulfillmentRecipient(order.id));}catch(cause){setMessage(cause instanceof Error?cause.message:'Falha ao consultar destinatário.');}}}>Consultar dados do destinatário para envio</button>
        {recipient && <p>CPF: {recipient.cpf} · Nascimento: {recipient.birthDate} · Telefone BR (+55): {recipient.phone} <button onClick={()=>setRecipient(null)}>Ocultar</button></p>}
        <label>IDs dos pedidos AliExpress, separados por vírgula<input value={ids} onChange={e=>setIds(e.target.value)}/></label>
        <button disabled={busy||!ids} onClick={()=>void action('manual-complete',{supplierOrderIds:ids.split(',').map(value=>value.trim())})}>Registrar pedidos criados manualmente</button>
      </div>}
      {fulfillment?.status==='CREATED' && <p>Pedido registrado no fornecedor. Não crie novamente. Pagamento ao fornecedor permanece manual.</p>}
    </div>}
    {state==='REFUND_REQUIRED' && <div><p>Envio bloqueado por mudança de custo. Cancele a compra e realize o reembolso integral no Mercado Pago. Este formulário apenas registra o comprovante; não movimenta dinheiro.</p>
      <label>Referência do comprovante de reembolso integral<input value={reference} onChange={e=>setReference(e.target.value)}/></label>
      <label><input type="checkbox" checked={checked} onChange={e=>setChecked(e.target.checked)}/>Já realizei e conferi o reembolso integral.</label>
      <button disabled={busy||!checked||!reference} onClick={()=>void action('refund-record',{reference})}>Registrar reembolso e cancelar pedido</button>
    </div>}
    {state==='REFUNDED' && <p>Reembolso registrado. Pedido cancelado, sem liberação de envio.</p>}
  </section>;
}
