# Validação territorial BR — 27/10/2026

Revisão posterior: [prontidão para publicação no Railway](ALIEXPRESS-TERRITORIAL-PUBLICATION.md). O caminho de token manual foi removido do ensaio operacional; nova regressão/empacotamento concluídos, sem nova chamada real e sem deploy.

## Resultado efetivo

Implementado diagnóstico ADMIN somente leitura de `aliexpress.ds.address.get`, com OAuth persistido e sem refresh. Consulta real realizada com o access token fornecido explicitamente pelo responsável, apenas em memória, usando a app key/secret locais existentes. O provedor recusou a chamada com **`error_response.code=IllegalAccessToken`**. Não retornou árvore territorial.

Portanto, **permissão efetiva da aplicação, códigos de Santa Catarina e Joinville e aceitação desses códigos por frete permanecem não confirmados**. Não confundir rejeição de token com negação de grant, inexistência da cidade ou ausência de cobertura. Não é possível determinar só pelo código se houve expiração, incompatibilidade entre token e app ou outro problema de validade.

Foram feitas duas chamadas do mesmo método de leitura: a primeira retornou recusa genérica no diagnóstico; após implementar códigos de erro sanitizados, a segunda identificou `IllegalAccessToken`. A versão final classifica esse código observado como `TOKEN_REJECTED / PROVIDER_TOKEN_REJECTED`; isso foi validado por replay sintético, sem terceira chamada real. As respostas integrais não foram gravadas. O recorte real preserva o status que o parser anterior emitia, além do código observado, para não reescrever a evidência histórica.

O fluxo ADMIN usa exclusivamente `AliExpressTokenService.existingAccessToken()`. A configuração local não contém `ALIEXPRESS_TOKEN_ENCRYPTION_KEY`; por isso não foi possível ler o OAuth criptografado do banco. O ensaio com token fornecido é separado e **não comprova que o OAuth persistido foi utilizado**. Não houve substituição, armazenamento ou refresh de credenciais.

## Contratos oficiais relidos

Leitura pública HTTP 200, realizada nesta investigação. URLs, hashes e campos estão em [ALIEXPRESS-TERRITORIAL-BR-EVIDENCE.json](ALIEXPRESS-TERRITORIAL-BR-EVIDENCE.json).

| Contrato | Constatação |
|---|---|
| [aliexpress.ds.address.get](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.address.get&methodType=GET%2FPOST) | docId 1584, lastModified 2024-03-25 10:47:28, título “Get restrict name on AE”, authType=2. countryCode String obrigatório; language e isMultiLanguage Strings opcionais. O token comum é opcional; o exemplo Java usa TOP sem token. Isso não impede enviá-lo, nem garante que um token inválido seja ignorado. |
| [aliexpress.ds.freight.query](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST) | docId 1579, lastModified 2024-03-04 20:23:41, authType=1. queryDeliveryReq contém shipToCountry, produto, selectedSkuId String, quantidade, moeda e opcionais territoriais. Sem campo de CEP. |

`ds.address.get` declara `result.ret` Boolean, `result.code` String, `result.data.country` String, `type` String e **`children` String contendo JSON**. O exemplo usa US e texto placeholder para children; não define o esquema interno nem fornece exemplo BR. Não estabelecer formato de código a partir desse exemplo. O exemplo Java oficial confirma TOP; a implementação conserva o transporte já usado na integração: `/sync`, session, HMAC-SHA256, timestamp epoch, v=2.0 e simplify=true. Não houve troca de host/protocolo por tentativa.

## Comparação territorial sem suposições

| Informação | Endereço DS | Frete DS | Estado da prova |
|---|---|---|---|
| País | countryCode=BR no request; data.country no response | queryDeliveryReq.shipToCountry=BR | BR é parâmetro solicitado, não país efetivamente retornado neste ensaio recusado. |
| Estado | Possíveis campos internos de children; esquema não definido no documento | province OU provinceCode | Não foi observado código de Santa Catarina; não enviar SC como provinceCode automaticamente. A descrição de província no frete se refere a local-to-local. |
| Cidade | Possível nó/identificador interno de children | cityCode opcional | Não foi observado código Joinville; não usar código IBGE ou deduzir que id/code/value equivale a cityCode. |
| CEP | Nenhum campo declarado no request de endereço | Nenhum campo declarado no request de frete | Não há evidência de suporte a CEP, nem de equivalência de preço/elegibilidade entre CEPs. |

O contrato de frete permite representar destino mais específico que apenas BR (província/cidade). **Ainda não foi provado que esses campos possam ser preenchidos corretamente para BR, sejam efetivamente considerados no produto em questão ou cubram o endereço completo.** Nenhum código foi enviado ao frete nesta investigação territorial.

## Implementação

Novo endpoint: `POST /api/admin/integrations/aliexpress/territorial-diagnostic`, sem body. ADMIN obrigatório, resposta `Cache-Control: no-store`; GET não dispara consulta. Não há consulta automática na inicialização nem alteração de frontend/checkout.

