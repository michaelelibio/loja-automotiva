/* Fixed-scope operational recovery. No deployment, global flags, schema changes, payment or retries. */
const {execFileSync}=require('node:child_process');
const crypto=require('node:crypto');const fs=require('node:fs');const path=require('node:path');
const ROOT=path.resolve(__dirname,'..');let stage='INITIAL';
const requireTrue=(v,code)=>{if(!v)throw new Error(code);};
const literal=v=>"'"+String(v).replaceAll("'","''")+"'";
function railway(command,input) {return execFileSync(process.env.ComSpec||'cmd.exe',['/d','/s','/c',command],{encoding:'utf8',input,stdio:['pipe','pipe','pipe'],timeout:60000});}
function sql(query,write=false) {
  const raw=railway('railway.cmd ssh --service Postgres --environment production -- psql -X -t -A -U postgres -d railway -v ON_ERROR_STOP=1',
    `BEGIN${write?'':' READ ONLY'}; SET LOCAL statement_timeout='15s'; ${query}; COMMIT;\n`);
  return JSON.parse(raw.split(/\r?\n/).find(s=>s.startsWith('{')));
}
const digest=`md5(row_to_json(o)::text || COALESCE((SELECT json_agg(row_to_json(i) ORDER BY i.id)::text FROM order_items i WHERE i.order_id=21),'') || COALESCE((SELECT json_agg(row_to_json(v) ORDER BY v.id)::text FROM product_variants v WHERE v.id IN(SELECT product_variant_id FROM order_items WHERE order_id=21)),''))`;
function cpfValid(value) {return /^\d{11}$/.test(value||'')&&!/^(\d)\1{10}$/.test(value)&&[9,10].every(n=>{const sum=[...value.slice(0,n)].reduce((s,d,i)=>s+Number(d)*(n+1-i),0),v=sum*10%11;return Number(value[n])===(v===10?0:v);});}
function technical(data) {
  const o=data.order,f=data.fulfillment,a=data.adminRun;
  requireTrue(o?.id===21&&['PAID','PROCESSING'].includes(o.status),'ORDER_NOT_PAID');
  requireTrue(f?.provider==='ALIEXPRESS'&&f.status==='PENDING'&&f.attempt_count===0&&!f.supplier_order_id&&!(f.supplier_order_ids||[]).length,'DUPLICATE_OR_PRIOR_ATTEMPT');
  requireTrue(!a||!a.real_key&&!a.submitted_at&&!(a.supplier_order_ids||[]).length,'ADMINISTRATIVE_ATTEMPT');
  for(const k of ['recipient_name','street','number','neighborhood','city','state','zip_code','recipient_cpf','recipient_birth_date','recipient_phone'])requireTrue(typeof o[k]==='string'&&o[k].trim(),'MISSING_RECIPIENT_FIELD');
  requireTrue(cpfValid(o.recipient_cpf)&&/^\d{8}$/.test(o.zip_code)&&/^\d{10,11}$/.test(o.recipient_phone),'INVALID_RECIPIENT');
  const age=(Date.now()-Date.parse(o.recipient_birth_date))/(365.25*86400000);requireTrue(age>0&&age<120,'INVALID_BIRTH_DATE');
  requireTrue(data.items?.length===1,'UNEXPECTED_ORDER_CONTENT');
  const {item:i,variant:v}=data.items[0];
  requireTrue(i.supplier==='ALIEXPRESS'&&i.fulfillment_type==='DROPSHIPPING'&&i.supplier_product_id==='1005007170269840'&&i.supplier_variant_id==='12000039689247941'&&i.quantity===1,'UNEXPECTED_PRODUCT');
  requireTrue(v?.supplier==='ALIEXPRESS'&&v.product_id===i.product_id&&v.supplier_product_id===i.supplier_product_id&&v.supplier_variant_id===i.supplier_variant_id&&v.raw_variant_key,'INVALID_VARIANT_LINK');
}
const provinces={AC:'Acre',AL:'Alagoas',AP:'Amapá',AM:'Amazonas',BA:'Bahia',CE:'Ceará',DF:'Distrito Federal',ES:'Espírito Santo',GO:'Goiás',MA:'Maranhão',MT:'Mato Grosso',MS:'Mato Grosso do Sul',MG:'Minas Gerais',PA:'Pará',PB:'Paraíba',PR:'Paraná',PE:'Pernambuco',PI:'Piauí',RJ:'Rio de Janeiro',RN:'Rio Grande do Norte',RS:'Rio Grande do Sul',RO:'Rondônia',RR:'Roraima',SC:'Santa Catarina',SP:'São Paulo',SE:'Sergipe',TO:'Tocantins'};
function payload(data) {
  const o=data.order;requireTrue(provinces[o.state],'INVALID_STATE');
  return {out_order_id:'INGARAGE-21',ds_extend_request:{trade_extra_param:{birthday:o.recipient_birth_date.slice(8,10)+o.recipient_birth_date.slice(5,7)+o.recipient_birth_date.slice(0,4)}},logistics_address:{country:'BR',province:provinces[o.state],city:o.city,address:o.street+', '+o.number,
    address2:o.neighborhood+(o.complement?' - '+o.complement:''),zip:o.zip_code,contact_person:o.recipient_name,full_name:o.recipient_name,
    cpf:o.recipient_cpf,mobile_no:o.recipient_phone,phone_country:'+55',locale:'pt_BR'},
    product_items:data.items.map(({item:i,variant:v})=>({product_id:Number(i.supplier_product_id),product_count:i.quantity,sku_attr:v.raw_variant_key}))};
}
async function run() {
  stage='VARIABLES';const vars=JSON.parse(railway('railway.cmd variables --json --service loja-automotiva --environment production'));
  requireTrue(vars.RAILWAY_PROJECT_ID==='f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79'&&vars.RAILWAY_ENVIRONMENT_NAME==='production','PROJECT_SCOPE');
  const appHash=crypto.createHash('sha256').update(vars.ALIEXPRESS_APP_KEY||'').digest('hex');
  stage='DATABASE_PREFLIGHT';const data=sql(`SELECT json_build_object('order',row_to_json(o),'fingerprint',${digest},
    'items',(SELECT json_agg(json_build_object('item',row_to_json(i),'variant',row_to_json(v)) ORDER BY i.id) FROM order_items i LEFT JOIN product_variants v ON v.id=i.product_variant_id WHERE i.order_id=21),
    'fulfillment',(SELECT row_to_json(f) FROM order_fulfillments f WHERE f.order_id=21),
    'payments',(SELECT json_agg(row_to_json(p) ORDER BY p.id) FROM payments p WHERE p.order_id=21),
    'adminRun',(SELECT row_to_json(a) FROM admin_aliexpress_test_runs a WHERE a.order_id=21),
    'authorization',(SELECT row_to_json(a) FROM aliexpress_authorizations a WHERE a.id=${literal(appHash)})) FROM orders o WHERE o.id=21`);
  technical(data);const previousAttempts=data.fulfillment.attempt_count;
  const p=(data.payments||[]).filter(p=>p.status==='PAID'&&p.method==='MERCADO_PAGO'&&p.provider_live_mode===true&&p.provider_verified_at&&p.paid_at&&p.provider_payment_id).at(-1);
  requireTrue(p,'PAYMENT_PROOF_MISSING');
  stage='MERCADO_PAGO';const mr=await fetch('https://api.mercadopago.com/v1/payments/'+encodeURIComponent(p.provider_payment_id),{headers:{Authorization:'Bearer '+vars.MERCADO_PAGO_ACCESS_TOKEN},redirect:'error',signal:AbortSignal.timeout(20000)});
  requireTrue(mr.ok,'PAYMENT_QUERY_FAILED');const mp=await mr.json();
  requireTrue(String(mp.id)===p.provider_payment_id&&mp.status==='approved'&&mp.live_mode===true&&mp.date_approved&&mp.transaction_amount_refunded===0&&mp.currency_id==='BRL'&&mp.transaction_amount===Number(data.order.total)&&mp.external_reference===`garage_order_21_payment_${p.id}`,'PAYMENT_NOT_APPROVED');
  const a=data.authorization;requireTrue(a?.buyer_user_id&&Date.parse(a.access_expires_at)>Date.now()+60000,'OAUTH_UNAVAILABLE');
  const bytes=Buffer.from(a.access_ciphertext.slice(3),'base64'),dec=crypto.createDecipheriv('aes-256-gcm',Buffer.from(vars.ALIEXPRESS_TOKEN_ENCRYPTION_KEY,'base64'),bytes.subarray(0,12));
  dec.setAAD(Buffer.from(a.id+':access'));dec.setAuthTag(bytes.subarray(-16));const token=Buffer.concat([dec.update(bytes.subarray(12,-16)),dec.final()]).toString('utf8');
  stage='PRODUCT_VALIDATION';const params={method:'aliexpress.ds.product.get',app_key:vars.ALIEXPRESS_APP_KEY,session:token,sign_method:'sha256',timestamp:String(Date.now()),v:'2.0',format:'json',product_id:data.items[0].item.supplier_product_id,ship_to_country:'BR',target_currency:'BRL',target_language:'pt'};
  params.sign=crypto.createHmac('sha256',vars.ALIEXPRESS_APP_SECRET).update(Object.keys(params).sort().map(k=>k+params[k]).join('')).digest('hex').toUpperCase();
  const pr=await fetch('https://api-sg.aliexpress.com/sync',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams(params),redirect:'error',signal:AbortSignal.timeout(25000)});
  requireTrue(pr.ok,'PRODUCT_QUERY_FAILED');const pb=await pr.json(),pe=pb.aliexpress_ds_product_get_response,product=pe?.result;
  requireTrue(!pb.error_response&&String(pe?.rsp_code)==='200'&&product?.ae_item_base_info_dto?.product_status_type==='onSelling','PRODUCT_UNAVAILABLE');
  const container=product.ae_item_sku_info_dtos,skus=Array.isArray(container)?container:container?.ae_item_sku_info_d_t_o;
  const sku=skus?.find(s=>String(s.sku_id)===data.items[0].item.supplier_variant_id);
  requireTrue(sku&&sku.sku_attr===data.items[0].variant.raw_variant_key&&Number.isInteger(Number(sku.sku_available_stock))&&Number(sku.sku_available_stock)>=1,'SKU_OR_STOCK_INVALID');
  const costs={source:'LIVE_PRODUCT_API',currency:/^[A-Z]{3}$/.test(sku.currency_code||'')?sku.currency_code:null,
    productTotal:/^\d+(\.\d+)?$/.test(sku.offer_sale_price||sku.sku_price||'')?Number(sku.offer_sale_price||sku.sku_price):null,shippingTotal:null,taxTotal:null,total:null,observedAt:new Date().toISOString()};
  const request=payload(data);const safe={orderId:21,paymentApproved:true,technicalDataValid:true,previousAttempts,supplierOrderIds:[],costs,
    financialRule:'ALERT_ONLY',paymentToSupplier:'MANUAL_ONLY',supplierHistoryConfirmed:process.argv.includes('--supplier-history-clear')};
  if(!process.argv.includes('--execute')){console.log(JSON.stringify({...safe,created:false},null,2));return;}
  requireTrue(process.argv.includes('--supplier-history-clear'),'SUPPLIER_DUPLICATE_CHECK_REQUIRED');
  requireTrue(fs.existsSync(path.join(ROOT,'target/order21-bridge/com/garage/garageapi/integration/aliexpress/AuthorizedOrder21Bridge.class')),'BRIDGE_NOT_COMPILED');
  const cp=path.join(ROOT,'target/order21-bridge')+path.delimiter+fs.readFileSync(path.join(ROOT,'target/order21-bridge.classpath.local'),'utf8').trim();
  const java=process.env.JAVA_HOME?path.join(process.env.JAVA_HOME,'bin/java.exe'):'C:/Program Files/Java/zulu25.36.15-ca-jdk25.0.4-win_x64/bin/java.exe';
  const bridge=input=>JSON.parse(execFileSync(java,['-Dfile.encoding=UTF-8','-Dstdout.encoding=UTF-8','--class-path',cp,'com.garage.garageapi.integration.aliexpress.AuthorizedOrder21Bridge'],{encoding:'utf8',input:JSON.stringify({orderId:21,key:vars.ALIEXPRESS_APP_KEY,secret:vars.ALIEXPRESS_APP_SECRET,token,...input}),stdio:['pipe','pipe','pipe'],timeout:60000}));
  const claimToken=crypto.randomUUID();stage='DURABLE_CLAIM';
  const claimed=sql(`SELECT id FROM orders WHERE id=21 FOR UPDATE;
    WITH claimed AS(UPDATE order_fulfillments f SET status='VERIFICATION_REQUIRED',supplier_status='UNKNOWN',attempt_count=${previousAttempts+1},
      processing_token=${literal(claimToken)},processing_started_at=NOW(),updated_at=NOW(),last_error='Authorized order21 submission. Result pending; never resend before supplier reconciliation.'
      WHERE f.order_id=21 AND f.provider='ALIEXPRESS' AND f.status=${literal(data.fulfillment.status)} AND f.attempt_count=${previousAttempts} AND f.supplier_order_id IS NULL
      AND ${previousAttempts===0?'TRUE':`f.last_error=${literal(data.fulfillment.last_error)}`}
      AND (f.supplier_order_ids IS NULL OR f.supplier_order_ids='[]'::jsonb)
      AND EXISTS(SELECT 1 FROM orders o WHERE o.id=21 AND o.status IN('PAID','PROCESSING') AND ${digest}=${literal(data.fingerprint)})
      AND EXISTS(SELECT 1 FROM payments p WHERE p.id=${Number(p.id)} AND p.order_id=21 AND p.status='PAID' AND p.method='MERCADO_PAGO' AND p.provider_live_mode=TRUE AND p.provider_verified_at IS NOT NULL AND p.paid_at IS NOT NULL AND p.provider_payment_id=${literal(p.provider_payment_id)})
      AND NOT EXISTS(SELECT 1 FROM admin_aliexpress_test_runs a WHERE a.order_id=21 AND(a.real_key IS NOT NULL OR a.submitted_at IS NOT NULL OR COALESCE(a.supplier_order_ids,'[]'::jsonb)<>'[]'::jsonb)) RETURNING order_id)
      SELECT json_build_object('claimed',count(*)) FROM claimed`,true);
  requireTrue(claimed.claimed===1,'CLAIM_NOT_CONFIRMED_NO_SUBMISSION');
  stage='SINGLE_CREATE';let result;
  try{result=bridge({action:'create',payload:request});}catch{result={orderIds:[],confirmed:false,rejected:false};}
  const ids=result.orderIds||[];requireTrue(ids.every(s=>typeof s==='string'&&/^[1-9][0-9]{0,29}$/.test(s)),'INVALID_RETURNED_ID');
  stage='SAVE_RESULT';const status=result.confirmed&&ids.length?'CREATED':result.rejected?'FAILED':'VERIFICATION_REQUIRED';
  const supplierStatus=status==='CREATED'?'CREATED_UNPAID_UNVERIFIED':status==='FAILED'?'REJECTED':'UNKNOWN';
  const diagnostic=status==='CREATED'?`AliExpress API product: ${costs.currency||'unknown'} ${costs.productTotal??'unknown'}; freight/taxes/total unknown. Supplier payment manual.`:
    `AliExpress: ${result.providerError?.code||'RESULT_UNKNOWN'}. No automatic retry; reconcile supplier before resending.`;
  const saved=sql(`SELECT id FROM orders WHERE id=21 FOR UPDATE;
    WITH saved AS(UPDATE order_fulfillments SET status=${literal(status)},supplier_status=${literal(supplierStatus)},supplier_order_id=${ids.length?literal(ids[0]):'NULL'},
      supplier_order_ids=${literal(JSON.stringify(ids))}::jsonb,last_error=${literal(diagnostic)},processing_token=NULL,
      created_externally_at=${status==='CREATED'?'NOW()':'NULL'},updated_at=NOW() WHERE order_id=21 AND processing_token=${literal(claimToken)} AND status='VERIFICATION_REQUIRED' RETURNING id)
    SELECT json_build_object('saved',count(*)) FROM saved;
    INSERT INTO manual_supplier_order_refs(fulfillment_id,supplier_order_id) SELECT f.id,x FROM order_fulfillments f CROSS JOIN jsonb_array_elements_text(f.supplier_order_ids) x WHERE f.order_id=21 ON CONFLICT DO NOTHING;
    ${status==='CREATED'?"UPDATE orders SET status='PROCESSING',processing_at=NOW(),updated_at=NOW() WHERE id=21 AND status='PAID';":''}`,true);
  requireTrue(saved.saved===1,'SAVE_NOT_CONFIRMED_RECONCILE_ONLY');
  const observation=[];
  if(status==='CREATED')for(const id of ids){stage='SUPPLIER_LOOKUP';try{observation.push(bridge({action:'query',supplierId:id}));}catch{observation.push({orderId:id,state:'UNKNOWN',verified:false,checkedAt:new Date().toISOString(),failureCode:'LOOKUP_FAILED'});}}
  if(observation.length){
    stage='SAVE_LOOKUP';const states=[...new Set(observation.map(p=>p.state))];
    // Production still uses the legacy Part record: preserve its exact JSON contract until publication.
    const sync={token:null,startedAt:new Date().toISOString(),checkedAt:new Date().toISOString(),parts:observation.map(({costs,...part})=>part)};
    const totals=observation.map(p=>p.costs).filter(Boolean);
    if(totals.length===ids.length&&totals.every(c=>c.total!=null&&c.currency===totals[0].currency))Object.assign(costs,{source:'SUPPLIER_ORDER_API',currency:totals[0].currency,total:totals.reduce((s,c)=>s+c.total,0)});
    sql(`WITH saved AS(UPDATE order_fulfillments SET supplier_status=${literal(states.length===1?states[0]:'MIXED')},supplier_sync=${literal(JSON.stringify(sync))}::jsonb,
      last_error=${literal(`AliExpress API costs: product=${costs.productTotal??'unknown'}; total=${costs.total??'unknown'} ${costs.currency||''}; freight/taxes unknown. Supplier payment manual.`)},updated_at=NOW()
      WHERE order_id=21 AND status='CREATED' AND supplier_order_ids=${literal(JSON.stringify(ids))}::jsonb RETURNING order_id) SELECT json_build_object('saved',count(*)) FROM saved`,true);
  }
  const evidence={...safe,created:status==='CREATED',status,supplierOrderIds:ids,providerError:result.providerError||null,observation,costs,checkedAt:new Date().toISOString()};
  fs.writeFileSync(path.join(ROOT,`target/order21-audit/authorized-create-attempt-${previousAttempts+1}.json`),JSON.stringify(evidence,null,2));
  fs.writeFileSync(path.join(ROOT,'target/order21-audit/authorized-create-result.json'),JSON.stringify(evidence,null,2));console.log(JSON.stringify(evidence,null,2));
}
if(require.main===module)run().catch(()=>{console.error(JSON.stringify({error:'ORDER21_OPERATION_STOPPED',stage,action:'Check durable database state before any further submission; no automatic retry.'}));process.exitCode=1;});
module.exports={technical,payload,cpfValid};
