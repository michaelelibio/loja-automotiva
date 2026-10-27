# DS freight: resposta monetária informada em 27/10/2026

Atualização posterior: o operador confirmou formato normalizado `24.00` e `MAJOR_MATCH`; a releitura pública oficial foi concluída com acesso de rede ampliado. Consulte [a investigação atual de moeda e endereço](ALIEXPRESS-MONEY-ADDRESS-REVIEW-20261027.md). Os limites de conectividade e de formato desconhecido descritos abaixo registram a etapa anterior, não o estado atual da evidência.

## Causa exata

Na opção de índice 0 informada pelo operador, `shipping_fee_cent` é STRING `24.00`, moeda BRL e `free_shipping=false`. A condição `cent.matches("[0-9]{1,16}\\.[0-9]+")` em `AliExpressDsFreightClient.parse` retorna imediatamente `INVALID_RESPONSE / AMBIGUOUS_MONETARY_UNIT`. O parser não chega à leitura da moeda, comparação do formato ou flag de gratuidade nesse caso. O gateway traduz para `AMBIGUOUS_MONEY`, exposto como HTTP 409 / `ALIEXPRESS_AMBIGUOUS_MONEY`.

O conteúdo efetivo de `shipping_fee_format` não foi fornecido: presença não comprova valor nem formato. Nenhuma consulta autenticada nova foi executada nesta investigação. Portanto não se afirma que a resposta real contenha R$ 24,00 ou R$ 0,24.

## Valores comparáveis e diagnóstico

| Observação ou hipótese | Valor decimal BRL |
|---|---|
| Campo informado, sem atribuir escala | 24.00 |
| Hipótese de unidade principal | 24.00 |
| Hipótese de centavos: BigDecimal.movePointLeft(2) | 0.2400 (numericamente 0.24) |
| Formato real | Ainda indisponível |

O diagnóstico ADMIN mantém `safeValue` como string. Reconhece agora também formatos BRL estritos como `R$ 24,00`, `R$ 24.00`, `BRL 24,00`, `BRL 24.00`, inclusive espaço não separável. Não aceita agrupamentos, HTML, sinais, texto adicional ou números extensos; não copia o texto original. A moeda deve ser permitida, e prefixos reconhecidos devem ser compatíveis com ela. Campos sensíveis continuam suprimidos.

Na linha `shipping_fee_format`, `comparison` compara por `BigDecimal.compareTo` o decimal sanitizado com o campo original e com seu deslocamento de duas casas. `MAJOR_MATCH` significa igualdade com o original; `MINOR_MATCH`, igualdade com original/100; `BOTH_MATCH` ocorre em zero; `NEITHER_MATCH` indica divergência; `UNAVAILABLE` indica ausência de dados seguros. Isso é evidência observacional, nunca confirmação contratual. O frontend permite apenas esses códigos fixos.

Exemplos sintéticos dos testes: original 24.00 e formato R$ 24,00 produzem MAJOR_MATCH; formato R$ 0,24 produz MINOR_MATCH. Ambos continuam bloqueados, sem modalidades aprovadas. Não são observações da resposta real.

## Contrato específico e limite da conclusão

Fonte oficial: https://openservice.aliexpress.com/handler/share/apidoc/getApi?cid=21038&path=aliexpress.ds.freight.query&methodType=GET%2FPOST (docId 1579).

Em 27/10 a tentativa por navegador de pesquisa retornou erro de acesso; a requisição pública direta falhou por conectividade. Não foi possível confirmar uma revisão atual do contrato. A evidência oficial anteriormente arquivada em `ALIEXPRESS-DS-FREIGHT-DIAGNOSTIC-EVIDENCE.json`, obtida em 20/10, declara String e centavos, mas registra o exemplo decimal `1.99` junto de `US $1.99`, USD. Nome, descrição e exemplo não permitem resolver com segurança a contradição. Não há evidência de que o nome esteja errado, nem de que o decimal represente centavos fracionários. Não foram importadas regras de outras APIs.

Falta obter: (1) `safeValue`/`comparison` da opção 0 da consulta real após disponibilização desta alteração por processo autorizado; (2) esclarecimento oficial específico de DS, ou contrato corrigido e consistente, definindo escala e comportamento de valores inteiros e decimais. A igualdade entre dois campos da mesma resposta não substitui essa confirmação.

Recomendação mínima segura: manter a rejeição decimal atual e ampliar somente o diagnóstico. Depois de confirmação oficial, alterar apenas a normalização documentada, com testes de inteiros e decimais, zero, moeda, precisão, divergência do formato e gratuidade. Não há base suficiente hoje para recomendar dividir 24.00 por 100 ou adotá-lo como R$ 24,00.

## Bloqueio comercial independente

`AliExpressDsFreightGateway` continua retornando cobertura `COUNTRY_ONLY`. `AliExpressFreightService` continua rejeitando essa cobertura com `DESTINATION_NOT_CONFIRMED`, mesmo quando o parser monetário aceita o retorno. Resolver a escala não confirma endereço, CEP, província/cidade nem autoriza checkout ou pagamentos.

Sem pedidos, pagamentos, alteração de credenciais ou deploy nesta investigação.
