/* eslint-disable @typescript-eslint/no-require-imports -- isolated TS test loader */
const {test}=require('node:test');const assert=require('node:assert/strict');const fs=require('node:fs'),path=require('node:path'),Module=require('node:module'),ts=require('typescript');
const React=require('react');const {renderToStaticMarkup}=require('react-dom/server');
function load(relative,mocks={}) {const filename=path.resolve(__dirname,'..',relative),mod=new Module(filename,module);mod.filename=filename;mod.paths=Module._nodeModulePaths(path.dirname(filename));mod.require=id=>id in mocks?mocks[id]:id.startsWith('@/')?load(id.slice(2)+'.ts',mocks):Module.prototype.require.call(mod,id);mod._compile(ts.transpileModule(fs.readFileSync(filename,'utf8'),{compilerOptions:{module:ts.ModuleKind.CommonJS,jsx:ts.JsxEmit.ReactJSX,esModuleInterop:true}}).outputText,filename);return mod.exports;}
const api=load('lib/product-options.ts');
const variant=(id,attrs={},extra={})=>({id,name:'1200000000000',attributes:attrs,availableForSale:true,stockQuantity:6,...extra});
test('single propertyless SKU selects its real ID without displaying internal identifiers',()=>{
 const v=variant('35');assert.equal(api.variantOptions([v]).automaticId,'35');assert.equal(api.variantLabel(v),'Opção única');
 assert.deepEqual(api.variantOptions([variant('35',{}, {availableForSale:false})]).available,[]);
});
test('groups real attributes, translates names and removes auxiliary keys with any casing',()=>{
 const attrs={'14':'Preto','14:NAME':'Color','14:VALUEID':'193','200007763':'Brasil','200007763:NAME':'Ships From','200007763:valueId':'1052','size':'500 ml'};
 assert.deepEqual(api.customerAttributes(variant('35',attrs)),{Cor:'Preto','Envio de':'Brasil',Tamanho:'500 ml'});
 const label=api.variantLabel(variant('35',attrs));for(const key of ['VALUEID','NAME','200007763','14'])assert.ok(!label.includes(key));
});
test('only valid real combinations are selectable, unavailable and ambiguous SKUs cannot be chosen',()=>{
 const vs=[variant('1',{Color:'Preto',Size:'P'}),variant('2',{Color:'Preto',Size:'G'}),variant('3',{Color:'Branco',Size:'P'},{stockQuantity:0})];
 const result=api.variantOptions(vs);assert.deepEqual(result.groups,['Tamanho']);assert.deepEqual(result.available.map(r=>r.variant.id),['1','2']);
 assert.equal(api.variantOptions([vs[0],{...vs[1],availableForSale:false}]).automaticId,'1');
 assert.equal(api.variantOptions([vs[0],{...vs[0],id:'4'}]).available.length,0);
 assert.equal(api.variantOptions([variant('1'),variant('2')]).available.length,0);
 assert.equal(api.variantOptions([variant('1',{'14':'Preto',Color:'Branco'})]).available.length,0);
});
test('selector renders customer labels only and adding a sole SKU sends original IDs and shows modal',()=>{
 const vs=[variant('35',{'14':'Preto','14:name':'Color'})];let state=[],index=0,added;
 const react={...React,useState(initial){const n=index++;if(!(n in state))state[n]=typeof initial==='function'?initial():initial;return [state[n],v=>{state[n]=typeof v==='function'?v(state[n]):v;}];}};
 const mocks={react,'next/link':({children,...props})=>React.createElement('a',props,children),'@/context/CartContext':{useCart:()=>({addItem:(...args)=>{added=args;}})},'./ProductVariantSelector':{ProductVariantSelector:()=>null},'@/components/PurchaseDialog':{PurchaseDialog:({title,children})=>React.createElement('div',{role:'dialog'},title,children)}};
 const {ProductPurchase}=load('app/produtos/[slug]/ProductPurchase.tsx',mocks);
 const product={id:'15',name:'Produto teste',availableForSale:true,variants:vs,requiresVariantSelection:true,fulfillmentType:'DROPSHIPPING'};
 function find(tree,fn){if(!tree||typeof tree!=='object')return null;if(fn(tree))return tree;for(const child of React.Children.toArray(tree.props?.children)){const result=find(child,fn);if(result)return result;}return null;}
 let tree=ProductPurchase({product,available:true,selectedVariantId:'',onVariantChange(){}});
 find(tree,n=>n.type==='button'&&n.props.className==='add-cart-button').props.onClick();
 assert.equal(added[0].id,'15');assert.equal(added[1],1);assert.equal(added[2],'35');
 index=0;tree=ProductPurchase({product,available:true,selectedVariantId:'',onVariantChange(){}});
 const html=renderToStaticMarkup(tree);assert.ok(html.includes('Produto adicionado com sucesso'));assert.ok(html.includes('Ver carrinho'));assert.ok(html.includes('Continuar comprando'));assert.ok(!html.includes('14:name'));
});
test('profile calls are authenticated, private, never use storage and never expose error bodies',async()=>{
 const old=global.fetch;const calls=[];
 try {global.fetch=async(url,init)=>{calls.push({url,init});return init.method==='GET'?Response.json({saved:true,recipientName:'Cliente Teste',recipient:{cpf:'52998224725',birthDate:'29/02/2000',phone:'47999999999'}}):new Response(null,{status:204});};
 const client=load('lib/api/addresses.ts',{'@/lib/auth/tokenStorage':{getToken:()=> 'synthetic-token'}});
 await client.getRecipientProfile(7);await client.saveRecipientProfile(7,'Cliente Teste',{cpf:'52998224725',birthDate:'29/02/2000',phone:'47999999999'});await client.deleteRecipientProfile(7);
 assert.deepEqual(calls.map(c=>c.init.method),['GET','PUT','DELETE']);for(const c of calls){assert.equal(c.init.cache,'no-store');assert.equal(c.init.headers.Authorization,'Bearer synthetic-token');assert.ok(!c.url.includes('52998224725'));}
 global.fetch=async()=>new Response('52998224725 private data',{status:500});
 await assert.rejects(client.getRecipientProfile(7),e=>!e.message.includes('52998224725'));
 }finally{global.fetch=old;}
});
