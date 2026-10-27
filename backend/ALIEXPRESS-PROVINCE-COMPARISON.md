# DS freight — comparar BR com BR + Santa Catarina

## Contrato e evidência disponível — 27/10/2026

Relido com HTTP 200 o [contrato oficial aliexpress.ds.freight.query, docId 1579](https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST), lastModified 2024-03-04 20:23:41. O campo **province é String opcional e representa o nome da província**, descrito para consulta de produtos local-to-local. Deve ser enviado province OU provinceCode, não ambos. cityCode é opcional; não há parâmetro city para nome literal nem parâmetro de CEP no esquema consultado.

Isso documenta o uso de um nome, permitindo preparar o ensaio com o literal `Santa Catarina`. Não comprova antecipadamente que o backend do fornecedor aceitará esse nome para qualquer SKU/origem ou que modificará o cálculo. Não assumir efeito para produtos cross-border.

O responsável informou execução no Railway de `ds.address.get` com SUCCESS/CALL_ACCEPTED e árvore BR -> Santa Catarina -> Joinville, contendo somente children/country/name/type. Essa observação é evidência de nomes reconhecidos pelo método territorial, não de códigos numéricos nem de grant/efeito de frete. Não foi reproduzida nesta tarefa. Fonte, hash do contrato e proveniência da observação estão em [ALIEXPRESS-PROVINCE-COMPARISON-EVIDENCE.json](ALIEXPRESS-PROVINCE-COMPARISON-EVIDENCE.json).

Não é necessário inventar provinceCode: o contrato oferece province como alternativa. Nenhum código será enviado e Joinville não será enviada como cityCode. Se o serviço rejeitar o nome ou passar a exigir códigos, interromper a progressão: a árvore territorial efetivamente observada não fornece esses códigos e nenhuma fonte oficial de códigos BR compatíveis foi comprovada. Solicitar ao suporte o endpoint/esquema oficial e a correspondência exata para provinceCode/cityCode; não converter UF ou IBGE por analogia.

## Implementação preparada

Novo endpoint ADMIN, POST, no-store:

`/api/admin/integrations/aliexpress/freight-diagnostic/province-comparison`

Body igual ao diagnóstico de frete existente: productId e skuId como Strings, quantity como inteiro. Província é fixa no backend; não há parâmetro de token ou de código territorial. Não há consulta automática de inicialização, frontend novo ou alteração de variáveis de produção.

O serviço lê uma vez `AliExpressTokenService.existingAccessToken()`, sem refresh/gravação, consulta e revalida uma vez produto/SKU/estoque e realiza até duas consultas planejadas, sequenciais, com o mesmo token, produto, SKU e quantidade:

| Campo em queryDeliveryReq | countryOnly | withProvince |
|---|---|---|
| shipToCountry | BR | BR |
| province | ausente | Santa Catarina |
| provinceCode | ausente | ausente |
| cityCode | ausente | ausente |
| language / locale / currency | pt_BR / pt_BR / BRL | pt_BR / pt_BR / BRL |

O SKU continua String. A origem não é inventada. Frete usa o mesmo transporte/parser existente, sem fallback ou retries. Falha no preflight não chama frete. Falha de autorização, permissão, comunicação, protocolo ou outro erro não elegível na primeira consulta impede a segunda. Uma rejeição monetária conhecida permite coletar a segunda observação sem aprovar valores. Nenhum pedido/pagamento é chamado.

Cada observação contém:

- `request.queryDeliveryReqFields`: nomes de campos do mesmo mapa efetivamente serializado e assinado; nenhum ID de produto/SKU ou credencial é copiado para essa evidência.
- `request.shipToCountry` e `request.province`: valores territoriais permitidos daquele mapa.
- `request.requestBodyWritten`: escrita do corpo no stream local do transporte concluída. Sozinho não comprova recepção ou interpretação pelo fornecedor.
- `request.httpResponseReceived`: transporte recebeu resposta HTTP; não significa sucesso de negócio ou que province foi considerado.
- `result`: diagnóstico de frete existente, inclusive métodos aprovados pelo parser quando aplicável, `monetaryStructure`, `safeValue`, `comparison`, moedas, free_shipping e rejeições monetárias. Somente a limitação de escopo da consulta com província passa a `COUNTRY_AND_PROVINCE_DIAGNOSTIC_NOT_ADDRESS_CONFIRMED`; o parser monetário não mudou.

`executionStatus=COMPLETED` significa que as duas observações foram coletadas, não que o frete foi aprovado. Outros valores: NOT_EXECUTED e STOPPED_AFTER_BASELINE. `responseComparison` compara status, safeCode, métodos e estrutura monetária sanitizada, sem comparar o corpo bruto ou limitações que apenas descrevem o contexto:

