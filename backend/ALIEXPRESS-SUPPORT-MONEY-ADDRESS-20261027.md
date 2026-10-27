# Technical support draft — not sent

Subject: DS freight query docId 1579: monetary unit contradiction and Brazil address-level freight confirmation

Updated evidence for this ticket (reported production diagnostic, 2026-10-27): productId `1005010661577657`, selectedSkuId `12000053109276547`, quantity 1. We sent one request with shipToCountry=BR and one with BR plus province="Santa Catarina", otherwise identical. Both requests were written and received HTTP responses. Both returned shipping_fee_cent="24.00", BRL and normalized formatted amount="24.00" (our comparison MAJOR_MATCH). Our sanitized projection showed NO_OBSERVED_DIFFERENCE. This does not prove equality of the complete response or that province was ignored.

An authenticated ds.address.get call in the same production environment recognized BR -> Santa Catarina -> Joinville, with children/country/name/type only, without numeric territorial IDs or city codes. The previous freight diagnostic suppressed ship_from_country when rejecting ambiguous money; therefore the real shipping origin is still unknown, not proven absent from your response. We have prepared a separate allowlisted origin/service-code projection without approving the price.

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
7. Is province intended to affect only local-to-local products? How do we determine this classification for the exact SKU above? Is there an explicit documented flag/API, or is ship_from_country=shipToCountry sufficient? Please distinguish store_country_code, SKU origin selection, delivery_options[].ship_from_country and the actual warehouse. If these disagree, which source is authoritative? Does any buyer/DS API expose warehouse identity or stock by warehouse?
8. Is equal freight with and without province expected for an international route, a local route with equal regional rates, or some other case? Please confirm whether province="Santa Catarina" is accepted and actually applied for this product/SKU; successful transport alone does not establish this. If codes are required for BR, please provide their official source and mapping rather than assuming UF/IBGE codes. Please confirm how to verify the exact returned delivery_options[].code for the final Brazilian address without creating an order.

Our immediate operational goal is one supervised sale with a full-address commercial quote before charging the customer. Please prioritize an authoritative monetary rule, the SKU's origin/classification, and a supported full-address freight/eligibility confirmation path. If no read-only API exists, please state that limitation and the supported unpaid-order/read/reconcile sequence, without requiring any payment attempt as a test. We will not use a country-only estimate as final checkout freight.

No orders or payments were created during this investigation. We can provide app identification and a provider request reference through your secure support channel if needed; no credentials, access tokens, raw responses or recipient data are included here.
