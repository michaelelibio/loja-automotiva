'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useState, type FormEvent } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';
import { useProducts } from '@/lib/useProducts';
import { products as fallbackProducts } from '@/data/products';
import { getAddresses } from '@/lib/api/addresses';
import { createOrder } from '@/lib/api/orders';
import { quoteShipping, ShippingApiError } from '@/lib/api/shipping';
import type { Address } from '@/lib/types/address';
import type { ShippingOption, ShippingQuoteRequest } from '@/lib/types/shipping';

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
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

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

  const availableProducts = productsError ? fallbackProducts : products.length > 0 ? products : fallbackProducts;
  const cartProducts = useMemo(() => items.flatMap((item) => {
    const product = availableProducts.find((candidate) => candidate.id === item.productId);
    return product ? [{ ...item, product }] : [];
  }), [items, availableProducts]);
  const subtotal = useMemo(() => cartProducts.reduce((sum, item) => sum + item.product.price * item.quantity, 0), [cartProducts]);
  const requestItems = useMemo(() => cartProducts.map((item) => ({ productId: Number(item.productId),
    ...(item.variantId ? { variantId: Number(item.variantId) } : {}), quantity: item.quantity })), [cartProducts]);
  const selectedAddress = addresses.find((address) => address.id === selectedAddressId) ?? null;
  const selectedShipping = shippingOptions.find((option) => option.code === selectedShippingCode) ?? null;
  const visualTotal = subtotal + (selectedShipping?.price ?? 0);
  const quoteKey = selectedAddress && requestItems.length > 0 ? JSON.stringify({ addressId: selectedAddress.id, zipCode: selectedAddress.zipCode, items: requestItems }) : '';

  useEffect(() => {
    const controller = new AbortController();
    async function loadQuote() {
      setShippingOptions([]); setSelectedShippingCode(null); setShippingError(null);
      if (!quoteKey) { setShippingLoading(false); return; }
      setShippingLoading(true);
      try {
        const trigger = JSON.parse(quoteKey) as ShippingQuoteRequest & { addressId: number };
        const result = await quoteShipping({ zipCode: trigger.zipCode, items: trigger.items }, controller.signal);
        if (controller.signal.aborted) return;
        setShippingOptions(result.options);
        setSelectedShippingCode(result.options.length === 1 ? result.options[0].code : null);
        if (result.options.length === 0) setShippingError('Nenhuma opção de entrega está disponível para este endereço.');
      } catch (reason) {
        if (controller.signal.aborted) return;
        if (reason instanceof ShippingApiError && reason.status === 401) { logout(); router.replace('/login'); return; }
        setShippingError('Não foi possível calcular o frete. Confira o endereço e tente novamente.');
      } finally { if (!controller.signal.aborted) setShippingLoading(false); }
    }
    void loadQuote();
    return () => controller.abort();
  }, [quoteKey, quoteAttempt, logout, router]);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitError(null);
    if (!selectedAddressId) return setSubmitError('Selecione um endereço de entrega.');
    if (requestItems.length === 0) return setSubmitError('Seu carrinho está vazio.');
    if (!selectedShipping) return setSubmitError('Selecione uma opção de entrega válida.');
    setSubmitting(true);
    try {
      const order = await createOrder({ addressId: selectedAddressId, shippingCode: selectedShipping.code, items: requestItems });
      try { sessionStorage.setItem('garage-last-order', JSON.stringify(order)); } catch { /* detalhe também consulta a API */ }
      clearCart();
      router.push(`/conta/pedidos/${order.id}`);
    } catch (reason) { setSubmitError(reason instanceof Error ? reason.message : 'Não foi possível finalizar o pedido.'); }
    finally { setSubmitting(false); }
  }

  function selectAddress(id: number) {
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
    <section className="checkout-fieldset"><h2>Entrega</h2>
      {!selectedAddress && !addressesLoading && <p className="shipping-empty">Selecione um endereço para calcular o frete.</p>}
      {selectedAddress && requestItems.length === 0 && <p className="shipping-empty">Adicione produtos ao carrinho para calcular o frete.</p>}
      {shippingLoading && <div className="shipping-loading" role="status"><span aria-hidden="true" />Calculando frete...</div>}
      {!shippingLoading && shippingError && <div className="shipping-quote-error" role="alert"><p>{shippingError}</p><button type="button" onClick={() => setQuoteAttempt((value) => value + 1)}>Recalcular frete</button></div>}
      {!shippingLoading && !shippingError && shippingOptions.length > 0 && <div className="shipping-options">{shippingOptions.map((option) => <label className={`shipping-option ${selectedShippingCode === option.code ? 'selected' : ''}`} key={option.code}><div className="shipping-option-choice"><input type="radio" name="shipping" checked={selectedShippingCode === option.code} onChange={() => setSelectedShippingCode(option.code)} /><div><strong>{option.name}</strong><span>Estimativa: até {option.estimatedDays} {option.estimatedDays === 1 ? 'dia' : 'dias'}</span></div></div><strong>{currency.format(option.price)}</strong></label>)}</div>}
    </section>
  </div><aside className="checkout-summary"><div className="summary-card"><p className="eyebrow">RESUMO DA COMPRA</p><h2>Seu pedido</h2><div className="summary-totals"><div><span>Produtos</span><strong>{currency.format(subtotal)}</strong></div><div><span>{selectedShipping?.name ?? 'Frete'}</span><strong>{selectedShipping ? currency.format(selectedShipping.price) : '—'}</strong></div><div className="summary-total"><span>Total</span><strong>{currency.format(visualTotal)}</strong></div></div><p className="summary-note">Esta é uma estimativa. Os valores definitivos serão confirmados pelo servidor ao criar o pedido.</p>{submitError && <p className="checkout-error" role="alert">{submitError}</p>}<button type="submit" className="checkout-button" disabled={submitting || requestItems.length === 0 || !selectedAddressId || shippingLoading || !!shippingError || !selectedShipping}>{submitting ? 'Finalizando...' : 'Finalizar pedido'}</button></div></aside></div></form></section>;
}