- OBSERVED_DIFFERENCE: essa projeção observável difere, inclusive diferenças de ordenação/representação nela.
- NO_OBSERVED_DIFFERENCE: nenhuma diferença nessa projeção. Não afirma igualdade da resposta integral nem prova que province foi ignorada.
- INSUFFICIENT_OBSERVABLE_DATA: falta evidência útil de uma das respostas; não apresentar falha de comunicação/recusa como preço inalterado.

Mesmo uma diferença não prova causalidade: consultas são sequenciais e o fornecedor pode alterar outras condições. A API não ecoa confirmação de aplicação do nome no esquema lido. Metadados de opções não presentes na projeção sanitizada, especialmente em rejeições monetárias, podem mudar sem aparecer no comparador. Avaliar os dois resultados e suas limitações.

## Procedimento no Railway após publicação manual

Não houve commit, deploy ou chamada autenticada de frete nesta tarefa. A comparação real BR versus província ainda não foi executada; não se afirma que a resposta mudou.

1. Publicar manualmente a versão revisada, incluindo a classe nova `AliExpressFreightProvinceComparison` e alterações do controller, serviço, fachada e cliente compartilhado. Nenhuma migração ou nova variável é necessária. Manter app key/secret, banco e ALIEXPRESS_TOKEN_ENCRYPTION_KEY existentes. Não adicionar access token manual e não gerar autorização nova.
2. Usar a autenticação ADMIN normal da inGarage no backend Railway. O Authorization da chamada é a sessão/JWT da inGarage, não o OAuth AliExpress. O backend lê o OAuth persistido. Não reutilizar a credencial rejeitada no ensaio local anterior.
3. Escolher produto/SKU real já conhecido. Se necessário, obter SKUs pelo POST existente `/api/admin/integrations/aliexpress/freight-diagnostic/product` com productId; essa preparação é uma leitura adicional. O comparador revalida produto e estoque por conta própria.
4. Fazer um POST para o endpoint novo, Content-Type application/json, substituindo os marcadores abaixo por IDs reais (não por códigos territoriais):

```json
{
  "productId": "ID_REAL_DO_PRODUTO",
  "skuId": "ID_REAL_DO_SKU",
  "quantity": 1
}
```

5. Conferir `executionStatus`/`safeCode`. Em countryOnly, province deve ser null e a lista de campos não deve conter province/provinceCode/cityCode. Em withProvince, province deve ser Santa Catarina e a lista deve conter province, sem provinceCode/cityCode. Conferir requestBodyWritten e httpResponseReceived de ambos; ausência de resposta não é ausência de diferença.
6. Comparar `result.status`, `result.safeCode`, `methods` e `monetaryStructure` dos dois lados, além de responseComparison. Exemplo: se ambos retornarem cent=24.00, BRL, formato normalizado=24.00 e MAJOR_MATCH, com AMBIGUOUS_MONETARY_UNIT, a ambiguidade continua intacta. Igualdade não libera a escala ou prova cobertura provincial/CEP. Rejeição do nome também não autoriza tentar códigos inventados.
7. Guardar apenas o retorno sanitizado e o contexto do ensaio em canal administrativo; não coletar resposta integral, tokens ou dados de destinatário. Investigar eventual mudança com o fornecedor, mantendo checkout bloqueado.

## Limites comerciais

Province não é confirmação de Joinville nem de CEP. O gateway de checkout continua construindo consulta sem province e retornando COUNTRY_ONLY; `DESTINATION_NOT_CONFIRMED` e proteções de pagamento permanecem. Nenhuma normalização monetária foi flexibilizada, nenhum pedido foi criado, nenhum pagamento foi executado, nenhuma credencial foi alterada.

## Testes locais

Regressão de AliExpress, frete e pagamentos: 376 casos contabilizados, **375 aprovados e 1 probe real ignorado**, zero falhas/erros, Maven package/repackage com BUILD SUCCESS. Após ajustar apenas a identificação de escopo da consulta com province, revalidação final de 34 casos aprovados e novo package/repackage bem-sucedido.

Os novos testes verificam os DTOs HTTP reais simulados e suas assinaturas (7 campos BR, 8 com province literal; códigos/CEP ausentes), dois retornos ambíguos preservando MAJOR_MATCH, evidência de escrita/resposta, timeout sem falsa igualdade, bloqueio de província arbitrária, comparação de valor diferente, snapshot/OAuth únicos, interrupção após falha e ADMIN/no-store. Fixtures não são respostas de produção. Logs locais: `target/province-comparison-tests.log` e `target/province-comparison-final.log`.
