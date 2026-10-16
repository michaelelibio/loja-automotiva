/* eslint-disable @typescript-eslint/no-require-imports -- Node's CommonJS test runner and isolated TS loader. */
const { test, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

// Use the installed TypeScript compiler and Node test runner, without new dependencies.
function load(relative, mocks = {}) {
  const filename = path.resolve(__dirname, '..', relative);
  const mod = new Module(filename, module);
  mod.filename = filename;
  mod.paths = Module._nodeModulePaths(path.dirname(filename));
  mod.require = (id) => {
    if (id in mocks) return mocks[id];
    if (id.startsWith('@/')) {
      const target = id.slice(2);
      return load(fs.existsSync(path.resolve(__dirname, '..', `${target}.ts`)) ? `${target}.ts` : `${target}.tsx`, mocks);
    }
    return Module.prototype.require.call(mod, id);
  };
  mod._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText, filename);
  return mod.exports;
}

const fixture = {
  fingerprint: 'a'.repeat(64),
  productId: '1005001234567890', name: 'Luz automotiva', currency: 'BRL',
  description: 'Luz para uso automotivo.', commercialCategory: 'Nacionais', domesticShippingConfirmed: false,
  images: ['https://example.com/product.jpg', 'javascript:alert(1)'],
  attributes: { Marca: 'Garage', 'source:0:attr_name_id': '-1' }, warnings: ['Confira a compatibilidade.', 'Impostos não incluídos.', 'Disponibilidade sujeita a confirmação.'],
  variants: [{ skuId: 'sku-1', skuCode: 'LED-A', price: 35.9, regularPrice: 40,
    currency: 'BRL', availableStock: 0, attributes: { '14': 'Azul', '14:name': 'Cor', '14:valueId': '771' }, images: ['https://example.com/variant.jpg'] }],
};
const originalFetch = global.fetch;
afterEach(() => { global.fetch = originalFetch; });
function api(token = 'admin-test-token') {
  return load('lib/api/admin/aliexpress.ts', { '@/lib/auth/tokenStorage': { getToken: () => token } });
}

test('posts only preview, using ADMIN token and the requested localization', async () => {
  const calls = [];
  global.fetch = async (url, init) => { calls.push({ url, init }); return Response.json(fixture); };
  const { previewAliExpressProduct } = api();
  for (const input of ['1005001234567890', 'https://pt.aliexpress.com/item/1005001234567890.html']) {
    assert.deepEqual(await previewAliExpressProduct(` ${input} `), fixture);
    const { url, init } = calls.at(-1);
    assert.ok(url.endsWith('/api/admin/integrations/aliexpress/preview'));
    assert.equal(init.method, 'POST');
    assert.equal(init.headers.Authorization, 'Bearer admin-test-token');
    assert.equal(init.cache, 'no-store');
    assert.deepEqual(JSON.parse(init.body), { input, shipToCountry: 'BR', targetCurrency: 'BRL', targetLanguage: 'pt_BR' });
  }
  assert.equal(calls.length, 2);
});

test('rejects blank/oversized input and missing session before any request', async () => {
  global.fetch = () => assert.fail('Must not request');
  for (const input of [' ', '1'.repeat(2049)]) await assert.rejects(api().previewAliExpressProduct(input), { status: 400 });
  await assert.rejects(api(null).previewAliExpressProduct('123'), { status: 401 });
});

test('HTTP errors never expose backend bodies, tokens or diagnostics', async () => {
  for (const status of [400, 401, 403, 404, 409, 422, 429, 500, 503, 504]) {
    global.fetch = async () => new Response('secret-token internal-stack-trace', { status });
    await assert.rejects(api().previewAliExpressProduct('123'), (error) => {
      assert.equal(error.status, status);
      assert.doesNotMatch(error.message, /secret|stack|token/i);
      return true;
    });
  }
});

test('network and malformed success responses produce safe errors', async () => {
  global.fetch = async () => { throw new Error('secret network diagnostics'); };
  await assert.rejects(api().previewAliExpressProduct('123'), { status: 0 });
  for (const body of ['invalid-json', 'null', '{}']) {
    global.fetch = async () => new Response(body);
    await assert.rejects(api().previewAliExpressProduct('123'), { status: 502 });
  }
});

