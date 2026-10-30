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