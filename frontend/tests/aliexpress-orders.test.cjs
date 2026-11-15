/* eslint-disable @typescript-eslint/no-require-imports -- Isolated TypeScript loader for Node tests. */
const { test, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path'); const fs = require('node:fs'); const Module = require('node:module');
const ts = require('typescript'); const React = require('react'); const { renderToStaticMarkup } = require('react-dom/server');
function load(relative, mocks = {}) {
  const filename = path.resolve(__dirname, '..', relative); const mod = new Module(filename, module);
  mod.filename = filename; mod.paths = Module._nodeModulePaths(path.dirname(filename));
  mod.require = (id) => {
    if (id in mocks) return mocks[id];
    if (id.startsWith('@/')) { const target = id.slice(2); return load(`${target}${fs.existsSync(path.resolve(__dirname, '..', `${target}.ts`)) ? '.ts' : '.tsx'}`, mocks); }
    return Module.prototype.require.call(mod, id);
  };
  mod._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText, filename);
  return mod.exports;
}
const originalFetch = global.fetch;
afterEach(() => { global.fetch = originalFetch; });
const mocks = { 'next/navigation': { useRouter: () => ({ replace() {} }) }, '@/context/AuthContext': { useAuth: () => ({ logout() {} }) } };
const state = { orderId: 123, applicable: true, paymentConfirmed: true, canSend: true, status: 'PENDING', blockReason: null,
  supplierStatus: null, supplierOrderIds: [], lastError: null, submittedAt: null, createdExternallyAt: null };
