# Validação local de frete AliExpress — 21/10/2026

## Resultado do Maven

Foi adicionado e executado `AliExpressShippingHttpIntegrationTests`:

- **7 testes, 0 falhas, 0 erros, 0 ignorados; Maven exit 0.**
- Relatório: `target/surefire-reports/com.garage.garageapi.integration.aliexpress.AliExpressShippingHttpIntegrationTests.txt`.
- Log: `target/local-freight-http-tests.log`.
- Maven 3.9.16 executado em modo offline, com dependências do cache local. Nenhuma credencial de produção foi utilizada.
- A primeira tentativa detectou conflitos de imports entre matchers HTTP/MockMvc e tipos do JUnit/Spring; os imports foram corrigidos e a execução final passou.

Só foram adicionados este relatório e o arquivo de teste. Código de produção, OAuth persistido, contratos públicos, bloqueios comerciais e configurações não foram alterados. Não houve commit, push ou deploy.

## O que passou com HTTP simulado

O teste usa `@SpringBootTest` e MockMvc no **POST `/api/shipping/quote`**. Controller, ShippingService, CompositeShippingProvider, AliExpressFreightService, gateway concreto e parser são reais. A resposta HTTP externa é interceptada por `MockRestServiceServer`, equivalente para este cliente RestClient a um servidor HTTP simulado, sem socket/TLS ou conexão ao AliExpress.

O token é sintético e obtido de uma leitura OAuth simulada. Os clientes de escrita de pedido AliExpress e cobrança também são mocks exclusivamente para assegurar que não são chamados. Não são substituídos o gateway, o serviço de frete ou o parser por retornos simulados. Um spy observa a execução real do gateway para inspecionar a avaliação normalizada antes do bloqueio comercial.

Fluxo exercitado:

```text
MockMvc POST /api/shipping/quote
  → ShippingService
  → CompositeShippingProvider
  → AliExpressFreightService
  → AliExpressDsFreightGateway concreto
  → AliExpressDsFreightClient real
  → HTTP interceptado / resposta simulada
  → parser real → avaliação → bloqueio comercial
```

| Caso | Resultado comprovado |
| --- | --- |
| Sucesso com `shipping_fee_cent="1230"`, BRL e modalidade CAINIAO_STANDARD | BigDecimal 12,30 BRL, origem CN e prazo 5–12 dias interpretados; HTTP **409 / ALIEXPRESS_DESTINATION_NOT_CONFIRMED** |
| Token rejeitado, código TOP 27 | **409 / ALIEXPRESS_AUTHENTICATION_FAILED**, sem refresh |
| Falta de permissão, código TOP 11 | **409 / ALIEXPRESS_PERMISSION_DENIED** |
| SKU/produto rejeitado pelo fornecedor com DELIVERY_INFO_EMPTY | **409 / ALIEXPRESS_INVALID_PARAMETERS**, sem opções alternativas |
| Variante local pertencente a outro produto | **409**, antes de OAuth e HTTP |
| Origem retornada desconhecida (`ZZ`) | Origem normalizada como ausente, sem inferir CN; compra continua bloqueada com **ALIEXPRESS_DESTINATION_NOT_CONFIRMED** |
| Valor ambíguo `shipping_fee_cent="1.99"` | **409 / ALIEXPRESS_AMBIGUOUS_MONEY**, sem presumir unidade ou oferecer preço |

O produto usa IDs externos realistas das fixtures existentes (`1005010661577657` / `12000053109276547`), mas sua existência/disponibilidade atual no fornecedor não foi comprovada por esses testes. A quantidade é 2 e o gateway precisa usar o `variantId` local persistido e resolver o SKU no banco. IDs externos/preço forjados no corpo do cliente são ignorados.

Foi usado um endereço **sintético** em Joinville/SC, BR, com CEP 89229030, rua/número/bairro de teste. O contrato público atual transporta somente CEP e itens; o teste não inventa suporte a `addressId` ou endereço completo. Confere-se que a requisição AliExpress contém apenas os sete campos suportados do diagnóstico por país e não envia CEP, rua, código de cidade/província presumido ou os IDs forjados.

O contexto Spring confirma que o bean `AliExpressFreightGateway` é o gateway concreto e que ADM e checkout compartilham o cliente de teste com parser real. A expectativa HTTP é satisfeita exatamente uma vez nos seis casos que chegam ao fornecedor. Isso demonstra que a antiga lambda bloqueadora não atende a requisição.

## Banco e efeitos de escrita

O teste força explicitamente `jdbc:h2:mem:garage_freight_http_local`, driver H2, usuário local e senha vazia, SQL init desativado e schema de teste. Antes de criar fixtures, uma asserção verifica a URL efetiva da conexão como H2 local. As fixtures de produto/variante são transacionais e revertidas.

A execução Maven também especificou datasource H2 em memória nos argumentos do processo, sem carregar scripts `.env.local.ps1` ou configurações com credenciais. Não houve conexão ao banco de produção.

Em **todos os sete casos**, antes/depois são verificadas contagens zero de pedidos e pagamentos, ausência de chamadas ao cliente de criação de pedidos AliExpress e ao gateway de cobrança e ausência de `tokens.accessToken()` (que poderia renovar credenciais). O teste chama somente a rota de cotação.

## O que foi comprovado com chamada real ao AliExpress

**Nenhuma chamada autenticada real foi executada nesta tarefa.** Os resultados acima comprovam o caminho Spring/HTTP/parsing e as proteções com respostas simuladas; não comprovam grants, validade de token, assinatura aceita remotamente, estoque real, tarifa, TLS/conectividade ou cobertura de endereço.

Na avaliação da possibilidade de teste real, o usuário informou que **não consegue confirmar a origem da autorização disponível**. A inspeção apenas dos nomes das variáveis do processo não identificou configuração ALIEXPRESS/datasource nesse terminal; isso não prova que inexista autorização em algum arquivo ou banco. Não foram lidos valores privados de arquivos de ambiente, descriptografados tokens de procedência incerta ou consultado banco de produção para obtê-los.

Por esse motivo, uma autorização desconhecida não foi tratada como autorização de teste legitimamente disponível. Não foram copiadas credenciais de produção nem solicitados tokens pelo chat.

## O que falta para um teste real e para o endereço BR

Um teste real somente de leitura é tecnicamente possível pelo mesmo backend, após disponibilizar uma conta/app OAuth de teste com origem confirmada, concedida legitimamente e isolada de produção. Deve utilizar armazenamento/configuração local próprios e banco local, sem importar credenciais ou base de produção. Não é necessário criar pedido ou executar pagamento para consultar frete.

Com esse ambiente comprovado, falta conferir uma resposta real de `ds.freight.query` para SKU/quantidade/BR, a aceitação da assinatura e a permissão efetiva da aplicação. O bloqueio por país deve permanecer, inclusive em caso de sucesso.

Também continuam pendentes a unidade monetária efetiva (o exemplo oficial é ambíguo), o mapeamento brasileiro de província/cidade, a cobertura do endereço selecionado e a validação comercial por pedido. O teste local não comprova frete por CEP/rua/número, nem autoriza persistência definitiva da cotação, compras ou pagamentos.
