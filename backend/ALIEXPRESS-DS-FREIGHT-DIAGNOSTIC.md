# Diagnóstico ADM — aliexpress.ds.freight.query — 20/10/2026

## Implementado e limites

O diagnóstico existente em `/admin/produtos` passa a consultar exclusivamente `aliexpress.ds.freight.query`, sem nova tela e sem fallback para `freight.calculate`. Produto e SKU são reais e revalidados pelo backend. A ausência de `Ships From` no produto deixa de bloquear o teste: a nova API devolve `ship_from_country` por modalidade e não exige origem na requisição. País da loja/destino não é usado para inferir origem.

A importação continua independente de frete. Checkout, pedidos e pagamentos AliExpress continuam bloqueados. Nenhuma credencial foi alterada/renovada. Não houve commit, push, deploy nem criação de pedido/pagamento.

**Permissão da inGarage e cotação real para BR permanecem pendentes.** Esta entrega prepara o teste administrativo; não afirma que houve execução no Railway. Uma resposta positiva desta operação também não comprova permissão para criar pedidos.

## Contrato e SDK conferidos

O [contrato público oficial](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST) foi relido em 20/10/2026: docId 1579, `authType=1`, exemplo Java com `Protocol.TOP`. Os campos, erros publicados, hashes e a inconsistência monetária estão no recorte `ALIEXPRESS-DS-FREIGHT-DIAGNOSTIC-EVIDENCE.json`, sem credenciais da aplicação/dados pessoais.

O SDK indicado pelo próprio contrato foi baixado apenas para leitura do fonte, sem execução:

- Origem: `https://ae-open-platform-public.oss-ap-southeast-1.aliyuncs.com/online/oss_1792472413910_0_kjhG.zip`.
- SHA-256: `293A815A5FCEB1747EEE650234C2B5FBF8D75ADA450CF66F3547280BFCA4EDB1`.
- `TopExecutor`: rota `/sync`, token em `session`, assinatura com prefixo de API vazio.
- `BaseExecutor`: SHA-256; `IopHashMap`: Date como epoch milliseconds; `IopUtils`: ordenação dos parâmetros e HMAC-SHA256.

Gateway único: `https://api-sg.aliexpress.com/sync`, o mesmo da integração funcional de produtos. POST form UTF-8, `app_key`, `session`, `sign_method=sha256`, timestamp em milissegundos, `format=json`, `v=2.0`, `simplify=true`. Assinatura hexadecimal maiúscula calculada antes do URL encoding, excluindo `sign`.

Os metadados comuns/cURL do portal incluem campos de outro protocolo, como `access_token`. O diagnóstico segue o executor TOP indicado pelo exemplo Java e conferido no SDK. Não mistura TOP/GOP, não tenta gateways alternativos e não faz retries ou refresh OAuth.

## Requisição brasileira suportada

O parâmetro de negócio `queryDeliveryReq` contém os sete campos obrigatórios:

| Campo | Valor |
| --- | --- |
| `productId` | ID externo do produto, String |
| `selectedSkuId` | SKU real pertencente ao produto, String sem conversão para Number JavaScript |
| `quantity` | Quantidade inteira escolhida, 1–100, limitada ao estoque retornado |
| `shipToCountry` | `BR` |
| `currency` | `BRL` solicitada; a moeda efetivamente retornada é preservada |
| `language` | `pt_BR` |
| `locale` | `pt_BR` |

`province`, `provinceCode` e `cityCode` são opcionais. O contrato determina usar província por nome **ou** código, não ambos, e relaciona a província ao frete local-to-local. Esses campos são omitidos enquanto não houver mapeamento BR validado; UF/código IBGE não são presumidos como códigos AliExpress.

O DTO não documenta CEP, rua, número, bairro, complemento ou dados do destinatário. O painel declara o escopo BR por país, sem endereço completo. Mesmo sucesso não constitui frete definitivo do checkout.

## Teste manual pelo ADM após publicação manual

1. Em `/admin/produtos`, selecionar um AliExpress da listagem atual ou informar seu ID externo.
2. Clicar **Consultar SKUs**. O backend lê o produto com token persistido, sem refresh.
3. Selecionar SKU e quantidade.
4. Clicar **Testar cotação de frete**. O backend relê produto/SKU/estoque e usa o mesmo token persistido em uma chamada de frete.
5. Registrar somente status/código seguro e os dados públicos normalizados. Não copiar payloads HTTP brutos, tokens ou dados de clientes.

Endpoints existentes, ADMIN e `Cache-Control: no-store`:

- `POST /api/admin/integrations/aliexpress/freight-diagnostic/product`: `{"productId":"..."}`.
- `POST /api/admin/integrations/aliexpress/freight-diagnostic`: `{"productId":"...","skuId":"...","quantity":1}`.