test('manual API calls use ADMIN token and never forward client payment, address or personal data', async () => {
  const api = load('lib/api/admin/orders.ts', { '@/lib/auth/tokenStorage': { getToken: () => 'test-admin-token' } });
  const calls = [];
  global.fetch = async (url, init) => { calls.push({ url, init }); return Response.json(state); };
  await api.getAdminAliExpressOrder(123); await api.sendAdminAliExpressOrder(123); await api.syncAdminAliExpressOrder(123);
  assert.ok(calls[0].url.endsWith('/api/admin/orders/123/aliexpress'));
  assert.ok(calls[1].url.endsWith('/api/admin/orders/123/aliexpress/send'));
  assert.equal(calls[1].init.method, 'POST'); assert.equal(calls[1].init.headers.Authorization, 'Bearer test-admin-token');
  assert.equal(calls[1].init.body, undefined);
  assert.ok(calls[2].url.endsWith('/api/admin/orders/123/aliexpress/sync'));
  assert.equal(calls[2].init.method, 'POST'); assert.equal(calls[2].init.body, undefined);
  global.fetch = async () => new Response('private-provider-response token-secret', { status: 500 });
  await assert.rejects(api.sendAdminAliExpressOrder(123), (error) => { assert.doesNotMatch(error.message, /private|secret/); return true; });
});
test('split supplier orders show every exact ID and independent verified status', () => {
  const { AliExpressOrderState } = load('components/admin/AliExpressOrderAction.tsx', mocks);
  const ids=['90000000000000001','90000000000000002','90000000000000003','90000000000000004'];
  const parts=ids.map((orderId,i)=>({orderId,state:['AWAITING_MANUAL_PAYMENT','PAYMENT_CONFIRMED','AWAITING_SHIPMENT','SHIPPED'][i],verified:true,checkedAt:'2026-10-30T12:00:00Z'}));
  const html=renderToStaticMarkup(React.createElement(AliExpressOrderState,{state:{...state,status:'CREATED',supplierOrderIds:ids,supplierSync:{checkedAt:'2026-10-30T12:00:00Z',inProgress:false,parts}}}));
  for(const id of ids) assert.ok(html.includes(id));
  for(const label of ['Aguardando pagamento manual','Pagamento ao fornecedor confirmado','Aguardando envio','Enviado']) assert.ok(html.includes(label));
  assert.ok(html.includes('Consultado em'));assert.ok(!html.includes('Marcar como pago'));
});
test('failed lookup cannot visually confirm payment and old results are identified during sync', () => {
  const { AliExpressOrderState } = load('components/admin/AliExpressOrderAction.tsx', mocks);
  const html=renderToStaticMarkup(React.createElement(AliExpressOrderState,{state:{...state,status:'CREATED',supplierOrderIds:['123'],supplierSync:{checkedAt:'2026-10-30T12:00:00Z',inProgress:true,parts:[{orderId:'123',state:'PAYMENT_CONFIRMED',verified:false,checkedAt:'2026-10-30T12:00:00Z'}]}}}));
  assert.ok(html.includes('Status desconhecido'));assert.ok(html.includes('desatualizados'));
  assert.ok(!html.includes('Pagamento ao fornecedor confirmado'));
});
test('created, rejected and ambiguous states show persisted supplier status without claiming delivery', () => {
  const { AliExpressOrderState } = load('components/admin/AliExpressOrderAction.tsx', mocks);
  const created = renderToStaticMarkup(React.createElement(AliExpressOrderState, { state: { ...state, status: 'CREATED' } }));
  assert.match(created, /Enviado ao fornecedor/); assert.match(created, /Aguardando pagamento manual/);
  assert.doesNotMatch(created, /Entregue|Despachado|Confirmar envio/);
  const uncertain = renderToStaticMarkup(React.createElement(AliExpressOrderState, { state: { ...state, status: 'VERIFICATION_REQUIRED' } }));
  assert.match(uncertain, /Verificação necessária/); assert.match(uncertain, /Reenvio bloqueado/);
});
function find(tree, predicate) {
  if (!tree || typeof tree !== 'object') return null;
  if (predicate(tree)) return tree;
  for (const child of React.Children.toArray(tree.props?.children)) { const found = find(child, predicate); if (found) return found; }
  return null;
}
async function harness(initial, fails = false) {
  const states = [], refs = []; let cursor, refCursor, first = true, calls = 0, resolve, reject, updated = 0, syncCalls = 0, resolveSync;
  const effects = [];
  const { AliExpressOrderAction } = load('components/admin/AliExpressOrderAction.tsx', { ...mocks,
    react: { ...React,
      useState(value) { const index = cursor++; if (!(index in states)) states[index] = value; return [states[index], (next) => { states[index] = next; }]; },
      useRef(value) { const index = refCursor++; return refs[index] ??= { current: value }; },
      useEffect(effect) { if (first) effects.push(effect); },
    },
    '@/lib/api/admin/orders': {
      AdminOrderApiError: class extends Error {},
      getAdminAliExpressOrder: async () => calls && fails ? { ...initial, status: 'VERIFICATION_REQUIRED', canSend: false } : initial,
      sendAdminAliExpressOrder() { calls++; return new Promise((res, rej) => { resolve = res; reject = rej; }); },
      syncAdminAliExpressOrder() { syncCalls++; return new Promise(res => { resolveSync = res; }); },
    },
  });
  function render() { cursor = 0; refCursor = 0; const tree = AliExpressOrderAction({ orderId: 123, onUpdated: () => updated++ }); if (first) { first = false; effects.forEach((effect) => effect()); } return tree; }
  render(); await Promise.resolve();
  return { render, calls: () => calls, updated: () => updated, resolve: (value) => resolve(value), reject: () => reject(new Error('timeout')),
    syncCalls: () => syncCalls, resolveSync: value => resolveSync(value) };
}

