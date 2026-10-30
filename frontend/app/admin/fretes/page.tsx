'use client';
import {useEffect,useState,type FormEvent} from 'react';
import Link from 'next/link';
import tokenStorage from '@/lib/auth/tokenStorage';
import {BulkFreightReview} from '@/components/admin/BulkFreightReview';
const UFS='AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO'.split(' ');
type Evidence={source:string;reference:string;verifiedAt:string;validUntil:string;monetaryScaleConfirmed:boolean;costsAndCoverageChecked:boolean};
type Row={fields:Record<string,string>;evidence:Evidence};
type Version={version:number;publishedAt:string|null;enabled:boolean;document:Record<string,unknown>};
const emptyEvidence=():Evidence=>({source:'DIAGNOSTIC_ONLY',reference:'',verifiedAt:'',validUntil:'',monetaryScaleConfirmed:false,costsAndCoverageChecked:false});
type Definition={key:string;label:string;options?:string[];numeric?:boolean};
const profileFields:Definition[]=[{key:'productId',label:'ID do produto',numeric:true},{key:'variantId',label:'ID da variante',numeric:true},
 {key:'supplierProductId',label:'ID do produto AliExpress'},{key:'supplierSkuId',label:'SKU AliExpress'},
 {key:'size',label:'Porte',options:['SMALL','MEDIUM','LARGE']},{key:'supplierStoreId',label:'Loja/fornecedor verificado'},
 {key:'originCountry',label:'Origem ISO (ex.: CN)'},{key:'supplierProductUnitBrl',label:'Custo unitário do produto, sem os impostos informados na tarifa (BRL)',numeric:true},
 {key:'otherSupplierUnitBrl',label:'Outros custos unitários do fornecedor (BRL)',numeric:true}];
const rateFields:Definition[]=[{key:'key',label:'Identificador único da tarifa'},{key:'state',label:'UF',options:UFS},
 {key:'category',label:'Categoria exata do catálogo'},{key:'size',label:'Porte',options:['SMALL','MEDIUM','LARGE']},
 {key:'supplierStoreId',label:'Loja/fornecedor verificado'},{key:'originCountry',label:'Origem ISO'},
 {key:'logisticsCode',label:'Código logístico do fornecedor'},{key:'logisticsName',label:'Nome logístico'},
 {key:'supplierFreightPerParcelBrl',label:'Custo do frete por volume (BRL)',numeric:true},{key:'customerFreightPerParcelBrl',label:'Frete cobrado do cliente por volume (BRL)',numeric:true},
 {key:'taxesPerUnitBrl',label:'Impostos por unidade, sem duplicar os incluídos no produto (BRL)',numeric:true},
 {key:'maximumVerifiedUnits',label:'Quantidade máxima TOTAL com custos e tributos comprovados',numeric:true},{key:'maximumVerifiedSupplierBaseBrl',label:'Base máxima comprovada do fornecedor (produtos + frete + outros, BRL)',numeric:true},{key:'unitsPerParcel',label:'Unidades comprovadas por volume',numeric:true},{key:'grouping',label:'Regra de remessa comprovada',options:['PER_SKU','CONSOLIDATED']},
 {key:'estimatedDays',label:'Prazo estimado máximo (dias)',numeric:true}];
const policyFields:Definition[]=[{key:'costReservePercent',label:'Reserva sobre todos os custos do fornecedor (%)',numeric:true},
 {key:'paymentFeePercent',label:'Taxa percentual do pagamento (%)',numeric:true},{key:'paymentFixedFeeBrl',label:'Taxa fixa por pedido (BRL)',numeric:true},
 {key:'minimumNetMarginPercent',label:'Margem líquida mínima sobre a venda (%)',numeric:true},{key:'minimumNetProfitBrl',label:'Lucro líquido mínimo por pedido (BRL)',numeric:true}];
