> Registro histórico da primeira análise. A regra financeira e as conclusões de bloqueio abaixo foram substituídas pela [regra definitiva de automação](ALIEXPRESS-AUTOMACAO-REGRA-DEFINITIVA-20261115.md), autorizada posteriormente pelo proprietário.

**Pedido #21: criação real interrompida por cotação incompleta na primeira análise.**

Em 15/11/2026, as consultas autenticadas confirmaram o pagamento Mercado Pago `approved`, `live_mode=true`, BRL 10,00 e referência exata do pagamento/pedido. O banco contém o pagamento PAID e sua verificação pelo backend. O fulfillment AliExpress estava PENDING, com zero tentativas e sem referências de fornecedor; não há tentativa administrativa do #21. O proprietário confirmou que também não encontrou compra correspondente na conta e que não a criou manualmente.

Produto do pedido: `1005007170269840`; SKU `12000039689247941`; quantidade 1. O vínculo do snapshot com a variante atual e os atributos originais coincidem. A API retornou `onSelling`, estoque 4, preço regular BRL 24,43 e oferta BRL 10,50. Esses preços são observações do catálogo, não um total de compra reservado. Os campos obrigatórios do destinatário estão preenchidos, com formatos de CEP/telefone e dígitos verificadores do CPF válidos; isso não comprova cobertura logística para o endereço.

`aliexpress.ds.freight.query` respondeu HTTP 200 para esse produto/SKU/quantidade e a UF real do pedido: AliExpress Selection Standard, `shipping_fee_cent="24.00"`, `shipping_fee_format="R$24,00"`, moeda BRL, `free_shipping=false`, `ddpIncludeVATTax="true"`. O contrato disponível não documenta CEP, rua ou número nessa chamada. A descrição monetária histórica também exige cuidado com a escala. A indicação DDP/VAT não fornece a decomposição dos tributos brasileiros. A consulta de produto informou `price_include_tax=false`, sem valor de imposto confirmado.

Continuam faltando:

- Cobertura e preço aplicáveis ao CEP/endereço completo do #21.
- Modalidade logística com valor de frete integral confirmado para esse destino.
- Todos os impostos/encargos, sua moeda e eventual inclusão no produto/frete, sem dupla contagem.
- Total final do fornecedor para a composição exata desse pedido.

Não foi somado R$ 10,50 com R$ 24,00 para inventar um total. `CATALOG_FREE` e a lista vazia de shipping legs representam a política comercial da loja e não uma cotação do fornecedor. O proprietário aceitou custo acima da venda, mas manteve como requisito a confirmação integral pela API. Nenhuma cotação artificial foi registrada para liberar o #21.

**Diagnóstico da automação e alterações locais**

O webhook já confirmava o pagamento e notificava o dispatcher de fornecedores. O dispatcher tratava somente CJ e DSERS, embora a inicialização já registrasse ALIEXPRESS. O envio AliExpress normal era uma ação separada do ADM. Além disso, em produção `controlled`, criação normal e contrato estão falsos; os verificadores de permissão retornavam falso fixo. A exceção administrativa existente é exclusiva do #19, cujo ID histórico já está no banco, e não habilita pedidos de clientes.

As alterações implementam:

- Roteamento AliExpress para executor limitado, após commit do pagamento; webhook repetido pode recuperar uma pendência sem repetir uma compra.
- Recuperação periódica a partir de pedidos pagos e fulfillments persistidos, inclusive se o processo cair antes do callback. Automação desligada por padrão.
- Caminho único de criação para worker e ADM, com lock do pedido e fulfillment, referência estável, hash do payload e marcador de resultado incerto **confirmado no banco antes da chamada externa**.
- Consulta atual do pagamento: ID, referência, valor, moeda, modo real, aprovação e ausência de reembolso; consulta de produto: venda ativa, SKU/atributos, estoque e preço.
- Token e identidade do comprador lidos sob o mesmo lock da autorização. Cotação vinculada à aplicação, comprador, destinatário, endereço, itens, quantidades e contexto comercial.
- Cotação completa obrigatória com produto, modalidade, frete, impostos explícitos, total, evidência e validade máxima de 30 minutos. Alteração do contexto, expiração ou diferença de preço bloqueia o envio.
- Endpoint ADMIN `POST /api/admin/orders/{id}/aliexpress/dispatch-quote` para registrar uma cotação integral previamente conferida. Ele exige confirmação de destino e ausência de compra, todos os valores, digest atual e aceite explícito quando o custo supera a venda; reconsulta produto/estoque. É um fluxo de exceção administrativa, não uma API que descobre frete/impostos. Não foi usado para o #21 e não substitui seu requisito de cotação integral pela API.
- Até três validações de comunicação anteriores ao envio, com espera persistida; bloqueios permanentes não geram consultas contínuas. Até três criações após recusas comprovadas, somente por repetição explícita no ADM. Webhook e scheduler não repetem recusas nem resultados desconhecidos.
- Timeout, erro genérico/5xx, resposta incompleta e falha ao gravar sucesso preservam o bloqueio de duplicidade. IDs parciais são mantidos. Todos os IDs registrados pelo fluxo normal também usam a restrição única de referências existente.
- Proteção cruzada entre tentativa administrativa e normal do mesmo pedido. Permissão operacional configurável com evidência ligada ao hash da aplicação, separada para criar e consultar, permanecendo desligada sem configuração explícita.
- ADM mostra falhas, pendência de cotação, tentativas, próxima validação, todos os IDs e atualização de acompanhamento por GET. Criação sem consulta posterior usa `CREATED_UNPAID_UNVERIFIED`; somente consulta ao fornecedor comprova seu estado atual. Nenhum endpoint de pagamento AliExpress foi adicionado.