test('supplier sync waits for backend proof, blocks duplicate clicks and never calls creation', async () => {
  const h=await harness({...state,status:'CREATED',canSend:false,paymentConfirmed:false,supplierOrderIds:['123']});
  const button=find(h.render(),node=>node.type==='button' && node.props.children==='Atualizar situação no AliExpress');
  button.props.onClick();button.props.onClick();assert.equal(h.syncCalls(),1);assert.equal(h.calls(),0);
  const before=find(h.render(),node=>node.type?.name==='AliExpressOrderState');
  assert.equal(before.props.state.supplierSync,undefined);
  h.resolveSync({...state,status:'CREATED',canSend:false,supplierOrderIds:['123'],supplierSync:{checkedAt:'2026-10-30T12:00:00Z',inProgress:false,parts:[{orderId:'123',state:'PAYMENT_CONFIRMED',verified:true,checkedAt:'2026-10-30T12:00:00Z'}]}});
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(h.updated(),1);assert.equal(h.calls(),0);
  assert.equal(find(h.render(),node=>node.type?.name==='AliExpressOrderState').props.state.supplierSync.parts[0].verified,true);
});
test('pending payments have no send button and missing prerequisites disable the paid action', async () => {
  const unpaid = await harness({ ...state, paymentConfirmed: false }); assert.equal(unpaid.render(), null);
  const blocked = await harness({ ...state, canSend: false, blockReason: 'CPF e frete não confirmados.' });
  assert.equal(find(blocked.render(), (node) => node.type === 'button' && node.props.children === 'Enviar ao AliExpress').props.disabled, true);
});

test('financial alerts permit creation and unknown costs are never displayed as zero', () => {
  const { AliExpressOrderState, AliExpressDispatchDetails } = load('components/admin/AliExpressOrderAction.tsx', mocks);
  const blocked={...state,canSend:true,blockReason:null,attemptCount:0,maxAttempts:3,
    validationAttemptCount:2,nextValidationAt:'2026-11-15T15:00:00Z',automaticEnabled:false,dispatchQuote:null,
    financialWarnings:['Impostos desconhecidos.','Custo superior ao valor recebido.'],
    supplierCosts:{source:'LIVE_PRODUCT_API',currency:'BRL',productTotal:100,shippingTotal:null,taxTotal:null,total:null,observedAt:null}};
  const html=renderToStaticMarkup(React.createElement(React.Fragment,null,
    React.createElement(AliExpressOrderState,{state:blocked}),React.createElement(AliExpressDispatchDetails,{state:blocked})));
  assert.match(html,/Aguardando criação automática/);assert.match(html,/Alertas financeiros — criação permitida/);
  assert.match(html,/Desconhecido/);assert.doesNotMatch(html,/Criação automática bloqueada|R\$[^0-9<]*0,00/);
  assert.match(html,/Tentativas de criação: 0\/3/);assert.match(html,/Validações: 2/);assert.match(html,/Nova validação prevista/);
  assert.match(html,/Processamento automático desativado/);assert.match(html,/exclusivamente manual/);
});

test('uncertain response retains supplier references and does not claim successful creation', () => {
  const { AliExpressOrderState } = load('components/admin/AliExpressOrderAction.tsx', mocks);
  const html=renderToStaticMarkup(React.createElement(AliExpressOrderState,{state:{...state,status:'VERIFICATION_REQUIRED',supplierOrderIds:['99112'],lastError:'Resultado incerto.'}}));
  assert.match(html,/99112/);assert.match(html,/Resultado incerto/);assert.doesNotMatch(html,/Enviado ao fornecedor/);
});

test('refreshing automatic dispatch uses only the read endpoint', async () => {
  const h=await harness({...state,canSend:false});
  button(h.render(),'Atualizar acompanhamento').props.onClick();
  await new Promise(resolve=>setImmediate(resolve));
  assert.equal(h.calls(),0);assert.equal(h.syncCalls(),0);assert.equal(h.updated(),1);
});
test('paid send requires confirmation, blocks duplicate clicks and refreshes backend states', async () => {
  const h = await harness(state);
  let tree = h.render(); find(tree, (node) => node.type === 'button' && node.props.children === 'Enviar ao AliExpress').props.onClick();
  assert.equal(h.calls(), 0);
  tree = h.render(); const confirm = find(tree, (node) => node.type === 'button' && node.props.children === 'Confirmar envio');
  confirm.props.onClick(); confirm.props.onClick(); assert.equal(h.calls(), 1);
  h.resolve({ ...state, status: 'CREATED', canSend: false }); await new Promise((res) => setImmediate(res));
  assert.equal(h.updated(), 1);
  assert.equal(find(h.render(), (node) => node.type === 'button' && node.props.children === 'Enviar ao AliExpress'), null);
});
test('browser timeout fetches persisted state and never enables a second purchase', async () => {
  const h = await harness(state, true);
  find(h.render(), (node) => node.type === 'button' && node.props.children === 'Enviar ao AliExpress').props.onClick();
  find(h.render(), (node) => node.type === 'button' && node.props.children === 'Confirmar envio').props.onClick();
  h.reject(); await new Promise((res) => setImmediate(res));
  assert.equal(h.calls(), 1);
  assert.equal(find(h.render(), (node) => node.type === 'button' && node.props.children === 'Enviar ao AliExpress'), null);
});

