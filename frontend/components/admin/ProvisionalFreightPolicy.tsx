'use client';
import {useState} from 'react';
import Link from 'next/link';
import tokenStorage from '@/lib/auth/tokenStorage';
import {STATES} from '@/lib/admin/national-freight';
type Version={version:number;enabled:boolean;publishedAt:string|null;document:Record<string,unknown>};
export function ProvisionalFreightPolicy({versions,onSaved}:{versions:Version[];onSaved:()=>Promise<void>}){
 const [ids,setIds]=useState('');const [until,setUntil]=useState('');const [maxOrder,setMaxOrder]=useState('');const [maxOrders,setMaxOrders]=useState('');const [maxSales,setMaxSales]=useState('');
 const [confirmed,setConfirmed]=useState(false);const [message,setMessage]=useState('');const [busy,setBusy]=useState(false);
 const policies=versions.filter(v=>v.document.provisional);const latest=versions.find(v=>v.publishedAt!==null);
 async function action(path:string,body:unknown){setBusy(true);try{
  const token=tokenStorage.getToken();if(!token)throw new Error('Entre como administrador.');
  const response=await fetch((process.env.NEXT_PUBLIC_API_BASE_URL??'http://localhost:8080')+'/api/admin/shipping-tables'+path,{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(body)});
  const data=await response.json();if(!response.ok)throw new Error(data.message??'Operação não concluída.');await onSaved();setMessage('Política registrada. Confira o estado da versão; pagamentos continuam bloqueados.');
 }catch(e){setMessage(e instanceof Error?e.message:'Falha ao salvar.');}finally{setBusy(false);}}
 function save(){
  const selected=ids.split(/[\s,;]+/).filter(Boolean);
  if(!selected.length||selected.some(id=>!/^\d+$/.test(id)||!Number.isSafeInteger(Number(id)))){setMessage('Informe IDs locais de variantes válidos.');return;}
  if(!confirmed||!until||!maxOrder||!maxOrders||!maxSales){setMessage('Defina validade, limites e confirme o porte/volume dos SKUs.');return;}
  void action('/provisional',{eligibleVariantIds:selected.map(Number),singleSmallUnitParcelConfirmed:confirmed,validUntil:new Date(until).toISOString(),maximumOrderTotalBrl:maxOrder,maximumOpenOrders:Number(maxOrders),maximumOpenSalesBrl:maxSales});
 }
 function copy(v:Version){const p=v.document.provisional as Record<string,unknown>;setIds((p.eligibleVariantIds as number[]).join(', '));const d=new Date(String(p.validUntil));setUntil(new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16));setMaxOrder(String(p.maximumOrderTotalBrl));setMaxOrders(String(p.maximumOpenOrders));setMaxSales(String(p.maximumOpenSalesBrl));setConfirmed(false);setMessage('Revise os SKUs e confirme novamente porte e embalagem. Salvar cria nova versão.');}
 return <section aria-label="Política provisória nacional"><h2>Frete comercial provisório: R$ 24,00</h2>
 <p>Preço da inGarage para uma unidade de um SKU pequeno elegível, em todas as UFs. Custo AliExpress, impostos, margem e prazo permanecem desconhecidos. Não é promoção Choice nem frete grátis acima de R$ 99.</p>
 <p>Ativação exige publicação administrativa e COMMERCIAL_FREIGHT_ENABLED. {latest?.enabled&&latest.document.provisional?'Política provisória publicada.':'Política provisória não está ativa.'} Publicar esta política substitui a tabela comercial vigente.</p>
 <Link href="/admin/produtos">Conferir produtos e variantes do catálogo</Link>
 <label>IDs locais das variantes elegíveis, separados por vírgula<input value={ids} onChange={e=>setIds(e.target.value)} placeholder="Selecione no catálogo; não use SKU externo"/></label>
 <label><input type="checkbox" checked={confirmed} onChange={e=>setConfirmed(e.target.checked)}/>Conferi que cada SKU selecionado é pequeno e uma unidade corresponde a um único volume</label>
 <label>Válida até<input type="datetime-local" value={until} onChange={e=>setUntil(e.target.value)}/></label>
 <label>Total máximo por pedido, produtos + frete (BRL)<input type="number" min="24.01" step="0.01" value={maxOrder} onChange={e=>setMaxOrder(e.target.value)}/></label>
 <label>Máximo de pedidos comerciais em aberto<input type="number" min="1" step="1" value={maxOrders} onChange={e=>setMaxOrders(e.target.value)}/></label>
 <label>Total máximo acumulado em aberto (BRL)<input type="number" min="24.01" step="0.01" value={maxSales} onChange={e=>setMaxSales(e.target.value)}/></label>
 <p>Limites contabilizam todos os pedidos comerciais não cancelados, não expirados e não entregues, inclusive versões anteriores. Não representam teto do custo real ou garantia de lucro. Sem limite configurado, não há ativação.</p>
 <table><thead><tr><th>UF</th><th>Pequeno elegível</th><th>Médio</th><th>Grande</th></tr></thead><tbody>{STATES.map(uf=><tr key={uf}><td>{uf}</td><td>R$ 24,00 — provisório</td><td>Rascunho, sem preço</td><td>Rascunho, sem preço</td></tr>)}</tbody></table>
 <button type="button" disabled={busy} onClick={save}>Salvar política provisória como rascunho</button>
 {policies.map(v=><article key={v.version}><p>Versão {v.version}: {v.publishedAt?(v.enabled?'publicada':'retirada'):'rascunho'}</p><button type="button" onClick={()=>copy(v)}>Revisar elegibilidade e limites</button>{!v.publishedAt&&<button type="button" disabled={busy} onClick={()=>void action('/'+v.version+'/publish',{})}>Ativar política provisória</button>}{v.enabled&&<button type="button" disabled={busy} onClick={()=>void action('/'+v.version+'/retire',{})}>Desativar política provisória</button>}</article>)}
 {message&&<p role="status">{message}</p>}</section>;
}
