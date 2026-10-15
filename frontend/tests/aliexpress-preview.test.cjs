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
  productId: '1005001234567890', name: 'Luz automotiva', currency: 'BRL',
  images: ['https://example.com/product.jpg', 'javascript:alert(1)'],
  attributes: { Marca: 'Garage' }, warnings: ['Confira a compatibilidade.'],
  variants: [{ skuId: 'sku-1', skuCode: 'LED-A', price: 35.9, regularPrice: 40,
    currency: 'BRL', availableStock: 0, attributes: { Cor: 'Azul' }, images: [] }],
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
test('details render variant price, zero stock, images, attributes and warnings safely', () => {
  const { AliExpressPreviewDetails } = load('components/admin/AliExpressProductPreview.tsx', mocks);
  const html = renderToStaticMarkup(React.createElement(AliExpressPreviewDetails, { product: fixture }));
  for (const text of ['Luz automotiva', 'BRL', '35,90', 'LED-A', 'fornecedor: 0', 'Garage', 'Azul', 'Confira a compatibilidade.', 'product.jpg']) assert.ok(html.includes(text), text);
  assert.ok(!html.includes('javascript:'));
  const empty = renderToStaticMarkup(React.createElement(AliExpressPreviewDetails, { product: { ...fixture, images: [], variants: [], attributes: {}, warnings: [] } }));
  assert.match(empty, /Nenhuma variante/);
  assert.match(empty, /Nenhum aviso/);
});

test('initial section has labeled search and import is always disabled', () => {
  const { AliExpressProductPreview } = load('components/admin/AliExpressProductPreview.tsx', mocks);
  const html = renderToStaticMarkup(React.createElement(AliExpressProductPreview));
  assert.match(html, /Buscar produto no AliExpress/);
  assert.match(html, /for="aliexpress-input"/);
  assert.match(html, /<button[^>]*disabled=""[^>]*>Importar produto<\/button>/);
  assert.match(html, /não importa, publica nem altera produtos/);
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
