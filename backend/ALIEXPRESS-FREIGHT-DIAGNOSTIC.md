# Consulta real de diagnóstico de frete AliExpress

Atualização do diagnóstico de protocolo: os erros TOP conhecidos agora preservam seu número em `safeCode`, por exemplo `TOP_22_INVALID_METHOD`, `TOP_25_INVALID_SIGNATURE` e `TOP_29_INVALID_APP_KEY`. Códigos de negócio não são interpretados como TOP por equivalência numérica. Consulte [ALIEXPRESS-FREIGHT-PROTOCOL.md](ALIEXPRESS-FREIGHT-PROTOCOL.md) para evidências, comparação com produtos e limitações da conclusão sobre o erro real.

## Endpoint

`POST /api/admin/integrations/aliexpress/freight-diagnostic`

Requer a autenticação ADMIN existente. Não recebe corpo nem parâmetros: o caso de teste é fixo no servidor. `GET` não executa consulta. A resposta usa `Cache-Control: no-store`. Erros de autenticação/autorização do ADM continuam HTTP 401/403; resultados da operação de diagnóstico usam HTTP 200 e o campo `status`.

Após revisar e publicar manualmente, execute esse POST uma vez no ambiente Railway que contém a autorização, usando o Bearer ADMIN pelo fluxo habitual. Não compartilhe esse Bearer, Access Token ou Secret. Esta tarefa implementou e testou o endpoint com HTTP simulado; não o publicou nem executou consulta usando a autorização do Railway.

## Contrato e limitações

Referências oficiais consultadas:

- Método: https://developer.alibaba.com/doc2/apiDetail.htm?apiId=39371
- Assinatura TOP e erros de plataforma: https://developer.alibaba.com/docs/doc.htm?articleId=101617&docType=1&treeId=153

Transporte documentado: HTTPS `https://eco.taobao.com/router/rest`, POST form UTF-8, autorização `session`, versão 2.0, retorno JSON. Assinatura `sign_method=hmac`: parâmetros ordenados por nome, concatenação de nomes/valores sem URL-encoding antes da assinatura, HMAC-MD5 com App Secret como chave, hexadecimal maiúsculo; timestamp `yyyy-MM-dd HH:mm:ss` em GMT+8. O cliente de produtos não foi alterado: seu protocolo `/sync` é diferente.

Objeto enviado em `param_aeop_freight_calculate_for_buyer_d_t_o`:

```json
{
  "product_id": 1005010661577657,
  "product_num": 1,
  "country_code": "BR",
  "send_goods_country_code": "CN",
  "price": "25.39",
  "price_currency": "BRL"
}
```

**`sku_id` não consta no contrato público desse DTO e não é enviado.** A SKU solicitada `12000053109276547` permanece referência do caso de teste; o resultado não comprova cotação para ela. CEP também não consta desse contrato. CN é uma hipótese fornecida para este teste, não uma origem confirmada. O sucesso não habilita compra, frete do checkout ou criação de ordens. Não há fallback automático para outro protocolo/método.

## OAuth e dados seguros

`AliExpressTokenService.existingAccessToken()` lê a autorização já persistida para a aplicação, sem bloqueio de escrita, e só a usa se restarem mais de 30 segundos de validade local. Se ausente, ilegível ou expirada, o diagnóstico retorna `LOCAL_AUTHORIZATION_UNAVAILABLE` sem chamar o fornecedor. Não chama `accessToken()`, refresh, troca de código, OAuth authorize ou gravação no banco. A infraestrutura de criptografia existente é reutilizada e nenhuma credencial é retornada.

O cliente possui host fixo, timeouts de conexão/leitura, redirects desabilitados, uma única chamada e nenhuma política de repetição implementada. Credenciais ficam no corpo transmitido por streaming, não na URL. A resposta é lida diretamente com limite de 256 KiB, sem conversores que imprimam corpo em DEBUG. Não há logs de parâmetros, token, Secret, respostas brutas, exceções do fornecedor ou dados pessoais nesse cliente.

