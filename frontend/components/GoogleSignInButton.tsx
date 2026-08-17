'use client';

import Script from 'next/script';
import { useCallback, useEffect, useRef, useState } from 'react';

const GOOGLE_CLIENT_ID = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID;

export function GoogleSignInButton({ onCredential, disabled = false }: {
  onCredential: (credential: string) => Promise<void>;
  disabled?: boolean;
}) {
  const containerRef = useRef<HTMLDivElement>(null);
  const handlingCredential = useRef(false);
  const onCredentialRef = useRef(onCredential);
  const [scriptReady, setScriptReady] = useState(false);
  const [scriptError, setScriptError] = useState(false);

  useEffect(() => {
    onCredentialRef.current = onCredential;
  }, [onCredential]);

  const renderButton = useCallback(() => {
    const container = containerRef.current;
    if (!container || !window.google || !GOOGLE_CLIENT_ID) return;
    container.replaceChildren();
    window.google.accounts.id.initialize({
      client_id: GOOGLE_CLIENT_ID,
      auto_select: false,
      callback: async ({ credential }) => {
        if (!credential || handlingCredential.current) return;
        handlingCredential.current = true;
        try { await onCredentialRef.current(credential); }
        finally { handlingCredential.current = false; }
      },
    });
    window.google.accounts.id.renderButton(container, {
      type: 'standard', theme: 'outline', size: 'large', text: 'continue_with',
      shape: 'rectangular', logo_alignment: 'left', locale: 'pt-BR',
      width: Math.max(200, Math.min(container.clientWidth, 400)),
    });
  }, []);

  useEffect(() => {
    if (!scriptReady) return;
    renderButton();
  }, [renderButton, scriptReady]);

  if (!GOOGLE_CLIENT_ID) {
    return <p className="google-signin-error" role="alert">Login com Google indisponível. Configure o Client ID.</p>;
  }

  return (
    <div className={`google-signin ${disabled ? 'loading' : ''}`} aria-busy={disabled}>
      <Script src="https://accounts.google.com/gsi/client?hl=pt-BR" strategy="afterInteractive" onLoad={() => setScriptReady(true)} onError={() => setScriptError(true)} />
      <div ref={containerRef} className="google-signin-button" />
      {!scriptReady && !scriptError && <p className="google-signin-status" role="status">Carregando Google...</p>}
      {disabled && <p className="google-signin-status" role="status">Entrando com Google...</p>}
      {scriptError && <p className="google-signin-error" role="alert">Não foi possível carregar o login do Google.</p>}
    </div>
  );
}
