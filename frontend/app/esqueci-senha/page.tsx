'use client';

import Link from 'next/link';
import { useState, type FormEvent } from 'react';
import { SecurityPage } from '@/components/SecurityPage';
import { forgotPassword } from '@/lib/api/account-security';

const neutralMessage = 'Se existir uma conta com esse e-mail, enviaremos as instruções.';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState(''); const [loading, setLoading] = useState(false); const [submitted, setSubmitted] = useState(false);
  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (loading) return; setLoading(true);
    try { await forgotPassword({ email }); } catch { /* resposta neutra evita enumeração de contas */ }
    finally { setSubmitted(true); setLoading(false); }
  }
  return <SecurityPage eyebrow="RECUPERAÇÃO DE ACESSO" title="Esqueci minha senha" description="Informe seu e-mail para receber as instruções de redefinição.">
    {submitted ? <div className="security-state success" role="status"><span className="security-state-mark">✓</span><h2>Confira seu e-mail</h2><p>{neutralMessage}</p><div className="security-actions"><Link className="btn-primary" href="/login">Voltar ao login</Link></div></div> : <><h2>Recuperar acesso</h2><form className="security-form" onSubmit={submit}><label htmlFor="recovery-email">E-mail</label><input id="recovery-email" type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required maxLength={320} /><button className="btn-primary" type="submit" disabled={loading}>{loading ? 'Enviando...' : 'Enviar instruções'}</button></form><p className="security-card-note">Por segurança, a resposta será a mesma para qualquer endereço informado.</p></>}
  </SecurityPage>;
}
