'use client';

import { useMemo, useState } from 'react';
import { useCart } from '@/context/CartContext';
import { useProducts } from '@/lib/useProducts';
import { products as fallbackProducts } from '@/data/products';

const currency = new Intl.NumberFormat('pt-BR', { style: 'currency', currency: 'BRL' });

type ShippingOption = {
  id: 'standard' | 'express';
  label: string;
  description: string;
  price: number;
};

const shippingOptions: ShippingOption[] = [
  { id: 'standard', label: 'Entrega padrão', description: '5 a 8 dias úteis', price: 12.9 },
  { id: 'express', label: 'Entrega expressa', description: '2 a 4 dias úteis', price: 29.9 },
];

const paymentOptions = [
  { id: 'pix', label: 'PIX' },
  { id: 'card', label: 'Cartão de crédito' },
];

const initialForm = {
  name: '',
  email: '',
  cpf: '',
  phone: '',
  cep: '',
  street: '',
  number: '',
  complement: '',
  district: '',
  city: '',
  state: '',
};

const requiredFields = [
  'name',
  'email',
  'cpf',
  'phone',
  'cep',
  'street',
  'number',
  'district',
  'city',
  'state',
] as const;

type FormField = keyof typeof initialForm;

type FormState = typeof initialForm;

type FormErrors = Partial<Record<FormField, string>>;

