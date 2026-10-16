# AliExpress — prévia e importação administrativa

## Arquitetura e contrato

O catálogo continua usando `Product`, `ProductVariant` e `ProductMedia`. Os novos campos são apenas metadados/currency do fornecedor no produto e quantidade observada no fornecedor por variante. CJ, pedidos, frete, checkout, Mercado Pago, JWT e CORS não foram alterados.

Foi reutilizado o OAuth existente. Antes desta etapa, os tokens eram descartados; agora o fluxo iniciado pelo administrador persiste os tokens criptografados e renova o access token sob demanda. Não é usado nenhum token do API Explorer.

Contrato consultado no portal oficial: [aliexpress.ds.product.get](https://openservice.aliexpress.com/doc/api.htm#/?cid=21038&path=aliexpress.ds.product.get&methodType=GET%2FPOST), conteúdo disponível no [endpoint público da documentação](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.product.get&methodType=GET%2FPOST). Conferimos também o SDK Java oficial indicado nesse documento, especialmente `TopExecutor`, `GopExecutor`, `IopUtils` e `IopHashMap`.

Decisão de transporte: o exemplo Java atual dessa operação usa `Protocol.TOP`. Portanto, produto usa `POST https://api-sg.aliexpress.com/sync` com `method=aliexpress.ds.product.get`, `app_key`, `session`, `sign_method=sha256`, `timestamp` epoch em milissegundos, `v=2.0`, `format=json`, `product_id`, `ship_to_country`, `target_currency` e `target_language`. HMAC-SHA256 sobre todos os parâmetros ordenados por nome, sem prefixo de API e sem incluir `sign`, convertido em hexadecimal maiúsculo. Isso segue o SDK atual, não o exemplo TOP legado de `eco.taobao.com` com HMAC-MD5 e horário formatado GMT+8.

OAuth continua usando GOP em `POST /rest/auth/token/create`; renovação usa `POST /rest/auth/token/refresh` com `refresh_token` e os parâmetros de sistema assinados. Nesses endpoints, o caminho da API é o prefixo da assinatura. Consulte [o guia oficial](https://openservice.aliexpress.com/doc/doc.htm#/?docId=1590).

O parâmetro opcional `state` da autorização também consta na [documentação oficial de autorização](https://developer.alibaba.com/docs/doc.htm?articleId=120687&docType=1&treeId=727). A aplicação exige seu valor de uso único no fluxo que salva tokens e mascara a URL em `toString()` para impedir impressão em logs DEBUG/TRACE.

São aceitos o envelope TOP `aliexpress_ds_product_get_response` e o corpo direto documentado. É exigido `rsp_code=200` e `result` válido; erros de transporte e `error_response` são tratados sem devolver o corpo do provedor. Listas de SKUs, propriedades e atributos aceitam arrays atuais e wrappers `ae_item_sku_info_d_t_o`, `ae_sku_property_d_t_o` e `ae_item_property` do formato anterior.

O 481 observado pelo usuário não está explicado numericamente na documentação consultada. A documentação lista restrições de país por produto e recomenda verificar país/produto. A aplicação retorna erro específico de país rejeitado, sem concluir que BR está bloqueado de forma permanente e sem trocar silenciosamente para US. A causa exata depende do app, produto, conta e permissões reais.

## Configuração e autorização

Preencha manualmente em ambiente seguro:

```env
ALIEXPRESS_APP_KEY=
ALIEXPRESS_APP_SECRET=
ALIEXPRESS_CALLBACK_URL=https://api.ingarage.com.br/api/integrations/aliexpress/callback
ALIEXPRESS_TOKEN_ENCRYPTION_KEY=
```

`ALIEXPRESS_TOKEN_ENCRYPTION_KEY` deve ser Base64 de 32 bytes aleatórios, exclusiva para essa finalidade. Gere e guarde essa chave em um gerenciador de secrets, mantendo o mesmo valor entre reinicializações e réplicas. Não use uma senha, o JWT secret ou o App Secret como chave. Não registre a chave no Git, frontend ou logs. Arquivos privados de ambiente não foram alterados por esta entrega. A variável foi acrescentada somente ao checklist versionável e à configuração do backend.

1. Faça uma requisição com JWT administrativo para `POST /api/admin/integrations/aliexpress/authorize`, sem corpo.
2. Abra no navegador a URL do campo `authorizationUrl`. Ela contém somente parâmetros públicos e um `state` aleatório, não tokens ou App Secret.
3. Autorize com a conta AE que deve ser usada pelo backend. O callback deve coincidir exatamente com o registrado no app.
4. O callback valida e consome o `state` antes da troca do código e salva os tokens apenas após sucesso. Cada state dura dez minutos e é de uso único. Em falha, inicie outro fluxo administrativo.

Compatibilidade: callbacks antigos sem `state` ainda fazem a troca e mantêm sua mensagem de sucesso, mas **descartam** os tokens e nunca substituem a autorização persistida. Para habilitar importação é necessário o fluxo `/authorize`. Qualquer `state` informado e inválido é rejeitado antes de chamar o AliExpress.

Os tokens são armazenados em `aliexpress_authorizations`, criptografados com AES-256-GCM, nonce aleatório por valor e AAD distinta por app/finalidade. O banco contém ciphertext, expirações e versão; não contém a chave, código de autorização ou identificadores pessoais desnecessários. O `state` é armazenado somente como hash SHA-256 em `aliexpress_oauth_states`. O endpoint administrativo não retorna access/refresh tokens.

Há uma autorização compartilhada por app/backend. Uma nova autorização administrativa substitui a anterior daquele app. O refresh ocorre quando restam até 30 segundos de access token, com lock pessimista no banco para evitar renovação concorrente. O refresh token rotacionado é salvo, sem estender a janela original de validade; `refresh_expires_in=0` impede refresh. Expiração/rejeição definitiva requer nova autorização. Falhas transitórias de refresh não apagam os tokens salvos. Se a chave for perdida ou trocada, a autorização anterior não poderá ser descriptografada: restaure a chave correta ou autorize novamente. Rotação multi-chave não foi implementada.

## Prévia

`POST /api/admin/integrations/aliexpress/preview`, com Bearer JWT de ADMIN:

```json
{
  "input": "https://pt.aliexpress.com/item/1005008752784588.html?spm=exemplo",
  "shipToCountry": "US",
  "targetCurrency": "USD",
  "targetLanguage": "en"
}
```

Também é aceito um product ID decimal em `input`. A URL é interpretada localmente, sem buscar seu conteúdo. Só são aceitos hosts AliExpress explicitamente permitidos e caminho `/item/ID.html`, em HTTPS. Credenciais na URL, portas alternativas, IPs, domínios semelhantes e redirecionadores são rejeitados. Links encurtados não são resolvidos nesta etapa; utilize o link completo. URLs recebidas nunca definem o destino HTTP do cliente: ele chama apenas a API fixa, com timeouts de conexão/leitura de 10/20 segundos e sem seguir redirects.

A resposta contém `productId`, `name`, `description`, `supplier`, `currency`, `images`, `variants`, `attributes`, `packageInfo`, `logistics`, `store`, `shipToCountry`, `commercialCategory`, `domesticShippingConfirmed`, `warnings` e `fingerprint`.

Cada variante contém `skuId`, `skuCode`, `rawSkuAttr`, `attributes`, `images`, `price`, `regularPrice`, `currency` e `availableStock`. Valores monetários usam BigDecimal. O preço promocional é usado quando informado; valor inválido não é substituído por um preço inventado. SKUs sem identificação ou estoque válido, duplicados ou em moedas divergentes tornam a resposta inválida.

Os identificadores de propriedades e valores são preservados junto dos rótulos originais. Não se deduz um SKU pelos textos de cor/modelo. Divergências entre `sku_attr` e propriedades geram avisos. As imagens são deduplicadas e limitadas aos CDNs AliExpress/Alibaba permitidos. O backend não baixa arquivos. URLs de imagens de variantes acima do limite da coluna de thumbnail ficam preservadas na galeria/metadados.

A descrição é convertida para texto com jsoup, removendo scripts, estilos e conteúdo incorporado. Título/descrição completos normalizados ficam nos metadados; os campos limitados do catálogo recebem resumos com aviso. Pesos e dimensões de `package_info_dto` são preservados exatamente como informados. Como o contrato consultado não explicita unidades desses campos, não atribuímos kg/cm nem fabricamos conversões para `weightGrams`/`lengthMm`.

O agrupamento comercial `Nacionais` corresponde à curadoria administrativa AliExpress solicitada. **Não comprova origem do estoque nem envio nacional:** `domesticShippingConfirmed` permanece false. A consulta US não comprova elegibilidade para venda/envio ao Brasil.

A prévia não cadastra produto nem variantes. Ela pode renovar a autorização quando necessário; isso é persistência de credenciais, não publicação no catálogo.

## Confirmação

Após revisar a prévia, envie `POST /api/admin/integrations/aliexpress/import`:

```json
{
  "input": "1005008752784588",
  "shipToCountry": "US",
  "targetCurrency": "USD",
  "targetLanguage": "en",
  "fingerprint": "COPIE_O_FINGERPRINT_DA_PREVIA",
  "name": "Nome comercial revisado",
  "category": "Acessórios",
  "variantSalePricesBrl": {"201": 99.90, "202": 99.90},
  "variantStocks": {}
}
```

`name` é obrigatório (até 150 caracteres) e `category` deve ser uma das categorias do catálogo: Lavagem, Proteção, Detalhamento ou Acessórios. `variantSalePricesBrl` contém somente os IDs das variantes selecionadas, cada um com um preço comercial obrigatório, positivo, até dez dígitos inteiros e duas casas decimais. O catálogo/checkout existente possui um único preço por produto: todos os preços selecionados devem ser iguais, independentemente da representação decimal (`99.9` e `99.90` são equivalentes). Preços diferentes são rejeitados pelo servidor; variantes ausentes no mapa não são importadas. Não há preenchimento automático com o custo.

Custos e preços do fornecedor nunca são aceitos do navegador: o backend consulta a API novamente e exige que o fingerprint dos dados seja igual ao da prévia. Mudanças exigem nova revisão. Campos de custo, estoque global e status falsificados no JSON não são usados. Os preços comerciais revisados também ficam registrados em `supplierMetadata.variantSalePricesBrl`; o preço efetivo comum é persistido em `Product.price` e aplicado a todas as variantes pelo catálogo existente. Não houve mudança no checkout nem necessidade de migração.

`variantStocks` permite quantidades revisadas por `skuId`; são aceitas somente reduções ou valores iguais ao observado na API, apenas para variantes selecionadas. IDs desconhecidos, quantidades negativas ou superiores ao snapshot são rejeitados. Campo omitido usa a quantidade observada. A interface atual mantém o estoque confirmado pelo fornecedor.

Em uma transação, são criados produto inativo, variantes selecionadas com IDs externos, SKUs e estoque do fornecedor, imagens e metadados. A resposta HTTP 201 contém `productId` interno, `supplierProductId`, `active=false` e `variantCount`. O SKU interno é `AE-ID`, e o slug `aliexpress-ID`. Nome e categoria são os valores revisados pelo administrador. O agrupamento da prévia não substitui essa escolha.

### Regra permanente de publicação

- Toda importação AliExpress impõe `active=false` no servidor. O contrato não aceita controle de publicação pelo navegador.
- Rascunhos aparecem na listagem administrativa padrão, que inclui ativos e inativos.
- Listagem/busca pública e os produtos relacionados usam o catálogo público filtrado por `active=true`; detalhes por ID e slug retornam 404 para inativos.
- Somente ADMIN autenticado pode ativar o produto pela funcionalidade administrativa existente. A importação nunca chama essa ativação.
- O frontend atualiza a listagem administrativa após importar e oferece um link para editar o rascunho.
- Conflitos HTTP 409 incluem apenas um código fixo em `fields.code`: `DUPLICATE`, `STALE_PREVIEW` ou `AUTHORIZATION_REQUIRED`. Nenhum texto interno do fornecedor é exibido.

Custos originais ficam nas variantes com a moeda efetiva retornada e nos metadados do produto. `supplierCostUsd` só é preenchido para USD; `costPrice` BRL só quando a API efetivamente retorna BRL. Nenhuma taxa USD/BRL fictícia é aplicada. O campo de preço comercial usa o valor BRL revisado pelo administrador.

Duplicidade por ALIEXPRESS + ID ou colisão de SKU externo causa HTTP 409, sem atualizar o produto anterior ou seu preço manual. A restrição global pré-existente de `supplier_product_id` também foi preservada: se outro fornecedor tiver exatamente o mesmo ID, a importação é rejeitada conservadoramente. Relaxar essa restrição para uma chave composta requer uma futura migração revisada, não executada nesta entrega.

A galeria usa `ProductMediaSource.MANUAL`, já existente, pois as imagens entram por curadoria confirmada; `sourceUrl` preserva a URL original e produto/variantes identificam o fornecedor ALIEXPRESS. Assim não foi necessário alterar as constraints/enums usados pela galeria CJ.

## Revisão posterior de estoque

- `GET /api/admin/integrations/aliexpress/products/{productId}/stock`: retorna IDs internos/externos das variantes e `supplierStockQuantity`.
- `PATCH /api/admin/integrations/aliexpress/products/{productId}/variants/{variantId}/stock`, corpo `{"quantity":4}`: ajusta quantidade revisada apenas no produto ALIEXPRESS inativo, sem exceder o snapshot original.
- O preço pode ser revisado pelo endpoint administrativo de produto já existente.

`stockQuantity` local continua zero para dropshipping; `supplierStockQuantity` é um snapshot administrativo, não reserva, sincronização em tempo real ou implementação do checkout AliExpress. **Não ative esses produtos para venda até validar frete, logística, preço e suporte operacional de pedidos.** Nenhuma criação de pedidos ou tracking AliExpress foi implementada.

## Erros e implantação futura

HTTP 400: entrada inválida; 409: reautorização, duplicidade ou prévia alterada; 404: produto não encontrado; 422: indisponível/país rejeitado; 429: limite; 503: configuração ausente; 504: timeout; 502: erro de comunicação, resposta incompleta, permissão externa ou persistência. Respostas usam ApiError com mensagens controladas, sem stack trace, tokens ou corpo externo. Logs da integração incluem apenas presença, expirações e categoria da falha. Requests sensíveis usam corpo streaming, evitando logs DEBUG do formulário pelo RestClient.

A migração `V20261014_01__aliexpress_import.sql` é aditiva: cria duas tabelas e três colunas, sem apagar linhas ou remover constraints. Foi incluída no mecanismo SQL de inicialização já existente. Nenhuma migração foi executada em produção. Em um futuro deploy autorizado, revise backup, permissões DDL e compatibilidade com o PostgreSQL real; os testes de persistência automatizados usam H2 em modo PostgreSQL e não comprovam a execução no banco Railway.

Para validar o fluxo real após configurar localmente, inicie autorização administrativa, obtenha prévia do ID indicado pelo usuário com US, revise a resposta e só então confirme a criação inativa. Repita BR separadamente para investigar o 481, mantendo registro apenas de informações não sensíveis. Não use credenciais de teste do Explorer como autorização permanente. Verifique acesso à operação Drop Shipping no app e o callback cadastrado. Essa validação real não foi executada automaticamente.

## Arquivos desta etapa

Criados no pacote `integration/aliexpress`: `AliExpressIntegrationException`, `AliExpressTokenCipher`, `AliExpressAuthorization`, `AliExpressAuthorizationRepository`, `AliExpressOAuthState`, `AliExpressOAuthStateRepository`, `AliExpressOAuthStateService`, `AliExpressTokenService`, `AliExpressProductInput`, `AliExpressProductClient`, `AliExpressProductPreview`, `AliExpressProductNormalizer`, `AliExpressProductService`, `AliExpressAdminController` e `AliExpressExceptionHandler`.

Criados seis testes no mesmo pacote de testes: `AliExpressProductInputTests`, `AliExpressProductNormalizerTests`, `AliExpressProductClientTests`, `AliExpressTokenCipherTests`, `AliExpressTokenPersistenceTests` e `AliExpressProductImportIntegrationTests`; fixture sintética `src/test/resources/aliexpress-product.json`; migração aditiva; este documento.

Modificados: `pom.xml` (jsoup), `Product`, `ProductVariant`, `AliExpressAuthService`, `AliExpressCallbackController`, seus dois testes existentes, `application.properties`, `.env.example` e `ALIEXPRESS-OAUTH.md`. Alterações anteriores do workspace foram preservadas.

## Validação desta entrega

Em 14/10/2026, `.\mvnw.cmd clean test` terminou com BUILD SUCCESS: **350 testes executados, zero falhas, zero erros e nenhum ignorado**. A compilação integra essa execução. `git diff --check` passou. O subconjunto AliExpress tem 82 testes, incluindo mocks de transporte, persistência H2, criptografia, state, callback completo, concorrência de refresh, prévia read-only, confirmação transacional e autorização.

Esses resultados não comprovam permissões do app, resposta real do produto informado, elegibilidade de envio BR nem migração no PostgreSQL Railway. Essas validações continuam pendentes e não houve deploy, commit ou push.
