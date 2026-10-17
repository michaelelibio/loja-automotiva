/* eslint-disable @typescript-eslint/no-require-imports -- Isolated TS loader for the existing Node test runner. */
const { test, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'); const path = require('node:path'); const Module = require('node:module');
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
const fixture = { status: 'SUCCESS', safeCode: 'CALL_SUCCEEDED', methods: [
  { serviceName: 'CAINIAO_STANDARD', amount: 12.3, currency: 'BRL', estimatedDeliveryTime: '5-8day' },
  { serviceName: 'EXPRESS', amount: 25.39, currency: 'USD', estimatedDeliveryTime: null },
] };
const helper = load('lib/admin/aliexpress-freight.ts');
function api(token = 'synthetic-admin-token') { return load('lib/api/admin/aliexpress.ts', { '@/lib/auth/tokenStorage': { getToken: () => token } }); }

test('freight diagnostic posts once with existing ADMIN auth, no parameters and no order/payment requests', async () => {
  const calls = [];
  global.fetch = async (url, init) => { calls.push({ url, init }); return Response.json({ ...fixture, secret: 'do-not-render', message: 'internal response' }); };
  const result = await api().testAliExpressFreight();
  assert.equal(calls.length, 1); assert.ok(calls[0].url.endsWith('/api/admin/integrations/aliexpress/freight-diagnostic'));
  assert.equal(calls[0].init.method, 'POST'); assert.equal(calls[0].init.body, undefined);
  assert.equal(calls[0].init.cache, 'no-store'); assert.equal(calls[0].init.headers.Authorization, 'Bearer synthetic-admin-token');
  assert.ok(calls[0].init.signal instanceof AbortSignal);
  assert.deepEqual(result, fixture); assert.ok(!('secret' in result));
});

test('missing session, HTTP 401/403/500/503 and network failures produce only fixed safe messages', async () => {
  let calls = 0; global.fetch = async () => { calls++; throw new Error('private-network-token'); };
  await assert.rejects(api('').testAliExpressFreight(), (error) => error.status === 401);
  assert.equal(calls, 0);
  for (const status of [401, 403, 404, 429, 500, 503]) {
    global.fetch = async () => new Response('private-provider-token CPF 52998224725', { status });
    await assert.rejects(api().testAliExpressFreight(), (error) => {
      assert.equal(error.status, status); assert.doesNotMatch(error.message, /private|52998224725/); return true;
    });
  }
  global.fetch = async () => { throw new Error('private-network-token'); };
  await assert.rejects(api().testAliExpressFreight(), (error) => error.status === 0 && !error.message.includes('private'));
});

test('malformed results and unknown statuses cannot be shown as a successful quote', async () => {
  for (const result of [null, { ...fixture, status: 'private-token' }, { ...fixture, methods: [] },
    { ...fixture, methods: [{ ...fixture.methods[0], amount: -1 }] },
    { ...fixture, methods: [{ ...fixture.methods[0], currency: 'BRL\n' }] },
    { ...fixture, methods: [{ ...fixture.methods[0], serviceName: 'synthetic-admin-token' }] }]) {
    global.fetch = async () => Response.json(result);
    await assert.rejects(api().testAliExpressFreight(), (error) => error.status === 502 && !error.message.includes('private-token'));
  }
  global.fetch = async () => new Response('<html>private-token</html>', { status: 200 });
  await assert.rejects(api().testAliExpressFreight(), (error) => error.status === 502);
  assert.equal(helper.parseFreightDiagnostic({ ...fixture, safeCode: 'private-provider-token' }, 'admin-token').safeCode, 'UNRECOGNIZED_RESULT');
});

test('known backend failure categories get distinct friendly messages without raw supplier text', () => {
  for (const status of Object.keys(helper.freightStatusMessages).filter((value) => value !== 'SUCCESS')) {
    const result = helper.parseFreightDiagnostic({ status, safeCode: 'UNCLASSIFIED_PROVIDER_REJECTION', methods: [], message: 'private-token' }, 'admin-token');
    assert.equal(result.status, status); assert.ok(helper.freightStatusMessages[status].length > 20);
    assert.ok(!JSON.stringify(result).includes('private-token'));
  }
});

const routeMocks = { 'next/navigation': { useRouter: () => ({ replace() {} }) }, '@/context/AuthContext': { useAuth: () => ({ logout() {} }) } };
test('results show returned currencies, amounts and estimated periods; unknown estimates are not invented', () => {
  const { AliExpressFreightResult } = load('components/admin/AliExpressFreightDiagnostic.tsx', routeMocks);
  const html = renderToStaticMarkup(React.createElement(AliExpressFreightResult, { result: fixture }));
  for (const text of ['Consulta concluída com sucesso', 'CALL_SUCCEEDED', 'CAINIAO_STANDARD', '12,30', '25,39', 'BRL', 'USD', 'Entre 5 e 8 dias', 'Prazo não informado']) assert.ok(html.includes(text), text);
  assert.equal(helper.freightEstimate('guaranteed private-token'), 'Prazo não informado');
  assert.equal(helper.freightEstimate('8-5day'), 'Prazo não informado');
  assert.equal(helper.freightEstimate('1day'), '1 dia estimado');
  const failure = renderToStaticMarkup(React.createElement(AliExpressFreightResult, { result: { status: 'PERMISSION_DENIED', safeCode: 'PROVIDER_PERMISSION_DENIED', methods: [] } }));
  assert.match(failure, /Não foi possível confirmar/); assert.match(failure, /permissões da aplicação/); assert.ok(!failure.includes('<table'));
});

test('specific TOP codes survive the API boundary and show their distinct safe explanations', async () => {
  const { AliExpressFreightResult } = load('components/admin/AliExpressFreightDiagnostic.tsx', routeMocks);
  for (const [code, reason] of [['TOP_21_MISSING_METHOD', 'método ausente'], ['TOP_22_INVALID_METHOD', 'método não reconhecido'],
    ['TOP_24_MISSING_SIGNATURE', 'assinatura ausente'], ['TOP_25_INVALID_SIGNATURE', 'assinatura rejeitada'],
    ['TOP_28_MISSING_APP_KEY', 'aplicação ausente'], ['TOP_29_INVALID_APP_KEY', 'aplicação rejeitada']]) {
    global.fetch = async () => Response.json({ status: 'PROTOCOL_ERROR', safeCode: code, methods: [],
      message: 'private-token', sub_msg: 'private-signature', request_id: 'private-trace' });
    const result = await api().testAliExpressFreight();
    assert.equal(result.safeCode, code);
    const html = renderToStaticMarkup(React.createElement(AliExpressFreightResult, { result }));
    assert.ok(html.includes(code)); assert.ok(html.includes(reason));
    assert.doesNotMatch(html, /private-token|private-signature|private-trace/);
  }
  const legacy = helper.parseFreightDiagnostic({ status: 'PROTOCOL_ERROR', safeCode: 'PROVIDER_PROTOCOL_REJECTED', methods: [] }, 'admin-token');
  assert.equal(helper.freightDiagnosticMessage(legacy), helper.freightStatusMessages.PROTOCOL_ERROR);
});

function find(tree, predicate) {
  if (!tree || typeof tree !== 'object') return null; if (predicate(tree)) return tree;
  for (const child of React.Children.toArray(tree.props?.children)) { const found = find(child, predicate); if (found) return found; }
  return null;
}
function harness() {
  const states = [], refs = []; let cursor = 0, refCursor = 0, calls = 0, resolve, reject, loggedOut = 0; const routes = [];
  const actualApi = api();
  const { AliExpressFreightDiagnostic } = load('components/admin/AliExpressFreightDiagnostic.tsx', {
    react: { ...React, useState(initial) { const index = cursor++; if (!(index in states)) states[index] = initial; return [states[index], (next) => { states[index] = next; }]; },
      useRef(initial) { return refs[refCursor++] ??= { current: initial }; } },
    'next/navigation': { useRouter: () => ({ replace: (route) => routes.push(route) }) },
    '@/context/AuthContext': { useAuth: () => ({ logout: () => loggedOut++ }) },
    '@/lib/api/admin/aliexpress': { AliExpressFreightError: actualApi.AliExpressFreightError, testAliExpressFreight() { calls++; return new Promise((res, rej) => { resolve = res; reject = rej; }); } },
  });
  return { render() { cursor = 0; refCursor = 0; return AliExpressFreightDiagnostic(); }, calls: () => calls,
    resolve: (result) => resolve(result), reject: (status) => reject(new actualApi.AliExpressFreightError(status)), routes, loggedOut: () => loggedOut };
}

test('button is manual, loading blocks duplicate clicks, success renders and diagnostic limits remain visible', async () => {
  const h = harness(); let tree = h.render(); assert.equal(h.calls(), 0);
  const initialHtml = renderToStaticMarkup(tree);
  for (const text of ['SKU ou CEP', 'China (CN)', 'não cria pedidos', 'não executa pagamentos', 'independente do produto']) assert.ok(initialHtml.includes(text), text);
  const button = find(tree, (node) => node.type === 'button'); button.props.onClick(); button.props.onClick();
  assert.equal(h.calls(), 1); tree = h.render();
  assert.equal(find(tree, (node) => node.type === 'button').props.disabled, true);
  assert.ok(find(tree, (node) => node.props?.role === 'status'));
  h.resolve(fixture); await new Promise((res) => setImmediate(res)); tree = h.render();
  assert.equal(find(tree, (node) => node.type === 'button').props.disabled, false);
  assert.ok(find(tree, (node) => node.type?.name === 'AliExpressFreightResult'));
  find(tree, (node) => node.type === 'button').props.onClick();
  assert.equal(find(h.render(), (node) => node.type?.name === 'AliExpressFreightResult'), null);
  h.resolve(fixture); await new Promise((res) => setImmediate(res));
});

test('HTTP errors release loading, allow manual retry and expire the session only on 401', async () => {
  for (const status of [401, 403, 500, 0]) {
    const h = harness(); find(h.render(), (node) => node.type === 'button').props.onClick();
    h.reject(status); await new Promise((res) => setImmediate(res)); const tree = h.render();
    assert.ok(find(tree, (node) => node.props?.role === 'alert'));
    assert.equal(find(tree, (node) => node.type === 'button').props.disabled, false);
    assert.equal(h.calls(), 1); assert.equal(h.loggedOut(), status === 401 ? 1 : 0);
    assert.deepEqual(h.routes, status === 401 ? ['/login'] : []);
  }
});
