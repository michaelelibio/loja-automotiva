/* eslint-disable @typescript-eslint/no-require-imports -- TS loader used by the repository test runner. */
const { test, afterEach } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs'); const path = require('node:path'); const Module = require('node:module');
const ts = require('typescript'); const React = require('react'); const { renderToStaticMarkup } = require('react-dom/server');
function load(relative, mocks = {}) {
  const filename = path.resolve(__dirname, '..', relative); const mod = new Module(filename, module);
  mod.filename = filename; mod.paths = Module._nodeModulePaths(path.dirname(filename));
  mod.require = (id) => {
    if (id in mocks) return mocks[id];
    const target = id.startsWith('@/') ? id.slice(2) : id.startsWith('.') ? path.relative(path.resolve(__dirname,'..'),path.resolve(path.dirname(filename),id)) : null;
    if (target !== null) return load(target + (fs.existsSync(path.resolve(__dirname,'..',target+'.tsx')) ? '.tsx' : '.ts'),mocks);
    return Module.prototype.require.call(mod,id);
  };
  mod._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText,filename); return mod.exports;
}
const originalFetch = global.fetch; afterEach(() => { global.fetch = originalFetch; });
const link = ({ href, children, ...props }) => React.createElement('a', { ...props, href }, children);
const metadata = { category: { id: 1, name: 'Lavagem', path: 'lavagem', slug: 'lavagem' }, subcategory: null, brand: null, surfaces: [], purposes: [], searchTerms: [], usage: null, concentrated: null, refill: null, featured: false, beginner: false, commercialOrigin: null, publishedAt: null };
const backend = { id: 81, slug: 'shampoo', name: 'Shampoo', price: 12, category: 'Legada', stockQuantity: 5, active: true, availableForSale: true, catalog: metadata };
test('normalization never fills missing facts, stock, discounts or popularity with an example product', () => {
  const { normalizeBackendProduct } = load('lib/products.ts');
  const fake = { id:'81',slug:'shampoo',features:['Diluição inventada'],oldPrice:999,stock:99,featured:true,image:'fake',category:'Acessórios',description:'inventada' };
  const result = normalizeBackendProduct({ id:81,slug:'shampoo',name:'Shampoo',price:12,category:'Interior' }, fake);
  assert.equal(result.oldPrice,undefined); assert.deepEqual(result.features,[]); assert.equal(result.stock,0); assert.equal(result.featured,false); assert.equal(result.description,''); assert.equal(result.category,'Interior'); assert.equal(result.image,'');
});
test('URL state preserves combined filters, sorting and pagination with existing search aliases', () => {
  const api = load('lib/catalog.ts'); const params = api.catalogParams({ busca:'espuma',categoria:'Legada',brand:'Marca & Cia',surface:'Pintura',sort:'lowest',page:'2',token:'secret' });
  const href = api.catalogHref('/produtos',params,{page:'3'}); const url=new URL(href,'http://localhost');
  assert.equal(url.searchParams.get('q'),'espuma'); assert.equal(url.searchParams.get('legacyCategory'),'Legada'); assert.equal(url.searchParams.get('brand'),'Marca & Cia'); assert.equal(url.searchParams.get('surface'),'Pintura'); assert.equal(url.searchParams.get('page'),'3'); assert.ok(!href.includes('secret'));
  assert.equal(api.catalogReturnTo('https://other.test'),'/produtos'); assert.equal(api.catalogReturnTo('//other.test'),'/produtos'); assert.equal(api.catalogReturnTo('/categorias/lavagem?q=espuma'),'/categorias/lavagem?q=espuma');
});
test('catalog API uses real server pages and retains classification without contacting checkout', async () => {
  const calls=[]; global.fetch=async(url,init)=>{calls.push({url,init});return Response.json({content:[backend],page:2,size:24,totalElements:70,totalPages:3,facets:{}});};
  const result=await load('lib/catalog.ts').fetchCatalog({q:'Shampoo',page:'2'});
  assert.equal(result.content[0].catalog.category.path,'lavagem');assert.equal(result.totalElements,70);assert.equal(result.page,2);
  assert.equal(calls.length,1);assert.ok(calls[0].url.includes('/api/catalog?'));assert.equal(calls[0].init.cache,'no-store');assert.ok(!calls[0].url.includes('payments'));
});
test('cart and checkout product lookup follows every API page instead of losing later catalog items', async () => {
  const calls=[];global.fetch=async(url)=>{calls.push(url);return Response.json(url.includes('page=1')?{content:[{...backend,id:182,slug:'pagina-dois'}],totalPages:2}:{content:[backend],totalPages:2});};
  const products=await load('lib/products.ts').fetchProducts();
  assert.deepEqual(products.map((p)=>p.id),['81','182']);assert.equal(calls.length,2);assert.ok(calls[1].includes('page=1'));
});
test('catalog filter UI uses only returned facets, preserves page links and supplies detail return URL', () => {
  let returnTo=''; const {ProductCatalog}=load('app/produtos/ProductCatalog.tsx',{'next/link':link,'@/components/ProductCard':{ProductCard:({returnTo:back})=>{returnTo=back;return React.createElement('span',null,'Produto');}}});
  const result={content:[backend],page:1,size:1,totalElements:3,totalPages:3,facets:{categories:[],brands:['Marca real'],surfaces:[],purposes:[],usages:[],origins:[],types:[],hasConcentrated:false,hasRefill:false}};
  const html=renderToStaticMarkup(React.createElement(ProductCatalog,{result,params:{brand:'Marca real',page:'1',q:'teste'}}));
  assert.ok(html.includes('Marca real'));assert.ok(!html.includes('name="surface"'));assert.ok(!html.includes('name="purpose"'));assert.ok(html.includes('page=2'));assert.ok(html.includes('3 produtos encontrados'));assert.ok(returnTo.includes('brand=Marca+real'));assert.ok(html.includes('method="get"'));
});
test('bulk request is authenticated and changes only the explicitly selected fields', async () => {
  const calls=[];global.fetch=async(url,init)=>{calls.push({url,init});return new Response('',{status:200});};
  const {bulkClassify}=load('lib/api/admin/catalog.ts',{'@/lib/auth/tokenStorage':{getToken:()=> 'admin-test-token'}});
  await bulkClassify([81,82],['brand'],{categoryId:null,subcategoryId:null,brand:' Marca real ',surfaces:[' Pintura ',''],purposes:[],searchTerms:[],usage:null,concentrated:null,refill:null,featured:false,beginner:false,commercialOrigin:null});
  assert.equal(calls.length,1);assert.equal(calls[0].init.headers.Authorization,'Bearer admin-test-token');assert.equal(calls[0].init.method,'PATCH');const body=JSON.parse(calls[0].init.body);assert.deepEqual(body.fields,['brand']);assert.deepEqual(body.productIds,[81,82]);assert.equal(body.catalog.brand,'Marca real');assert.deepEqual(body.catalog.surfaces,['Pintura']);
});
test('category page shows taxonomy, filter state and readable breadcrumb links', async () => {
  const category={id:2,parentId:1,name:'Lavagem automotiva',path:'lavagem/lavagem-automotiva',description:'Descrição confirmada'};
  let query;const {CatalogPage}=load('components/CatalogPage.tsx',{'next/link':link,'@/components/Header':{Header:()=>null},'@/components/Footer':{Footer:()=>null},'@/app/produtos/ProductCatalog':{ProductCatalog:()=>React.createElement('p',null,'Resultados')},'@/lib/catalog':{catalogParams:()=>({brand:'Real'}),catalogHref:()=>'/categorias/lavagem/lavagem-automotiva?brand=Real',resolveCategory:async(path)=>path.includes('/')?category:{name:'Lavagem',path:'lavagem'},fetchCatalog:async(p)=>{query=p;return {content:[]};}}});
  const html=renderToStaticMarkup(await CatalogPage({raw:{brand:'Real'},categoryPath:category.path}));
  assert.equal(query.category,'lavagem');assert.equal(query.subcategory,category.path);assert.equal(query.brand,'Real');assert.ok(html.includes('/categorias/lavagem'));assert.ok(html.includes('Lavagem automotiva'));
});
