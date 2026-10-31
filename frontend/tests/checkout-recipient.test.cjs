/* eslint-disable @typescript-eslint/no-require-imports -- Isolated TypeScript loader for Node tests. */
const { test } = require('node:test'); const assert = require('node:assert/strict');
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
const helper = load('lib/checkout/recipient.ts');
const recipient = { cpf: '529.982.247-25', birthDate: '29/02/2000', phone: '(47) 99999-9999' };
const address = { id: 7, recipientName: 'Cliente Teste', zipCode: '89229040', street: 'Rua Original', number: '10', neighborhood: 'Centro', city: 'Joinville', state: 'SC', complement: null };
test('masks CPF, Brazilian birth date and phone without changing the represented values', () => {
  assert.equal(helper.maskCpf('52998224725'), recipient.cpf);
  assert.equal(helper.maskBirthDate('29022000'), recipient.birthDate);
  assert.equal(helper.maskPhone('47999999999'), recipient.phone);
  assert.equal(helper.maskPhone('4733334444'), '(47) 3333-4444');
  assert.equal(helper.cpfValid(recipient.cpf), true);
  for (const invalid of ['111.111.111-11', '529.982.247-24', 'abc52998224725', '', '00000000000']) assert.equal(helper.cpfValid(invalid), false);
});
test('validates real dates, future dates and the server-equivalent date window', () => {
  assert.equal(helper.birthDateValid('29/02/2000', '2026-10-15'), true);
  for (const invalid of ['29/02/2001', '31/02/2000', '01/01/2999', '15/10/2026', '14/10/1906', '2000-02-29', '29022000']) assert.equal(helper.birthDateValid(invalid, '2026-10-15'), false, invalid);
  assert.equal(helper.birthDateValid('15/10/1906', '2026-10-15'), true);
});
test('recipient and existing address must be complete; neighborhood, UF, full name and DDD are checked', () => {
  assert.deepEqual(helper.validateRecipient(recipient, address), {});
  assert.ok(helper.validateRecipient({ ...recipient, phone: '(20) 99999-9999' }, address).phone);
  assert.ok(helper.validateRecipient({ ...recipient, cpf: '' }, address).cpf);
  assert.ok(helper.validateRecipient(recipient, { ...address, recipientName: 'Cliente' }).address);
  assert.ok(helper.validateRecipient(recipient, { ...address, neighborhood: '' }).address);
  assert.ok(helper.validateRecipient(recipient, { ...address, state: 'XX' }).address);
});
test('form fields use Brazilian input labels, masks and errors without duplicating the address', () => {
  const { AliExpressRecipientFields } = load('components/AliExpressRecipientFields.tsx');
  const html = renderToStaticMarkup(React.createElement(AliExpressRecipientFields, { value: helper.emptyRecipient, onChange() {}, errors: { cpf: 'Informe um CPF válido.' }, disabled: false }));
  for (const label of ['CPF', 'DD/MM/AAAA', 'Telefone com DDD', 'aria-invalid="true"']) assert.ok(html.includes(label));
  for (const field of ['recipientName', 'street', 'zipCode', 'neighborhood']) assert.ok(!html.includes(`name="${field}"`));
});
test('admin sees completeness and pending field names without CPF or full birth date', () => {
  const { AliExpressRecipientStatus } = load('components/admin/AliExpressRecipientStatus.tsx');
  const render = (status) => renderToStaticMarkup(React.createElement(AliExpressRecipientStatus, { status }));
  const pending = render({ required: true, complete: false, pendingFields: ['recipient.cpf', 'recipient.birthDate', 'recipient.phone'] });
  for (const label of ['CPF', 'Data de nascimento', 'Telefone com DDD']) assert.ok(pending.includes(label));
  assert.match(render({ required: true, complete: true, pendingFields: [] }), /pronto para validação de frete/);
  assert.equal(render({ required: false, complete: true, pendingFields: [] }), '');
  assert.ok(!pending.includes(recipient.cpf)); assert.ok(!pending.includes(recipient.birthDate));
});
function find(tree, predicate) {
  if (!tree || typeof tree !== 'object') return null; if (predicate(tree)) return tree;
  for (const child of React.Children.toArray(tree.props?.children)) { const found = find(child, predicate); if (found) return found; }
  return null;
}
function checkout(ali, personal = recipient, freightError = null) {
  const states = [[address], 7, false, null, freightError ? [] : [{ code: 'CATALOG_FREE', name: 'Entrega gr?tis', price: 0 }], 'CATALOG_FREE', false, freightError, 0, false, null, personal, {}, JSON.stringify({ addressId: 7, address, zipCode: address.zipCode, items: [{ productId: 1, variantId: 2, quantity: 1 }] })];
  let index = 0, payload, calls = 0;
  const { CheckoutForm } = load('app/checkout/CheckoutForm.tsx', {
    react: { ...React, useMemo: (fn) => fn(), useRef: (initial) => ({current:initial}), useEffect() {}, useState(initial) { const current = index++; if (!(current in states)) states[current] = initial; return [states[current], (value) => { states[current] = value; }]; } },
    'next/link': (props) => React.createElement('a', props), 'next/navigation': { useRouter: () => ({ replace() {}, push() {} }) },
    '@/context/AuthContext': { useAuth: () => ({ isAuthenticated: true, isLoading: false }) },
    '@/context/CartContext': { useCart: () => ({ items: [{ productId: '1', variantId: '2', quantity: 1 }], isHydrated: true, clearCart() {}, reconcileItems() {} }) },
    '@/lib/useProducts': { useProducts: () => ({ products: [{ id: '1', name: 'Produto', price: 50, variants: [{ id: '2', name: 'Azul' }], image: '', requiresAliExpressRecipientData: ali }], loading: false, error: null }) },
    '@/data/products': { products: [] }, '@/lib/api/addresses': {}, '@/lib/api/shipping': {},
    '@/lib/api/orders': { async createOrder(value) { calls++; payload = value; return { id: 17 }; } },
  });
  return { tree: CheckoutForm(), calls: () => calls, payload: () => payload };
}
test('AliExpress checkout includes the three personal fields only after validation', async () => {
  const h = checkout(true);
  assert.ok(find(h.tree, (node) => node.type?.name === 'AliExpressRecipientFields'));
  await find(h.tree, (node) => node.type === 'form').props.onSubmit({ preventDefault() {} });
  assert.equal(h.calls(), 1); assert.deepEqual(h.payload().recipient, recipient);
  assert.equal(h.payload().shippingCode, 'CATALOG_FREE'); assert.equal(h.payload().expectedTotal, 50); assert.equal(h.payload().expectedAddress.street, address.street); assert.equal(h.payload().addressId, 7); assert.ok(!('shippingAddress' in h.payload()));
  const invalid = checkout(true, { ...recipient, cpf: '00000000000' });
  await find(invalid.tree, (node) => node.type === 'form').props.onSubmit({ preventDefault() {} });
  assert.equal(invalid.calls(), 0);
});
test('other suppliers keep the existing checkout and never send unnecessary personal fields', async () => {
  const h = checkout(false);
  assert.equal(find(h.tree, (node) => node.type?.name === 'AliExpressRecipientFields'), null);
  await find(h.tree, (node) => node.type === 'form').props.onSubmit({ preventDefault() {} });
  assert.equal(h.calls(), 1); assert.ok(!('recipient' in h.payload()));
});

