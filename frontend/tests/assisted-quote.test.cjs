/* eslint-disable @typescript-eslint/no-require-imports -- Isolated TS loader for the existing test runner. */
const {test,afterEach}=require('node:test');const assert=require('node:assert/strict');
const fs=require('node:fs');const path=require('node:path');const Module=require('node:module');
const ts=require('typescript');const React=require('react');const {renderToStaticMarkup}=require('react-dom/server');
function load(relative,mocks={}) {
  const filename=path.resolve(__dirname,'..',relative);const mod=new Module(filename,module);
  mod.filename=filename;mod.paths=Module._nodeModulePaths(path.dirname(filename));
  mod.require=id=>id in mocks?mocks[id]:id.startsWith('@/')?load(id.slice(2)+'.ts',mocks):Module.prototype.require.call(mod,id);
  mod._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename);return mod.exports;
}
const originalFetch=global.fetch;afterEach(()=>{global.fetch=originalFetch;});
const base={id:7,subtotal:100,status:'PENDING_PAYMENT',quote:{state:'WAITING',revision:0,validUntil:null},items:[{supplierVariantId:'12001'},{supplierVariantId:'12002'}]};
function panel(order=base,fulfillment={status:'PENDING'}) {
 const {AssistedQuotePanel}=load('components/admin/AssistedQuotePanel.tsx',{'@/lib/api/admin/orders':{}});
 return renderToStaticMarkup(React.createElement(AssistedQuotePanel,{order,fulfillment,onUpdated:async()=>{}}));
}
test('admin quote form covers each SKU and requires complete address, taxes, logistics and evidence',()=>{
 const html=panel();for(const text of ['12001','12002','Código logístico','Custo total do fornecedor','impostos','Referência da evidência','Confirmar cotação'])assert.ok(html.includes(text),text);
 assert.ok(html.includes('disabled'));assert.ok(!html.includes('Registrar pedidos criados manualmente'));
});
test('cost hold exposes manual refund recording and removes supplier forwarding actions',()=>{
 const html=panel({...base,status:'PAID',quote:{...base.quote,state:'REFUND_REQUIRED'}});
 assert.ok(html.includes('reembolso integral'));assert.ok(html.includes('não movimenta dinheiro'));
 assert.ok(!html.includes('Reservar encaminhamento'));assert.ok(!html.includes('Registrar pedidos criados'));
});
test('manual claim has a persistent reconciliation screen and never offers a second claim',()=>{
 const html=panel({...base,status:'PAID',quote:{...base.quote,state:'ACCEPTED'}},{status:'VERIFICATION_REQUIRED'});
 assert.ok(html.includes('não repita a compra'));assert.ok(html.includes('Registrar pedidos criados manualmente'));
 assert.ok(!html.includes('Reservar encaminhamento manual único'));
});
test('customer acceptance posts revision and exact displayed total with authentication',async()=>{
 const calls=[];global.fetch=async(url,init)=>{calls.push({url,init});return Response.json({id:7,quote:{state:'ACCEPTED'}});};
 const api=load('lib/api/orders.ts',{'@/lib/auth/tokenStorage':{getToken:()=> 'test-token'}});
 const result=await api.acceptOrderQuote(7,1,124);
 assert.equal(result.quote.state,'ACCEPTED');assert.equal(calls.length,1);
 assert.ok(calls[0].url.endsWith('/api/orders/7/quote/accept'));assert.equal(calls[0].init.headers.Authorization,'Bearer test-token');
 assert.deepEqual(JSON.parse(calls[0].init.body),{revision:1,expectedTotal:124});
});
test('valid decimal observations are accepted diagnostically without payment authorization',()=>{
 const helper=load('lib/admin/aliexpress-freight.ts');
 const result=helper.parseFreightDiagnostic({status:'MONEY_SCALE_UNCONFIRMED',safeCode:'AMBIGUOUS_MONETARY_UNIT',methods:[],monetaryStructure:[{optionIndex:0,path:'shipping_fee_cent',present:true,jsonType:'STRING',currency:'BRL',documentedScale:'CENT_1_100_DESCRIPTION_EXAMPLE_CONFLICT',safeValue:'24.00'}]},'test-token');
 assert.equal(result.status,'MONEY_SCALE_UNCONFIRMED');assert.equal(result.methods.length,0);assert.equal(result.monetaryStructure[0].safeValue,'24.00');
});

