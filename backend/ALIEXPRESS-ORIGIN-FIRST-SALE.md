# Origem logística e menor caminho até a primeira venda — 27/10/2026

## Conclusão para o ensaio informado

Produto `1005010661577657`, SKU `12000053109276547`, quantidade 1: o responsável confirmou envio e resposta HTTP das consultas BR e BR + province Santa Catarina; ambas mostraram cent=24.00, BRL, MAJOR_MATCH e NO_OBSERVED_DIFFERENCE. **A origem desse SKU continua não confirmada.** O diagnóstico anterior não expunha ship_from_country quando a validação monetária rejeitava o resultado; isso não demonstra que o fornecedor omitiu o campo.

NO_OBSERVED_DIFFERENCE dizia respeito somente à projeção sanitizada então disponível. Não prova origem CN, produto internacional, aplicação/omissão de province ou igualdade de todos os metadados das duas respostas. Também não resolve a escala monetária. Não houve consulta autenticada adicional nesta investigação.

## País da loja, origem da rota e armazém

Os contratos oficiais foram relidos publicamente nesta investigação; os recortes e hashes estão em [ALIEXPRESS-ORIGIN-CHECKOUT-EVIDENCE.json](ALIEXPRESS-ORIGIN-CHECKOUT-EVIDENCE.json).

| Informação | O que comprova e o que não comprova |
|---|---|
| ds.product.get / ae_store_info.store_country_code | País da loja. A descrição oficial também sugere seu uso como ship-from do SKU, mas não identifica armazém nem substitui a evidência específica da opção de entrega. Em caso de divergência com ship_from_country, preservar ambas as observações e investigar; não escolher silenciosamente uma origem. |
| logistics_info_dto.ship_to_country | País de destino da consulta. BR aqui não comprova estoque brasileiro. |
| Propriedades do SKU, como um rótulo Ships From | Podem indicar a opção de origem selecionada. Preservar sku_id/sku_attr e a identidade da propriedade; rótulos não autorizam inventar país, ID de armazém ou mudar de variante. |
| ds.freight.query / delivery_options[].ship_from_country | Campo String descrito como o país de onde o SKU será enviado. É a observação pré-pedido mais direta da origem da **rota/opção retornada para aquele SKU, destino e quantidade**, não uma confirmação de expedição física já ocorrida. |
| Armazém específico | O contrato de frete inspecionado não expõe warehouseId, localização do armazém ou reserva de estoque. País de envio não identifica armazém. |
| País de fabricação/manufacturer | Também não determina de onde a remessa será expedida. |

Fontes: [ds.product.get](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.product.get&methodType=GET%2FPOST), [ds.freight.query](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST). IDs/datas brutos do documento estão no arquivo de evidência; o portal retornou docId 13122 para produto nesta releitura, diferente de snapshots anteriores. Não inferir equivalência de revisão apenas por TOP v=2.0.

## Local-to-local e province

O contrato de ds.freight.query descreve province/provinceCode para cálculo de produtos local-to-local, com nome OU código. Não afirma explicitamente que todo produto internacional ignore o parâmetro nem garante variação de preço dentro do mesmo país. cityCode é opcional; não há CEP ou nome city no request documentado.

Para destino BR:

- ship_from_country=BR é evidência de rota no mesmo país e candidato a local-to-local. Não comprova por si só participação em um programa comercial local-to-local; falta definição/indicador oficial para esse enquadramento.
- Um outro país ISO, por exemplo CN se efetivamente retornado, caracteriza a rota observada como transfronteiriça. Nesse caso, preço igual com province é compatível com o escopo documentado, mas continua sem prova de que a API ignorou o parâmetro.
- Campo ausente/inválido: origem desconhecida, sem default CN ou BR.
- Se houver várias opções/origens, avaliar cada código e índice. Não classificar todo o produto usando só a primeira opção.

Mesmo para uma rota BR -> BR, taxas iguais podem ocorrer; o ensaio de um único SKU e uma única província não comprova nem refuta a implementação regional. Não usar a ausência de diferença como teste de classificação.

## Diagnóstico ampliado sem liberar dinheiro

