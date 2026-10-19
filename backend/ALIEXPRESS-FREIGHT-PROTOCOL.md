# Investigação de PROVIDER_PROTOCOL_REJECTED

Atualização de 17/10/2026: o resultado real agora identificado como TOP 29 motivou nova investigação. Foi possível obter o catálogo e o SDK oficiais atuais, que publicam `freight.calculate` e a alternativa `freight.get` com `Protocol.TOP` atual, além de documentarem SKU. As limitações históricas de acesso e a comparação com a referência legada abaixo foram superadas por novas evidências: consulte [ALIEXPRESS-TOP29-INVESTIGATION.md](ALIEXPRESS-TOP29-INVESTIGATION.md). O código de execução não foi alterado nessa investigação.

## Causa comprovada e limite da conclusão

O cliente descartava a distinção entre os códigos TOP **22 (Invalid Method), 25 (Invalid Signature) e 29 (Invalid App Key)**. Todos viravam `PROTOCOL_ERROR / PROVIDER_PROTOCOL_REJECTED`. Essa é uma falha comprovada do diagnóstico: o resultado informado do Railway não permite recuperar qual dos três foi recebido. Além disso, a mesma classificação era aplicada a `error_code` de negócio, embora a referência não documente equivalência com os códigos TOP.

**A causa da rejeição real ainda não está comprovada.** Não é correto afirmar que foi assinatura, App Key, token ou endpoint somente a partir desse código agregado. `LOCALLY_USABLE` comprova disponibilidade/descriptografia/expiração local, não aceitação pelo fornecedor nem compatibilidade entre protocolos.

## Conferência do transporte

Referências oficiais consultadas:

- Contrato público de frete: https://developer.alibaba.com/doc2/apiDetail.htm?apiId=39371
- Assinatura e códigos TOP: https://developer.alibaba.com/docs/doc.htm?articleId=101617&docType=1&treeId=153
- A implementação de produtos já funcional tem sua evidência de SDK documentada em `ALIEXPRESS-IMPORT.md` e testes em `AliExpressProductClientTests`.

| Item | Diagnóstico de frete atual | Produtos existentes |
| --- | --- | --- |
| Destino HTTP | `https://eco.taobao.com/router/rest` | `https://api-sg.aliexpress.com/sync` |
| Verbo e codificação | POST, form UTF-8 | POST, form UTF-8 |
| Nome da operação | `aliexpress.logistics.buyer.freight.calculate` | `aliexpress.ds.product.get` |
| Aplicação | `app_key`, da mesma configuração existente | Mesma configuração |
| Token persistido | `session`, sem renovação | `session`, pelo fluxo normal existente |
| Assinatura | `sign_method=hmac`, HMAC-MD5 | `sign_method=sha256`, HMAC-SHA256 |
| Entrada da assinatura | Nomes/valores ordenados, sem prefixo e sem `sign`, antes do URL encoding | Nomes/valores ordenados, sem prefixo e sem `sign` |
| Timestamp | Horário GMT+8, `yyyy-MM-dd HH:mm:ss` | Epoch em milissegundos |
| Dados da consulta | JSON no parâmetro `param_aeop_freight_calculate_for_buyer_d_t_o` | Parâmetros de produto diretos |
| Resposta | `format=json`, `v=2.0` | `format=json`, `v=2.0` |

O cliente de frete corresponde à referência pública legada consultada. Nessa referência, o Access Token é passado como **`session`**, não como um novo campo `access_token`. Não foi comprovada divergência de assinatura, timestamp, nome do parâmetro ou App Key no código; os testes existentes conferem a requisição fixa e recalculam sua assinatura com credenciais sintéticas.

Diferenças em relação ao cliente de produtos não demonstram, por si só, que os dois métodos devam usar o mesmo transporte. A documentação atual no domínio `openservice.aliexpress.com` não pôde ser obtida nesta investigação, inclusive em tentativa de leitura pública sem credenciais. A compatibilidade do método de frete com `/sync` e com esta aplicação permanece pendente de documentação específica/evidência real. Não foi introduzida troca de host, parâmetro de token ou assinatura por tentativa, nem fallback automático.