function isEmailValid(value: string) {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

function isCpfValid(value: string) {
  const digits = value.replace(/\D/g, '');
  return digits.length === 11;
}

function isPhoneValid(value: string) {
  const digits = value.replace(/\D/g, '');
  return digits.length >= 10 && digits.length <= 11;
}

function isCepValid(value: string) {
  const digits = value.replace(/\D/g, '');
  return digits.length === 8;
}

export function CheckoutForm() {
  const { items } = useCart();
  const { products, error } = useProducts();
  const [form, setForm] = useState<FormState>(initialForm);
  const [selectedShipping, setSelectedShipping] = useState<ShippingOption>(shippingOptions[0]);
  const [selectedPayment, setSelectedPayment] = useState(paymentOptions[0].id);
  const [errors, setErrors] = useState<FormErrors>({});
  const [successMessage, setSuccessMessage] = useState('');

  const availableProducts = error ? fallbackProducts : products.length > 0 ? products : fallbackProducts;

  const cartProducts = useMemo(
    () => items.flatMap((item) => {
      const product = availableProducts.find((candidate) => candidate.id === item.productId);
      return product ? [{ ...item, product }] : [];
    }),
    [items, availableProducts],
  );

  const productsSubtotal = useMemo(
    () => cartProducts.reduce((sum, item) => sum + item.product.price * item.quantity, 0),
    [cartProducts],
  );

  const total = useMemo(() => productsSubtotal + selectedShipping.price, [productsSubtotal, selectedShipping.price]);

  function updateField(field: FormField, value: string) {
    setForm((current) => ({ ...current, [field]: value }));
  }

  function validateForm() {
    const nextErrors: FormErrors = {};

    requiredFields.forEach((field) => {
      if (!form[field].trim()) {
        nextErrors[field] = 'Campo obrigatório';
      }
    });

    if (form.email && !isEmailValid(form.email)) {
      nextErrors.email = 'E-mail inválido';
    }

    if (form.cpf && !isCpfValid(form.cpf)) {
      nextErrors.cpf = 'CPF deve conter 11 dígitos';
    }

    if (form.phone && !isPhoneValid(form.phone)) {
      nextErrors.phone = 'Telefone inválido';
    }

    if (form.cep && !isCepValid(form.cep)) {
      nextErrors.cep = 'CEP inválido';
    }

    setErrors(nextErrors);
    return Object.keys(nextErrors).length === 0;
  }

  function handleSubmit(event: React.FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSuccessMessage('');

    if (!validateForm()) {
      return;
    }

    setSuccessMessage('Checkout preparado. O pagamento será integrado posteriormente.');
  }

  return (
    <section className="checkout-section">
      <form className="checkout-form" onSubmit={handleSubmit} noValidate>
        <div className="checkout-columns">
          <div className="checkout-panel">
            <fieldset className="checkout-fieldset">
              <legend>DADOS DO CLIENTE</legend>
              <div className="field-grid">
                <label>
                  Nome completo
                  <input type="text" value={form.name} onChange={(event) => updateField('name', event.target.value)} />
                  {errors.name && <span className="field-error">{errors.name}</span>}
                </label>
                <label>
                  E-mail
                  <input type="email" value={form.email} onChange={(event) => updateField('email', event.target.value)} />
                  {errors.email && <span className="field-error">{errors.email}</span>}
                </label>
                <label>
                  CPF
                  <input type="text" value={form.cpf} onChange={(event) => updateField('cpf', event.target.value)} maxLength={14} placeholder="000.000.000-00" />
                  {errors.cpf && <span className="field-error">{errors.cpf}</span>}
                </label>
                <label>
                  Telefone
                  <input type="text" value={form.phone} onChange={(event) => updateField('phone', event.target.value)} maxLength={15} placeholder="(00) 00000-0000" />
                  {errors.phone && <span className="field-error">{errors.phone}</span>}
                </label>
              </div>
            </fieldset>

            <fieldset className="checkout-fieldset">
              <legend>ENDEREÇO DE ENTREGA</legend>
              <div className="field-grid">
                <label>
                  CEP
                  <input type="text" value={form.cep} onChange={(event) => updateField('cep', event.target.value)} maxLength={9} placeholder="00000-000" />
                  {errors.cep && <span className="field-error">{errors.cep}</span>}
                </label>
                <label>
                  Rua
                  <input type="text" value={form.street} onChange={(event) => updateField('street', event.target.value)} />
                  {errors.street && <span className="field-error">{errors.street}</span>}
                </label>
                <label>
                  Número
                  <input type="text" value={form.number} onChange={(event) => updateField('number', event.target.value)} />
                  {errors.number && <span className="field-error">{errors.number}</span>}
                </label>
                <label>
                  Complemento
                  <input type="text" value={form.complement} onChange={(event) => updateField('complement', event.target.value)} />
                </label>
                <label>
                  Bairro
                  <input type="text" value={form.district} onChange={(event) => updateField('district', event.target.value)} />
                  {errors.district && <span className="field-error">{errors.district}</span>}
                </label>
                <label>
                  Cidade
                  <input type="text" value={form.city} onChange={(event) => updateField('city', event.target.value)} />
                  {errors.city && <span className="field-error">{errors.city}</span>}
                </label>
                <label>
                  Estado
                  <input type="text" value={form.state} onChange={(event) => updateField('state', event.target.value)} maxLength={2} placeholder="SP" />
                  {errors.state && <span className="field-error">{errors.state}</span>}
                </label>
              </div>
            </fieldset>

            <fieldset className="checkout-fieldset">
              <legend>ENTREGA</legend>
              <div className="shipping-options">
                {shippingOptions.map((option) => (
                  <label key={option.id} className={`shipping-option${selectedShipping.id === option.id ? ' selected' : ''}`}>
                    <input type="radio" name="shipping" value={option.id} checked={selectedShipping.id === option.id} onChange={() => setSelectedShipping(option)} />
                    <div>
                      <strong>{option.label}</strong>
                      <span>{option.description}</span>
                    </div>
                    <strong>{currency.format(option.price)}</strong>
                  </label>
                ))}
              </div>
            </fieldset>

            <fieldset className="checkout-fieldset">
              <legend>PAGAMENTO</legend>
              <div className="payment-options">
                {paymentOptions.map((option) => (
                  <label key={option.id} className={`payment-option${selectedPayment === option.id ? ' selected' : ''}`}>
                    <input type="radio" name="payment" value={option.id} checked={selectedPayment === option.id} onChange={() => setSelectedPayment(option.id)} />
                    <span>{option.label}</span>
                  </label>
                ))}
              </div>
            </fieldset>
          </div>

          <aside className="checkout-summary">
            <div className="summary-card">
              <h2>Resumo do pedido</h2>
              {cartProducts.length === 0 ? (
                <p className="summary-empty">O carrinho está vazio. Adicione produtos antes de finalizar.</p>
              ) : (
                <div className="summary-items">
                  {cartProducts.map(({ product, quantity }) => (
                    <div className="summary-item" key={product.id}>
                      <div>
                        <p>{product.name}</p>
                        <span>{quantity} × {currency.format(product.price)}</span>
                      </div>
                      <strong>{currency.format(product.price * quantity)}</strong>
                    </div>
                  ))}
                </div>
              )}
              <div className="summary-totals">
                <div><span>Subtotal dos produtos</span><strong>{currency.format(productsSubtotal)}</strong></div>
                <div><span>Frete</span><strong>{currency.format(selectedShipping.price)}</strong></div>
                <div className="summary-total"><span>Total</span><strong>{currency.format(total)}</strong></div>
              </div>
              <p className="summary-note">Valores exibidos são representação frontend. No futuro, o backend validará produtos, preços, estoque, frete e total.</p>
              <button type="submit" className="checkout-button">Finalizar pedido</button>
              {successMessage && <p className="checkout-success">{successMessage}</p>}
            </div>
          </aside>
        </div>
      </form>
    </section>
  );
}