O endpoint existente `/api/admin/integrations/aliexpress/freight-diagnostic/province-comparison` agora expõe, fora de result.methods, `countryOnly.deliveryOptions` e `withProvince.deliveryOptions`, inclusive quando result.safeCode continua AMBIGUOUS_MONETARY_UNIT. Cada linha tem somente:

```json
{
  "optionIndex": 0,
  "serviceCode": "CODIGO_OBSERVADO_OU_NULL",
  "shipFromCountry": null,
  "routeEvidence": "ORIGIN_NOT_CONFIRMED"
}
```

Código e país são extraídos diretamente dos campos documentados, validados e sanitizados; não há company, corpo bruto, endereço, credenciais ou inferência de armazém. Código numérico não é convertido em número; é identificador opaco. Um país válido gera CROSS_BORDER_ROUTE_OBSERVED ou SAME_COUNTRY_ROUTE_CANDIDATE em relação ao destino fixo BR. Esses rótulos não aprovam frete ou enquadramento comercial.

O endpoint de preparação de produto `/freight-diagnostic/product` acrescenta storeCountryCode validado, separado da origem de cada opção. O comparador passa a considerar também as opções sanitizadas de origem/código. Portanto comparações novas podem detectar diferenças que o comparador antigo não observava. Nenhuma regra de moeda, valor aprovado, checkout, pagamento ou payload de pedido foi flexibilizada.

Depois de publicação manual autorizada, executar a preparação e a comparação com os IDs acima, usando exclusivamente a sessão ADMIN da inGarage e o OAuth persistido resolvido no backend. Conferir storeCountryCode separadamente de cada deliveryOptions[].shipFromCountry; comparar os serviceCode correspondentes, sem assumir estabilidade do índice se a ordenação mudar. Se o país vier null, manter o bloqueio e levar a ausência ao suporte. Nenhuma publicação ou chamada de produção foi feita aqui.

## Cotação internacional por endereço antes da cobrança

Não foi encontrado, **nos contratos públicos verificados**, endpoint de consulta DS que aceite endereço completo/CEP e devolva confirmação comercial/reserva de frete internacional para BR antes da criação do pedido. Isso não prova inexistência de método privado ou concedido apenas ao app; pedir confirmação ao suporte.

| Contrato verificado | Limite para checkout |
|---|---|
| ds.freight.query | Produto/SKU/quantidade e país/província/cidade parcial; sem CEP, endereço completo ou identificador de reserva de preço documentado. |
| [logistics.buyer.freight.get](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.get&methodType=GET%2FPOST) e [calculate](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.logistics.buyer.freight.calculate&methodType=GET%2FPOST) | Também sem CEP no request lido; têm contratos monetários próprios. Não são fallback comprovado de cotação por endereço. |
| [trade.buy.placeorder](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.buy.placeorder&methodType=GET%2FPOST) / [ds.order.create](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.order.create&methodType=GET%2FPOST) | Aceitam endereço completo, mas criam pedidos reais; não são consultas/preflight. DS create inclui controle try_to_pay; não presumir default ou ausência de débito sem confirmação. |
| [trade.ds.order.get](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.trade.ds.order.get&methodType=GET%2FPOST) | Lê pedido existente, frete efetivo/desconto/tributo/total. Candidato à reconciliação futura antes de pagar ao fornecedor; não resolve sozinho confirmação antes de cobrar o cliente e não comprova disponibilidade desses valores em estado não pago. |

## Código da modalidade e pedido

O contrato DS descreve delivery_options[].code para uso na criação do pedido. Ambos os contratos de criação possuem product_items[].logistics_service_name. Esse é o vínculo documental: **código bruto**, preservado por item/SKU/quantidade/destino/origem, nunca company ou nome de exibição.

Sem criar pedido ou obter um preflight oficial, podemos validar formato e vínculo interno do código com a observação, mas não comprovar que o fornecedor o aceitará para o endereço final. DELIVERY_METHOD_NOT_EXIST continua possível. Não chamar criação para “validar” esse código nesta tarefa.

Há um bloqueio concreto no código atual: `AliExpressOrderPreparation.payload` usa o name da primeira shipping leg como logistics_service_name. Isso deve ser corrigido para o código confirmado e por linha antes de habilitar API de compra. Também continua pendente a divergência de aniversário BR: código atual envia birth_date no endereço, enquanto o contrato de trade.buy.placeorder documenta birthday em ds_extend_request.trade_extra_param. Nenhuma dessas rotas foi ativada/alterada nesta investigação.

