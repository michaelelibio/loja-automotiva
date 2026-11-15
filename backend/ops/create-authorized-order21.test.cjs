const {test}=require('node:test');const assert=require('node:assert/strict');
const {technical,payload}=require('./create-authorized-order21.cjs');
function fixture() {return {order:{id:21,status:'PAID',recipient_name:'Synthetic Person',recipient_cpf:'52998224725',recipient_birth_date:'1990-01-01',recipient_phone:'47999999999',street:'Synthetic Street',number:'1',neighborhood:'Center',city:'Joinville',state:'SC',zip_code:'89229040'},
  fulfillment:{provider:'ALIEXPRESS',status:'PENDING',attempt_count:0},
  items:[{item:{supplier:'ALIEXPRESS',fulfillment_type:'DROPSHIPPING',supplier_product_id:'1005007170269840',supplier_variant_id:'12000039689247941',quantity:1,product_id:12},
    variant:{supplier:'ALIEXPRESS',supplier_product_id:'1005007170269840',supplier_variant_id:'12000039689247941',raw_variant_key:'14:1',product_id:12}}]};}
test('technical preparation needs no financial quote and omits unknown delivery service',()=>{
  const d=fixture();technical(d);const p=payload(d);assert.equal(p.out_order_id,'INGARAGE-21');assert.equal(p.ds_extend_request.trade_extra_param.birthday,'01011990');assert.equal(p.logistics_address.zip,d.order.zip_code);assert.equal(p.product_items[0].logistics_service_name,undefined);
});
test('birthday reconciliation is narrow and never automatically retries unknown results',()=>{
  const d=fixture();d.fulfillment.status='VERIFICATION_REQUIRED';d.fulfillment.attempt_count=1;
  d.fulfillment.last_error='AliExpress: B_DROPSHIPPER_DELIVERY_ADDRESS_CPF_BIRTHDAY_NO_MATCH. No automatic retry; reconcile supplier before resending.';
  assert.throws(()=>technical(d));assert.throws(()=>technical(d,true));
  d.fulfillment.last_error='UNKNOWN';assert.throws(()=>technical(d,true));
});
test('prior or ambiguous attempts and references stop the one-shot operation',()=>{
  for(const modify of [d=>d.fulfillment.attempt_count=1,d=>d.fulfillment.supplier_order_id='999',d=>d.fulfillment.supplier_order_ids=['999'],d=>d.adminRun={real_key:'attempt'},d=>d.fulfillment.status='VERIFICATION_REQUIRED']){
    const d=fixture();modify(d);assert.throws(()=>technical(d));
  }
});
test('wrong payment, missing recipient and changed product are rejected',()=>{
  for(const modify of [d=>d.order.status='PENDING_PAYMENT',d=>d.order.recipient_cpf='11111111111',d=>d.order.zip_code='',d=>d.items[0].variant.supplier_variant_id='other',d=>d.items[0].item.quantity=2]){
    const d=fixture();modify(d);assert.throws(()=>technical(d));
  }
});
