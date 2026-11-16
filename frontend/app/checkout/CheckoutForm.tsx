'use client';

import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useEffect, useMemo, useRef, useState, type FormEvent } from 'react';
import { useAuth } from '@/context/AuthContext';
import { useCart } from '@/context/CartContext';
import { useProducts } from '@/lib/useProducts';
import { getAddresses, getRecipientProfile, saveRecipientProfile, deleteRecipientProfile } from '@/lib/api/addresses';
import { createOrder } from '@/lib/api/orders';
import { checkFreeDelivery, ShippingApiError } from '@/lib/api/shipping';
import type { Address } from '@/lib/types/address';
import type { ShippingOption, ShippingQuoteRequest } from '@/lib/types/shipping';
import { AliExpressRecipientFields } from '@/components/AliExpressRecipientFields';
import { emptyRecipient, validateRecipient, type AliExpressRecipientInput, type RecipientErrors } from '@/lib/checkout/recipient';
import { maskCpf, maskPhone } from '@/lib/checkout/recipient';
import { PurchaseDialog } from '@/components/PurchaseDialog';
import { variantLabel } from '@/lib/product-options';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });
const zip = (value: string) => value.replace(/\D/g, '').replace(/^(\d{5})(\d{3})$/, '$1-$2');

/** A different signed-in account never inherits another account's form memory. */
export function CheckoutSession() {
  const {user}=useAuth();
  return <CheckoutForm key={user?.id??'guest'}/>;
}

