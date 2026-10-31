'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';
import { useProducts } from '@/lib/useProducts';
import { getAddresses } from '@/lib/api/addresses';
import { createOrder } from '@/lib/api/orders';
import { checkFreeDelivery, ShippingApiError } from '@/lib/api/shipping';
import type { Address } from '@/lib/types/address';
import type { ShippingOption, ShippingQuoteRequest } from '@/lib/types/shipping';
import { AliExpressRecipientFields } from '@/components/AliExpressRecipientFields';
import { emptyRecipient, validateRecipient, type AliExpressRecipientInput, type RecipientErrors } from '@/lib/checkout/recipient';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const zip = (value: string) => value.replace(/\D/g, '').replace(/^(\d{5})(\d{3})$/, '$1-$2');

export function CheckoutForm() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, sessionError, logout } = useAuth();
  const { items, isHydrated, clearCart, reconcileItems } = useCart();
  const { products, loading: productsLoading, error: productsError } = useProducts();
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [addressesLoading, setAddressesLoading] = useState(true);
  const [addressesError, setAddressesError] = useState<string | null>(null);
  const [shippingOptions, setShippingOptions] = useState<ShippingOption[]>([]);
  const [selectedShippingCode, setSelectedShippingCode] = useState<string | null>(null);
  const [shippingLoading, setShippingLoading] = useState(false);
  const [shippingError, setShippingError] = useState<string | null>(null);
  const [quoteAttempt, setQuoteAttempt] = useState(0);
  const requestKey = useRef<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);
  const [recipient, setRecipient] = useState<AliExpressRecipientInput>(emptyRecipient);
  const [recipientErrors, setRecipientErrors] = useState<RecipientErrors>({});
  const [resolvedQuoteKey,setResolvedQuoteKey]=useState('');

  useEffect(() => {
    if (!authLoading && !sessionError && !isAuthenticated) router.replace('/login');
  }, [authLoading, sessionError, isAuthenticated, router]);

  useEffect(() => {
    if (authLoading || sessionError || !isAuthenticated) return;
    let active = true;
    getAddresses().then((data) => {
      if (!active) return;
      setAddresses(data);
      setSelectedAddressId(data.find((address) => address.isPrimary)?.id ?? data[0]?.id ?? null);
    }).catch(() => { if (active) setAddressesError('Não foi possível carregar seus endereços.'); })
      .finally(() => { if (active) setAddressesLoading(false); });
    return () => { active = false; };
  }, [authLoading, sessionError, isAuthenticated]);

  useEffect(() => {
    if (!productsLoading && !productsError) reconcileItems(products.map((product) => product.id));
  }, [productsLoading, productsError, products, reconcileItems]);

  const availableProducts = productsError ? [] : products;
  const cartProducts = useMemo(() => items.flatMap((item) => {
    const product = availableProducts.find((candidate) => candidate.id === item.productId);
    return product ? [{ ...item, product }] : [];
  }), [items, availableProducts]);
  const subtotal = useMemo(() => cartProducts.reduce((sum, item) => sum + item.product.price * item.quantity, 0), [cartProducts]);
  const requestItems = useMemo(() => cartProducts.map((item) => ({ productId: Number(item.productId),
    ...(item.variantId ? { variantId: Number(item.variantId) } : {}), quantity: item.quantity })), [cartProducts]);
  const selectedAddress = addresses.find((address) => address.id === selectedAddressId) ?? null;
  const requiresRecipient = cartProducts.some(({ product }) => product.requiresAliExpressRecipientData === true);
  const selectedShipping = shippingOptions.find((option) => option.code === selectedShippingCode) ?? null;
  const visualTotal = subtotal + (selectedShipping?.price ?? 0);
  const quoteKey = selectedAddress && requestItems.length > 0 ? JSON.stringify({ addressId: selectedAddress.id, address:selectedAddress, zipCode: selectedAddress.zipCode, items: requestItems }) : '';

  useEffect(() => {
    const controller = new AbortController();
    requestKey.current=null;
    async function loadQuote() {
      setShippingOptions([]); setSelectedShippingCode(null); setShippingError(null);
      if (!quoteKey) { setShippingLoading(false); return; }
      setShippingLoading(true);
      try {
        const trigger = JSON.parse(quoteKey) as ShippingQuoteRequest & { addressId: number };
        const result = await checkFreeDelivery(trigger.addressId, { zipCode: trigger.zipCode, items: trigger.items }, controller.signal);
        if (controller.signal.aborted) return;
        if (result.options.length !== 1 || result.options[0].code !== 'CATALOG_FREE' || result.options[0].price !== 0) throw new Error('Entrega indisponível');
        setShippingOptions(result.options);
        setSelectedShippingCode(result.options.length === 1 ? result.options[0].code : null);
      } catch (reason) {
        if (controller.signal.aborted) return;
        if (reason instanceof ShippingApiError && reason.status === 401) { logout(); router.replace('/login'); return; }
        if (reason instanceof ShippingApiError && reason.status === 409) {
          setShippingError('Um dos produtos selecionados está indisponível para compra.');
        } else if (reason instanceof ShippingApiError && reason.status >= 500) {
          setShippingError('Não foi possível validar o pedido. Tente novamente.');
        } else {
          setShippingError('Confira os produtos e o endereço e tente novamente.');
        }
      } finally { if (!controller.signal.aborted) {setShippingLoading(false);setResolvedQuoteKey(quoteKey);} }
    }
    void loadQuote();
    return () => controller.abort();
  }, [quoteKey, quoteAttempt, logout, router, requiresRecipient]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitError(null);
    if (!selectedAddressId) return setSubmitError('Selecione um endereço de entrega.');
    if (productsLoading || productsError || requestItems.length !== items.length) return setSubmitError('Atualize os produtos antes de finalizar.');
    if (requestItems.length === 0) return setSubmitError('Seu carrinho está vazio.');
    if (requiresRecipient) {
      const errors = validateRecipient(recipient, selectedAddress);
      setRecipientErrors(errors);
      if (Object.keys(errors).length) return setSubmitError('Confira os dados do destinatário destacados abaixo.');
    }
    if (shippingLoading || resolvedQuoteKey !== quoteKey || !selectedShipping || selectedShipping.code !== 'CATALOG_FREE' || selectedShipping.price !== 0) return setSubmitError('Selecione uma opção de entrega válida.');
    setSubmitting(true);
    try {
      if (!requestKey.current) requestKey.current = crypto.randomUUID();
      const order = await createOrder({ addressId: selectedAddressId,
        shippingCode: 'CATALOG_FREE', items: requestItems,
        requestKey: requestKey.current,
        ...(requiresRecipient ? { recipient } : {}),
        ...(selectedAddress ? {expectedTotal:Math.round(visualTotal*100)/100,expectedAddress:{recipientName:selectedAddress.recipientName,zipCode:selectedAddress.zipCode,street:selectedAddress.street,number:selectedAddress.number,complement:selectedAddress.complement,neighborhood:selectedAddress.neighborhood,city:selectedAddress.city,state:selectedAddress.state}} : {}) });
      setRecipient(emptyRecipient); setRecipientErrors({});
      try { sessionStorage.setItem('garage-last-order', JSON.stringify(order)); } catch { /* detalhe também consulta a API */ }
      clearCart();
      router.push(`/conta/pedidos/${order.id}`);
    } catch (reason) { setSubmitError(reason instanceof Error ? reason.message : 'Não foi possível finalizar o pedido.'); }
    finally { setSubmitting(false); }
  }

  function selectAddress(id: number) {
    setRecipient(emptyRecipient); setRecipientErrors({});
    setSelectedAddressId(id); setShippingOptions([]); setSelectedShippingCode(null); setShippingError(null);
  }

  if (authLoading || sessionError || !isAuthenticated || !isHydrated) return <section className="checkout-section checkout-state"><p>{sessionError ?? 'Preparando seu checkout...'}</p></section>;

  return <section className="checkout-section"><form onSubmit={submit}><div className="checkout-columns"><div className="checkout-panel">
    <section className="checkout-fieldset"><h2>Produtos</h2>{cartProducts.length === 0 ? <div className="checkout-empty"><p>Seu carrinho está vazio.</p><Link href="/produtos">Ver produtos</Link></div> : <div className="checkout-products">{cartProducts.map(({ product, variantId, quantity }) => <article key={`${product.id}:${variantId ?? ''}`}>
      <div className="checkout-product-image" style={{ backgroundColor: product.accent }}>{product.image && <img src={product.image} alt="" />}</div><div><h3>{product.name}</h3>{variantId && <p>{product.variants.find((variant) => variant.id === variantId)?.name}</p>}<p>{currency.format(product.price)} por unidade</p><span>Quantidade: {quantity}</span></div><strong>{currency.format(product.price * quantity)}</strong>
    </article>)}</div>}</section>
    <section className="checkout-fieldset"><div className="checkout-section-heading"><h2>Endereço de entrega</h2>{addresses.length > 0 && <Link href="/conta#addresses">Gerenciar endereços</Link>}</div>
      {addressesLoading && <p className="account-status">Carregando endereços...</p>}
      {addressesError && <p className="checkout-error">{addressesError}</p>}
      {!addressesLoading && !addressesError && addresses.length === 0 && <div className="checkout-empty"><p>Você precisa cadastrar um endereço para continuar.</p><Link href="/conta#addresses">Cadastrar endereço</Link></div>}
      <div className="checkout-addresses">{addresses.map((address) => <label className={selectedAddressId === address.id ? 'selected' : ''} key={address.id}><input type="radio" name="address" checked={selectedAddressId === address.id} onChange={() => selectAddress(address.id)} /><div><div className="checkout-address-title"><strong>{address.label || 'Endereço'}</strong>{address.isPrimary && <span>Principal</span>}</div><p>{address.recipientName}</p><p>{address.street}, {address.number}{address.complement ? ` · ${address.complement}` : ''}</p><p>{address.neighborhood} · {address.city} - {address.state}</p><p>CEP {zip(address.zipCode)}</p></div></label>)}</div>
    </section>
    {requiresRecipient && <AliExpressRecipientFields value={recipient} onChange={(value) => { setRecipient(value); setRecipientErrors({}); }} errors={recipientErrors} disabled={submitting} />}
    <section className="checkout-fieldset"><h2>Entrega</h2>
      {!selectedAddress && <p>Selecione um endereço de entrega.</p>}
      {shippingLoading && <p role="status">Verificando disponibilidade de entrega...</p>}
      {!shippingLoading && shippingError && <div role="alert"><p>Não foi possível validar os produtos do pedido. Confira os itens e o endereço.</p><button type="button" onClick={() => setQuoteAttempt(value => value + 1)}>Tentar novamente</button></div>}
      {!shippingLoading && selectedShipping && resolvedQuoteKey === quoteKey && <p>Entrega grátis (R$ 0,00).</p>}
    </section>
  </div><aside className="checkout-summary"><div className="summary-card">
    <p className="eyebrow">RESUMO DA COMPRA</p><h2>Seu pedido</h2>
    <div className="summary-totals">
      <div><span>Produtos</span><strong>{currency.format(subtotal)}</strong></div>
      <div><span>Entrega</span><strong>{selectedShipping && resolvedQuoteKey === quoteKey ? 'Grátis' : 'A confirmar'}</strong></div>
      <div className="summary-total"><span>Total</span><strong>{selectedShipping && resolvedQuoteKey === quoteKey ? currency.format(visualTotal) : 'A confirmar'}</strong></div>
    </div>
    <p className="summary-note">Os valores e a disponibilidade serão validados novamente ao finalizar.</p>
    {submitError && <p className="checkout-error" role="alert">{submitError}</p>}
    <button type="submit" className="checkout-button" disabled={submitting || resolvedQuoteKey !== quoteKey || requestItems.length === 0 || !selectedAddressId || shippingLoading || !!shippingError || !selectedShipping || !!productsError || productsLoading}>
      {submitting ? 'Registrando...' : 'Finalizar pedido'}
    </button>
  </div></aside></div></form></section>;
}
