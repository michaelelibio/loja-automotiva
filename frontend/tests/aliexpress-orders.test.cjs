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
  assert.equal(find(blocked.render(), (node) => node.type === 'button').props.disabled, true);
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