const mocks = {
  'next/navigation': { useRouter: () => ({ replace() {} }) },
  '@/context/AuthContext': { useAuth: () => ({ logout() {} }) },
  '@/components/admin/AdminProductImage': { AdminProductImage: ({ src, alt }) => React.createElement('img', { src, alt }) },
};
test('compact preview shows commercial fields and preserves important warnings, hiding technical data', () => {
  const { AliExpressPreviewDetails } = load('components/admin/AliExpressProductPreview.tsx', mocks);
  const html = renderToStaticMarkup(React.createElement(AliExpressPreviewDetails, { product: fixture }));
  for (const text of ['Luz automotiva', 'Luz para uso automotivo.', 'Nacionais', 'Preço de venda', 'Não definido', '35,90', 'LED-A', '>0</td>', 'Cor: Azul', 'Confira a compatibilidade.', 'Impostos não incluídos.', 'Disponibilidade sujeita a confirmação.', 'product.jpg', 'variant.jpg', 'Frete e prazo não confirmados.']) assert.ok(html.includes(text), text);
  for (const text of ['Garage', 'source:0:', 'attr_name_id', '14:name', '14:valueId', '771', 'Preço original', '40,00', fixture.productId]) assert.ok(!html.includes(text), text);
  assert.match(html, /<input[^>]*id="aliexpress-product-name"[^>]*value="Luz automotiva"/);
  assert.match(html, /Edição somente nesta prévia/);
  assert.ok(!html.includes('javascript:'));
  const empty = renderToStaticMarkup(React.createElement(AliExpressPreviewDetails, { product: { ...fixture, images: [], variants: [], attributes: {}, warnings: [] } }));
  assert.match(empty, /Nenhuma variante/);
  assert.ok(!empty.includes('Atenção antes de cadastrar'));
});

test('never converts supplier currency or invents missing cost, stock or shipping', () => {
  const { AliExpressPreviewDetails } = load('components/admin/AliExpressProductPreview.tsx', mocks);
  const html = renderToStaticMarkup(React.createElement(AliExpressPreviewDetails, { product: {
    ...fixture, description: '', commercialCategory: '', domesticShippingConfirmed: true,
    variants: [{ ...fixture.variants[0], currency: 'USD', price: 9, skuCode: null, availableStock: null },
      { ...fixture.variants[0], skuId: 'missing-cost', price: null }],
  } }));
  for (const text of ['Custo em BRL não informado', '9,00', 'Preço não informado', 'Não informado', 'Descrição não informada', 'Não informada', 'Envio nacional confirmado.', 'sku-1']) assert.ok(html.includes(text), text);
  assert.ok(!html.includes('35,90'));
});

test('hides technical warnings and keeps commercial alerts without modifying API data', () => {
  const { AliExpressPreviewDetails } = load('components/admin/AliExpressProductPreview.tsx', mocks);
  const product = { ...fixture, description: '<script>alert(1)</script>', warnings: [
    ...fixture.warnings,
    'Categoria comercial Nacionais e curadoria administrativa; envio nacional nao confirmado.',
    'Peso e dimensoes preservados como informados; unidades nao inferidas.',
    'Atributos do SKU secret-sku-id inconsistentes; identificadores originais preservados.',
    'Imagem invalida ou fora dos CDNs permitidos foi omitida.',
  ] };
  const before = JSON.stringify(product);
  const html = renderToStaticMarkup(React.createElement(AliExpressPreviewDetails, { product }));
  assert.match(html, /Confira as opções das variantes/);
  assert.match(html, /Algumas imagens não estão disponíveis/);
  for (const text of ['metadados', 'CDNs', 'identificadores', 'secret-sku-id', 'curadoria', 'unidades nao inferidas', '<script>']) assert.ok(!html.includes(text), text);
  assert.equal(JSON.stringify(product), before);
});

test('initial section has labeled search and import is disabled until a preview is prepared', () => {
  const { AliExpressProductPreview } = load('components/admin/AliExpressProductPreview.tsx', mocks);
  const html = renderToStaticMarkup(React.createElement(AliExpressProductPreview));
  assert.match(html, /Buscar produto no AliExpress/);
  assert.match(html, /for="aliexpress-input"/);
  assert.match(html, /<button[^>]*disabled=""[^>]*>Importar produto<\/button>/);
  assert.match(html, /Busque um produto para preparar/);
});