test('controlled publication hides payment action by default and when explicitly disabled',()=>{
 const previous=process.env.NEXT_PUBLIC_PAYMENT_CREATION_ENABLED;
 try {
  for(const value of [undefined,'false']) {
   if(value===undefined) delete process.env.NEXT_PUBLIC_PAYMENT_CREATION_ENABLED;
   else process.env.NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=value;
   const {MercadoPagoPayment}=load('components/MercadoPagoPayment.tsx',{'@/lib/api/payments':{}});
   const html=renderToStaticMarkup(React.createElement(MercadoPagoPayment,{orderId:7}));
   assert.ok(html.includes('Nenhuma cobrança foi iniciada'));
   assert.ok(!html.includes('Ir para pagamento'));
   assert.ok(!html.includes('<button'));
  }
 } finally {
  if(previous===undefined) delete process.env.NEXT_PUBLIC_PAYMENT_CREATION_ENABLED;
  else process.env.NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=previous;
 }
});
test('freight observations prefill metadata but never reinterpret decimal money',()=>{
 const helper=load('lib/admin/assisted-freight.ts');
 const leg={code:'',name:'',originCountry:'',shippingBrl:'',estimatedDays:'',skuIds:['456']};
 const option={optionIndex:0,code:'CAINIAO_STANDARD',name:'AliExpress Standard Shipping',originCountry:'CN',estimatedDays:25,receivedAmount:'24.00',formattedAmount:'24.00',currency:'BRL',documentedScale:'CONFLICT',monetaryConsistency:'MAJOR_MATCH',shippingBrl:null};
 const filled=helper.prefillObservedLeg(leg,option);
 assert.equal(filled.code,'CAINIAO_STANDARD');assert.equal(filled.originCountry,'CN');assert.equal(filled.estimatedDays,'25');assert.equal(filled.shippingBrl,'');
 assert.equal(helper.prefillObservedLeg(leg,{...option,shippingBrl:0}).shippingBrl,'0');
 assert.deepEqual(helper.prefillObservedLeg({...filled,shippingBrl:'30.00'},option),{...filled,shippingBrl:'30.00'});
 assert.deepEqual(helper.prefillObservedLeg({...leg,code:'MANUAL'},option),{...leg,code:'MANUAL'});
 assert.deepEqual(helper.prefillObservedLeg({...leg,skuIds:['456','789']},option),{...leg,skuIds:['456','789']});
 const preview={reviewBefore:new Date(Date.now()+60000).toISOString(),lines:[{skuId:'456',options:[option]}]};
 assert.deepEqual(helper.prefillUnambiguousLegs([leg],preview),[filled]);
 assert.deepEqual(helper.prefillUnambiguousLegs([leg],{...preview,lines:[{skuId:'456',options:[option,{...option,code:'OTHER'}]}]}),[leg]);
 assert.deepEqual(helper.prefillUnambiguousLegs([leg],{...preview,reviewBefore:'2000-01-01T00:00:00Z'}),[leg]);
});

test('suggestions use authenticated admin endpoint and only the internal order id',async()=>{
 const calls=[];global.fetch=async(url,init)=>{calls.push({url,init});return Response.json({orderId:7,commercialAuthorized:false});};
 const api=load('lib/api/admin/orders.ts',{'@/lib/auth/tokenStorage':{getToken:()=> 'test-token'}});
 const result=await api.getAssistedFreightSuggestions(7);
 assert.equal(result.commercialAuthorized,false);assert.equal(calls[0].init.method,'POST');
 assert.ok(calls[0].url.endsWith('/api/admin/orders/7/quote/suggestions'));
 assert.equal(calls[0].init.headers.Authorization,'Bearer test-token');assert.equal(calls[0].init.body,undefined);
});

test('empty freight remains an unknown total in the admin confirmation form',()=>{
 const html=panel();assert.ok(html.includes('Consulta automática AliExpress'));assert.ok(html.includes('a confirmar'));
 assert.ok(html.includes('Atualizar dados do AliExpress'));assert.ok(html.includes('disabled'));
});
test('commercial freight administration covers all UFs without invented prices',()=>{
 const {default:Page}=load('app/admin/fretes/page.tsx',{'next/link':props=>React.createElement('a',props),'@/lib/auth/tokenStorage':{getToken:()=>null},'@/components/admin/BulkFreightReview':{BulkFreightReview:()=>null}});
 const html=renderToStaticMarkup(React.createElement(Page));
 for(const uf of 'AC AL AP AM BA CE DF ES GO MA MT MS MG PA PB PR PE PI RJ RN RS RO RR SC SP SE TO'.split(' '))assert.ok(html.includes('>'+uf+'</th>'));
 assert.equal((html.match(/Sem evidência cadastrada/g)||[]).length,81);
 assert.ok(html.includes('Salvar nova versão em rascunho'));assert.ok(!html.includes('R$ 24'));
});

