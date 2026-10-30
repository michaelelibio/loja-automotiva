'use client';
import {useState} from 'react';
import tokenStorage from '@/lib/auth/tokenStorage';
import {STATES,SIZES,prepareNational,importNational,commercialPrice,fromVersion,type BulkDocument,type FreightRate} from '@/lib/admin/national-freight';

type Version={version:number;document:Record<string,unknown>};
const numeric=['supplierFreightPerParcelBrl','customerFreightPerParcelBrl','taxesPerUnitBrl','maximumVerifiedSupplierBaseBrl'];
const integer=['maximumVerifiedUnits','unitsPerParcel','estimatedDays'];
const details=[['logisticsCode','Código logístico'],['logisticsName','Nome logístico'],['supplierFreightPerParcelBrl','Custo verificado AliExpress por volume (BRL)'],['customerFreightPerParcelBrl','Frete fixo comercial por volume (BRL)'],['taxesPerUnitBrl','Impostos por unidade (BRL)'],['maximumVerifiedSupplierBaseBrl','Base máxima verificada (BRL)'],['maximumVerifiedUnits','Quantidade total máxima verificada'],['unitsPerParcel','Unidades por volume'],['estimatedDays','Prazo máximo em dias']];
function localDate(value:unknown){if(!value)return '';const date=new Date(String(value));return Number.isNaN(date.getTime())?'':new Date(date.getTime()-date.getTimezoneOffset()*60000).toISOString().slice(0,16);}

