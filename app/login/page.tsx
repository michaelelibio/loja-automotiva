'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setLoading(true);
    try {
      const result = await login({ email, password });
      if (result.success) {
        router.push('/');
      } else {
        setError(result.message ?? 'Credenciais inválidas');
      }
    } catch (err: any) {
      setError(err?.message ?? 'Erro ao conectar com o servidor');
    } finally {
      setLoading(false);
    }
  };

  return (
    <main id="top">
      <Header />
      <div className="auth-page">
        <div className="auth-grid">
          <div className="auth-left">
            <p className="eyebrow">ÁREA DO CLIENTE</p>
            <h2>Bem-vindo de volta.</h2>
            <p className="user-note">Acesse seus pedidos, favoritos e histórico de compras. Em breve você poderá sincronizar seu carrinho e acompanhar pedidos.</p>
          </div>
          <div className="auth-right">
            <section className="auth-card">
              <h3>Entrar</h3>
              <form onSubmit={handleSubmit} className="auth-form">
                <label>
                  E-mail
                  <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                </label>
                <label>
                  Senha
                  <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
                </label>

                {error && <div className="form-error">{error}</div>}

                <button className="btn-primary" type="submit" disabled={loading}>{loading ? 'Entrando...' : 'Entrar'}</button>

                <div className="auth-links">
                  <Link href="#" className="text-link inline">Esqueceu sua senha?</Link>
                  <Link href="/cadastro" className="text-link inline">Não tem uma conta? Criar conta</Link>
                </div>

                <div className="divider">OU</div>

                <button className="btn-google" type="button" disabled title="Continuar com Google (em breve)">Continuar com Google</button>
              </form>
            </section>
          </div>
        </div>
      </div>
      <Footer />
    </main>
  );
}