test('preparation requires explicit positive equal prices and valid name, category and variants', () => {
  const { prepareAliExpressImport } = load('lib/admin/aliexpress-import.ts');
  const product = { ...fixture, variants: [...fixture.variants, { ...fixture.variants[0], skuId: 'sku-2' }] };
  const prepare = (prices, name = 'Nome comercial', category = 'Acessórios', selected = ['sku-1', 'sku-2']) => prepareAliExpressImport(product, name, category, selected, prices);
  for (const value of ['', '0', '-1', 'NaN', '1.234', '10000000000', '1e2']) {
    assert.equal(prepare({ 'sku-1': value, 'sku-2': value }).payload, null);
  }
  assert.equal(prepare({}).payload, null);
  assert.equal(prepare({ 'sku-1': '35.90', 'sku-2': '50' }).payload, null);
  assert.equal(prepare({ 'sku-1': '35.90' }, '', 'Acessórios', ['sku-1']).payload, null);
  assert.equal(prepare({ 'sku-1': '35.90' }, 'Nome', 'Nacionais', ['sku-1']).payload, null);
  assert.equal(prepare({}, 'Nome', 'Acessórios', []).payload, null);
  assert.equal(prepare({ unknown: '35.90' }, 'Nome', 'Acessórios', ['unknown']).payload, null);
  const result = prepare({ 'sku-1': '79,9', 'sku-2': '79.90' });
  assert.deepEqual(result.payload.variantSalePricesBrl, { 'sku-1': '79.90', 'sku-2': '79.90' });
  assert.equal(result.payload.name, 'Nome comercial');
  assert.ok(!('active' in result.payload));
  assert.ok(!('price' in result.payload));
  assert.ok(!('attributes' in result.payload));
  assert.equal(prepareAliExpressImport({ ...product, fingerprint: undefined }, 'Nome', 'Acessórios', ['sku-1'], { 'sku-1': '79' }).payload, null);
});

test('import posts authenticated commercial data to the existing endpoint and requires inactive result', async () => {
  const { importAliExpressProduct } = api();
  const { prepareAliExpressImport } = load('lib/admin/aliexpress-import.ts');
  const { payload } = prepareAliExpressImport(fixture, 'Nome comercial', 'Acessórios', ['sku-1'], { 'sku-1': '99,90' });
  global.fetch = async (url, init) => {
    assert.ok(url.endsWith('/api/admin/integrations/aliexpress/import'));
    assert.equal(init.method, 'POST');
    assert.equal(init.headers.Authorization, 'Bearer admin-test-token');
    assert.deepEqual(JSON.parse(init.body), payload);
    return Response.json({ productId: 7, active: false, supplierProductId: fixture.productId, variantCount: 1 });
  };
  assert.equal((await importAliExpressProduct(payload)).productId, 7);
  global.fetch = async () => Response.json({ productId: 7, active: true });
  await assert.rejects(importAliExpressProduct(payload), { status: 502 });
  await assert.rejects(api(null).importAliExpressProduct(payload), { status: 401 });
});

test('import distinguishes duplicate and stale preview using only safe codes', async () => {
  for (const code of ['DUPLICATE', 'STALE_PREVIEW', 'AUTHORIZATION_REQUIRED', 'secret-internal-diagnostics']) {
    global.fetch = async () => Response.json({ message: 'secret-token', fields: { code } }, { status: 409 });
    await assert.rejects(api().importAliExpressProduct({}), (error) => {
      assert.equal(error.code, code.startsWith('secret') ? '' : code);
      assert.doesNotMatch(error.message, /secret|internal/);
      return true;
    });
  }
  for (const status of [400, 401, 403, 429, 500, 502, 504]) {
    global.fetch = async () => new Response('secret-token', { status });
    await assert.rejects(api().importAliExpressProduct({}), (error) => {
      assert.equal(error.status, status); assert.doesNotMatch(error.message, /secret-token/); return true;
    });
  }
});

function findNode(tree, predicate) {
  if (!tree || typeof tree !== 'object') return null;
  if (predicate(tree)) return tree;
  for (const child of React.Children.toArray(tree.props?.children)) {
    const result = findNode(child, predicate);
    if (result) return result;
  }
  return null;
}

function importHarness() {
  const states = [], refs = [], busyStates = [];
  let cursor, refCursor, resolve, reject, calls = 0, payload, imported = 0;
  const service = api();
  const { AliExpressImportForm } = load('components/admin/AliExpressProductPreview.tsx', {
    ...mocks,
    react: { ...React,
      useState(initial) { const index = cursor++; if (!(index in states)) states[index] = initial; return [states[index], (value) => { states[index] = typeof value === 'function' ? value(states[index]) : value; }]; },
      useRef(initial) { const index = refCursor++; return refs[index] ??= { current: initial }; },
    },
    '@/lib/api/admin/aliexpress': { ...service, importAliExpressProduct(value) { calls++; payload = value; return new Promise((res, rej) => { resolve = res; reject = rej; }); } },
  });
  const render = () => { cursor = 0; refCursor = 0; return AliExpressImportForm({ product: fixture, onBusyChange: (value) => busyStates.push(value), onImported: () => imported++ }); };
  return { render, busyStates, service, calls: () => calls, payload: () => payload, imported: () => imported,
    resolve: (value) => resolve(value), reject: (error) => reject(error) };
}

