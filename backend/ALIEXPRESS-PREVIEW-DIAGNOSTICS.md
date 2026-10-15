# Diagnóstico de INCOMPLETE na prévia AliExpress

## Escopo e conclusão

O fluxo é `AliExpressProductService.preview` → obtenção/possível refresh do token → `AliExpressProductClient.get` → `AliExpressProductNormalizer.normalize`.

OAuth concluído não comprova que a resposta de produto satisfaz todas as validações do normalizador. Sem reproduzir a chamada real ou receber os novos logs seguros, a condição concreta da ocorrência relatada permanece não confirmada. Esta alteração acrescenta observabilidade; não adapta formatos, afrouxa validações nem muda preços, estoque, persistência ou publicação.

Os logs específicos começam com `AliExpress preview incomplete`. `AliExpressExceptionHandler` continua registrando a categoria `reason=INCOMPLETE` e retornando o mesmo HTTP 502/ApiError com mensagem genérica. Nenhum detalhe diagnóstico foi incluído na exceção pública ou no corpo HTTP.

## Todos os pontos de falha

| Etapa | Campo/condição observada | Diagnóstico |
|---|---|---|
| `response_body` | corpo Java null ou raiz JSON não objeto | `expected_object`, com tipo `NO_BODY`, `ARRAY`, `STRING`, `NULL`, etc. |
| `response_result` | `result` ausente, null, array ou escalar, depois da checagem de sucesso | `expected_object`, com tipo JSON observado |
| `base` | `product_id`/`subject` obrigatório ausente, null, vazio ou acima de 150/1000 caracteres | `required_value_missing_or_blank` ou `value_too_long` |
| `base` | ID retornado diferente do solicitado | `product_id_mismatch`, sem registrar nenhum dos IDs |
| `description` | `detail` acima de 200.000 caracteres | `description_too_long` |
| `main_images` / `variant_properties` | mais de 100 imagens válidas distintas em uma lista processada | `too_many_images` |
| `variants`, `variant_properties`, `product_attributes` | coleção ou wrapper presente em formato diferente de array | `expected_array` |
| `variants` | nenhum SKU ou mais de 1.000 variantes | `no_variants` / `too_many_variants` |
| `variant` | `sku_id` obrigatório ausente/vazio/acima de 150 caracteres, ou duplicado | validação obrigatória/limite ou `duplicate_sku_id` |
| `variant` | `sku_attr` acima de 500 caracteres | `sku_attr_too_long` |
| `variant` | `sku_code` acima de 150 caracteres | `value_too_long` |
| `variant_prices` | `sku_price`/`offer_sale_price` incompatível com o formato existente ou não positivo | `invalid_price_format` / `non_positive_price` |
| `variant_currency` | `currency_code` obrigatório ausente/vazio/acima de três caracteres, ou código não reconhecido por `Currency.getInstance` | validação obrigatória/limite ou `unexpected_exception` com `IllegalArgumentException` |
| `variants` | moedas divergentes entre SKUs | `mixed_currencies` |
| `variant_stock` | `sku_available_stock` obrigatório ausente/vazio/acima de dez caracteres, não inteiro, fora de int32 ou negativo | validação obrigatória/limite, `unexpected_exception` com `NumberFormatException`, ou `negative_stock` |
| propriedades/atributos/campos auxiliares | chave acima de 150 caracteres, valor acima de 1.000 ou valores conflitantes para a mesma chave | `attribute_key_too_long`, `attribute_value_too_long`, `conflicting_attribute` |
| `logistics` | `ship_to_country` retornado diferente do país consultado | `country_mismatch`, sem registrar os valores |
| qualquer leitura via `text` | campo esperado como escalar recebido como objeto/array | `expected_scalar`, com nome fixo do campo e tipo observado |
| `fingerprint` | exceção na serialização/hash da prévia | `unexpected_exception`, campo `normalized_preview` |
| qualquer etapa do normalizador | exceção não classificada anteriormente, inclusive parse numérico/moeda, raiz null ou falha na normalização | `unexpected_exception`, com etapa/campo atuais e somente nome da classe da exceção |
| `token_expiry` | expiração de acesso/refresh fora do intervalo suportado durante renovação | `invalid_expiration`, campo fixo `access_token_expiration` ou `refresh_token_expiration` |

Os helpers `text`, `required`, `bounded`, `array`, `images`, `money` e `put` agora recebem o contexto local da normalização. As condições e limites anteriores foram preservados. Coleções opcionais ausentes continuam aceitas onde já eram aceitas. URLs de imagem inválidas continuam gerando o mesmo aviso de omissão; não passaram a causar INCOMPLETE.

Erros de transporte/JSON do cliente, erros retornados pelo provedor e produtos fora de venda seguem suas categorias anteriores. Nem todo HTTP 502 significa INCOMPLETE; confirme a categoria do handler e a linha diagnóstica anterior.

## Como ler os logs

Exemplo de ausência de identificação em um SKU:

```text
AliExpress preview incomplete; stage=variant; field=sku_id; condition=required_value_missing_or_blank; variantIndex=0; propertyIndex=-1; actualType=MISSING; exceptionType=NONE
```

Exemplo de tipo inesperado do resultado:

```text
AliExpress preview incomplete; stage=response_result; field=result; condition=expected_object; actualType=STRING
```

`variantIndex` e `propertyIndex` usam base zero. `-1` significa que o diagnóstico não se refere a um elemento individual. `actualType` informa o tipo de um campo inspecionado; `NOT_INSPECTED` é usado quando não há um escalar JSON específico, e `ARRAY` identifica a coleção nas checagens de quantidade. Os nomes de campo são definidos no código; nunca se registra a chave dinâmica de atributo fornecida pelo vendedor.

Não são registrados valores dos campos, IDs de produtos/SKUs, preços, estoque, países, moedas, URLs de autorização, credenciais, códigos de autorização, tokens, respostas completas ou mensagens/causas/stack traces das exceções. Apenas os tipos das exceções são registrados. O contexto é local a cada chamada e não usa estado global ou ThreadLocal.

Para investigar a ocorrência real: reinicie o backend local com esta versão, repita exatamente a mesma requisição administrativa de prévia e procure a linha `AliExpress preview incomplete` imediatamente anterior ao erro genérico. Compartilhe somente essa linha se precisar continuar a investigação. Não é necessário ativar TRACE de HTTP, dump de resposta nem logs de parâmetros sensíveis. Esta entrega não fez chamadas reais à API nem deployment.

## Arquivos e testes

Produção: `AliExpressProductClient.java`, `AliExpressProductNormalizer.java` e somente o diagnóstico de expiração em `AliExpressTokenService.java`.

Testes: nova `AliExpressPreviewDiagnosticsTests.java`; ampliados `AliExpressProductClientTests.java`, `AliExpressProductImportIntegrationTests.java` e `AliExpressTokenPersistenceTests.java`.

Cobertura adicional: 42 cenários para formatos do corpo/result, campos obrigatórios, limites, arrays, SKUs duplicados, preços, moedas, estoque, atributos, país, fingerprint, expirações e resposta HTTP genérica. Valores sintéticos sensíveis foram injetados nas fontes e nas mensagens de exceção para confirmar que não aparecem nos logs. A suíte AliExpress passou com 124 testes usando mocks/H2.

Validação final: `.\mvnw.cmd clean test` terminou com **BUILD SUCCESS, 392 testes, zero falhas/erros/ignorados**. `git diff --check` passou. Não houve chamada real ao AliExpress, alteração de configuração privada, commit, push ou deploy.
