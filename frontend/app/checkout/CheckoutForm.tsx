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
import type { Address } from '@/lib/types/address';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const zip = (value: string) => value.replace(/\D/g, '').replace(/^(\d{5})(\d{3})$/, '$1-$2');

export function CheckoutForm() {
  const router = useRouter();
  const { isAuthenticated, isLoading: authLoading, sessionError } = useAuth();
  const { items, isHydrated, clearCart, reconcileItems } = useCart();
  const { products, loading: productsLoading, error: productsError } = useProducts();
  const [addresses, setAddresses] = useState<Address[]>([]);
  const [selectedAddressId, setSelectedAddressId] = useState<number | null>(null);
  const [addressesLoading, setAddressesLoading] = useState(false);
  const [addressesError, setAddressesError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  useEffect(() => {
    if (!authLoading && !sessionError && !isAuthenticated) router.replace('/login');
  }, [authLoading, sessionError, isAuthenticated, router]);

  useEffect(() => {
    if (authLoading || sessionError || !isAuthenticated) return;
    let active = true;
    setAddressesLoading(true); setAddressesError(null);
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

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault(); setSubmitError(null);
    if (!selectedAddressId) return setSubmitError('Selecione um endereço de entrega.');
    if (items.length === 0) return setSubmitError('Seu carrinho está vazio.');
    const requestItems = cartProducts.map((item) => ({ productId: Number(item.productId), quantity: item.quantity }));
    setSubmitting(true);
    try {
      const order = await createOrder({ addressId: selectedAddressId, items: requestItems });
      try { sessionStorage.setItem('garage-last-order', JSON.stringify(order)); } catch { /* detalhe também consulta a API */ }
      clearCart();
      router.push(`/conta/pedidos/${order.id}`);
    } catch (reason) { setSubmitError(reason instanceof Error ? reason.message : 'Não foi possível finalizar o pedido.'); }
    finally { setSubmitting(false); }
  }

  if (authLoading || sessionError || !isAuthenticated || !isHydrated) return <section className="checkout-section checkout-state"><p>{sessionError ?? 'Preparando seu checkout...'}</p></section>;

  return <section className="checkout-section"><form onSubmit={submit}><div className="checkout-columns"><div className="checkout-panel">
    <section className="checkout-fieldset"><h2>Produtos</h2>{cartProducts.length === 0 ? <div className="checkout-empty"><p>Seu carrinho está vazio.</p><Link href="/produtos">Ver produtos</Link></div> : <div className="checkout-products">{cartProducts.map(({ product, quantity }) => <article key={product.id}>
      <div className="checkout-product-image" style={{ backgroundColor: product.accent }}>{product.image && <img src={product.image} alt="" />}</div><div><h3>{product.name}</h3><p>{currency.format(product.price)} por unidade</p><span>Quantidade: {quantity}</span></div><strong>{currency.format(product.price * quantity)}</strong>
    </article>)}</div>}</section>
    <section className="checkout-fieldset"><div className="checkout-section-heading"><h2>Endereço de entrega</h2>{addresses.length > 0 && <Link href="/conta#addresses">Gerenciar endereços</Link>}</div>
      {addressesLoading && <p className="account-status">Carregando endereços...</p>}
      {addressesError && <p className="checkout-error">{addressesError}</p>}
      {!addressesLoading && !addressesError && addresses.length === 0 && <div className="checkout-empty"><p>Você precisa cadastrar um endereço para continuar.</p><Link href="/conta#addresses">Cadastrar endereço</Link></div>}
      <div className="checkout-addresses">{addresses.map((address) => <label className={selectedAddressId === address.id ? 'selected' : ''} key={address.id}><input type="radio" name="address" checked={selectedAddressId === address.id} onChange={() => setSelectedAddressId(address.id)} /><div><div className="checkout-address-title"><strong>{address.label || 'Endereço'}</strong>{address.isPrimary && <span>Principal</span>}</div><p>{address.recipientName}</p><p>{address.street}, {address.number}{address.complement ? ` · ${address.complement}` : ''}</p><p>{address.neighborhood} · {address.city} - {address.state}</p><p>CEP {zip(address.zipCode)}</p></div></label>)}</div>
    </section>
  </div><aside className="checkout-summary"><div className="summary-card"><p className="eyebrow">RESUMO DA COMPRA</p><h2>Seu pedido</h2><div className="summary-totals"><div><span>Subtotal</span><strong>{currency.format(subtotal)}</strong></div><div><span>Frete</span><strong>Grátis</strong></div><div className="summary-total"><span>Total</span><strong>{currency.format(subtotal)}</strong></div></div><p className="summary-note">O valor definitivo será confirmado pelo servidor ao criar o pedido.</p>{submitError && <p className="checkout-error" role="alert">{submitError}</p>}<button type="submit" className="checkout-button" disabled={submitting || items.length === 0 || !selectedAddressId}>{submitting ? 'Finalizando...' : 'Finalizar pedido'}</button></div></aside></div></form></section>;
}