export function CheckoutForm() {
  const router = useRouter();
  const { user, isAuthenticated, isLoading: authLoading, sessionError, logout } = useAuth();
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
  const [showConfirmation,setShowConfirmation]=useState(false);
  const [profileLoading,setProfileLoading]=useState(false);
  const [profileError,setProfileError]=useState<string|null>(null);
  const [profileSaved,setProfileSaved]=useState(false);
  const [rememberRecipient,setRememberRecipient]=useState(true);
  const inFlight=useRef(false);
  const completed=useRef(false);
  const reviewContext=useRef('');
  const requestContext=useRef('');

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
  }, [authLoading, sessionError, isAuthenticated, user?.id]);

  useEffect(() => {
    if (!productsLoading && !productsError) reconcileItems(products);
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

  useEffect(()=>{
    const controller=new AbortController();
    let active=true;
    queueMicrotask(()=>{
    if(!active)return;
    setRecipient(emptyRecipient);setProfileSaved(false);setProfileError(null);
    if(!requiresRecipient||!selectedAddress||!isAuthenticated){setProfileLoading(false);return;}
    setProfileLoading(true);
    getRecipientProfile(selectedAddress.id,controller.signal).then(data=>{
      if(!active)return;
      if(data?.saved && data.recipient && data.recipientName.trim()===selectedAddress.recipientName.trim()) {
        const saved={cpf:maskCpf(data.recipient.cpf),birthDate:data.recipient.birthDate,phone:maskPhone(data.recipient.phone)};
        if(!Object.keys(validateRecipient(saved,selectedAddress)).length){setRecipient(saved);setProfileSaved(true);}
      }
    }).catch(()=>{if(active)setProfileError('Não foi possível carregar os dados salvos. Você pode preencher os campos abaixo.');})
      .finally(()=>{if(active)setProfileLoading(false);});
    });
    return ()=>{active=false;controller.abort();};
  },[selectedAddress,requiresRecipient,isAuthenticated,user?.id]);

  useEffect(() => {
    const controller = new AbortController();
    if(requestContext.current!==quoteKey){requestKey.current=null;requestContext.current=quoteKey;}
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

  function validCheckout() {
    if (!selectedAddressId) {setSubmitError('Selecione um endereço de entrega.');return false;}
    if (productsLoading || productsError || requestItems.length !== items.length) {setSubmitError('Atualize os produtos antes de finalizar.');return false;}
    if (requestItems.length === 0) {setSubmitError('Seu carrinho está vazio.');return false;}
    if(profileLoading){setSubmitError('Aguarde o carregamento dos dados do destinatário.');return false;}
    if (requiresRecipient) {
      const errors = validateRecipient(recipient, selectedAddress);
      setRecipientErrors(errors);
      if (Object.keys(errors).length) {setSubmitError('Confira os dados do destinatário destacados abaixo.');return false;}
    }
    if (shippingLoading || resolvedQuoteKey !== quoteKey || !selectedShipping || selectedShipping.code !== 'CATALOG_FREE' || selectedShipping.price !== 0) {setSubmitError('Selecione uma opção de entrega válida.');return false;}
    return true;
  }
  function submit(event:FormEvent<HTMLFormElement>) {
    event.preventDefault();setSubmitError(null);
    if(inFlight.current||completed.current||!validCheckout())return;
    reviewContext.current=JSON.stringify({quoteKey,visualTotal,recipient});
    setShowConfirmation(true);
  }
  async function confirmOrder() {
    if(inFlight.current||completed.current)return;
    if(!validCheckout()||reviewContext.current!==JSON.stringify({quoteKey,visualTotal,recipient})) {
      setShowConfirmation(false);setSubmitError('Os dados mudaram. Confira o resumo novamente.');return;
    }
    if(selectedAddressId==null)return;
    inFlight.current=true;
    setSubmitting(true);
    try {
      if(requiresRecipient&&rememberRecipient&&selectedAddress)await saveRecipientProfile(selectedAddress.id,selectedAddress.recipientName,recipient);
      if (!requestKey.current) requestKey.current = crypto.randomUUID();
      const order = await createOrder({ addressId: selectedAddressId,
        shippingCode: 'CATALOG_FREE', items: requestItems,
        requestKey: requestKey.current,
        ...(requiresRecipient ? { recipient } : {}),
        ...(selectedAddress ? {expectedTotal:Math.round(visualTotal*100)/100,expectedAddress:{recipientName:selectedAddress.recipientName,zipCode:selectedAddress.zipCode,street:selectedAddress.street,number:selectedAddress.number,complement:selectedAddress.complement,neighborhood:selectedAddress.neighborhood,city:selectedAddress.city,state:selectedAddress.state}} : {}) });
      setRecipient(emptyRecipient); setRecipientErrors({});
      completed.current=true;
      clearCart();
      router.push(`/conta/pedidos/${order.id}`);
    } catch { setSubmitError('Não foi possível concluir agora. Tente novamente neste resumo; a solicitação será identificada para evitar duplicidade.'); }
    finally { inFlight.current=false;setSubmitting(false); }
  }

  function selectAddress(id: number) {
    setRecipient(emptyRecipient); setRecipientErrors({});setProfileLoading(requiresRecipient);
    setSelectedAddressId(id); setShippingOptions([]); setSelectedShippingCode(null); setShippingError(null);
  }

  if (authLoading || sessionError || !isAuthenticated || !isHydrated) return <section className="checkout-section checkout-state"><p>{sessionError ?? 'Preparando seu checkout...'}</p></section>;

  return <section className="checkout-section"><form onSubmit={submit}><div className="checkout-columns"><div className="checkout-panel">
    <section className="checkout-fieldset"><h2>Produtos</h2>{cartProducts.length === 0 ? <div className="checkout-empty"><p>Seu carrinho está vazio.</p><Link href="/produtos">Ver produtos</Link></div> : <div className="checkout-products">{cartProducts.map(({ product, variantId, quantity }) => <article key={`${product.id}:${variantId ?? ''}`}>
      <div className="checkout-product-image" style={{ backgroundColor: product.accent }}>{product.image && <img src={product.image} alt="" />}</div><div><h3>{product.name}</h3>{variantId && <p>{variantLabel(product.variants.find((variant) => variant.id === variantId))}</p>}<p>{currency.format(product.price)} por unidade</p><span>Quantidade: {quantity}</span></div><strong>{currency.format(product.price * quantity)}</strong>
    </article>)}</div>}</section>
    <section className="checkout-fieldset"><div className="checkout-section-heading"><h2>Endereço de entrega</h2>{addresses.length > 0 && <Link href="/conta#addresses">Gerenciar endereços</Link>}</div>
      {addressesLoading && <p className="account-status">Carregando endereços...</p>}
      {addressesError && <p className="checkout-error">{addressesError}</p>}
      {!addressesLoading && !addressesError && addresses.length === 0 && <div className="checkout-empty"><p>Você precisa cadastrar um endereço para continuar.</p><Link href="/conta#addresses">Cadastrar endereço</Link></div>}
      <div className="checkout-addresses">{addresses.map((address) => <label className={selectedAddressId === address.id ? 'selected' : ''} key={address.id}><input type="radio" name="address" checked={selectedAddressId === address.id} onChange={() => selectAddress(address.id)} /><div><div className="checkout-address-title"><strong>{address.label || 'Endereço'}</strong>{address.isPrimary && <span>Principal</span>}</div><p>{address.recipientName}</p><p>{address.street}, {address.number}{address.complement ? ` · ${address.complement}` : ''}</p><p>{address.neighborhood} · {address.city} - {address.state}</p><p>CEP {zip(address.zipCode)}</p></div></label>)}</div>
    </section>
    {requiresRecipient && <><AliExpressRecipientFields value={recipient} onChange={(value) => { setRecipient(value); setRecipientErrors({}); }} errors={recipientErrors} disabled={submitting||profileLoading} />
      {profileLoading&&<p role="status">Carregando dados deste destinatário...</p>}
      {profileError&&<p role="status">{profileError}</p>}
      <label className="recipient-remember"><input type="checkbox" checked={rememberRecipient} disabled={submitting||profileLoading} onChange={e=>setRememberRecipient(e.target.checked)}/>Salvar estes dados para futuras compras neste endereço.</label>
      <p className="recipient-save-note">Os dados são protegidos na sua conta e podem ser editados antes de confirmar.</p>
      {profileSaved&&<button type="button" disabled={submitting||profileLoading} onClick={async()=>{
        if(!selectedAddress)return;
        setProfileLoading(true);
        try{await deleteRecipientProfile(selectedAddress.id);setProfileSaved(false);setRememberRecipient(false);setRecipient(emptyRecipient);setProfileError(null);}
        catch{setProfileError('Não foi possível remover os dados salvos. Tente novamente.');}
        finally{setProfileLoading(false);}
      }}>Remover dados salvos deste endereço</button>}</>}
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
    <button type="submit" className="checkout-button" disabled={submitting || profileLoading || resolvedQuoteKey !== quoteKey || requestItems.length === 0 || !selectedAddressId || shippingLoading || !!shippingError || !selectedShipping || !!productsError || productsLoading}>
      {submitting ? 'Registrando...' : 'Finalizar pedido'}
    </button>
  </div></aside></div>
    {showConfirmation&&<PurchaseDialog title="Confira seu pedido" onClose={()=>setShowConfirmation(false)} busy={submitting}>
      <ul className="purchase-review-items">{cartProducts.map(({product,variantId,quantity})=><li key={`${product.id}:${variantId??''}`}><strong>{product.name}</strong><span>{variantLabel(product.variants.find(v=>v.id===variantId))}</span><span>{quantity} × {currency.format(product.price)}</span></li>)}</ul>
      <p><strong>Entrega para {selectedAddress?.recipientName}</strong><br/>{selectedAddress?.street}, {selectedAddress?.number} · {selectedAddress?.city} - {selectedAddress?.state}</p>
      <p>Entrega grátis · Total <strong>{currency.format(visualTotal)}</strong></p>
      <p>Ao confirmar, você registra o pedido. O pagamento será feito na próxima etapa.</p>
      {submitError&&<p className="checkout-error" role="alert">{submitError}</p>}
      <div className="purchase-dialog-actions"><button type="button" className="purchase-primary" disabled={submitting} onClick={()=>setShowConfirmation(false)}>Voltar e editar</button>
        <button type="button" className="purchase-primary" disabled={submitting||profileLoading} onClick={confirmOrder}>{submitting?'Registrando...':'Confirmar pedido'}</button></div>
    </PurchaseDialog>}
  </form></section>;
}