test('product and cart calculator does not present unknown shipping as free',()=>{
 const {ImmediateFreightCalculator}=load('components/ImmediateFreightCalculator.tsx',{'@/lib/api/shipping':{}});
 const html=renderToStaticMarkup(React.createElement(ImmediateFreightCalculator,{items:[{productId:1,variantId:2,quantity:1}],subtotal:50}));
 assert.ok(html.includes('CEP'));assert.ok(!html.includes('R$'));assert.ok(!html.includes('Grátis'));
});

test('national template creates 81 unknown inactive cells per actual scope without overwrites',()=>{
 const helper=load('lib/admin/national-freight.ts');
 const scope={category:'synthetic-category',supplierStoreId:'synthetic-store',originCountry:'CN'};
 const doc=helper.prepareNational({reviewRows:[]},scope);
 assert.equal(doc.reviewRows.length,81);assert.equal(new Set(doc.reviewRows.map(r=>r.rate.state)).size,27);
 assert.ok(doc.reviewRows.every(r=>!r.approved&&r.rate.supplierFreightPerParcelBrl===null&&r.rate.customerFreightPerParcelBrl===null&&r.rate.evidence===null&&!r.rate.packagingVerified));
 const marked={...doc,reviewRows:doc.reviewRows.map((r,i)=>i===0?{...r,rate:{...r.rate,customerFreightPerParcelBrl:'17.31'}}:r)};
 const again=helper.prepareNational(marked,scope);assert.equal(again.reviewRows.length,81);assert.equal(again.reviewRows[0].rate.customerFreightPerParcelBrl,'17.31');
 assert.equal(helper.prepareNational(again,{...scope,supplierStoreId:'another-synthetic-store'}).reviewRows.length,162);
});

test('national bulk price separates customer charges and clears approvals without inventing supplier costs',()=>{
 const helper=load('lib/admin/national-freight.ts');let doc=helper.prepareNational({reviewRows:[]},{category:'test',supplierStoreId:'test',originCountry:'CN'});
 doc.reviewRows[0].approved=true;const changed=helper.commercialPrice(doc,[0,1],'19.30');
 assert.equal(changed.reviewRows[0].approved,false);assert.equal(changed.reviewRows[0].rate.customerFreightPerParcelBrl,'19.30');
 assert.equal(changed.reviewRows[0].rate.supplierFreightPerParcelBrl,null);assert.equal(changed.reviewRows[2].rate.customerFreightPerParcelBrl,null);
 assert.throws(()=>helper.commercialPrice(doc,[0],'-1'));assert.throws(()=>helper.commercialPrice(doc,[0],'1.001'));
});

test('national imports remove all approvals and reject duplicate or invalid UF selectors',()=>{
 const helper=load('lib/admin/national-freight.ts');const doc=helper.prepareNational({reviewRows:[]},{category:'test',supplierStoreId:'test',originCountry:'CN'});
 doc.reviewRows[0].approved=true;doc.rates=[{state:'SC'}];const imported=helper.importNational(JSON.stringify(doc));
 assert.ok(imported.reviewRows.every(r=>!r.approved));assert.deepEqual(imported.rates,[]);
 assert.throws(()=>helper.importNational(JSON.stringify({...doc,reviewRows:[doc.reviewRows[0],doc.reviewRows[0]]})));
 assert.throws(()=>helper.importNational(JSON.stringify({...doc,reviewRows:[{rate:{...doc.reviewRows[0].rate,state:'XX'}}]})));
 assert.throws(()=>helper.importNational('{}'));
});

test('national mass review exposes import, verification and explicit draft saving',()=>{
 const {BulkFreightReview}=load('components/admin/BulkFreightReview.tsx',{'@/lib/auth/tokenStorage':{getToken:()=>null}});
 const html=renderToStaticMarkup(React.createElement(BulkFreightReview,{versions:[],getBaseDocument:()=>({}),onSaved:async()=>{}}));
 for(const label of ['Importar arquivo JSON','Preparar 27 UFs','Salvar revisão nacional como rascunho','Custos desconhecidos','Aprovar linhas filtradas'])assert.ok(html.includes(label),label);
 assert.ok(!html.includes('Publicar automaticamente'));
});
