'use client';

import Link from 'next/link';
import { Suspense, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { SecurityPage } from '@/components/SecurityPage';
import { verifyEmail } from '@/lib/api/account-security';

type VerificationState = 'loading' | 'success' | 'error';

function VerifyEmailContent() {
  const searchParams = useSearchParams(); const router = useRouter();
  const [state, setState] = useState<VerificationState>('loading');
  const attempted = useRef(false);
  const token = searchParams.get('token');

  useEffect(() => { let active = true;
    if (attempted.current) return;
    attempted.current = true;
    if (!token) { queueMicrotask(() => { if (active) setState('error'); }); return () => { active = false; }; }
    verifyEmail({ token }).then(() => { if (active) setState('success'); }).catch(() => { if (active) setState('error'); }).finally(() => { if (active) router.replace('/verificar-email'); });
    return () => { active = false; };
  }, [token, router]);

  return <SecurityPage eyebrow="SEGURANÇA DA CONTA" title="Verificação de e-mail" description="Estamos validando a confirmação da sua conta GARAGE.">
    <div className={`security-state ${state}`} aria-live="polite">
      {state === 'loading' && <><span className="security-loader" aria-hidden="true" /><h2>Confirmando seu e-mail...</h2><p>Isso deve levar apenas alguns segundos.</p></>}
      {state === 'success' && <><span className="security-state-mark">✓</span><h2>E-mail confirmado com sucesso</h2><p>Sua conta agora está com o endereço de e-mail verificado.</p></>}
      {state === 'error' && <><span className="security-state-mark">!</span><h2>Não foi possível confirmar</h2><p>O link de verificação é inválido ou expirou.</p></>}
      {state !== 'loading' && <div className="security-actions"><Link className="btn-primary" href="/login">Voltar ao login</Link><Link className="text-link inline" href="/conta">Ir para minha conta</Link></div>}
    </div>
  </SecurityPage>;
}

export default function VerifyEmailPage() { return <Suspense fallback={<SecurityPage eyebrow="SEGURANÇA DA CONTA" title="Verificação de e-mail" description="Preparando a confirmação."><div className="security-state loading"><span className="security-loader" /><h2>Carregando...</h2></div></SecurityPage>}><VerifyEmailContent /></Suspense>; }