test('unconfirmed delivery blocks checkout without creating an assisted request', async () => {
  const error = 'O frete dos produtos AliExpress ainda não pode ser confirmado.';
  const h = checkout(true, recipient, error);
  assert.equal(find(h.tree, (node) => node.type === 'button' && node.props.type === 'submit').props.disabled, true);
  await find(h.tree, (node) => node.type === 'form').props.onSubmit({ preventDefault() {} });
  assert.equal(h.calls(), 0);
  assert.equal(h.payload(), undefined);
  const html = renderToStaticMarkup(h.tree);
  assert.ok(html.includes('Finalizar pedido'));
  assert.ok(!html.includes('Solicitar cotação'));
  assert.ok(!html.includes('tarifa comercial'));
});

test('free delivery verification sends authenticated address and cart without calling freight calculation', async () => {
  const original = global.fetch;
  let request;
  global.fetch = async (url, options) => {
    request = { url, options };
    return { ok: true, json: async () => ({ options: [{ code: 'CATALOG_FREE', price: 0 }] }) };
  };
  try {
    const api = load('lib/api/shipping.ts', { '@/lib/auth/tokenStorage': { getToken: () => 'synthetic-token' } });
    const cart = { zipCode: address.zipCode, items: [{ productId: 1, variantId: 2, quantity: 1 }] };
    await api.checkFreeDelivery(7, cart);
    assert.ok(request.url.endsWith('/api/orders/delivery-eligibility'));
    assert.equal(request.options.headers.Authorization, 'Bearer synthetic-token');
    assert.deepEqual(JSON.parse(request.options.body), { addressId: 7, cart });
  } finally { global.fetch = original; }
});

test('eligibility errors never expose provider response bodies or personal values', async () => {
  const original = global.fetch;
  try {
    const api = load('lib/api/shipping.ts', { '@/lib/auth/tokenStorage': { getToken: () => 'synthetic-token' } });
    for (const status of [401, 409, 500]) {
      global.fetch = async () => ({ ok: false, status, text: async () => 'synthetic-secret-52998224725' });
      await assert.rejects(api.checkFreeDelivery(7, { zipCode: address.zipCode, items: [] }), error => {
        assert.equal(error.status, status);
        assert.ok(!error.message.includes('synthetic-secret'));
        assert.ok(!error.message.includes('52998224725'));
        return true;
      });
    }
  } finally { global.fetch = original; }
});
test('catalog normalization preserves only the server-provided recipient requirement boolean', () => {
  const { normalizeBackendProduct } = load('lib/products.ts');
  const product = { id: 1, slug: 'ali-test', name: 'Produto', variants: [], price: 50, requiresAliExpressRecipientData: true };
  assert.equal(normalizeBackendProduct(product).requiresAliExpressRecipientData, true);
  assert.equal(normalizeBackendProduct({ ...product, requiresAliExpressRecipientData: undefined }).requiresAliExpressRecipientData, false);
});
