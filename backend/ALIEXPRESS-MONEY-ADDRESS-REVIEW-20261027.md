# inGarage — moeda DS e frete por endereço (27/10/2026)

## Resultado

O `MAJOR_MATCH` real é agora conhecido: `24.00` coincide numericamente com o formato normalizado `24.00`, moeda BRL, `free_shipping=false`, opção 0. É evidência observacional favorável à unidade principal, mas a escala contratual continua sem confirmação. Manter `AMBIGUOUS_MONETARY_UNIT`; não promover essa igualdade a regra de cobrança.

A releitura pública oficial **foi concluída com HTTP 200** nesta investigação, após liberar o acesso de rede do processo. Isso substitui a limitação de conectividade registrada na investigação anterior. Foram consultados somente documentos públicos e baixado o SDK para análise estática. Não houve chamada autenticada de frete/endereço/pedido, criação de pedido, pagamento, alteração de credenciais ou deploy.

Novos caminhos identificados: árvore territorial em `ds.address.get` e valores efetivos de pedido em `trade.ds.order.get`. Nenhum dos contratos de frete examinados comprova cotação definitiva por CEP/endereço antes da criação do pedido. Não se afirma que inexista outro método privado ou concedido apenas ao app: isso precisa ser respondido pelo suporte.

## Evidência monetária e versão exata

O código em `AliExpressDsFreightClient` usa `aliexpress.ds.freight.query`, POST `https://api-sg.aliexpress.com/sync`, TOP, `v=2.0`, `simplify=true`, `session`, HMAC-SHA256. O parâmetro 2.0 é versão de protocolo TOP; não identifica uma revisão independente da regra monetária. Não há revisão de negócio fixada no request. A equivalência exata entre o backend servido à aplicação e o documento público deve ser confirmada pelo fornecedor.

[Contrato oficial DS, docId 1579](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST), relido em 27/10, informa `lastModified=2024-03-04 20:23:41` (fuso não informado pelo documento). Continua declarando String e centavos, enquanto os exemplos simplificado e completo mostram `shipping_fee_cent="1.99"`, formato `US $1.99`, USD. A documentação atual não esclarece se o nome/descrição está errado, se o exemplo está errado ou se existem variantes de comportamento. Não aplicar automaticamente regras de `logistics.buyer.freight.*`.