Retorno: `status`, `safeCode`, `methods` e `limitations`. Cada modalidade contém identificador, `amount` decimal, moeda e prazo estimado reconhecido. Nomes de serviços/códigos são restritos, mensagens livres do fornecedor não são retornadas. Um prazo de formato desconhecido retorna `null`; não vira prazo garantido nem prazo inventado. Valores ausentes/negativos ou conflitantes entre `amount` e `cent` invalidam o resultado. Não existe conversão para BRL, arredondamento, estimativa de impostos ou frete grátis presumido. Moeda retornada diferente de BRL é preservada.

## Interpretação

- `SUCCESS`: resposta explícita de sucesso com modalidades válidas. Evidência apenas desta operação e de seus parâmetros limitados.
- `PERMISSION_DENIED`: negativa estruturada conhecida (por exemplo TOP 11), que também pode envolver pacote de acesso ou restrição de IP. Conferir o console do app; não significa necessariamente que o token está expirado.
- `TOKEN_REJECTED`: TOP 26/27 ou código estruturado conhecido de sessão inválida. Representa rejeição neste transporte/método, não comprova revogação global do token em outros endpoints; pode envolver compatibilidade entre sessão, aplicação e protocolo. Não há renovação automática.
- `PRODUCT_UNAVAILABLE`: resposta de sucesso sem modalidades para os parâmetros de teste. Não significa produto globalmente indisponível; a origem presumida pode influenciar.
- `PARAMETER_ERROR`: rejeição estruturada de parâmetros; não comprova ausência de permissão.
- `PROTOCOL_ERROR`: método, assinatura ou App Key rejeitados pelo TOP; não muda configurações nem tenta outro transporte.
- `COMMUNICATION_ERROR`: timeout, falha de rede ou HTTP sem evidência estruturada aceita. Um HTTP 403 isolado não é usado como prova de falta de permissão da API.
- `INVALID_RESPONSE`: corpo/formato/valores inválidos ou inconsistentes.
- `PROVIDER_REJECTED`: rejeição sem classificação comprovada pelos códigos conhecidos. Não infere causa a partir de mensagens livres.
- `RATE_LIMITED`, `NOT_CONFIGURED`, `LOCAL_AUTHORIZATION_UNAVAILABLE`: bloqueios explícitos correspondentes.

`safeCode` é um código controlado pela inGarage; códigos e textos arbitrários do fornecedor nunca são ecoados. As limitações sempre incluem origem CN não confirmada, SKU não enviada, ausência de CEP, prazo estimado e bloqueio do checkout.

O resultado não é persistido: o GET anterior de autorização continua com `providerValidation` e `freightPermission` em `NOT_VERIFIED`. Preserve somente o resultado sanitizado deste POST para análise; não trate uma consulta pontual como autorização permanente.

## Arquivos alterados

- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressTokenService.java`
- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressDiagnosticsController.java`
- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticClient.java`
- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticResult.java`
- `src/main/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticService.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressExistingAccessTokenTests.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticClientTests.java`
- `src/test/java/com/garage/garageapi/integration/aliexpress/AliExpressFreightDiagnosticIntegrationTests.java`
- Este documento.
- `ALIEXPRESS-AUTHORIZATION-DIAGNOSTIC.md` (referência ao novo POST; GET inalterado).

Sem migração, frontend, atualização de pedidos, pagamento, compra, alteração de credenciais, commit, push ou deploy.

## Validações

- Suíte completa do backend: `mvn test`, 507 testes, zero falhas/erros. Log local: `target/freight-diagnostic-full.log`.
- Os 20 novos casos cobrem assinatura/DTO/transporte, modalidades e moedas, negativas conhecidas e desconhecidas, parâmetros, comunicação/timeout, corpo inválido, valores conflitantes/ausentes, ADMIN e proibição de refresh/persistência do token.
- `git diff --check`: aprovado.
- Frontend não foi alterado nesta tarefa.
