'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AliExpressFreightError, testAliExpressFreight } from '@/lib/api/admin/aliexpress';
import { freightAmount, freightEstimate, freightStatusMessages, type FreightDiagnosticResult } from '@/lib/admin/aliexpress-freight';

export function AliExpressFreightResult({ result }: { result: FreightDiagnosticResult }) {
  const success = result.status === 'SUCCESS';
  return <div className="admin-freight-result">
    <div className={`admin-freight-status ${success ? 'success' : 'error'}`} role={success ? 'status' : 'alert'}>
      <h3>{success ? 'Consulta concluída com sucesso' : 'Não foi possível confirmar a cotação'}</h3>
      <p>{freightStatusMessages[result.status]}</p><p>Código: <code>{result.safeCode}</code></p>
    </div>
    {result.methods.length > 0 && <div className="admin-aliexpress-table-wrap"><table className="admin-aliexpress-table">
      <caption className="sr-only">Modalidades retornadas no diagnóstico de frete</caption>
      <thead><tr><th scope="col">Modalidade</th><th scope="col">Valor</th><th scope="col">Moeda</th><th scope="col">Prazo estimado</th></tr></thead>
      <tbody>{result.methods.map((method) => <tr key={method.serviceName}>
        <td data-label="Modalidade">{method.serviceName}</td><td data-label="Valor">{freightAmount(method)}</td>
        <td data-label="Moeda">{method.currency}</td><td data-label="Prazo estimado">{freightEstimate(method.estimatedDeliveryTime)}</td>
      </tr>)}</tbody>
    </table></div>}
  </div>;
}

export function AliExpressFreightDiagnostic() {
  const router = useRouter();
  const { logout } = useAuth();
  const busy = useRef(false);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<FreightDiagnosticResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  async function test() {
    if (busy.current) return;
    busy.current = true; setLoading(true); setResult(null); setError(null);
    try { setResult(await testAliExpressFreight()); }
    catch (cause) {
      setError(cause instanceof AliExpressFreightError
        ? `${cause.message} Código: ${cause.status === 0 ? 'CONNECTION_FAILURE' : cause.status}.`
        : 'Não foi possível executar o diagnóstico. Tente novamente mais tarde. Código: DIAGNOSTIC_FAILURE.');
      if (cause instanceof AliExpressFreightError && cause.status === 401) { logout(); router.replace('/login'); }
    } finally { busy.current = false; setLoading(false); }
  }

  return <section className="admin-products-panel admin-aliexpress-panel admin-freight-panel" aria-labelledby="aliexpress-freight-title" aria-busy={loading}>
    <div className="admin-aliexpress-heading">
      <h2 id="aliexpress-freight-title">Diagnóstico da integração AliExpress</h2>
      <p>Teste a autorização e a consulta de frete com o caso fixo configurado no servidor. O teste é independente do produto buscado nesta página.</p>
      <aside className="admin-aliexpress-warnings" aria-label="Limitações do diagnóstico">
        <p>Esta cotação é apenas diagnóstica e não representa necessariamente o frete definitivo por SKU ou CEP. A origem China (CN) é uma hipótese ainda não confirmada; os prazos são estimados.</p>
        <p>O teste não cria pedidos, não executa pagamentos e não libera o frete no checkout.</p>
      </aside>
    </div>
    <div className="admin-aliexpress-footer">
      <button type="button" onClick={() => void test()} disabled={loading} aria-describedby="aliexpress-freight-title">
        {loading ? 'Consultando frete…' : 'Testar cotação de frete'}
      </button>
      {loading && <p role="status">Consultando o AliExpress. Aguarde o resultado…</p>}
      {error && <p className="admin-freight-error" role="alert">{error}</p>}
    </div>
    {result && <AliExpressFreightResult result={result} />}
  </section>;
}
