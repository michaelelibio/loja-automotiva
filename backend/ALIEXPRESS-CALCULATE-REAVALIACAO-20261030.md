# freight.calculate — conclusão da reavaliação em 30/10/2026

**Resultado: existência documental confirmada; autorização e funcionamento para a aplicação inGarage NÃO comprovados. Nenhum valor real novo coletado. Investigação encerrada nesta etapa, sem nova integração de execução.** Isso não significa que o fornecedor negou a API: não houve uma chamada autenticada a calculate nesta execução.

## Evidência pública consultada agora

- https://developer.alibaba.com/doc2/apiDetail.htm?apiId=39371 abriu e descreve `aliexpress.logistics.buyer.freight.calculate`, com autorização obrigatória. Uma página disponível não comprova operação ou concessão à nossa aplicação.
- https://developer.alibaba.com/docs/doc.htm?articleId=39371&docType=2&treeId=762 identifica a árvore documental como `Dropshipping（已废弃）` (descontinuada). Isso caracteriza a seção legada, não prova que o método homônimo esteja indisponível no catálogo atual.
- O exemplo de resposta publica `freight.amount="1.0"`, `freight.cent=1000`, `currency_code="USD"`. Sob a hipótese usual de centavos, 1000/100=10, e não 1. Isso é inconsistência do exemplo, NÃO cotação observada nem prova de outra escala. `BigDecimal` resolve sintaxe, não resolve semântica comercial.
- País, quantidade, produto e origem são entradas; província/cidade são opcionais. O contrato legado não documenta CEP nem SKU. Não demonstra cobertura brasileira por região.

## Evidência atual já preservada no projeto

`ALIEXPRESS-MONEY-ADDRESS-EVIDENCE-20261027.json` preserva o contrato público AliExpress docId 525 (snapshot SHA-256 `7a86e98047ca80b423f1120a3635b9e89bb065d7e63c370713a968ba6d0bf518`). Nesse contrato, `sku_id` é String opcional, com exigência para Choice descrita no snapshot anterior `ALIEXPRESS-ASSISTED-PREFILL-EVIDENCE.json`. Não confundir ausência de SKU na referência legada com ausência no contrato atual. `amount` aparece como String no snapshot atual; deve ser analisado como BigDecimal, preservando o original.

Após a falha inicial do navegador de pesquisa e do cliente restrito, o endpoint público abaixo foi recuperado via HTTPS, sem autenticação, com HTTP 200. A reconsulta confirmou docId 525, authType=1, SKU e os campos de resposta citados no snapshot anterior. O exemplo ATUAL também repete amount=1.0, cent=1000 e USD. O recorte sanitizado, o hash e os campos estão em `ALIEXPRESS-CALCULATE-EVIDENCE-20261030.json`; o contrato bruto público fica somente em target. HTTP 200 do catálogo é prova de disponibilidade dos metadados, não de execução autorizada da operação.

https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.calculate&methodType=GET%2FPOST

`ALIEXPRESS-TOP29-INVESTIGATION.md` registra rejeição histórica de identificação no gateway legado (`TOP_29_INVALID_APP_KEY`), sem comprovar negação de permissão no gateway AliExpress atual. Também descreve o transporte TOP atual. Não enviar as credenciais atuais ao gateway legado por tentativa, nem trocar algoritmo, token ou ambiente automaticamente.

Documentos antigos do projeto dizem que havia diagnóstico calculate implementado; isso é histórico. O código ATUAL é decisivo: `AliExpressFreightDiagnosticClient` delega a `AliExpressDsFreightClient`, portanto testa `aliexpress.ds.freight.query`, não calculate.

## Comparação prática

| Aspecto | ds.freight.query atual | buyer.freight.calculate |
| --- | --- | --- |
| Prova operacional na inGarage | Retorno anterior relatado/registrado de 24.00 BRL | Nenhuma nova resposta autenticada comprovada |
| Dinheiro | shipping_fee_cent / shipping_fee_format; escala ainda não aprovada | amount / cent / currency_code; exemplo legado conflitante; exige contrato e retorno reais coerentes |
| SKU | selecionado explicitamente | Presente no snapshot atual; ausente na referência legada |
| Destino | País e parâmetros territoriais parciais | País e parâmetros territoriais parciais; não comprova CEP nem regiões brasileiras |
| Origem | Pode ser observada por opção retornada | Origem enviada na requisição; não é confirmação independente de origem retornada |
| Modalidade | code e company têm papéis diferentes | service_name não deve ser tratado automaticamente como código de criação de pedido |