const testState = { orderId: 19, testOrder: true, simulatedPaymentAt: null, simulatedPaymentBy: null,
  simulatedSupplierAt: null, simulatedSupplierId: null, simulatedSupplierBy: null,
  canCreateReal: false, realBlockers: ['Criação administrativa desabilitada na configuração.'], fixture: null,
  realStatus: null, idempotencyKey: null, submittedAt: null, createdAt: null, submittedBy: null, supplierOrderIds: [] };

test('test endpoints isolate simulated actions and send only explicit confirmation for real action', async () => {
  const api = load('lib/api/admin/orders.ts', { '@/lib/auth/tokenStorage': { getToken: () => 'test-admin-token' } });
  const calls = [];
  global.fetch = async (url, init) => { calls.push({ url, init }); return Response.json(testState); };
  await api.runAdminAliExpressTest(123, 'simulate-payment');
  await api.runAdminAliExpressTest(123, 'simulate-supplier');
  const confirmation = { phrase: 'explicit', idempotencyKey: 'own-fixture', fixtureDigest: 'digest' };
  await api.runAdminAliExpressTest(123, 'create-real', confirmation);
  assert.ok(calls[0].url.endsWith('/aliexpress-tests/simulate-payment'));
  assert.ok(calls[1].url.endsWith('/aliexpress-tests/simulate-supplier'));
  assert.equal(calls[0].init.body, undefined); assert.equal(calls[1].init.body, undefined);
  assert.equal(calls[2].init.headers.Authorization, 'Bearer test-admin-token');
  assert.deepEqual(JSON.parse(calls[2].init.body), confirmation);
});

async function testHarness(initial) {
  const states = [], refs = [], effects = [], calls = []; let cursor, refCursor, first = true, resolve, reject;
  const { AliExpressTestActions } = load('components/admin/AliExpressTestActions.tsx', {
    react: { ...React,
      useState(value) { const i = cursor++; if (!(i in states)) states[i] = value; return [states[i], next => { states[i] = next; }]; },
      useRef(value) { const i = refCursor++; return refs[i] ??= { current: value }; },
      useEffect(effect) { if (first) effects.push(effect); },
    },
    '@/lib/api/admin/orders': { AdminOrderApiError: class extends Error {},
      getAdminAliExpressTest: async () => initial,
      getAdminAliExpressTestPayload: async () => ({ orderId: initial.orderId, digest: initial.fixture?.digest, costs: initial.fixture, payload: { product_items: [] } }),
      preflightAdminAliExpressTest: async () => ({ orderId: initial.orderId, digest: initial.fixture?.digest, costs: initial.fixture, payload: { logistics_address: { full_name: 'Owner Name', address: 'Authorized Street, 19' } }, preflightId: 'server-review-19', buyerAccount: 'Existing OAuth account', buyerState: 'UNKNOWN', warnings: [], productCostSource: 'LIVE_PRODUCT_READ', inGarageProductTotal: 50, checkedAt: new Date().toISOString(), validUntil: new Date(Date.now()+120000).toISOString() }),
      runAdminAliExpressTest(id, action, confirmation) { calls.push({ id, action, confirmation }); return new Promise((res, rej) => { resolve = res; reject = rej; }); },
    },
  });
  function render() { cursor = 0; refCursor = 0; const tree = AliExpressTestActions({ orderId: initial.orderId }); if (first) { first = false; effects.forEach(effect => effect()); } return tree; }
  render(); await Promise.resolve();
  return { render, calls, resolve: value => resolve(value), reject: () => reject(new Error('timeout')) };
}
const button = (tree, label) => find(tree, node => node.type === 'button' && node.props.children === label);

