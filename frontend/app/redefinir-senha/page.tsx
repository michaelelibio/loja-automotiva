'use client';

import Link from 'next/link';
import { Suspense, useState, type FormEvent } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { SecurityPage } from '@/components/SecurityPage';
import { AccountSecurityApiError, resetPassword } from '@/lib/api/account-security';

function ResetPasswordContent() {
  const searchParams = useSearchParams(); const router = useRouter(); const token = searchParams.get('token');
  const [password, setPassword] = useState(''); const [confirmation, setConfirmation] = useState('');
  const [loading, setLoading] = useState(false); const [success, setSuccess] = useState(false); const [error, setError] = useState<string | null>(null);

  async function submit(event: FormEvent<HTMLFormElement>) { event.preventDefault(); if (loading) return; setError(null);
    if (!token) return setError('O link de recuperação é inválido ou expirou.');
    if (password.length < 8 || password.length > 72) return setError('A senha deve ter entre 8 e 72 caracteres.');
    if (password !== confirmation) return setError('As senhas não coincidem.');
    setLoading(true);
    try { await resetPassword({ token, newPassword: password }); setSuccess(true); router.replace('/redefinir-senha'); }
    catch (cause) { if (cause instanceof AccountSecurityApiError && [400, 401, 404, 409].includes(cause.status)) setError('O link de recuperação é inválido ou expirou.'); else setError('Não foi possível redefinir sua senha agora. Tente novamente.'); }
    finally { setLoading(false); }
  }

  return <SecurityPage eyebrow="NOVA SENHA" title="Redefinir senha" description="Crie uma nova senha para sua conta GARAGE.">
    {success ? <div className="security-state success" role="status"><span className="security-state-mark">✓</span><h2>Senha alterada com sucesso</h2><p>Use sua nova senha no próximo acesso.</p><div className="security-actions"><Link className="btn-primary" href="/login">Ir para login</Link></div></div> : <><h2>Escolha sua nova senha</h2>{!token && <p className="security-form-error" role="alert">O link de recuperação é inválido ou expirou.</p>}<form className="security-form" onSubmit={submit}><label htmlFor="new-password">Nova senha</label><input id="new-password" type="password" autoComplete="new-password" minLength={8} maxLength={72} value={password} onChange={(event) => setPassword(event.target.value)} disabled={!token || loading} required /><small>Use entre 8 e 72 caracteres.</small><label htmlFor="confirm-password">Confirmar nova senha</label><input id="confirm-password" type="password" autoComplete="new-password" minLength={8} maxLength={72} value={confirmation} onChange={(event) => setConfirmation(event.target.value)} disabled={!token || loading} required />{error && <p className="security-form-error" role="alert">{error}</p>}<button className="btn-primary" type="submit" disabled={!token || loading}>{loading ? 'Alterando senha...' : 'Redefinir senha'}</button></form><div className="security-bottom-link"><Link className="text-link inline" href="/esqueci-senha">Solicitar um novo link</Link></div></>}
  </SecurityPage>;
}

export default function ResetPasswordPage() { return <Suspense fallback={<SecurityPage eyebrow="NOVA SENHA" title="Redefinir senha" description="Preparando a recuperação."><div className="security-state loading"><span className="security-loader" /><h2>Carregando...</h2></div></SecurityPage>}><ResetPasswordContent /></Suspense>; }