- `AliExpressTerritorialDiagnosticService` lê uma única vez o token persistido via método sem refresh; falha local não chama o fornecedor.
- `AliExpressTerritorialClient` fixa método de leitura, país BR e `isMultiLanguage="true"`. O parâmetro language, opcional, é omitido para não inventar mapeamento PT/pt_BR. Sem retries, redirects, fallback sem token, frete, pedido ou pagamento.
- Aceita envelope completo/simplificado e ret Boolean ou String estrita. Exige data.country=BR antes de analisar a árvore. Decodifica children como JSON, com limites de corpo (4 MiB), profundidade, nós e quantidade de matches.
- Procura rótulos exatos Santa Catarina e Joinville, sem distinção de caixa/acentos. Emite somente campos territoriais permitidos e ancestrais observados, preservando String versus Number e zeros iniciais quando a API entrega String. Campos arbitrários, mensagens e corpo bruto não são copiados. Esquema não suportado é sinalizado; alvo não observado nunca significa cidade indisponível.
- `observedFieldNames` identifica somente nomes de campos permitidos vistos na árvore. `matches[].observed.fields` mantém nomes de origem (por exemplo id/code/value), sem renomear para provinceCode/cityCode. Os ancestrais devem comprovar que o Joinville observado está sob Santa Catarina; homônimo isolado não basta.
- `providerCode`/`providerSubCode` passam por validação de formato e supressão de credenciais. Erros desconhecidos não viram confirmação de permissão. `CALL_ACCEPTED` só ocorre após retorno de sucesso e país BR, não significa grant de frete nem reserva de preço.

Os exemplos dos testes usam IDs fictícios como STATE-EXAMPLE, city-example e 0099. **Não são códigos AliExpress reais** e não devem ser utilizados em produção.

## Execução controlada

O endpoint deve ser chamado manualmente em ambiente que já tenha a versão e as configurações necessárias, com sessão ADMIN existente. Não foi feito deploy para disponibilizá-lo em produção nesta tarefa.

Há também `AliExpressTerritorialLiveProbeTests`, desabilitado por padrão. Só executa com `ALIEXPRESS_TERRITORIAL_LIVE=true`. Usa ambiente existente; sem inicializar Spring, Hibernate ou schema. Lê apenas access_ciphertext/access_expires_at da autorização correspondente à aplicação em transação JDBC read-only, rollback e timeout; reutiliza a descriptografia/validação de `AliExpressTokenService.existingAccessToken()`. Não lê refresh token. Na revisão para publicação, o caminho alternativo de token manual foi removido também do probe. A evidência histórica USER_SUPPLIED_ACCESS_TOKEN registra exclusivamente o ensaio anterior recusado; essa credencial não será reutilizada.

O resultado operacional é escrito apenas em `target/territorial-live-sanitized.json`, sem credenciais. Não colocar valores de credenciais em arquivos, exemplos, commits ou relatórios. A suíte normal mantém o probe desabilitado e usa mocks/H2.

## Bloqueios e próximo passo

1. Executar o diagnóstico com o OAuth persistido existente em ambiente capaz de descriptografá-lo, ou fornecer ao processo local a configuração já existente por mecanismo seguro. Não criar/rotacionar credenciais como parte desta tarefa. Se houver recusa, verificar vínculo app/token e validade no fluxo administrativo existente. O token enviado nesta conversa foi recusado e não será tentado novamente.
2. Após sucesso, registrar apenas os campos sanitizados do país e dos nós Santa Catarina/Joinville, incluindo a cadeia de ancestrais e os tipos originais. Se o esquema interno for diferente do suportado, ampliar o parser somente após examinar estrutura sanitizada ou esquema oficial, sem coletar resposta integral em logs.
3. Obter esclarecimento do suporte sobre quais campos de children correspondem a province/provinceCode/cityCode no frete, inclusive diferenças local-to-local/cross-border. Documentação aceita os parâmetros, mas não comprova esse mapeamento. Não usar UF ou IBGE por analogia.
4. Só depois testar, em diagnóstico de leitura separado, mesmo produto/SKU/quantidade com BR, província e cidade oficialmente mapeados. Uma resposta bem-sucedida não prova que campos foram considerados nem suporte a CEP. Validar isso com o fornecedor antes de qualquer cobertura comercial.

Checkout e pagamentos continuam bloqueados por `COUNTRY_ONLY -> DESTINATION_NOT_CONFIRMED` e pelas proteções existentes de pagamento. A regra monetária não foi alterada nem reaberta nesta tarefa. Nenhum pedido AliExpress, pagamento, alteração de credenciais ou deploy foi executado.

## Testes entregues e executados

**91 testes aprovados, zero falhas/erros; 1 ensaio real ignorado por padrão** na regressão final (Maven exit 0). O ensaio operacional real foi executado separadamente duas vezes e a recusa está registrada na evidência; sucesso do teste operacional significa que ele produziu diagnóstico, não que o provedor aceitou a chamada.

- 16 casos de cliente territorial: assinatura/transporte, BR fixo, preservação de tipos/zeros/ancestrais, JSON em String, envelope, sanitização, erros de permissão/token, código real IllegalAccessToken, árvore inválida, país diferente, limites e falhas sem retry.
- 3 casos de integração ADMIN: 401/403, GET sem efeito, no-store, uso único do token persistido, nenhuma chamada comercial e falha local sem fornecedor.
- 72 regressões existentes: diagnóstico de frete (cliente e HTTP), segurança de pagamentos e frete HTTP mantendo a rejeição de COUNTRY_ONLY.
- `git diff --check`: sem erros de whitespace. Sem alterações de frontend.

Log local: `target/territorial-final-tests.log`. Os testes de árvore usam respostas sintéticas; não comprovam IDs de Santa Catarina/Joinville nem grants da aplicação.
