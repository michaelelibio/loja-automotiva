# Technical support draft — not sent

Subject: DS freight query docId 1579: monetary unit contradiction and Brazil address-level freight confirmation

We integrate `aliexpress.ds.freight.query` through `https://api-sg.aliexpress.com/sync`, TOP protocol `v=2.0`, `simplify=true`. Our request uses BR destination, BRL currency, productId, selectedSkuId and quantity.

An authenticated production diagnostic returned this sanitized observation for option index 0:

```json
{
  "shipping_fee_cent": "24.00",
  "shipping_fee_currency": "BRL",
  "shipping_fee_format_normalized": "24.00",
  "free_shipping": false
}
```

`shipping_fee_format_normalized` is our diagnostic representation, not an AliExpress response field. We are deliberately blocking commercial checkout because matching fields alone do not establish their official scale.

On 2026-10-27 we re-read official docId 1579 (lastModified 2024-03-04 20:23:41). Its description and generated Java DTO call the field cents, but its example shows a decimal 1.99 matching the formatted USD amount. The current generated DTO retains shippingFeeCent as String without conversion.

Please provide the authoritative contract applicable to this method and gateway:

1. Does `shipping_fee_cent="24.00"`, BRL, mean BRL 24.00 or BRL 0.24? Is the field name/description inaccurate? Is the formatted value in major currency units? Please clarify integer forms (`"24"`, `"2400"`), decimal forms (`"0.24"`, `"24.00"`), zero, allowed precision and whether the rule varies by currency, product origin, app or protocol. Please provide a corrected versioned specification or written confirmation with consistent examples.
2. For Brazilian destinations, which read-only endpoint provides a commercial freight amount AND delivery eligibility for a full address/CEP before order creation? DS freight query exposes province/provinceCode/cityCode but no CEP. Do these fields affect cross-border products, local-to-local products, or both? Can CEP change price/eligibility within the same city? Is there any price lock/quote ID/validity guarantee?
3. Does `aliexpress.ds.address.get(countryCode=BR)` provide the official IDs/names accepted in queryDeliveryReq.provinceCode/cityCode and order logistics_address.province/city/location_tree_address_id? Please provide the children JSON schema and an anonymized Brazilian mapping. Are province names or codes required? We will not assume Brazilian UF or IBGE codes are AE codes.
4. Is `delivery_options[].code` the exact value for `product_items[].logistics_service_name` in `aliexpress.trade.buy.placeorder` and `aliexpress.ds.order.create`, for the same SKU/quantity/destination? The create API error guidance references `aliexpress.freight.query`, for which the public portal returned no API contract. Is that a typo referring to `aliexpress.ds.freight.query`?
5. If full-address freight can only be finalized after order creation, please confirm the supported create-without-payment and subsequent read/reconcile sequence. Does explicit String `ds_extend_request.payment.try_to_pay="false"` guarantee no payment attempt in ds.order.create? What happens if omitted? Does trade.buy.placeorder always create an unpaid order without initiating payment? Which API confirms the unpaid state and final freight/discounts/taxes/currency/total before supplier payment? Are the actual_shipping_fee and other amounts in trade.ds.order.get available in that state, what are their units, and do they remain stable until payment? Please specify timeout, idempotency and cancellation/reconciliation behavior.
6. Which API package permissions are required for address lookup, address-level quote/preflight, order creation and order detail under our DS integration? How can we confirm the business contract revision applied to our app, beyond the TOP protocol version 2.0?

No orders or payments were created during this investigation. We can provide app identification and a provider request reference through your secure support channel if needed; no credentials, access tokens, raw responses or recipient data are included here.
