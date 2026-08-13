'use client';

import React, { useState } from 'react';
import { useRouter } from 'next/navigation';
import Link from 'next/link';
import { useAuth } from '@/context/AuthContext';
import { Header } from '@/components/Header';
import { Footer } from '@/components/Footer';

export default function RegisterPage() {
  const router = useRouter();
  const { register } = useAuth();
  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    if (!name.trim()) return setError('Nome é obrigatório');
    if (!email.includes('@')) return setError('E-mail inválido');
    if (password.length < 6) return setError('Senha precisa ter ao menos 6 caracteres');
    if (password !== confirm) return setError('Confirmação de senha não confere');

    setLoading(true);
    try {
      const result = await register({ name, email, password });
      if (result.success) {
        router.push('/');
      } else {
        setError(result.message ?? 'Erro ao cadastrar');
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
            <h2>Crie sua conta</h2>
            <p className="user-note">Tenha acesso rápido a pedidos e histórico. Em breve integraremos favoritos e sincronização de carrinho.</p>
          </div>

          <div className="auth-right">
            <section className="auth-card">
              <h3>Cadastrar</h3>
              <form onSubmit={handleSubmit} className="auth-form">
                <label>
                  Nome
                  <input type="text" value={name} onChange={(e) => setName(e.target.value)} required />
                </label>
                <label>
                  E-mail
                  <input type="email" value={email} onChange={(e) => setEmail(e.target.value)} required />
                </label>
                <label>
                  Senha
                  <input type="password" value={password} onChange={(e) => setPassword(e.target.value)} required />
                </label>
                <label>
                  Confirmar senha
                  <input type="password" value={confirm} onChange={(e) => setConfirm(e.target.value)} required />
                </label>

                {error && <div className="form-error">{error}</div>}

                <button className="btn-primary" type="submit" disabled={loading}>{loading ? 'Cadastrando...' : 'Criar conta'}</button>

                <div className="auth-links">
                  <Link href="/login" className="text-link inline">Já tem uma conta? Entrar</Link>
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