test('only identified test orders show test actions and real action is blocked by backend readiness', async () => {
  const unmarked = await testHarness({ ...testState, orderId:20, testOrder: false }); assert.equal(unmarked.render(), null);
  const h = await testHarness(testState);
  assert.equal(button(h.render(), 'Simular pagamento').props.disabled, false);
  assert.equal(button(h.render(), 'Executar AliExpress simulado').props.disabled, true);
  assert.equal(button(h.render(), 'Criar pedido real no AliExpress').props.disabled, true);
  button(h.render(), 'Simular pagamento').props.onClick();
  button(h.render(), 'Simular pagamento').props.onClick();
  assert.equal(h.calls.length, 1); assert.equal(h.calls[0].action, 'simulate-payment');
  h.resolve({ ...testState, simulatedPaymentAt: '2026-11-04T12:00:00Z', simulatedPaymentBy: 'admin' });
  await new Promise(res => setImmediate(res));
  assert.equal(button(h.render(), 'Executar AliExpress simulado').props.disabled, false);
  assert.equal(button(h.render(), 'Criar pedido real no AliExpress').props.disabled, true);
});

test('real test requires phrase and fixture binding and a browser timeout blocks repeated purchase', async () => {
  const h = await testHarness({ ...testState, canCreateReal: true, realBlockers: [], fixture: {
    id: 'owned-fixture', digest: 'fixture-digest', authorizationReference: 'owned-test-data',
  } });
  assert.equal(button(h.render(), 'Criar pedido real no AliExpress').props.disabled, false);
  button(h.render(), 'Criar pedido real no AliExpress').props.onClick();
  await new Promise(res => setImmediate(res));
  assert.equal(h.calls.length, 0);
  assert.equal(button(h.render(), 'Confirmar criação real não paga').props.disabled, true);
  find(h.render(), node => node.type === 'input' && node.props.type === 'text').props.onChange({ target: { value: 'CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS' } });
  find(h.render(), node => node.type === 'input' && node.props.type === 'checkbox').props.onChange({ target: { checked: true } });
  const confirm = button(h.render(), 'Confirmar criação real não paga');
  assert.equal(confirm.props.disabled, false); confirm.props.onClick(); confirm.props.onClick();
  assert.equal(h.calls.length, 1);
  assert.deepEqual(h.calls[0].confirmation, { phrase: 'CRIAR PEDIDO REAL DE TESTE NÃO PAGO NO ALIEXPRESS', idempotencyKey: 'owned-fixture', fixtureDigest: 'fixture-digest', preflightId: 'server-review-19', acceptKnownAndUnknownCosts: true });
  h.reject(); await new Promise(res => setImmediate(res));
  assert.equal(button(h.render(), 'Criar pedido real no AliExpress').props.disabled, true);
});

test('test order lookup shows awaiting payment only from verified supplier observation', async () => {
  const initial = { ...testState, realStatus: 'CREATED_UNPAID_UNVERIFIED', supplierOrderIds: ['900001'],
    supplierSync: { checkedAt: '2026-11-05T20:00:00Z', inProgress: false, parts: [
      { orderId: '900001', state: 'AWAITING_MANUAL_PAYMENT', verified: true, checkedAt: '2026-11-05T20:00:00Z' },
    ] } };
  const h = await testHarness(initial);
  let html = renderToStaticMarkup(h.render());
  assert.match(html, /Aguardando pagamento manual confirmado por consulta/);
  assert.match(html, /900001/); assert.match(html, /Última consulta real/);
  button(h.render(), 'Consultar pedido real de teste').props.onClick();
  assert.equal(h.calls[0].action, 'sync');
  h.resolve({ ...initial, supplierSync: { ...initial.supplierSync, parts: [{ ...initial.supplierSync.parts[0], verified: false, state: 'UNKNOWN' }] } });
  await new Promise(res => setImmediate(res));
  html = renderToStaticMarkup(h.render());
  assert.doesNotMatch(html, /Aguardando pagamento manual confirmado por consulta/);
  assert.match(html, /Pagamento ao fornecedor desconhecido/);
});

 test('other identified administrative orders never expose the real purchase button', async () => {
  const h=await testHarness({...testState, orderId:20, canCreateReal:true});
  assert.equal(button(h.render(),'Criar pedido real no AliExpress'),null);
  assert.equal(button(h.render(),'Confirmar criação real não paga'),null);
});