O [SDK oficial vinculado pelo documento nesta releitura](https://ae-open-platform-public.oss-ap-southeast-1.aliyuncs.com/online/oss_1793077216670_0_DN7u.zip) contém `gop-sdk-Java1793077207627-source.jar`. Foram inspecionados request, DTO, response e executor, sem executar o SDK:

- `AliexpressDsFreightQueryRequest` mantém o nome exato e serializa `queryDeliveryReq`.
- `AliexpressDsFreightQueryDeliveryOptionDTO.shippingFeeCent` é String, mapeada por `@ApiField("shipping_fee_cent")`; comentário repete centavos; getter/setter apenas armazenam/devolvem a String. Nenhuma conversão comprova escala.
- `shippingFeeFormat` também é String; importar `BigDecimal` no arquivo gerado não transforma esses campos em BigDecimal nem estabelece unidade.
- `TopExecutor` define versão 2.0, `session` e `/sync`. O DTO de destino confirma a ausência de campo de CEP.

Hashes, URLs, datas e campos extraídos estão em [ALIEXPRESS-MONEY-ADDRESS-EVIDENCE-20261027.json](ALIEXPRESS-MONEY-ADDRESS-EVIDENCE-20261027.json). Snapshots completos e fontes do SDK ficam em `target/`, não são payloads de produção. O arquivo de evidência diferencia contratos realmente encontrados de respostas HTTP/documentais vazias.

| Comparação feita com BigDecimal | Resultado |
|---|---|
| `new BigDecimal("24.00").compareTo(new BigDecimal("24.00"))` | 0: igualdade observada, MAJOR_MATCH |
| `new BigDecimal("24.00").movePointLeft(2)` | 0.2400, numericamente R$ 0,24 se o campo for centavos |
| Valor original / 100 comparado ao formato 24.00 | Diferente |
| `free_shipping=false` | Compatível com ambas as hipóteses positivas; não identifica escala |

O bloqueio exato continua sendo a expressão `cent.matches("[0-9]{1,16}\\.[0-9]+")`, antes da comparação do formato no parser comercial. O diagnóstico separado consegue comparar a resposta rejeitada. O formato bruto de produção não foi recebido nesta conversa, apenas sua representação normalizada.

Não corrigir com `stripTrailingZeros`, teste de magnitude, presença de ponto ou MAJOR_MATCH. Isso também deixaria indefinida a interpretação de `24`, `2400` e zero. A aceitação legada de inteiros como centavos permanece sem ampliação; a resposta do suporte deve abranger igualmente os inteiros. Não há regra nova de escala comprovada para implementar hoje.

## Endereço: o que cada contrato permite

| Método oficial relido | Evidência e limite |
|---|---|
| [ds.freight.query, 1579](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST) | Produto, SKU String, quantidade, país, moeda; `province` OU `provinceCode`, `cityCode`. Província é descrita para local-to-local. Não contém CEP/rua/endereço integral ou reserva de preço. Não comprova que enviar cidade transforme o resultado em frete por CEP. |
| [ds.address.get, 1584](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.address.get&methodType=GET%2FPOST) | Request: `countryCode`, `language`, `isMultiLanguage`. Retorna país/tipo e `children` como String de JSON. Candidato oficial para obter nomenclatura/árvore de endereços AE. O exemplo não fornece árvore BR nem define ligação dos IDs com provinceCode/cityCode/location_tree_address_id. Não inventar essa ligação, não usar UF/IBGE automaticamente. |
| [logistics.buyer.freight.get, 1442](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.get&methodType=GET%2FPOST) e [calculate, 525](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.calculate&methodType=GET%2FPOST) | Possuem SKU e destino parcial, sem campo de CEP no esquema consultado. Não resolvem a lacuna por simples troca de método e têm contratos monetários próprios. Sem fallback automático. |
| [trade.buy.placeorder, 479](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.buy.placeorder&methodType=GET%2FPOST) | Método de criação usado atualmente pelo código. Endereço contém `zip`, `city`, `province`, `address`, `address2`, `country`, dados de destinatário e `location_tree_address_id`. Produto/quantidade/`sku_attr` e modalidade por item. Criação é mutação, não consulta de frete. Response traz IDs/sucesso, não uma reserva de cotação completa. |
| [ds.order.create, 1615](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.order.create&methodType=GET%2FPOST) | Também aceita endereço completo, mas inclui `ds_extend_request.payment.try_to_pay` String. Não migrar silenciosamente. Um eventual ensaio futuro de pedido não pago deve fixar explicitamente `"false"` e ter comportamento confirmado pelo fornecedor; o default não foi comprovado. |
| [trade.ds.order.get, 524](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.ds.order.get&methodType=GET%2FPOST) | Consulta com `single_order_query.order_id`; retorna estado, timeout de pagamento, totais/moedas e por linha SKU, quantidade, frete, desconto, frete efetivo e tributo. É candidato para reconciliação de pedido existente antes de pagar ao fornecedor. O contrato não prova sozinho disponibilidade/completude desses valores no estado não pago ou vínculo integral do endereço. |
| [ds.trade.order.get, 1199](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.trade.order.get&methodType=GET%2FPOST) | Consulta pedido/total, mas não oferece no esquema lido o mesmo detalhamento de frete efetivo de 524. Não substituir um pelo outro pelo nome parecido. |

`aliexpress.ds.order.get` e `aliexpress.freight.query` foram consultados na documentação pública e não retornaram contrato correspondente (`apiList` vazio). HTTP 200/`success=true` do portal não significa método disponível. O segundo nome aparece na orientação de erro `DELIVERY_METHOD_NOT_EXIST` de `ds.order.create`; essa inconsistência também deve ser esclarecida.

## Modalidade e melhor caminho

O campo `delivery_options[].code` de DS é documentado para criar pedido. Os dois contratos de criação possuem `product_items[].logistics_service_name`: o mapeamento documental é o **code bruto da opção**, por linha/SKU/quantidade/origem, nunca `company`. Isso confirma a intenção de uso, mas não garante aceitação para qualquer endereço/app/data; `DELIVERY_METHOD_NOT_EXIST` continua possível. Não houve pedido de teste.

Melhor sequência de investigação: obter confirmação oficial da escala e da cobertura BR; depois consultar somente leitura de `ds.address.get` para BR com autorização persistida, validar árvore/esquema e provar com o fornecedor o mapeamento para frete/pedido. Pedir método oficial de preflight/cotação por endereço e garantia de cobertura de CEP (ou declaração de que ele não altera elegibilidade/preço, se essa for a regra oficial). Sem isso, estado/cidade continuam sendo granularidade parcial.

Se o fornecedor confirmar que só a criação calcula o valor final por endereço, o caminho alternativo é **projeto separado** de pedido fornecedor não pago, leitura/reconciliação via 524 e pagamento condicionado à confirmação. Isso exige autorização futura para criar pedidos, idempotência/reconciliação, prevenção de duplicidade, expiração/cancelamento e validação de tributos/descontos/moedas. Não presumir que pedido não pago reserve preço. O fluxo atual exige pagamento do cliente antes de envio administrativo ao AliExpress; portanto essa alternativa não resolve o checkout atual sem redesenhar a ordem das etapas. Não cobrar o cliente com a estimativa por país para financiar a descoberta posterior do preço.

Até confirmação suficiente, `COUNTRY_ONLY -> DESTINATION_NOT_CONFIRMED` e o bloqueio de `PaymentAttemptService` permanecem independentes da validação monetária.

## Próximas alterações mínimas, condicionadas às respostas

1. **Agora:** registrar a observação real e evidências atuais; manter parser, cobertura e pagamentos bloqueados; encaminhar a pergunta técnica preparada no arquivo de suporte por canal escolhido pelo responsável. Nenhuma mensagem foi enviada.
2. **Após escala confirmada:** modificar somente a normalização de `shipping_fee_cent` conforme a regra oficial para Strings inteiras E decimais. Usar BigDecimal, rejeitar precisão não representável sem arredondar, moeda inválida/conflitante, inconsistência com formato validado e gratuidade. Formato permanece verificador, nunca fonte substituta do preço. Se unidades principais forem confirmadas, divisão por 100 deixa de ocorrer nesse contrato; não selecionar unidade pelo formato lexical.
3. **Após cobertura comprovada:** incluir `addressId` no contrato interno de cotação; resolver proprietário e endereço/códigos no servidor, persistir contexto/versionamento da cotação e seleção por remessa. Só acrescentar cobertura confirmada quando a evidência do fornecedor justificar. Expiração local não é garantia contratual.
4. **Antes de habilitar pedidos:** persistir e usar código de modalidade por linha; hoje `AliExpressOrderPreparation` usa o nome da primeira shipping leg. Corrigir também o nascimento BR: o código atual envia `logistics_address.birth_date`, enquanto `trade.buy.placeorder` documenta `ds_extend_request.trade_extra_param.birthday` dentro do objeto principal; DS create tem extensão separada. Não alterar campos nem método automaticamente nesta investigação.

## Testes e critérios antes de pagamentos

| Área | Provas necessárias |
|---|---|
| Moeda | 24.00/24.00 com contrato confirmado; 24/2400 conforme regra oficial; comparação por BigDecimal independente de escala textual; MAJOR/MINOR/NEITHER/BOTH; formato divergente, prefixo incompatível, formato inválido/ausente conforme obrigatoriedade; negativo, expoente, excesso de precisão/limites; zero e free_shipping incoerentes; moeda inesperada sem câmbio implícito. |
| Destino | Matriz BR de CEPs, cidades/UFs e origens local-to-local/cross-border com produto/SKU/quantidade iguais; mapeamento territorial oficial; CEP inexistente ou não atendido; troca de CEP na mesma cidade; mudança de endereço mesmo com mesmo CEP. Igualdade de preços entre CEPs não prova cobertura. |
| Cotação | Propriedade do endereço/quoteId; mudança de SKU/quantidade/remessa/origem/endereço invalida; expiração e reconsulta; modalidade removida; moeda/valor alterados; carrinho misto e múltiplos fornecedores; não somar fretes como remessa consolidada sem contrato. |
| Pedido | Código bruto por item, sku_attr correto, payload BR, idempotência, resposta parcial/timeout sem reenvio cego. Se houver fluxo não pago autorizado futuramente: estado realmente não pago, ausência de débito e valores finais disponíveis por leitura. |
| Pagamentos | Cotação só por país e ambiguidade sempre bloqueiam; tentativas antigas e concorrentes também; preço/tributo/desconto/total reconciliados na moeda correta; consumo transacional da cotação; valor alterado nunca debitado silenciosamente; nenhum pagamento ao fornecedor como efeito de diagnóstico. |
| Privacidade | ADMIN/no-store, sem resposta integral, tokens ou dados pessoais; códigos e valores sanitizados limitados; suporte recebe somente o necessário por canal autorizado. |

As regressões existentes de diagnóstico já reproduzem `24.00`, MAJOR_MATCH, MINOR_MATCH e divergência preservando o bloqueio. Os testes com mocks comprovam comportamento da aplicação; não substituem escala/cobertura confirmadas pelo fornecedor.

Validação desta investigação: 87 testes aprovados, zero falhas/erros/ignorados em `AliExpressFreightDiagnosticClientTests` (54), `AliExpressDsFreightGatewayTests` (14), `AliExpressFreightQuoteTests` (9), `AliExpressFreightSafetyTests` (3) e `AliExpressShippingHttpIntegrationTests` (7). Maven offline, respostas simuladas/H2, exit 0; log local em `target/money-address-review-tests.log`. Nenhuma alteração funcional foi feita no parser, cobertura, pedidos ou pagamentos. As alterações versionáveis são a documentação e o recorte de evidências.
