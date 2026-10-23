'use client';

import { useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { useAuth } from '@/context/AuthContext';
import { AliExpressFreightError, testAliExpressFreight, prepareAliExpressFreight, type FreightProduct } from '@/lib/api/admin/aliexpress';
import type { AdminProduct } from '@/lib/types/admin-product';
import { freightAmount, freightEstimate, freightDiagnosticMessage, type FreightDiagnosticResult } from '@/lib/admin/aliexpress-freight';

export function AliExpressFreightResult({ result }: { result: FreightDiagnosticResult }) {
  const success = result.status === 'SUCCESS';
  return <div className="admin-freight-result">
    <div className={`admin-freight-status ${success ? 'success' : 'error'}`} role={success ? 'status' : 'alert'}>
      <h3>{success ? 'Consulta concluída com sucesso' : 'Não foi possível confirmar a cotação'}</h3>
      <p>Status: <code>{result.status}</code></p><p>{freightDiagnosticMessage(result)}</p><p>Código: <code>{result.safeCode}</code></p>
    </div>
    {!!result.monetaryStructure?.length && <div className="admin-aliexpress-table-wrap"><table className="admin-aliexpress-table">
      <caption>Estrutura monetária sanitizada. Valores observados sem conversão; vazio indica valor suprimido.</caption>
      <thead><tr><th>Opção</th><th>Campo</th><th>Presente</th><th>Tipo JSON</th><th>Moeda</th><th>Escala documentada</th><th>Valor observado</th></tr></thead>
      <tbody>{result.monetaryStructure.map((field) => <tr key={field.optionIndex + ':' + field.path}>
        <td>{field.optionIndex + 1}</td><td><code>{field.path}</code></td><td>{field.present ? 'Sim' : 'Não'}</td>
        <td>{field.jsonType}</td><td>{field.currency ?? '—'}</td><td>{field.documentedScale}</td><td>{field.safeValue ?? '—'}</td>
      </tr>)}</tbody>
    </table></div>}
    {result.methods.length > 0 && <div className="admin-aliexpress-table-wrap"><table className="admin-aliexpress-table">
      <caption className="sr-only">Modalidades retornadas no diagnóstico de frete</caption>
      <thead><tr><th scope="col">Modalidade / código</th><th scope="col">Origem retornada</th><th scope="col">Valor</th><th scope="col">Moeda</th><th scope="col">Prazo estimado</th></tr></thead>
      <tbody>{result.methods.map((method) => <tr key={method.serviceName}>
        <td data-label="Modalidade">{method.company && <span>{method.company}<br /></span>}<code>{method.serviceName}</code></td><td data-label="Origem">{method.originCountry ?? 'Não informada'}</td><td data-label="Valor">{freightAmount(method)}</td>
        <td data-label="Moeda">{method.currency}</td><td data-label="Prazo estimado">{freightEstimate(method.estimatedDeliveryTime)}</td>
      </tr>)}</tbody>
    </table></div>}
  </div>;
}

export function AliExpressFreightDiagnostic({ products = [] }: { products?: AdminProduct[] }) {
  const router = useRouter();
  const { logout } = useAuth();
  const busy = useRef(false);
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState<FreightDiagnosticResult | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [productId, setProductId] = useState('');
  const [product, setProduct] = useState<FreightProduct | null>(null);
  const [skuId, setSkuId] = useState('');
  const [quantity, setQuantity] = useState(1);
  const selected = product?.variants.find((v) => v.skuId === skuId);
  function changeProduct(id: string) { setProductId(id); setProduct(null); setSkuId(''); setResult(null); setError(null); }

  async function test(prepare = false) {
    if (busy.current) return;
    busy.current = true; setLoading(true); setResult(null); setError(null);
    try {
      if (prepare) {
        const data = await prepareAliExpressFreight(productId.trim());
        setProduct(data); setSkuId(data.variants[0]?.skuId ?? '');
      } else if (product) setResult(await testAliExpressFreight({ productId: product.productId, skuId, quantity }));
    }
    catch (cause) {
      setError(cause instanceof AliExpressFreightError
        ? `${cause.message} Código: ${cause.status === 0 ? 'CONNECTION_FAILURE' : cause.status}.`
        : 'Não foi possível executar o diagnóstico. Tente novamente mais tarde. Código: DIAGNOSTIC_FAILURE.');
      if (cause instanceof AliExpressFreightError && cause.status === 401) { logout(); router.replace('/login'); }
    } finally { busy.current = false; setLoading(false); }
  }

  return <section className="admin-products-panel admin-aliexpress-panel admin-freight-panel" aria-labelledby="aliexpress-freight-title" aria-busy={loading}>
    <div className="admin-aliexpress-heading">
      <h2 id="aliexpress-freight-title">Diagnóstico de frete AliExpress</h2>
      <p>API: <code>aliexpress.ds.freight.query</code>. Destino: Brasil (BR), consulta por país, moeda solicitada BRL.</p>
      <p>Selecione um produto AliExpress da listagem atual ou informe seu ID. Consulte os SKUs reais antes de testar o frete para o Brasil.</p>
      <aside className="admin-aliexpress-warnings" aria-label="Limitações do diagnóstico">
        <p>Esta consulta é diagnóstica, sem CEP ou endereço completo e com prazos estimados. Uma resposta por país não é frete definitivo por endereço. A origem é exibida somente quando retornada pela API. OAuth e consulta de produtos não comprovam permissão de frete.</p>
        <p>O teste não cria pedidos, não executa pagamentos e não libera o frete no checkout.</p>
      </aside>
    </div>
    <div className="admin-aliexpress-footer">
      <label>Produto importado (listagem atual)<select value="" disabled={loading} onChange={(event) => changeProduct(event.target.value)}>
        <option value="">Selecionar produto AliExpress</option>
        {products.filter((p) => p.supplier === 'ALIEXPRESS' && p.supplierProductId).map((p) => <option key={p.id} value={p.supplierProductId!}>{p.name} — {p.supplierProductId}</option>)}
      </select></label>
      <label>ID AliExpress<input value={productId} disabled={loading} inputMode="numeric" maxLength={18} onChange={(event) => changeProduct(event.target.value)} /></label>
      <button type="button" disabled={loading || !/^[0-9]{1,18}$/.test(productId.trim())} onClick={() => void test(true)}>Consultar SKUs</button>
      {product && <>
        <label>SKU<select value={skuId} disabled={loading} onChange={(event) => { setSkuId(event.target.value); setResult(null); }}>
          {product.variants.map((v) => <option key={v.skuId} value={v.skuId}>{v.skuId} — estoque: {v.availableStock}</option>)}
        </select></label>
        <label>Quantidade<input type="number" min={1} max={100} value={quantity} disabled={loading} onChange={(event) => { setQuantity(Number(event.target.value)); setResult(null); }} /></label>
        <p>Destino: BR, sem códigos de província ou cidade não validados. Produto, SKU e estoque serão consultados novamente antes do teste.</p>
      </>}
      <button type="button" onClick={() => void test()} disabled={loading || !selected || !Number.isInteger(quantity) || quantity < 1 || quantity > 100 || quantity > selected.availableStock} aria-describedby="aliexpress-freight-title">
        {loading ? 'Consultando frete…' : 'Testar cotação de frete'}
      </button>
      {loading && <p role="status">Consultando o AliExpress. Aguarde o resultado…</p>}
      {error && <p className="admin-freight-error" role="alert">{error}</p>}
    </div>
    {result && <AliExpressFreightResult result={result} />}
  </section>;
}
