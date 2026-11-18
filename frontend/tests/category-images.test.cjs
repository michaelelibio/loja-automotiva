/* eslint-disable @typescript-eslint/no-require-imports -- Isolated TS loader used by the repository test runner. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const Module = require('node:module');
const ts = require('typescript');
const React = require('react');
const { renderToStaticMarkup } = require('react-dom/server');

function load(relative, mocks = {}) {
  const filename = path.resolve(__dirname, '..', relative);
  const mod = new Module(filename, module);
  mod.filename = filename;
  mod.paths = Module._nodeModulePaths(path.dirname(filename));
  mod.require = (id) => {
    if (id in mocks) return mocks[id];
    if (id.startsWith('@/')) {
      const target = id.slice(2);
      return load(`${target}${fs.existsSync(path.resolve(__dirname, '..', `${target}.tsx`)) ? '.tsx' : '.ts'}`, mocks);
    }
    return Module.prototype.require.call(mod, id);
  };
  mod._compile(ts.transpileModule(fs.readFileSync(filename, 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true },
  }).outputText, filename);
  return mod.exports;
}

const category = (name, imageUrl) => ({ id: 1, name, slug: name, path: name, parentId: null, position: 0, count: 1, active: true, imageUrl });
const image = ({ fill, unoptimized, ...props }) => { void fill; void unoptimized; return React.createElement('img', props); };
const link = ({ href, children, ...props }) => React.createElement('a', { href, ...props }, children);

test('category artwork distinguishes all eight families and recognizes new related categories', () => {
  const { categoryIllustration } = load('lib/category-images.ts');
  const families = new Map([
    ['Lavagem', 'lavagem'], ['Proteção', 'protecao'], ['Detalhamento', 'detalhamento'], ['Polimento', 'polimento'],
    ['Cuidados internos', 'cuidados-internos'], ['Cuidados externos', 'cuidados-externos'], ['Ferramentas', 'ferramentas'], ['Acessórios', 'acessorios'],
  ]);
  for (const [name, asset] of families) assert.equal(categoryIllustration(category(name)), `/categories/${asset}.svg`);
  assert.equal(categoryIllustration({ name: 'Novos produtos', slug: 'couro', path: 'cuidados/couro' }), '/categories/cuidados-internos.svg');
  assert.equal(categoryIllustration(category('Rodas e pneus')), '/categories/cuidados-externos.svg');
  assert.equal(categoryIllustration(category('Vitrificadores cerâmicos')), '/categories/protecao.svg');
  assert.equal(categoryIllustration(category('Novidades')), '/categories/acessorios.svg');
});

test('specific supplied images take precedence while invalid or unsafe sources use local artwork', () => {
  const { categoryImageSource } = load('lib/category-images.ts');
  for (const source of ['https://media.example.com/custom.webp', '/uploads/lavagem.webp']) {
    assert.equal(categoryImageSource(category('Lavagem', ` ${source} `)), source);
  }
  for (const source of [undefined, null, '', '//other.example.com/a.jpg', '/\\other.example.com/a.jpg', 'javascript:alert(1)', 'data:image/svg+xml,broken', 'http://media.example.com/a.jpg', 'https://user:secret@example.com/a.jpg', 'broken']) {
    assert.equal(categoryImageSource(category('Lavagem', source)), '/categories/lavagem.svg');
  }
});

test('failed supplied media falls back once and all failed image requests retain an inline illustration', () => {
  let failed = [];
  const hooks = { ...React, useState: () => [failed, (update) => { failed = update(failed); }] };
  const { CategoryImage } = load('components/CategoryImage.tsx', { react: hooks, 'next/image': image });
  const input = { category: category('Proteção', 'https://media.example.com/broken.webp') };
  const children = (tree) => React.Children.toArray(tree.props.children);
  let tree = CategoryImage(input);
  let media = children(tree).find((node) => node.type === image);
  assert.equal(media.props.src, input.category.imageUrl);
  media.props.onError();
  tree = CategoryImage(input);
  media = children(tree).find((node) => node.type === image);
  assert.equal(media.props.src, '/categories/protecao.svg');
  media.props.onError();
  tree = CategoryImage(input);
  assert.equal(children(tree).filter((node) => node.type === image).length, 0);
  assert.ok(children(tree).some((node) => node.type === 'svg'));
  assert.deepEqual(failed, [input.category.imageUrl, '/categories/protecao.svg']);
});

test('home cards retain category links and labels with decorative responsive lazy-loaded images', async () => {
  const items = [category('Lavagem'), { ...category('Cuidados internos'), id: 2, path: 'internos' }, { ...category('Shampoos'), id: 3, parentId: 1 }];
  const { default: Home } = load('app/page.tsx', {
    'next/link': link, 'next/image': image,
    '@/components/Header': { Header: () => null }, '@/components/Footer': { Footer: () => null },
    '@/components/ProductCard': { ProductCard: () => null },
    '@/lib/catalog': { fetchCategories: async () => items, fetchCatalog: async () => ({ content: [] }) },
  });
  const html = renderToStaticMarkup(await Home());
  assert.ok(html.includes('href="/categorias/Lavagem"'));
  assert.ok(html.includes('href="/categorias/internos"'));
  assert.ok(!html.includes('href="/categorias/Shampoos"'));
  assert.ok(html.includes('src="/categories/lavagem.svg"'));
  assert.ok(html.includes('src="/categories/cuidados-internos.svg"'));
  assert.ok(html.includes('alt=""'));
  assert.ok(html.includes('loading="lazy"'));
  assert.ok(html.includes('sizes="(max-width: 350px) 90vw, (max-width: 900px) 45vw, 23vw"'));
  assert.ok(html.includes('Cuidados internos'));
});

test('all store-owned category illustrations are lightweight self-contained SVGs with one framing', () => {
  const { CATEGORY_ILLUSTRATIONS } = load('lib/category-images.ts');
  assert.equal(new Set(Object.values(CATEGORY_ILLUSTRATIONS)).size, 8);
  for (const source of Object.values(CATEGORY_ILLUSTRATIONS)) {
    const svg = fs.readFileSync(path.resolve(__dirname, '../public', source.slice(1)), 'utf8');
    assert.match(svg, /viewBox="0 0 640 480"/);
    assert.ok(Buffer.byteLength(svg) < 5000);
    assert.doesNotMatch(svg, /<(?:script|image|foreignObject)|(?:href|src)=|@import|url\(https?:/i);
  }
});