export function BulkFreightReview({versions,getBaseDocument,onSaved}:{versions:Version[];getBaseDocument:()=>Record<string,unknown>;onSaved:()=>Promise<void>}){
 const [document,setDocument]=useState<BulkDocument>({reviewRows:[]});const [scope,setScope]=useState({category:'',supplierStoreId:'',originCountry:''});
 const [uf,setUf]=useState('');const [size,setSize]=useState('');const [price,setPrice]=useState('');const [selected,setSelected]=useState<number|null>(null);
 const [message,setMessage]=useState('');const [busy,setBusy]=useState(false);
 const visible=document.reviewRows.map((row,i)=>({row,i})).filter(({row})=>(!uf||row.rate.state===uf)&&(!size||row.rate.size===size));
 function run(action:()=>void){try{action();setMessage('Revisão atualizada localmente. Salve uma nova versão para persistir.');}catch(e){setMessage(e instanceof Error?e.message:'Operação inválida.');}}
 function edit(i:number,patch:Partial<FreightRate>){setDocument(d=>({...d,rates:[],reviewRows:d.reviewRows.map((row,j)=>i===j?{approved:false,rate:{...row.rate,...patch}}:row)}));}
 function exportFile(){const url=URL.createObjectURL(new Blob([JSON.stringify(document,null,2)],{type:'application/json'}));const a=window.document.createElement('a');a.href=url;a.download='fretes-nacional-rascunho.json';a.click();URL.revokeObjectURL(url);}
 async function save(){setBusy(true);try{
  const token=tokenStorage.getToken();if(!token)throw new Error('Entre como administrador.');
  const response=await fetch((process.env.NEXT_PUBLIC_API_BASE_URL??'http://localhost:8080')+'/api/admin/shipping-tables/bulk',{method:'POST',headers:{Authorization:'Bearer '+token,'Content-Type':'application/json'},body:JSON.stringify(document)});
  const result=await response.json();if(!response.ok)throw new Error(result.message??'Revise as linhas aprovadas.');
  await onSaved();setMessage('Rascunho '+result.version+' salvo. Nenhuma tarifa foi publicada.');
 }catch(e){setMessage(e instanceof Error?e.message:'Falha ao salvar.');}finally{setBusy(false);}}
 const chosen=selected===null?null:document.reviewRows[selected];
 const evidence=(chosen?.rate.evidence??{}) as Record<string,unknown>;
 function editEvidence(key:string,value:unknown){if(selected!==null)edit(selected,{evidence:{...evidence,[key]:value}});}
 const labels={SMALL:'Pequeno',MEDIUM:'Médio',LARGE:'Grande'};
 return <section aria-label="Revisão nacional em lote"><h2>Tabela nacional: preparação e revisão em massa</h2>
 <p>81 linhas por categoria/fornecedor/origem. Custos desconhecidos ficam em branco e inativos. R$ 24 ambíguos não são evidência. O preço é fixo por UF, porte e volume; quantidades seguem a capacidade comprovada da embalagem.</p>
 <label>Carregar versão para revisão<select defaultValue="" onChange={e=>run(()=>{const v=versions.find(v=>v.version===Number(e.target.value));if(v){setDocument(fromVersion(v.document));setSelected(null);}})}><option value="">Selecionar</option>{versions.map(v=><option key={v.version} value={v.version}>Versão {v.version}</option>)}</select></label>
 <button type="button" onClick={()=>run(()=>{setDocument(d=>({...fromVersion(getBaseDocument()),reviewRows:d.reviewRows.map(row=>({...row,approved:false}))}));})}>Atualizar política, reserva e perfis do formulário abaixo</button>
 <p>Preencha a política e os SKUs no formulário existente abaixo. Atualizar a política retira as aprovações para uma nova revisão.</p>
 {(['category','supplierStoreId','originCountry'] as const).map(k=><label key={k}>{k==='category'?'Categoria exata do catálogo':k==='supplierStoreId'?'Fornecedor/loja conferido':'Origem ISO conferida'}<input value={scope[k]} onChange={e=>setScope({...scope,[k]:e.target.value})}/></label>)}
 <button type="button" onClick={()=>run(()=>setDocument(d=>prepareNational(d.reviewRows.length?d:fromVersion(getBaseDocument()),scope)))}>Preparar 27 UFs × 3 portes sem preços</button>
 <button type="button" disabled={!document.reviewRows.length} onClick={()=>run(()=>setDocument(d=>{
   const scopes=new Map(d.reviewRows.map(({rate:r})=>[JSON.stringify([r.category,r.supplierStoreId,r.originCountry]),{category:r.category,supplierStoreId:r.supplierStoreId,originCountry:r.originCountry}]));
   return [...scopes.values()].reduce((next,s)=>prepareNational(next,s),d);
 }))}>Completar cobertura nacional dos escopos carregados</button>
 <label>Importar arquivo JSON de revisão<input type="file" accept=".json,application/json" onChange={async e=>{const f=e.target.files?.[0];if(!f)return;if(f.size>4_000_000){setMessage('Arquivo excede 4 MB.');return;}try{const imported=importNational(await f.text());setDocument(imported);setSelected(null);setMessage('Importado para revisão. Todas as aprovações foram removidas.');}catch(cause){setMessage(cause instanceof Error?cause.message:'Arquivo inválido.');}}}/></label>
 <button type="button" disabled={!document.reviewRows.length} onClick={exportFile}>Exportar revisão JSON</button>
 <label>Filtrar UF<select value={uf} onChange={e=>setUf(e.target.value)}><option value="">Todas</option>{STATES.map(s=><option key={s}>{s}</option>)}</select></label>
 <label>Filtrar porte<select value={size} onChange={e=>setSize(e.target.value)}><option value="">Todos</option>{SIZES.map(s=><option key={s} value={s}>{labels[s]}</option>)}</select></label>
 <label>Preço comercial em BRL para linhas filtradas<input type="number" min="0" step="0.01" value={price} onChange={e=>setPrice(e.target.value)}/></label>
 <button type="button" disabled={!visible.length} onClick={()=>run(()=>setDocument(d=>commercialPrice(d,visible.map(v=>v.i),price)))}>Aplicar preço às {visible.length} linhas filtradas (retira aprovação)</button>
 <button type="button" disabled={!visible.length} onClick={()=>run(()=>setDocument(d=>({...d,reviewRows:d.reviewRows.map((row,i)=>visible.some(v=>v.i===i)?{...row,approved:true}:row)})))}>Aprovar linhas filtradas para validação ao salvar</button>
 <button type="button" onClick={()=>setDocument(d=>({...d,reviewRows:d.reviewRows.map(row=>({...row,approved:false}))}))}>Retirar todas as aprovações</button>
 <p>{document.reviewRows.length} linhas; {document.reviewRows.filter(r=>r.approved).length} marcadas para aprovação. Publicação exige validação do backend; rentabilidade é recalculada para cada carrinho.</p>
 <div style={{overflowX:'auto'}}><table><thead><tr><th>UF</th><th>Porte</th><th>Categoria / fornecedor / origem</th><th>Cliente (BRL/volume)</th><th>Custo AliExpress (BRL/volume)</th><th>Revisão</th></tr></thead><tbody>{visible.map(({row,i})=><tr key={row.rate.key}><td>{row.rate.state}</td><td>{row.rate.size}</td><td>{row.rate.category} / {row.rate.supplierStoreId} / {row.rate.originCountry}</td><td><input aria-label={'Preço cliente '+row.rate.key} type="number" min="0" step="0.01" value={String(row.rate.customerFreightPerParcelBrl??'')} onChange={e=>edit(i,{customerFreightPerParcelBrl:e.target.value===''?null:e.target.value})}/></td><td>{String(row.rate.supplierFreightPerParcelBrl??'Não comprovado')}</td><td><label><input type="checkbox" checked={row.approved} onChange={e=>setDocument(d=>({...d,reviewRows:d.reviewRows.map((r,j)=>i===j?{...r,approved:e.target.checked}:r)}))}/>Aprovar</label><button type="button" onClick={()=>setSelected(i)}>Revisar custos e evidência</button></td></tr>)}</tbody></table></div>
 {chosen&&selected!==null&&<fieldset><legend>Revisão de {chosen.rate.state} / {chosen.rate.size} / {chosen.rate.category}</legend>
 {details.map(([key,label])=><label key={key}>{label}<input type={numeric.includes(key)||integer.includes(key)?'number':'text'} min="0" step={integer.includes(key)?'1':'0.01'} value={String(chosen.rate[key]??'')} onChange={e=>edit(selected,{[key]:integer.includes(key)?Number(e.target.value):numeric.includes(key)?(e.target.value===''?null:e.target.value):e.target.value})}/></label>)}
 <label>Agrupamento<select value={String(chosen.rate.grouping)} onChange={e=>edit(selected,{grouping:e.target.value})}><option>PER_SKU</option><option>CONSOLIDATED</option></select></label>
 <label><input type="checkbox" checked={chosen.rate.packagingVerified===true} onChange={e=>edit(selected,{packagingVerified:e.target.checked})}/>Embalagem e consolidação verificadas</label>
 <label>Fonte<select value={String(evidence.source??'DIAGNOSTIC_ONLY')} onChange={e=>editEvidence('source',e.target.value)}><option>DIAGNOSTIC_ONLY</option><option>ALIEXPRESS_PORTAL</option><option>OFFICIAL_SUPPORT</option></select></label>
 <label>Referência da verificação, sem dados pessoais<input value={String(evidence.reference??'')} onChange={e=>editEvidence('reference',e.target.value)}/></label>
 {['verifiedAt','validUntil'].map(key=><label key={key}>{key==='verifiedAt'?'Data de verificação':'Validade'}<input type="datetime-local" value={localDate(evidence[key])} onChange={e=>editEvidence(key,e.target.value?new Date(e.target.value).toISOString():null)}/></label>)}
 {['monetaryScaleConfirmed','costsAndCoverageChecked'].map(key=><label key={key}><input type="checkbox" checked={evidence[key]===true} onChange={e=>editEvidence(key,e.target.checked)}/>{key==='monetaryScaleConfirmed'?'Moeda BRL e escala comprovadas':'Custos, tributos e cobertura do destino verificados'}</label>)}
 <p>Editar qualquer dado retira a aprovação da linha. Verifique a rentabilidade antes da publicação; o checkout bloqueia margens insuficientes.</p></fieldset>}
 <button type="button" disabled={busy||!document.reviewRows.length} onClick={()=>void save()}>Salvar revisão nacional como rascunho</button>
 {message&&<p role="status">{message}</p>}
 </section>;
}
