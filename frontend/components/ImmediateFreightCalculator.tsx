'use client';
import { useEffect, useState } from 'react';
import { quoteShipping } from '@/lib/api/shipping';
import type { ShippingOption, ShippingQuoteRequest } from '@/lib/types/shipping';
const brl=(value:number)=>value.toLocaleString('pt-BR',{style:'currency',currency:'BRL'});
export function ImmediateFreightCalculator({items,subtotal}:{items:ShippingQuoteRequest['items'];subtotal:number}) {
  const [zip,setZip]=useState('');const [option,setOption]=useState<ShippingOption|null>(null);
  const [error,setError]=useState<string|null>(null);const [loading,setLoading]=useState(false);
  const [resolvedKey,setResolvedKey]=useState('');
  const key=JSON.stringify(items);
  useEffect(()=>{
    const controller=new AbortController();
    const timer=setTimeout(async()=>{
      setOption(null);setError(null);setLoading(false);
      const lines=JSON.parse(key) as ShippingQuoteRequest['items'];
      if(zip.length!==8 || lines.length===0)return;
      setLoading(true);
      try {const quote=await quoteShipping({zipCode:zip,items:lines},controller.signal);if(!controller.signal.aborted){setResolvedKey(key+zip);setOption(quote.options[0]??null);if(!quote.options.length)setError('Sem tarifa válida. Solicite cotação assistida no checkout.');}}
      catch {if(!controller.signal.aborted)setError('Frete imediato indisponível para esta seleção. Solicite cotação assistida no checkout.');}
      finally {if(!controller.signal.aborted)setLoading(false);}
    },350);
    return()=>{clearTimeout(timer);controller.abort();};
  },[key,zip]);
  // A quote from previous items/CEP is cleared immediately by the event or hidden while refreshing.
  return <section className="order-detail-card"><h3>Frete e total para seu CEP</h3>
    <label>CEP<input inputMode="numeric" autoComplete="postal-code" maxLength={9} value={zip} onChange={e=>{setZip(e.target.value.replace(/\D/g,'').slice(0,8));setOption(null);setError(null);}} placeholder="Somente números"/></label>
    {loading&&<p role="status">Calculando frete…</p>}
    {option&&resolvedKey===key+zip&&<p>{option.name}: <strong>{brl(option.price)}</strong> · Total: <strong>{brl(subtotal+option.price)}</strong> · até {option.estimatedDays} dias estimados. Reconfirmado no checkout.</p>}
    {error&&<p role="status">{error}</p>}
  </section>;
}
