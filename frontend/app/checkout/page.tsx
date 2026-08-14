'use client';

import { Footer } from '@/components/Footer';
import { Header } from '@/components/Header';
import { CheckoutForm } from './CheckoutForm';

export default function CheckoutPage() {
  return (
    <main id="top">
      <Header />
      <section className="checkout-intro" aria-labelledby="checkout-title">
        <p className="eyebrow">Pronto para o próximo passo</p>
        <h1 id="checkout-title">Checkout</h1>
        <p className="checkout-copy">Revise seus produtos e escolha o endereço de entrega antes de finalizar.</p>
      </section>
      <CheckoutForm />
      <Footer />
    </main>
  );
}