## Recomendação: primeira venda assistida, depois automação

O menor caminho seguro proposto é **cotação manual por endereço, vinculada ao checkout da inGarage**, com um produto/SKU, quantidade pequena, uma remessa e valores BRL confirmados. Não esperar toda a automação DS para começar a validar a operação, e não liberar frete por país como preço definitivo.

Fluxo proposto, ainda não implementado nem executado:

1. Cliente solicita cotação e informa endereço; nenhuma cobrança é iniciada. Operador confere produto/SKU/quantidade/disponibilidade e o endereço completo na interface oficial AliExpress até a revisão do checkout, sem criar/pagar pedido para descobrir preço. Registrar somente os valores e a modalidade efetivamente apresentados; confirmar se há pendência de validação de endereço/custos. Se a interface não fornecer confirmação suficiente antes da criação, não aprovar a cotação.
2. Operador aprova no backend uma cotação comercial manual: proprietário, fingerprint do endereço, itens/SKU/quantidade, origem conhecida quando disponível, modalidade manual, fonte da verificação, moeda/valores de fornecedor e preço final ao cliente, responsável/data, expiração local e versão. O valor observado na interface é fonte independente; não reinterpretar 24.00 da API nem afirmar que a escala foi resolvida. Sem câmbio implícito ou dupla contagem de frete/tributos/descontos.
3. Checkout aceita somente quoteId desse registro, após revalidar contexto/disponibilidade/validade e confirmar o total com o cliente. Pedido e tentativa de pagamento verificam e consomem essa cotação de modo transacional/idempotente. Criar um caminho explícito para cotação manual validada por endereço; não trocar o bloqueio atual por um booleano genérico nem alterar a classificação COUNTRY_ONLY.
4. Após pagamento do cliente confirmado, operador realiza a compra no fornecedor manualmente, com nova conferência. Eventual diferença de custo deve seguir política comercial explícita antes da venda: absorção pela loja ou nova proposta/solução consentida, nunca cobrança extra silenciosa. A cotação manual não reserva estoque/preço do fornecedor. Nenhuma compra é autorizada por este relatório.

Essa proposta evita depender de API de criação/pagamento para a primeira operação, mas exige implementar cotação manual persistida e sua validação em pedido/pagamento. O checkout atual permanece bloqueado até isso existir. Se for exigido checkout totalmente automático desde a primeira venda, o caminho crítico continua sendo obter do suporte escala monetária e confirmação de preço/elegibilidade por endereço, além de corrigir modalidade/payload/idempotência; os testes BR versus província não substituem esses requisitos.

Para a futura aprovação manual, testar alteração de endereço/SKU/quantidade, cotação de outro usuário, expiração/revogação, moeda/valor divergente, concorrência/reuso de quoteId, tentativas antigas e webhook repetido. A importação pode continuar independente desse fluxo.

Solicitação pronta, não enviada: [ALIEXPRESS-SUPPORT-MONEY-ADDRESS-20261027.md](ALIEXPRESS-SUPPORT-MONEY-ADDRESS-20261027.md).

## Verificação local

Regressão AliExpress/frete/pagamento: 382 casos contabilizados, **381 aprovados, 1 probe real ignorado**, zero falhas/erros, package/repackage bem-sucedido. Após alinhar a sanitização de códigos opacos com o parser existente (inclusive prefixo numérico), revalidação final de **41 casos aprovados** e novo JAR gerado com BUILD SUCCESS. Os testes cobrem origem BR/CN/ausente/inválida/sensível, retenção de código, comparação de origem diferente com dinheiro igual, exposição ADMIN, separação do país da loja e manutenção das rejeições monetárias/comerciais. São fixtures, não prova de origem deste SKU em produção.

Logs locais: `target/origin-review-tests.log` e `target/origin-review-final.log`. Nenhum refresh, novo uso do token rejeitado, pedido real, pagamento, commit ou deploy. Arquivos locais não relacionados que já estavam sem rastreamento no workspace não foram alterados.