O navegador envia somente produto, SKU e quantidade ao backend; nunca recebe token AliExpress. Os botões impedem disparo repetido durante a operação. Nenhuma rota de pedido/pagamento é acionada.

## Retorno seguro

O parser aceita os envelopes completo e simplificado publicados, lista direta `delivery_options` e booleanos estritos ou strings exatas `"true"`/`"false"`. Exige `result.code=200` junto do sucesso; desconhecido/inconsistente não vira sucesso.

| Situação | Status / código seguro |
| --- | --- |
| Token local ausente, ilegível ou expirado | `LOCAL_AUTHORIZATION_UNAVAILABLE / LOCAL_TOKEN_UNAVAILABLE`, sem renovação |
| Falha na leitura prévia de produtos | Status dessa etapa / `PRODUCT_LOOKUP_FAILED`; frete não foi chamado |
| Permissão explicitamente negada | `PERMISSION_DENIED / PROVIDER_PERMISSION_DENIED` |
| Token rejeitado pelo fornecedor | `TOKEN_REJECTED / PROVIDER_TOKEN_REJECTED` |
| Parâmetros rejeitados | `PARAMETER_ERROR / PROVIDER_PARAMETER_REJECTED` |
| `DELIVERY_INFO_EMPTY` publicado | `PARAMETER_ERROR / PROVIDER_PRODUCT_ID_REJECTED`; documentação aponta produto inválido |
| `DELIVERY_NOT_AVAILABLE_TO_YOUR_ADDRESS` publicado | `PRODUCT_UNAVAILABLE / PROVIDER_DELIVERY_UNAVAILABLE` |
| Sucesso sem modalidades | `PRODUCT_UNAVAILABLE / NO_DELIVERY_METHODS`, nunca frete grátis implícito |
| Assinatura, método ou identificação rejeitados | `PROTOCOL_ERROR / TOP_...` específico |
| Limite/comunicação/resposta inválida | Status próprio e código fixo, sem mensagens brutas |
| Unidade monetária ambígua | `INVALID_RESPONSE / AMBIGUOUS_MONETARY_UNIT` |
| Sucesso com modalidades validadas | `SUCCESS / CALL_SUCCEEDED` |

TOP só é interpretado dentro de `error_response`. Erros numéricos de negócio não viram automaticamente token/permissão/assinatura. Os dois erros simbólicos publicados são reconhecidos por igualdade exata, sem inferência por texto livre. Erro desconhecido permanece `PROVIDER_REJECTED / UNCLASSIFIED_PROVIDER_REJECTION`; HTTP 403 isolado não prova falta de grant.

O painel exibe código da modalidade (`serviceName` no DTO local), empresa sanitizada, origem retornada reconhecida como país ISO, valor/moeda e estimativa, quando válidos. Descrições livres e request IDs não são expostos.

## Inconsistência monetária oficial

A descrição declara `shipping_fee_cent` em centavos, mas o exemplo oficial apresenta `shipping_fee_cent="1.99"` junto de `shipping_fee_format="US $1.99"`. Não é seguro presumir que esse decimal deva ser dividido por 100 ou que esteja em unidade principal.

O diagnóstico aceita apenas centavos inteiros não negativos, normalizados exatamente com BigDecimal e duas casas. Decimais nesse campo ou contradição com uma representação formatada simples reconhecível geram `AMBIGUOUS_MONETARY_UNIT`. O valor formatado nunca determina o montante normalizado. A flag `free_shipping` deve corresponder ao valor, ou o retorno é rejeitado. Não há heurística para resolver o conflito oficial.

Os valores normalizados são serializados como strings decimais e exibidos sem conversão para Number JavaScript, preservando centavos acima do limite inteiro seguro. A moeda retornada é preservada sem câmbio. O frontend aceita números do backend anterior para compatibilidade durante atualização manual.

## Validação e pendências

Testes cobrem transporte/assinatura, ausência de refresh, ADMIN, SKU sem origem, envelopes/booleanos, valores exatos, ambiguidade monetária, erros publicados, sanitização, estoque e manutenção dos bloqueios.

- Backend: suíte completa, 547 testes em 62 classes, zero falhas/erros/ignorados; Maven exit 0 (`target/ds-freight-regression.log`).
- Frontend: 42 testes aprovados; TypeScript (`tsc --noEmit`) e ESLint dos arquivos alterados aprovados.
- `git diff --check`: aprovado.
- Mocks/H2 não comprovam cotação real.

Após publicação manual, falta executar uma consulta com OAuth persistido no Railway, confirmar acesso para esta operação e verificar a unidade monetária efetiva. Mapeamento territorial BR, cobertura por endereço e condições comerciais não documentadas continuam pendentes. Nenhuma flag de checkout/pedido/pagamento deve ser liberada por sucesso deste diagnóstico por país.
