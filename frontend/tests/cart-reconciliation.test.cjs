/* eslint-disable @typescript-eslint/no-require-imports -- Isolated TS loader, no browser or API calls. */
const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const ts = require('typescript');
const mod = { exports: {} };
new Function('exports', 'module', ts.transpileModule(fs.readFileSync(require('node:path').join(__dirname,'../lib/cart-reconciliation.ts'),'utf8'),
  { compilerOptions: { module: ts.ModuleKind.CommonJS } }).outputText)(mod.exports, mod);
const { reconcileCartItems } = mod.exports;
const product = { id:'15', availableForSale:true, requiresVariantSelection:true, variants:[{id:'35'}] };
test('keeps the exact imported variant and quantity without replacing or changing the SKU', () => {
  const item={productId:'15',variantId:'35',quantity:2};
  assert.deepEqual(reconcileCartItems([item],[product]),[item]);
});
test('removes deleted products, old foreign variants and missing required variants', () => {
  const items=[{productId:'1',variantId:'2',quantity:1},{productId:'15',variantId:'34',quantity:1},{productId:'15',quantity:1}];
  assert.deepEqual(reconcileCartItems(items,[product]),[]);
});
test('does not keep unavailable products and preserves products without required variants', () => {
  const item={productId:'15',quantity:1};
  assert.deepEqual(reconcileCartItems([item],[{...product,requiresVariantSelection:false,variants:[]}]),[item]);
  assert.deepEqual(reconcileCartItems([{...item,variantId:'35'}],[{...product,availableForSale:false}]),[]);
});
