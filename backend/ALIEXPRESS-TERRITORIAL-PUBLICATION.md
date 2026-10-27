# Revisão para publicação do diagnóstico territorial — 27/10/2026

## Parecer

O endpoint está pronto, pela revisão de código, regressão local e empacotamento, para publicação do **diagnóstico administrativo somente leitura**. Isso não é confirmação de sucesso do fornecedor no Railway nem liberação comercial. Nenhuma chamada ao Railway/AliExpress foi executada nesta revisão; a credencial anteriormente rejeitada não foi reutilizada.

## Caminho da credencial e acesso

`POST /api/admin/integrations/aliexpress/territorial-diagnostic`

`AliExpressDiagnosticsController -> AliExpressTerritorialDiagnosticService.run -> AliExpressTokenService.existingAccessToken -> repositório/cipher -> AliExpressTerritorialClient.query`

- Endpoint sem parâmetros de token, protegido por ADMIN na configuração de segurança e na classe; resposta `Cache-Control: no-store`. GET não executa diagnóstico.
- A autorização é buscada pelo hash da app key já configurada. Apenas access token persistido, descriptografado com a chave de criptografia já configurada no backend; leitura transacional read-only, sem lock pessimista.
- Token ausente, ilegível, expirado ou expirando falha localmente; não executa refresh, nova autorização, gravação ou chamada ao fornecedor. Não tenta corrigir ou substituir chave/token.
- Corpo/query com token manual não substituem a credencial armazenada. Há teste explícito desse comportamento.
- O cliente fixa `aliexpress.ds.address.get`, país BR e endpoint `/sync`; não chama frete, pedidos ou pagamentos, não faz retry nem fallback sem token e só retorna a projeção sanitizada.
- O caminho de token manual também foi removido de `AliExpressTerritorialLiveProbeTests`. Esse teste continua opt-in, desabilitado nesta revisão e excluído do JAR de produção.

## Validação executada

Maven offline, testes `AliExpress*Tests`, `ShippingIntegrationTests`, `PaymentIntegrationTests` e `PaymentWebhookIntegrationTests`, seguido de `package`/Spring Boot repackage: **370 casos contabilizados, 369 aprovados, 1 ignorado (probe real), zero falhas/erros; BUILD SUCCESS**.

Os testes novos verificam também a cadeia com o serviço real de tokens e repositório/cipher simulados: leitura/descriptografia única, nenhuma escrita/refresh/lock, falha com autorização ausente/expirada ou erro de descriptografia. A regressão mantém bloqueios de frete COUNTRY_ONLY, pedidos e pagamentos.

Artefato local: `target/garage-api-0.0.1-SNAPSHOT.jar`. Conferida a presença de `AliExpressDiagnosticsController`, `AliExpressTerritorialClient`, `AliExpressTerritorialDiagnosticService` e `AliExpressTerritorialDiagnosticResult` em BOOT-INF/classes; o probe real não está empacotado. Log local: `target/territorial-publication-check.log`. `git diff --check` sem erros de whitespace.

## Publicação e chamada posterior

Incluir os novos arquivos de produção e testes na revisão/publicação, além da alteração do controller: parte deles ainda está sem rastreamento no Git. Nenhum commit, staging ou deploy foi realizado automaticamente. Não há migração nem nova variável de produção para este endpoint; conservar a configuração Railway existente, inclusive `ALIEXPRESS_TOKEN_ENCRYPTION_KEY`. Não adicionar access token manual, flags do probe ou gerar autorização nova.

Após a publicação manual autorizada, acionar o POST sem body com a autenticação ADMIN normal da inGarage. Essa autenticação é a sessão da aplicação, não o OAuth AliExpress. O backend resolverá o OAuth armazenado. HTTP 200 significa entrega do diagnóstico: conferir também `status`, `safeCode` e `applicationAccess`; `CALL_ACCEPTED` não confirma grant de frete nem cobertura de CEP.

Se houver sucesso, revisar os campos sanitizados e ancestrais de Santa Catarina/Joinville. Se retornar `LOCAL_TOKEN_UNAVAILABLE`, investigar a autorização/configuração existente sem substituí-la automaticamente. Se o fornecedor recusar, preservar os códigos sanitizados para investigação. A validação territorial real e o mapeamento para frete seguem pendentes até essa chamada.

Checkout e pagamentos permanecem bloqueados. A publicação do diagnóstico não modifica a regra monetária nem promove a consulta territorial a cotação comercial.