test('import enables only after preparation, blocks repeated clicks, and links to the inactive draft', async () => {
  const harness = importHarness();
  let tree = harness.render();
  assert.equal(findNode(tree, (node) => node.type === 'button' && node.props.type === 'submit').props.disabled, true);
  const editor = findNode(tree, (node) => !!node.props?.editor).props.editor;
  editor.setName('Nome revisado'); editor.setCategory('Acessórios'); editor.setPrice('sku-1', '79,90');
  tree = harness.render();
  assert.equal(findNode(tree, (node) => node.type === 'button' && node.props.type === 'submit').props.disabled, false);
  const event = { preventDefault() {} };
  const pending = tree.props.onSubmit(event);
  await tree.props.onSubmit(event);
  assert.equal(harness.calls(), 1);
  assert.equal(harness.render().props['aria-busy'], true);
  harness.resolve({ productId: 17, active: false, variantCount: 1 });
  await pending;
  tree = harness.render();
  assert.equal(harness.imported(), 1);
  assert.deepEqual(harness.busyStates, [true, false]);
  assert.equal(harness.payload().name, 'Nome revisado');
  assert.ok(findNode(tree, (node) => node.props?.href === '/admin/produtos/17'));
  assert.equal(findNode(tree, (node) => node.type === 'button' && node.props.type === 'submit'), null);
});

test('duplicate, stale preview and uncertain import failures require a new preview and release loading', async () => {
  for (const [status, code] of [[409, 'DUPLICATE'], [409, 'STALE_PREVIEW'], [502, ''], [0, '']]) {
    const harness = importHarness();
    const editor = findNode(harness.render(), (node) => !!node.props?.editor).props.editor;
    editor.setCategory('Lavagem'); editor.setPrice('sku-1', '59.90');
    const pending = harness.render().props.onSubmit({ preventDefault() {} });
    harness.reject(new harness.service.AliExpressImportError(status, code));
    await pending;
    const tree = harness.render();
    assert.equal(tree.props['aria-busy'], false);
    assert.equal(findNode(tree, (node) => node.type === 'button' && node.props.type === 'submit').props.disabled, true);
    assert.ok(findNode(tree, (node) => node.props?.role === 'alert'));
    assert.equal(harness.imported(), 0);
  }
});

test('search transitions through loading, success, retry and safe error; prevents duplicate requests', async () => {
  const states = [];
  const refs = [];
  let cursor = 0;
  let refCursor = 0;
  let resolve;
  let reject;
  let calls = 0;
  const service = api();
  const { AliExpressProductPreview } = load('components/admin/AliExpressProductPreview.tsx', {
    ...mocks,
    react: { ...React,
      useState(initial) {
        const index = cursor++;
        if (!(index in states)) states[index] = initial;
        return [states[index], (value) => { states[index] = value; }];
      },
      useRef(initial) {
        const index = refCursor++;
        return refs[index] ??= { current: initial };
      },
    },
    '@/lib/api/admin/aliexpress': { ...service, previewAliExpressProduct() {
      calls++;
      return new Promise((res, rej) => { resolve = res; reject = rej; });
    } },
  });
  function render() { cursor = 0; refCursor = 0; return AliExpressProductPreview(); }
  function find(tree, predicate) {
    if (!tree || typeof tree !== 'object') return null;
    if (predicate(tree)) return tree;
    for (const child of React.Children.toArray(tree.props?.children)) {
      const result = find(child, predicate);
      if (result) return result;
    }
    return null;
  }
  const event = { preventDefault() {} };
  let tree = render();
  await find(tree, (node) => node.type === 'form').props.onSubmit(event);
  assert.equal(calls, 0);
  assert.match(states[3], /Informe o ID/);
  find(tree, (node) => node.type === 'input').props.onChange({ target: { value: fixture.productId } });
  tree = render();
  const pending = find(tree, (node) => node.type === 'form').props.onSubmit(event);
  await find(tree, (node) => node.type === 'form').props.onSubmit(event);
  assert.equal(calls, 1);
  tree = render();
  assert.equal(find(tree, (node) => node.type === 'form').props['aria-busy'], true);
  assert.equal(find(tree, (node) => node.type === 'input').props.disabled, true);
  resolve(fixture);
  await pending;
  assert.equal(states[1], fixture);
  assert.equal(states[2], false);
  tree = render();
  const retry = find(tree, (node) => node.type === 'form').props.onSubmit(event);
  assert.equal(states[1], null);
  reject(new service.AliExpressPreviewError(429));
  await retry;
  assert.match(states[3], /Limite de consultas/);
  assert.equal(states[2], false);
  assert.equal(calls, 2);
});