test('order19 shows a disabled real button and exact blockers before private configuration', async () => {
  const h=await testHarness({...testState,testOrder:false});
  assert.equal(button(h.render(),'Criar pedido real no AliExpress').props.disabled,true);
  assert.equal(button(h.render(),'Simular pagamento'),null);
});

test('review preserves unknown costs, higher supplier price and unknown buyer without fictitious zero totals',()=>{
  const {AliExpressRealReview}=load('components/admin/AliExpressTestActions.tsx');
  const html=renderToStaticMarkup(React.createElement(AliExpressRealReview,{review:{orderId:19,buyerState:'UNKNOWN',buyerAccount:'Existing OAuth account',payload:{},costs:{productId:'1005000000000000',skuId:'123456',quantity:1,deliveryService:null,currency:'BRL',productCost:125,shippingCost:null,taxCost:null,totalCost:null},inGarageProductTotal:50,productCostSource:'LIVE_PRODUCT_READ',warnings:[],checkedAt:null,validUntil:null}}));
  assert.match(html,/Desconhecido/);assert.match(html,/125,00/);assert.match(html,/50,00/);assert.doesNotMatch(html,/>R\$[^0-9<]*0,00<\//);
});

test('official sanitized rejection is displayed with code and message and blocks another create',async()=>{
 const h=await testHarness({...testState,realStatus:'REJECTED_NO_RETRY',providerError:{code:'29',subCode:'isv.permission-denied',message:'Insufficient permissions',httpStatus:403}});
 const html=renderToStaticMarkup(h.render());assert.match(html,/29/);assert.match(html,/Insufficient permissions/);assert.doesNotMatch(html,/NOT_VERIFIED/);assert.equal(button(h.render(),'Criar pedido real no AliExpress').props.disabled,true);
});

const recipientState={orderId:123,maskedCpf:'***.***.***-25',birthDatePresent:true,phonePresent:true,complete:true,pendingFields:[],
  canEdit:true,canReconcile:false,manualHold:false,manualRetryReady:false,attemptCount:1,lastAttemptAt:'2026-11-15T12:00:00Z',contextDigest:'a'.repeat(64),recipientRevision:null,correctedAt:null};
test('recipient endpoints use ADMIN authentication, no cache and fixed private-data errors',async()=>{
  const api=load('lib/api/admin/orders.ts',{'@/lib/auth/tokenStorage':{getToken:()=> 'synthetic-admin-token'}});const calls=[];
  global.fetch=async(url,init)=>{calls.push({url,init});return Response.json(recipientState);};
  await api.getAdminAliExpressRecipient(123);
  const edit={cpf:'11144477735',birthDate:'02/02/1992',expectedContext:recipientState.contextDigest};
  await api.updateAdminAliExpressRecipient(123,edit);
  await api.reconcileAdminAliExpressRecipient(123,{expectedContext:recipientState.contextDigest,expectedAttemptCount:1,recipientRevision:1,recipientDataConfirmed:true,allPreviousAttemptsChecked:true,noSupplierOrderConfirmed:true});
  assert.equal(calls.length,4);assert.equal(calls[0].init.cache,'no-store');assert.equal(calls[1].init.method,'PATCH');
  assert.deepEqual(JSON.parse(calls[1].init.body),edit);assert.ok(calls[2].url.endsWith('/recipient/reconcile'));
  assert.ok(calls.every(c=>c.init.headers.Authorization==='Bearer synthetic-admin-token'&&!c.url.endsWith('/send')));
  global.fetch=async()=>Response.json({message:'11144477735 02/02/1992 private-secret'},{status:400});
  await assert.rejects(api.updateAdminAliExpressRecipient(123,edit),e=>{assert.doesNotMatch(e.message,/11144477735|02\/02\/1992|private-secret/);return true;});
});
test('recipient summary reveals only masked CPF and whether birth date exists',()=>{
  const {AliExpressRecipientSummary}=load('components/admin/AliExpressRecipientEditor.tsx',mocks);
  const html=renderToStaticMarkup(React.createElement(AliExpressRecipientSummary,{value:recipientState}));
  assert.match(html,/\*\*\*\.\*\*\*\.\*\*\*-25/);assert.match(html,/Cadastrada/);assert.doesNotMatch(html,/52998224725|1990-01-01/);
});
async function recipientHarness(initial){
  const states=[],refs=[],effects=[];let cursor,refCursor,first=true,updates=0,saves=0,reconciliations=0,submitted;
  const {AliExpressRecipientEditor}=load('components/admin/AliExpressRecipientEditor.tsx',{...mocks,
    react:{...React,useState(v){const n=cursor++;if(!(n in states))states[n]=v;return[states[n],v=>{states[n]=v;}];},
      useRef(v){const n=refCursor++;return refs[n]??={current:v};},useEffect(effect){if(first)effects.push(effect);}},
    '@/lib/api/admin/orders':{AdminOrderApiError:class extends Error{},getAdminAliExpressRecipient:async()=>initial,
      updateAdminAliExpressRecipient:async(id,data)=>{saves++;submitted=data;return{...initial,manualHold:true,canReconcile:true,recipientRevision:2};},
      reconcileAdminAliExpressRecipient:async()=>{reconciliations++;return{...initial,manualHold:true,canReconcile:false,manualRetryReady:true};}}});
  function render(){cursor=0;refCursor=0;const tree=AliExpressRecipientEditor({orderId:123,onUpdated:()=>updates++});if(first){first=false;effects.forEach(e=>e());}return tree;}
  render();await Promise.resolve();return{render,saves:()=>saves,reconciliations:()=>reconciliations,submitted:()=>submitted,updates:()=>updates};
}
test('saving correction clears personal form values and does not release or create an order',async()=>{
  const h=await recipientHarness(recipientState);button(h.render(),'Corrigir CPF e nascimento').props.onClick();
  find(h.render(),n=>n.type==='input'&&n.props.name==='recipientCpf').props.onChange({target:{value:'11144477735'}});
  find(h.render(),n=>n.type==='input'&&n.props.name==='recipientBirthDate').props.onChange({target:{value:'02/02/1992'}});
  const form=find(h.render(),n=>n.type==='form');const e={preventDefault(){}};form.props.onSubmit(e);form.props.onSubmit(e);
  await new Promise(res=>setImmediate(res));assert.equal(h.saves(),1);assert.equal(h.reconciliations(),0);assert.equal(h.updates(),1);
  assert.equal(find(h.render(),n=>n.type==='form'),null);button(h.render(),'Corrigir CPF e nascimento').props.onClick();
  assert.equal(find(h.render(),n=>n.type==='input'&&n.props.name==='recipientCpf').props.value,'');
  assert.equal(find(h.render(),n=>n.type==='input'&&n.props.name==='recipientBirthDate').props.value,'');
});
test('manual reconciliation requires both explicit checks and sends no supplier creation',async()=>{
  const h=await recipientHarness({...recipientState,manualHold:true,canReconcile:true,recipientRevision:2});
  const label='Registrar reconciliação e liberar envio manual';assert.equal(button(h.render(),label).props.disabled,true);
  const checks=[];function collect(t){if(!t||typeof t!=='object')return;if(t.type==='input'&&t.props.type==='checkbox')checks.push(t);React.Children.toArray(t.props?.children).forEach(collect);}collect(h.render());
  checks.forEach(c=>c.props.onChange({target:{checked:true}}));const confirm=button(h.render(),label);assert.equal(confirm.props.disabled,false);
  confirm.props.onClick();confirm.props.onClick();await new Promise(res=>setImmediate(res));assert.equal(h.reconciliations(),1);assert.equal(h.saves(),0);
  assert.equal(button(h.render(),label),null);
});
