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
    if (id === 'next/navigation') return { useRouter: () => ({ push() {}, refresh() {} }), redirect: (url) => { throw new Error(`redirect:${url}`); } };
    const target = id.startsWith('@/') ? id.slice(2) : id.startsWith('.') ? path.relative(path.resolve(__dirname,'..'),path.resolve(path.dirname(filename),id)) : null;
    if (target !== null) return load(target + (fs.existsSync(path.resolve(__dirname,'..',target+'.tsx')) ? '.tsx' : '.ts'),mocks);
    return Module.prototype.require.call(mod,id);
  };
  mod._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'), { compilerOptions: { module: ts.ModuleKind.CommonJS, jsx: ts.JsxEmit.ReactJSX, esModuleInterop: true } }).outputText,filename); return mod.exports;
}
const originalFetch = global.fetch; afterEach(() => { global.fetch = originalFetch; });
const link = ({ href, children, onNavigate, ...props }) => { void onNavigate; return React.createElement('a', { ...props, href }, children); };
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

test('filter application and sort changes reset pagination while page navigation preserves every filter', () => {
  const api=load('lib/catalog.ts');
  const params={category:'lavagem',subcategory:'lavagem/shampoos',brand:'Marca & Cia',minPrice:'10',maxPrice:'25',surface:'Pintura',purpose:'Limpeza',usage:'BOTH',origin:'NATIONAL',concentrated:'true',refill:'false',type:'KIT',section:'featured',sort:'lowest',q:'espuma',page:'7'};
  const next=new URL(api.catalogHref('/produtos',params,{page:'8'}),'http://localhost');
  for(const [key,value] of Object.entries({...params,page:'8'})) assert.equal(next.searchParams.get(key),value);
  assert.equal(new URL(api.catalogHref('/produtos',params,{sort:'highest'}),'http://localhost').searchParams.has('page'),false);
  const applied=api.catalogFilterParams(Object.entries({...params,category:'interior',size:'100',unknown:'ignored'}));
  assert.equal(applied.page,undefined);assert.equal(applied.subcategory,undefined);assert.equal(applied.size,undefined);assert.equal(applied.unknown,undefined);assert.equal(applied.brand,params.brand);
  assert.equal(api.catalogHref('/produtos',{}),'/produtos');
  for(const page of ['-1','abc','1.5','1000001']) assert.equal(api.catalogParams({page}).page,undefined);
});

test('pagination uses a bounded window, first/last pages and ellipses for large catalogs', () => {
  const {catalogPages}=load('lib/catalog.ts');
  for(const count of [0,7,12,13,24,25,121]) {
    const total=Math.ceil(count/12);
    for(let current=0;current<total;current++) {
      const pages=catalogPages(current,total), numbers=pages.filter(Number.isInteger);
      assert.ok(pages.length<=7);assert.equal(numbers[0],0);assert.equal(numbers.at(-1),total-1);assert.ok(numbers.includes(current));assert.equal(new Set(numbers).size,numbers.length);
    }
  }
  assert.deepEqual(catalogPages(5,100),[0,'ellipsis',4,5,6,'ellipsis',99]);
  assert.deepEqual(catalogPages(0,100),[0,1,2,3,'ellipsis',99]);
  assert.deepEqual(catalogPages(99,100),[0,'ellipsis',96,97,98,99]);
});
test('catalog API uses real server pages and retains classification without contacting checkout', async () => {
  const calls=[]; global.fetch=async(url,init)=>{calls.push({url,init});return Response.json({content:[backend],page:2,size:24,totalElements:70,totalPages:3,facets:{}});};
  const result=await load('lib/catalog.ts').fetchCatalog({q:'Shampoo',page:'2'});
  assert.equal(result.content[0].catalog.category.path,'lavagem');assert.equal(result.totalElements,70);assert.equal(result.page,2);
  assert.equal(calls.length,1);assert.ok(calls[0].url.includes('/api/catalog?'));assert.equal(calls[0].init.cache,'no-store');assert.ok(!calls[0].url.includes('payments'));
  assert.equal(new URL(calls[0].url).searchParams.get('size'),'12');assert.equal(new URL(calls[0].url).searchParams.get('page'),'2');
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
  let query;const {CatalogPage}=load('components/CatalogPage.tsx',{'next/link':link,'@/components/Header':{Header:()=>null},'@/components/Footer':{Footer:()=>null},'@/app/produtos/ProductCatalog':{ProductCatalog:()=>React.createElement('p',null,'Resultados')},'@/lib/catalog':{catalogParams:()=>({brand:'Real'}),catalogHref:()=>'/categorias/lavagem/lavagem-automotiva?brand=Real',resolveCategory:async(path)=>path.includes('/')?category:{name:'Lavagem',path:'lavagem'},fetchCategories:async()=>[category],fetchCatalog:async(p)=>{query=p;return {content:[]};}}});
  const html=renderToStaticMarkup(await CatalogPage({raw:{brand:'Real'},categoryPath:category.path}));
  assert.equal(query.category,'lavagem');assert.equal(query.subcategory,category.path);assert.equal(query.brand,'Real');assert.ok(html.includes('/categorias/lavagem'));assert.ok(html.includes('Lavagem automotiva'));
});

