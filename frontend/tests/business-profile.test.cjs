/* eslint-disable @typescript-eslint/no-require-imports -- Isolated TypeScript loader for Node tests. */
const { test } = require('node:test');
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
const { readBusinessProfile } = load('lib/store/business-profile.ts');
const key = (value) => `INGARAGE_PUBLIC_${value}`;
// Synthetic fixture only. The alphanumeric CNPJ is the Receita Federal calculation example.
const complete = {
  [key('BUSINESS_CONFIRMED')]: 'true', [key('POLICIES_CONFIRMED')]: 'true',
  [key('LEGAL_NAME')]: 'Loja de Teste Automatizado', [key('TAX_ID_TYPE')]: 'CNPJ', [key('TAX_ID')]: '12.ABC.345/01DE-35',
  [key('BUSINESS_ADDRESS')]: 'Endereço sintético do teste', [key('SUPPORT_EMAIL')]: 'atendimento@loja-sintetica.com.br',
  [key('PRIVACY_EMAIL')]: 'dados@loja-sintetica.com.br', [key('SUPPORT_HOURS')]: 'Horário sintético para teste',
  [key('DELIVERY_TERMS')]: 'Condições sintéticas confirmadas no teste para seu pedido.', [key('RETURN_INSTRUCTIONS')]: 'Instruções sintéticas de teste.',
  [key('REFUND_TERMS')]: 'Reembolso sintético do teste.', [key('PRIVACY_RETENTION')]: 'Conservação sintética do teste.',
  [key('PRIVACY_TRANSFERS')]: 'Condições sintéticas de compartilhamento.', [key('POLICY_UPDATED_AT')]: '2026-11-15',
};

test('empty configuration blocks launch without inventing identity, contact or operational terms', () => {
  const profile = readBusinessProfile({});
  assert.equal(profile.ready, false); assert.equal(profile.legalName, null); assert.equal(profile.supportEmail, null);
  assert.equal(profile.taxId, null); assert.equal(profile.deliveryTerms, null); assert.equal(profile.policyUpdatedAt, null);
  for (const field of ['LEGAL_NAME', 'TAX_ID', 'BUSINESS_ADDRESS', 'SUPPORT_EMAIL', 'PRIVACY_EMAIL', 'DELIVERY_TERMS', 'RETURN_INSTRUCTIONS', 'REFUND_TERMS']) assert.ok(profile.pendingFields.includes(key(field)));
});

test('proposed or populated values remain hidden until the owner confirms their public use', () => {
  const profile = readBusinessProfile({ ...complete, [key('BUSINESS_CONFIRMED')]: 'false', [key('POLICIES_CONFIRMED')]: 'false' });
  assert.equal(profile.ready, false); assert.equal(profile.legalName, null); assert.equal(profile.supportEmail, null);
  assert.equal(profile.privacyEmail, null); assert.equal(profile.deliveryTerms, null); assert.equal(profile.policyUpdatedAt, null);
});

test('complete confirmed configuration removes pending notice, preserving text and optional phone semantics', () => {
  const profile = readBusinessProfile(complete);
  assert.equal(profile.ready, true); assert.deepEqual(profile.pendingFields, []); assert.equal(profile.phone, null);
  assert.equal(profile.taxId, '12ABC34501DE35'); assert.ok(profile.deliveryTerms.includes('seu pedido'));
  const withPhone = readBusinessProfile({ ...complete, [key('SUPPORT_PHONE')]: '+5547999999999' });
  assert.equal(withPhone.ready, true); assert.equal(withPhone.phone, '+5547999999999');
});