const api=process.env.NEXT_PUBLIC_API_BASE_URL??'http://localhost:8080';
async function request(path='',body?:unknown){
 const token=tokenStorage.getToken();if(!token)throw new Error('Entre como administrador.');
 const response=await fetch(api+'/api/admin/shipping-tables'+path,{method:body===undefined?'GET':'POST',cache:'no-store',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},...(body===undefined?{}:{body:JSON.stringify(body)})});
 const data=await response.json();if(!response.ok)throw new Error(data.message??'Operação não concluída.');return data;
}
function Fields({definitions,value,onChange}:{definitions:Definition[];value:Record<string,string>;onChange:(value:Record<string,string>)=>void}){
 return <>{definitions.map(d=><label key={d.key}>{d.label}{d.options?<select required value={value[d.key]??''} onChange={e=>onChange({...value,[d.key]:e.target.value})}><option value="">Selecionar</option>{d.options.map(o=><option key={o}>{o}</option>)}</select>:<input required type={d.numeric?'number':'text'} min={d.numeric?0:undefined} step={d.numeric?'any':undefined} value={value[d.key]??''} onChange={e=>onChange({...value,[d.key]:e.target.value})}/>}</label>)}</>;
}
function EvidenceForm({value,onChange,allowPayment=false}:{value:Evidence;onChange:(v:Evidence)=>void;allowPayment?:boolean}){
 return <fieldset><legend>Evidência e validade</legend><label>Fonte<select value={value.source} onChange={e=>onChange({...value,source:e.target.value})}><option value="DIAGNOSTIC_ONLY">Diagnóstico: não autoriza preço comercial</option><option value="ALIEXPRESS_PORTAL">Preço conferido no portal AliExpress</option><option value="OFFICIAL_SUPPORT">Confirmação do suporte oficial</option>{allowPayment&&<option value="PAYMENT_PROVIDER_PLAN">Plano de taxas do provedor de pagamento</option>}</select></label>
 <label>Referência interna, sem dados pessoais ou credenciais<input required value={value.reference} onChange={e=>onChange({...value,reference:e.target.value})}/></label>
 <label>Verificado em<input required type="datetime-local" value={value.verifiedAt} onChange={e=>onChange({...value,verifiedAt:e.target.value})}/></label>
 <label>Válido até<input required type="datetime-local" value={value.validUntil} onChange={e=>onChange({...value,validUntil:e.target.value})}/></label>
 <label><input type="checkbox" checked={value.monetaryScaleConfirmed} onChange={e=>onChange({...value,monetaryScaleConfirmed:e.target.checked})}/>Escala e moeda BRL comprovadas; não inferidas de MAJOR_MATCH</label>
 <label><input type="checkbox" checked={value.costsAndCoverageChecked} onChange={e=>onChange({...value,costsAndCoverageChecked:e.target.checked})}/>Custos e cobertura comercial conferidos na fonte</label></fieldset>;
}
const serializeEvidence=(e:Evidence)=>({...e,verifiedAt:e.verifiedAt?new Date(e.verifiedAt).toISOString():null,validUntil:e.validUntil?new Date(e.validUntil).toISOString():null});
export default function FreightTablesPage(){
 const [versions,setVersions]=useState<Version[]>([]);const [policy,setPolicy]=useState<Record<string,string>>({});
 const [feeEvidence,setFeeEvidence]=useState<Evidence>(emptyEvidence);const [profiles,setProfiles]=useState<Row[]>([]);const [rates,setRates]=useState<Row[]>([]);
 const [message,setMessage]=useState('');const [busy,setBusy]=useState(false);
 useEffect(()=>{request().then(setVersions).catch(cause=>setMessage(String(cause.message)));},[]);
 function baseDocument(){return {...Object.fromEntries(policyFields.map(f=>[f.key,policy[f.key]||null])),paymentFeeEvidence:serializeEvidence(feeEvidence),profiles:profiles.map(p=>({...Object.fromEntries(profileFields.map(f=>[f.key,p.fields[f.key]||null])),productId:p.fields.productId?Number(p.fields.productId):null,variantId:p.fields.variantId?Number(p.fields.variantId):null,evidence:serializeEvidence(p.evidence)})),rates:[]};}
 async function action(path:string,body:unknown){setBusy(true);try{await request(path,body);setVersions(await request());setMessage('Versão registrada. Tarifas só são usadas quando publicadas, válidas e comercialmente habilitadas.');}catch(cause){setMessage(cause instanceof Error?cause.message:'Falha ao registrar.');}finally{setBusy(false);}}
 function save(e:FormEvent){e.preventDefault();if(!profiles.length||!rates.length){setMessage('Cadastre pelo menos um SKU e uma tarifa comprovada.');return;}
  void action('',{...policy,paymentFeeEvidence:serializeEvidence(feeEvidence),profiles:profiles.map(p=>({...p.fields,productId:Number(p.fields.productId),variantId:Number(p.fields.variantId),evidence:serializeEvidence(p.evidence)})),rates:rates.map(r=>({...r.fields,maximumVerifiedUnits:Number(r.fields.maximumVerifiedUnits),unitsPerParcel:Number(r.fields.unitsPerParcel),estimatedDays:Number(r.fields.estimatedDays),packagingVerified:r.fields.packagingVerified==='true',evidence:serializeEvidence(r.evidence)}))});
 }
 function copy(version:Version){
  const doc=version.document;setPolicy(Object.fromEntries(policyFields.map(f=>[f.key,String(doc[f.key]??'')])));
  const ev=(raw:unknown):Evidence=>{const e=(raw??emptyEvidence()) as Evidence;const local=(s:string)=>{if(!s)return '';const d=new Date(s);return new Date(d.getTime()-d.getTimezoneOffset()*60000).toISOString().slice(0,16);};return {...e,verifiedAt:local(e.verifiedAt),validUntil:local(e.validUntil)};};
  setFeeEvidence(ev(doc.paymentFeeEvidence));const rows=(raw:unknown):Row[]=>((raw??[]) as Record<string,unknown>[]).map(r=>({fields:Object.fromEntries(Object.entries(r).filter(([k])=>k!=='evidence').map(([k,v])=>[k,v==null?'':String(v)])),evidence:ev(r.evidence)}));
  setProfiles(rows(doc.profiles));setRates(rows(doc.rates));setMessage('Versão copiada para revisão. Salvar cria outro rascunho, sem alterar pedidos existentes.');
 }
 return <section className="admin-card"><h1>Tabela comercial de fretes por UF</h1><p>Tarifa da inGarage, baseada em evidências verificadas. Nenhum preço foi preenchido por estimativa. Diagnósticos não comprovam escala nem impostos.</p>
 <p>Salvar cria rascunho imutável por versão. Publicar exige evidências válidas e a liberação operacional após revisar a proposta; pagamentos permanecem bloqueados.</p>
 <Link href="/admin/produtos/importar">Consultar evidências AliExpress existentes</Link>
 {message&&<p role="status">{message}</p>}
 <BulkFreightReview versions={versions} getBaseDocument={baseDocument} onSaved={async()=>setVersions(await request())}/>
 <table><thead><tr><th>UF</th><th>Pequeno</th><th>Médio</th><th>Grande</th></tr></thead><tbody>{UFS.map(uf=><tr key={uf}><th>{uf}</th>{['SMALL','MEDIUM','LARGE'].map(size=><td key={size}>{rates.some(r=>r.fields.state===uf&&r.fields.size===size)?'Regra em revisão':'Sem evidência cadastrada'}</td>)}</tr>)}</tbody></table>
 <form onSubmit={save}><h2>Rentabilidade e reserva</h2><Fields definitions={policyFields} value={policy} onChange={setPolicy}/><EvidenceForm allowPayment value={feeEvidence} onChange={setFeeEvidence}/>
 <h2>Produtos e custos por SKU</h2>{profiles.map((p,i)=><fieldset key={i}><legend>SKU {i+1}</legend><Fields definitions={profileFields} value={p.fields} onChange={fields=>setProfiles(profiles.map((v,j)=>j===i?{...v,fields}:v))}/><EvidenceForm value={p.evidence} onChange={evidence=>setProfiles(profiles.map((v,j)=>j===i?{...v,evidence}:v))}/><button type="button" onClick={()=>setProfiles(profiles.filter((_,j)=>j!==i))}>Remover SKU do rascunho</button></fieldset>)}
 <button type="button" onClick={()=>setProfiles([...profiles,{fields:{},evidence:emptyEvidence()}])}>Adicionar SKU verificado</button>
 <h2>Tarifas por remessa</h2>{rates.map((r,i)=><fieldset key={i}><legend>Tarifa {i+1}</legend><Fields definitions={rateFields} value={r.fields} onChange={fields=>setRates(rates.map((v,j)=>j===i?{...v,fields}:v))}/>
 <label><input type="checkbox" checked={r.fields.packagingVerified==='true'} onChange={e=>setRates(rates.map((v,j)=>j===i?{...v,fields:{...v.fields,packagingVerified:String(e.target.checked)}}:v))}/>Comprovei separação/consolidação dos SKUs e capacidade por volume; não há cobrança duplicada</label>
 <EvidenceForm value={r.evidence} onChange={evidence=>setRates(rates.map((v,j)=>j===i?{...v,evidence}:v))}/><button type="button" onClick={()=>setRates(rates.filter((_,j)=>j!==i))}>Remover tarifa do rascunho</button></fieldset>)}
 <button type="button" onClick={()=>setRates([...rates,{fields:{},evidence:emptyEvidence()}])}>Adicionar tarifa com evidência</button><button disabled={busy}>Salvar nova versão em rascunho</button></form>
 <h2>Versões</h2>{versions.map(v=><article key={v.version}><p>Versão {v.version} — {v.publishedAt?(v.enabled?'Publicada':'Retirada'):'Rascunho'}</p><button onClick={()=>copy(v)}>Copiar para revisão</button>{!v.publishedAt&&<button disabled={busy} onClick={()=>void action('/'+v.version+'/publish',{})}>Publicar após aprovação</button>}{v.enabled&&<button disabled={busy} onClick={()=>void action('/'+v.version+'/retire',{})}>Retirar de uso</button>}</article>)}
 </section>;
}