## Autenticação segura e impedimento concreto

Os endpoints existentes são ADMIN e retornam no-store:

- GET `/api/admin/integrations/aliexpress/authorization-diagnostic`: estado local da autorização; retorna freightPermission=NOT_VERIFIED, não lista concessões específicas.
- POST `/api/admin/integrations/aliexpress/freight-diagnostic`: somente DS.
- POST `/api/admin/integrations/aliexpress/freight-diagnostic/province-comparison`: somente DS, BR versus Santa Catarina.
- POST `/api/admin/integrations/aliexpress/territorial-diagnostic`: consulta territorial existente; reconhecer Brasil/SC/Joinville não prova que calculate use os mesmos códigos ou respeite o destino.

`existingAccessToken()` é usado internamente sem refresh. O token não é devolvido ao cliente. Nenhum endpoint aceita um nome arbitrário de método para invocar calculate; não criar essa abertura genérica. Não foi fornecida sessão ADMIN autenticada ou resultado do console para confirmar a concessão exata, e não há um diagnóstico calculate publicado. Não foi acessado banco, backup ou OAuth de produção nesta reavaliação.

A condição solicitada — implementar o diagnóstico **se autorizada** — não foi satisfeita. Não foi adicionado cliente/endpoint nem coletor em lote com uma autorização presumida. Não é necessário redesenhar checkout, tabela nacional ou pedidos para resolver esse impedimento.

## O que falta, especificamente

1. No console AliExpress da MESMA aplicação, confirmar a concessão exata de `aliexpress.logistics.buyer.freight.calculate` e o gateway/protocolo correspondente. Informar somente nome do método e situação da permissão, nunca segredos. OAuth válido para produtos/DS não substitui essa confirmação.
2. Se concedida, implementar diagnóstico dedicado e restrito, usando internamente `existingAccessToken()` e IDs de produto/variante do catálogo. Uma consulta deliberada por vez; nenhuma criação de pedido, pagamento, retry de compra ou extração do token.
3. Confirmar códigos brasileiros oficialmente aceitos por ESSA operação e comparar país, província e cidades diferentes com mesmo SKU, quantidade e origem. Registrar entradas separadamente do escopo efetivamente confirmado na resposta. Preço igual não prova que o filtro foi respeitado.
4. Preservar amount/cent/moeda brutos, interpretar números com BigDecimal, verificar consistência e obter confirmação oficial da escala. Exigir BRL ou política cambial explícita; não usar maior/menor valor por heurística.
5. Só depois agregar observações com produto, SKU, quantidade, categoria/porte conferidos, fornecedor, origem, UF/cidade, modalidade, data, evidência e validade. Não extrapolar um SKU às 81 células nacionais. Observações não são preços aprovados e não comprovam impostos, taxas, embalagem ou custo total.
6. Usar a revisão nacional existente para aprovação manual quando todos os custos e limites estiverem completos. Manter DIAGNOSTIC_ONLY enquanto houver lacunas; não marcar moeda/cobertura como comprovadas só porque houve HTTP 200 ou success.

## Valores observados e preservação comercial

**calculate: nenhum valor real novo.** Os números 1.0/1000/USD são exemplos de documentação. O 24.00 BRL é observação histórica de DS com escala não comprovada e não foi cadastrado como custo real. Os valores dos testes são sintéticos.

Checkout imediato por tarifas próprias aprovadas e fallback assistido permanecem intactos. Nenhuma configuração, tabela nacional, credencial ou banco foi alterado. Perfil controlled e bloqueio de pagamentos preservados. Sem compras, pedidos AliExpress, commit, push ou deploy.


Validação local desta reavaliação: 19 testes aprovados, zero falhas/erros/ignorados, BUILD SUCCESS (diagnóstico ADMIN, serviço com OAuth simulado e ControlledPublicationPaymentTests). Sem PostgreSQL ou chamadas autenticadas ao fornecedor. Log ignorado: target/homologation-final/calculate-reassessment-tests.log. Nenhum fonte de execução foi alterado; builds de frontend e backend não foram repetidos para esta mudança exclusivamente documental.