test('invalid dates, placeholder contact, unsafe link schemes and invalid document check digits block launch', () => {
  for (const value of ['2026-02-30', '15/11/2026', 'a definir']) assert.equal(readBusinessProfile({ ...complete, [key('POLICY_UPDATED_AT')]: value }).policyUpdatedAt, null);
  for (const value of ['javascript:alert(1)', 'suporte@example.com', 'contato@empresa.test', 'support@real.com\r\nBcc:other@real.com']) {
    const profile = readBusinessProfile({ ...complete, [key('SUPPORT_EMAIL')]: value });
    assert.equal(profile.supportEmail, null); assert.equal(profile.ready, false);
  }
  for (const [type, value] of [['CNPJ', '12ABC34501DE36'], ['CNPJ', '00000000000000'], ['CPF', '00000000000'], ['CPF', '52998224724']]) {
    assert.equal(readBusinessProfile({ ...complete, [key('TAX_ID_TYPE')]: type, [key('TAX_ID')]: value }).taxId, null);
  }
  assert.equal(readBusinessProfile({ ...complete, [key('TAX_ID_TYPE')]: 'CPF', [key('TAX_ID')]: '529.982.247-25' }).taxId, '52998224725');
  assert.equal(readBusinessProfile({ ...complete, [key('SUPPORT_PHONE')]: 'javascript:alert(1)' }).ready, false);
});

test('public components escape configured text and only produce validated contact links', () => {
  const { BusinessIdentity, BusinessContact, BusinessReadinessNotice } = load('components/BusinessInformation.tsx');
  const profile = readBusinessProfile({ ...complete, [key('LEGAL_NAME')]: '<script>alert("injection")</script>' });
  const html = renderToStaticMarkup(React.createElement(React.Fragment, null, React.createElement(BusinessIdentity, { profile }), React.createElement(BusinessContact, { profile })));
  assert.ok(html.includes('&lt;script&gt;')); assert.ok(!html.includes('<script>')); assert.ok(html.includes('mailto:atendimento@loja-sintetica.com.br'));
  assert.equal(renderToStaticMarkup(React.createElement(BusinessReadinessNotice, { profile })), '');
  const pending = readBusinessProfile({ ...complete, [key('BUSINESS_CONFIRMED')]: 'false' });
  const unconfirmed = renderToStaticMarkup(React.createElement(BusinessContact, { profile: pending }));
  assert.ok(!unconfirmed.includes('mailto:')); assert.ok(!unconfirmed.includes(complete[key('SUPPORT_EMAIL')]));
});

function pageHtml(route, profile) {
  const { default: Page } = load(`app/${route}/page.tsx`, {
    'next/link': (props) => React.createElement('a', props),
    '@/lib/store/business-profile': { readBusinessProfile: () => profile },
    '@/components/InstitutionalPage': { InstitutionalPage: ({ title, introduction, sections }) => React.createElement('article', null, React.createElement('h1', null, title), introduction, sections.map((section) => React.createElement('section', { key: section.title }, React.createElement('h2', null, section.title), section.content))) },
  });
  return renderToStaticMarkup(React.createElement(Page));
}

test('all five pages share readiness and display only confirmed policy text without editorial placeholders', () => {
  for (const route of ['contato', 'termos', 'entrega', 'trocas-e-devolucoes', 'privacidade']) {
    const pending = pageHtml(route, readBusinessProfile({}));
    assert.ok(pending.includes('Informações comerciais em revisão')); assert.doesNotMatch(pending, /\[.*A DEFINIR|endpoint|backend|placeorder/i);
    const ready = pageHtml(route, readBusinessProfile(complete));
    assert.ok(!ready.includes('Informações comerciais em revisão'));
    if (route !== 'contato') assert.ok(ready.includes('15/11/2026'));
  }
});

test('privacy accurately discloses recipient birthday, CPF and AliExpress sharing; delivery keeps free shipping', () => {
  const privacy = pageHtml('privacidade', readBusinessProfile(complete));
  for (const phrase of ['CPF', 'data de nascimento', 'AliExpress', 'fora do Brasil', 'Mercado Pago']) assert.ok(privacy.includes(phrase));
  assert.ok(!privacy.includes('não há compartilhamento'));
  assert.ok(pageHtml('entrega', readBusinessProfile(complete)).includes('R$ 0,00'));
  assert.ok(pageHtml('trocas-e-devolucoes', readBusinessProfile(complete)).includes('7 dias'));
});
