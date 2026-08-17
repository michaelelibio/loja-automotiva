'use client';

import Link from 'next/link';
import { useState, type FormEvent } from 'react';
import { AccountSecurityApiError, changePassword } from '@/lib/api/account-security';

const PASSWORD_MIN = 8;
const PASSWORD_MAX = 72;

export function ChangePasswordForm() {
  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirmation, setConfirmation] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [feedback, setFeedback] = useState<{ kind: 'success' | 'error'; message: string } | null>(null);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isSubmitting) return;
    setFeedback(null);
    if (newPassword.length < PASSWORD_MIN || newPassword.length > PASSWORD_MAX) {
      setFeedback({ kind: 'error', message: 'A nova senha deve ter entre 8 e 72 caracteres.' });
      return;
    }
    if (newPassword !== confirmation) {
      setFeedback({ kind: 'error', message: 'A confirmação não corresponde à nova senha.' });
      return;
    }

    setIsSubmitting(true);
    try {
      const response = await changePassword({ currentPassword, newPassword });
      setCurrentPassword(''); setNewPassword(''); setConfirmation('');
      setFeedback({ kind: 'success', message: response.message || 'Senha alterada com sucesso.' });
    } catch (error) {
      const message = error instanceof AccountSecurityApiError
        ? error.status === 401
          ? 'Sua sessão expirou. Entre novamente.'
          : error.status === 400 && error.message.toLowerCase().includes('senha atual')
            ? 'Senha atual incorreta.'
            : 'Não foi possível alterar sua senha. Tente novamente.'
        : 'Não foi possível alterar sua senha. Tente novamente.';
      setFeedback({ kind: 'error', message });
    } finally { setIsSubmitting(false); }
  }

  return (
    <form className="account-password-form" onSubmit={handleSubmit}>
      <div className="account-password-heading">
        <div><strong>Alterar senha</strong><p>Use uma senha exclusiva com 8 a 72 caracteres.</p></div>
        <Link href="/esqueci-senha">Não lembra sua senha? Redefinir por e-mail</Link>
      </div>
      <label><span>Senha atual</span><input type="password" autoComplete="current-password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required disabled={isSubmitting} /></label>
      <label><span>Nova senha</span><input type="password" autoComplete="new-password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} minLength={PASSWORD_MIN} maxLength={PASSWORD_MAX} required disabled={isSubmitting} /></label>
      <label><span>Confirmar nova senha</span><input type="password" autoComplete="new-password" value={confirmation} onChange={(event) => setConfirmation(event.target.value)} minLength={PASSWORD_MIN} maxLength={PASSWORD_MAX} required disabled={isSubmitting} /></label>
      <div className="account-form-actions">
        <button type="submit" disabled={isSubmitting}>{isSubmitting ? 'Alterando...' : 'Alterar senha'}</button>
        {feedback && <p className={`account-form-feedback ${feedback.kind}`} role={feedback.kind === 'error' ? 'alert' : 'status'}>{feedback.message}</p>}
      </div>
    </form>
  );
}