## Correção aplicada

O contrato HTTP local mantém os mesmos campos. `safeCode` passa a distinguir os códigos conhecidos abaixo, somente quando recebidos no envelope TOP `error_response`:

| Código TOP | safeCode | Significado seguro |
| --- | --- | --- |
| 21 | `TOP_21_MISSING_METHOD` | Método ausente |
| 22 | `TOP_22_INVALID_METHOD` | Método não reconhecido neste endpoint |
| 24 | `TOP_24_MISSING_SIGNATURE` | Assinatura ausente |
| 25 | `TOP_25_INVALID_SIGNATURE` | Assinatura rejeitada |
| 28 | `TOP_28_MISSING_APP_KEY` | Identificação da aplicação ausente |
| 29 | `TOP_29_INVALID_APP_KEY` | Identificação da aplicação rejeitada neste endpoint |

O painel traduz cada caso com mensagens locais. O código genérico antigo continua aceito para compatibilidade, mas não oferece evidência suficiente para uma correção de transporte. Textos, submensagens, identificadores de rastreamento, assinatura, tokens e credenciais do fornecedor não são retornados. Valores/códigos arbitrários não são ecoados.

Erros numéricos em `result.error_code` ou nas modalidades permanecem rejeições de negócio sem classificação comprovada; não viram automaticamente erro de assinatura, token, permissão ou limite TOP. Os subcódigos explicitamente conhecidos continuam reconhecidos. Um código de plataforma conhecido prevalece sobre um subcódigo contraditório.

## Próximo teste no Railway

Após revisão/publicação manual, executar uma vez pelo botão já existente em `/admin/produtos`, ou pelo POST ADMIN `/api/admin/integrations/aliexpress/freight-diagnostic`. Compartilhar somente `status` e `safeCode` sanitizados.

- Se vier 22, confirmar disponibilidade e transporte do método para o app no portal oficial; não concluir que o token foi revogado.
- Se vier 25, confrontar o contrato efetivo de transporte/assinatura do app; a implementação atual corresponde à referência legada. Não regenerar credenciais por tentativa.
- Se vier 29, conferir ambiente e identificação da aplicação no console, sem expor seu valor e sem concluir que a credencial seja globalmente inválida.
- Se ainda vier `PROVIDER_PROTOCOL_REJECTED`, a versão antiga do backend continua atendendo a consulta.
- Qualquer sucesso continua sendo diagnóstico com origem CN presumida, sem confirmação por SKU ou CEP. Não libera checkout ou ordens.

Nenhuma consulta real com a autorização Railway foi executada nesta tarefa. Não foram alterados OAuth, credenciais, produtos, pedidos, pagamentos ou checkout. A proteção ADMIN e a leitura sem refresh permanecem. Sem migração, commit, push ou deploy.

## Arquivos desta correção

- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticClient.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticClientTests.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticIntegrationTests.java`
- `../frontend/lib/admin/aliexpress-freight.ts`
- `../frontend/components/admin/AliExpressFreightDiagnostic.tsx`
- `../frontend/tests/aliexpress-freight.test.cjs`
- `ALIEXPRESS-FREIGHT-DIAGNOSTIC.md` e este documento.

## Validações executadas

- Suíte completa do backend: 523 testes, zero falhas/erros (`target/freight-protocol-regression.log`). Foram adicionados 16 casos de regressão para códigos específicos, origem dos erros, campos sensíveis e resposta ADMIN sem refresh.
- Frontend: 36 testes aprovados, incluindo a preservação dos códigos específicos e suas mensagens; `npx tsc --noEmit` aprovado.
- ESLint dos arquivos frontend alterados: aprovado.
- `git diff --check`: aprovado.