test('pagination markup identifies current page, disabled boundaries, counts and clear actions', () => {
  const {ProductCatalog}=load('app/produtos/ProductCatalog.tsx',{'next/link':link,'@/components/ProductCard':{ProductCard:()=>React.createElement('span',null,'Produto')}});
  const facets={categories:[],brands:[],surfaces:[],purposes:[],usages:[],origins:[],types:[],hasConcentrated:false,hasRefill:false};
  for(const count of [0,7,12,13,24,25,121]) {
    const html=renderToStaticMarkup(React.createElement(ProductCatalog,{result:{content:count?[backend]:[],page:0,size:12,totalElements:count,totalPages:Math.ceil(count/12),facets},params:{}}));
    assert.equal(html.includes('aria-label="Paginação de produtos"'),count>12);
    if(count>12) {assert.ok(html.includes('aria-current="page"'));assert.ok(html.includes('aria-disabled="true"'));assert.ok(html.includes('#catalog-results'));}
    if(count===0)assert.ok(html.includes('Nenhum produto encontrado'));
    assert.ok(html.includes('catalog-button-primary'));assert.ok(html.includes('catalog-button-secondary'));assert.ok(html.includes('href="/produtos#catalog-results"'));
  }
});

test('catalog page exposes retry on API error and normalizes saved pages beyond the last page', async () => {
  const api=load('lib/catalog.ts');
  const mocks={'next/link':link,'@/components/Header':{Header:()=>null},'@/components/Footer':{Footer:()=>null},'@/lib/catalog':{...api,fetchCategories:async()=>[],fetchCatalog:async()=>{throw Error('offline');}}};
  const {CatalogPage}=load('components/CatalogPage.tsx',mocks);
  const html=renderToStaticMarkup(await CatalogPage({raw:{brand:'Real',page:'9'}}));
  assert.ok(html.includes('role="alert"'));assert.ok(html.includes('Tentar novamente'));assert.ok(html.includes('brand=Real'));
  mocks['@/lib/catalog'].fetchCatalog=async()=>({page:9,totalPages:3,facets:{}});
  await assert.rejects(load('components/CatalogPage.tsx',mocks).CatalogPage({raw:{brand:'Real',page:'9'}}),/redirect:\/produtos\?brand=Real&page=2#catalog-results/);
});

test('catalog interactions apply combined filters, sort, clear and navigate using real URL state', () => {
  const pushes=[],refreshes=[];
  const hooks={...React,useRef:()=>({current:null}),useEffect:()=>{},useState:()=>['page',()=>{}],useTransition:()=>[false,(fn)=>fn()]};
  const router={push:(...args)=>pushes.push(args),refresh:()=>refreshes.push(true)};
  const {ProductCatalog}=load('app/produtos/ProductCatalog.tsx',{'react':hooks,'next/navigation':{useRouter:()=>router},'next/link':link});
  const facets={categories:[],brands:['Marca A'],surfaces:[],purposes:[],usages:[],origins:[],types:[],hasConcentrated:false,hasRefill:false};
  const tree=ProductCatalog({result:{content:[],page:4,size:12,totalElements:121,totalPages:11,facets},params:{brand:'Marca A',page:'4',sort:'lowest'}});
  function nodes(node) {if(!React.isValidElement(node))return [];return [node,...React.Children.toArray(node.props.children).flatMap(nodes)];}
  const all=nodes(tree),form=all.find(n=>n.type==='form');
  const min={value:'10'},max={value:'25',setCustomValidity() {}};
  const fakeForm={elements:{namedItem:(key)=>key==='minPrice'?min:max},reportValidity:()=>true};
  const originalFormData=global.FormData;
  global.FormData=class {entries(){return Object.entries({brand:'Marca A',minPrice:'10',maxPrice:'25',sort:'highest',page:'4'});}};
  try {form.props.onSubmit({preventDefault(){},currentTarget:fakeForm});} finally {global.FormData=originalFormData;}
  const applied=new URL(pushes[0][0],'http://localhost');
  assert.equal(applied.searchParams.get('brand'),'Marca A');assert.equal(applied.searchParams.get('maxPrice'),'25');assert.equal(applied.searchParams.get('sort'),'highest');assert.equal(applied.searchParams.has('page'),false);assert.equal(applied.hash,'#catalog-results');assert.deepEqual(pushes[0][1],{scroll:false});
  let submitted=false;all.find(n=>n.type==='select'&&n.props.name==='sort').props.onChange({currentTarget:{form:{requestSubmit:()=>{submitted=true;}}}});assert.ok(submitted);
  const pageLink=all.find(n=>n.props['aria-label']==='Página 6');pageLink.props.onNavigate({preventDefault(){}});
  const pageUrl=new URL(pushes[1][0],'http://localhost');assert.equal(pageUrl.searchParams.get('page'),'5');assert.equal(pageUrl.searchParams.get('brand'),'Marca A');assert.equal(pageUrl.searchParams.get('sort'),'lowest');
  all.find(n=>n.props.className==='catalog-button catalog-button-secondary').props.onNavigate({preventDefault(){}});
  assert.equal(pushes[2][0],'/produtos#catalog-results');assert.deepEqual(refreshes,[]);
});

test('pending catalog navigation disables filter actions and announces loading', () => {
  const hooks={...React,useRef:()=>({current:null}),useEffect:()=>{},useState:()=>['apply',()=>{}],useTransition:()=>[true,()=>{}]};
  const {ProductCatalog}=load('app/produtos/ProductCatalog.tsx',{'react':hooks,'next/link':link,'@/components/ProductCard':{ProductCard:()=>null}});
  const facets={categories:[],brands:[],surfaces:[],purposes:[],usages:[],origins:[],types:[],hasConcentrated:false,hasRefill:false};
  const html=renderToStaticMarkup(ProductCatalog({result:{content:[],page:0,size:12,totalElements:0,totalPages:0,facets},params:{}}));
  assert.ok(html.includes('aria-busy="true"'));assert.ok(html.includes('Carregando produtos…'));assert.ok(html.includes('Aplicando…'));assert.ok(html.includes('disabled=""'));
});

test('page navigation scrolls and focuses the beginning of the updated listing', () => {
  const calls=[];
  const hooks={...React,useRef:()=>({current:{scrollIntoView:(options)=>calls.push(['scroll',options]),focus:(options)=>calls.push(['focus',options])}}),useEffect:(fn)=>fn(),useState:()=>['page',()=>{}],useTransition:()=>[false,()=>{}]};
  const {ProductCatalog}=load('app/produtos/ProductCatalog.tsx',{'react':hooks});
  const facets={categories:[],brands:[],surfaces:[],purposes:[],usages:[],origins:[],types:[],hasConcentrated:false,hasRefill:false};
  const originalWindow=global.window;global.window={location:{hash:'#catalog-results'}};
  try {ProductCatalog({result:{content:[],page:1,size:12,totalElements:13,totalPages:2,facets},params:{page:'1'}});} finally {global.window=originalWindow;}
  assert.deepEqual(calls,[['scroll',{block:'start'}],['focus',{preventScroll:true}]]);
});