O contrato de criação utilizado não fornece uma chave remota comprovada de idempotência/reconciliação por referência interna. A garantia implementada é durável no banco da inGarage: após envio incerto, não se repete a criação. A resolução desses casos exige conferência no AliExpress; uma ausência de ID local não é autorização para reenviar.

**Registro em produção, sem publicação**

Foi atualizado somente o diagnóstico do fulfillment do #21 nos campos já existentes `supplier_status=BLOCKED`, `last_error` e `updated_at`, com condição de zero tentativas, ausência de IDs, pedido PAID e ausência de tentativa administrativa. O ADM atual já mostra esse last_error na seção Fornecedor ao atualizar o detalhe do pedido. A gravação confirmou uma única linha alterada. Não foi feita inspeção visual de uma sessão administrativa autenticada.

O pedido da loja continua PAID; fulfillment continua PENDING; tentativas continuam 0; ID AliExpress continua nulo. Não houve criação, pagamento de fornecedor, mudança de flags, migração de produção, commit, push ou deploy. Evidência sanitizada: [ALIEXPRESS-PEDIDO21-20261115.evidence.json](ALIEXPRESS-PEDIDO21-20261115.evidence.json).

**Validação e publicação posterior**

Backend: suíte afetada com 576 casos, zero falhas e zero erros; 515 aprovados e 61 opt-in ignorados (incluindo sondas reais e bancos opcionais). Abrange AliExpress, webhooks/assinatura Mercado Pago, evidência de pagamento real, checkout, cotações, ADM e preservação dos fluxos CJ/DSERS. Inclui concorrência, webhook repetido, reembolso, sandbox, custo/estoque/conta alterados, timeout, IDs parciais, interrupção entre criação e gravação, recuperação de callback perdido, limite de tentativas e cotação incompleta/expirada. As APIs de criação são mockadas, com IDs sintéticos.

Após o último ajuste para pedidos legados sem fulfillment, os dois fluxos de criação foram recompilados e verificados novamente: **62 testes aprovados, zero falhas/erros**, incluindo o novo caso que mantém esse pedido bloqueado sem chamar credenciais ou fornecedor. Não houve criação real durante os testes.

Frontend: 76 testes aprovados; TypeScript, lint dos arquivos afetados e `next build` aprovados. A migração aditiva foi executada duas vezes em PostgreSQL 18 local descartável, dentro de transação revertida: SQL válido, execução idempotente e linha histórica preservada. O servidor de teste foi encerrado.

Logs locais ignorados pelo Git: `backend/target/order21-final-tests.log`, `order21-final-regression.log`, `order21-frontend-tests.log`, `order21-frontend-types.log`, `order21-frontend-lint.log`, `order21-frontend-build.log` e `order21-migration-result.log`. O backend usou cópia do POM com diretório de build isolado em `target/order21-validation`, preservando os artefatos operacionais anteriores existentes em target.

Antes de uma publicação manual, aplicar/revisar `V20261115_01__aliexpress_automatic_dispatch.sql` no ambiente controlado e revisar a evidência de permissão/aplicação/conta. Ativar deliberadamente as flags de criação/contrato e `ALIEXPRESS_AUTO_DISPATCH_ENABLED` apenas quando houver fonte confiável para a cotação completa. Para consulta, revisar o método exato concedido e suas flags/evidências independentes. O código local não está ativo em produção.

A integração ainda depende de contrato/API autorizado que confirme custos completos para o endereço. Sem isso, pedidos com custo desconhecido permanecem bloqueados. Não habilitar a criação do #21 apenas mudando flags ou preenchendo assertions administrativas.

Lista completa dos arquivos desta alteração: [ALIEXPRESS-PEDIDO21-20261115.files.txt](ALIEXPRESS-PEDIDO21-20261115.files.txt). Alterações preexistentes em `.env.example`, teste de ativação de pagamentos e documentos anteriores foram preservadas.
