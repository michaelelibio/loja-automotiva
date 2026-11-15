# Frete imediato por UF — proposta antes da habilitação

## Decisão implementada

A inGarage pode oferecer preço próprio, definitivo para o cliente, quando houver uma tarifa comercial comprovada. O preço é da loja; não é apresentado como uma garantia de pré-cotação AliExpress por CEP. O checkout não interpreta shipping_fee_cent para produzir tarifas comerciais.

**Nenhuma tarifa real foi habilitada.** COMMERCIAL_FREIGHT_ENABLED permanece false por padrão. O perfil controlled foi preservado integralmente, novos pagamentos continuam bloqueados por HTTP 409 e envio ao AliExpress não foi ativado. As únicas tarifas com números usadas na validação são fixtures sintéticas dos testes, em H2/PostgreSQL isolado; não são preços recomendados ou dados para produção.

## Evidências reais disponíveis e conclusão de preço

- O histórico do projeto registra shipping_fee_cent="24.00", moeda BRL, apresentação normalizada "24.00" e MAJOR_MATCH para BR e BR + Santa Catarina. A escala continua conflitante no contrato oficial. Não há comprovação suficiente para classificar essa observação como frete comercial de produto pequeno, médio ou grande, nem para extrapolar para outra UF/quantidade/origem.
- A fixture histórica de produto mostra preço unitário BRL, mas não custo completo de entrega, tributos e taxas. Preço de produto não será usado como preço de frete.
- Os diagnósticos administrativos existentes continuam disponíveis para coletar referências sanitizadas com OAuth mantido no backend. Não foram executadas consultas autenticadas, compras ou leitura do banco Railway nesta implementação.

Portanto **não há base para propor preços numéricos aprovados por UF/porte nesta etapa**. A comparação honesta é a matriz abaixo. "Sem evidência" não significa zero, gratuito, indisponibilidade comprovada ou que o preço de outra UF pode ser reutilizado.

| UF | Pequeno | Médio | Grande |
|---|---|---|---|
| AC | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| AL | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| AP | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| AM | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| BA | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| CE | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| DF | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| ES | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| GO | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| MA | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| MT | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| MS | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| MG | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| PA | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| PB | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| PR | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| PE | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| PI | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| RJ | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| RN | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| RS | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| RO | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| RR | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| SC | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| SP | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| SE | Sem evidência válida | Sem evidência válida | Sem evidência válida |
| TO | Sem evidência válida | Sem evidência válida | Sem evidência válida |

Para cada célula realmente ofertada, coletar produto/SKU, loja fornecedora, origem, dimensões/peso e critério de porte, modalidade/código, quantidade, capacidade de embalagem, frete BRL, impostos discriminados sem duplicar os já incluídos, outros custos, data, validade e referência verificável. Cotações de diferentes lojas/origens não são agrupadas por semelhança. Uma célula não verificada continua oferecendo cotação assistida.

## Proposta de cálculo e reserva

Para cada versão da política:

1. Classificar SKU como SMALL, MEDIUM ou LARGE a partir de dimensões/peso e embalagem realmente verificados. Não foram inventados limites de peso/dimensão.
2. Definir tarifa por UF, categoria exata do catálogo, porte, loja fornecedora e origem. Conferir o domínio fiscal/logístico: quantidade TOTAL máxima e base máxima de fornecedor (produtos + frete + outros) em que a composição de custos é comprovada. Se tributos variarem de forma não linear, usar um limite superior comprovado para todo esse domínio ou restringi-lo, por exemplo à quantidade efetivamente verificada. Não extrapolar uma leitura de uma unidade para compras maiores.
3. Para uma remessa comprovada, volumes = teto(unidades / capacidade). PER_SKU declara separação por SKU; CONSOLIDATED exige comprovação de consolidação para SKUs da mesma regra. Repetições do mesmo SKU no carrinho são agregadas antes de contar volumes.
4. Custo fornecedor = produtos + fretes por volume + impostos exclusivos + outros custos. O maior custo entre o cadastro verificado e um custo BRL importado conhecido é usado conservadoramente; isso não é uma nova confirmação de checkout no AliExpress.
5. Reserva = custo fornecedor × percentual de reserva, arredondado para cima ao centavo. O percentual é uma decisão aprovada da loja, não um valor da API. Calibrá-lo com séries reais por UF/porte/origem: medir aumentos relativos e custos adicionais, escolher uma cobertura para a variação observada e documentar a decisão. Sem série verificada, a reserva necessária continua não calibrada; não foi escolhido um percentual para completar preços. A configuração exige reserva positiva.
6. Venda = subtotal dos produtos + frete próprio cobrado. Taxas = venda × taxa percentual do provedor + taxa fixa por pedido, com arredondamento conservador ao centavo. A fonte dessas taxas precisa ser um plano do provedor de pagamento ou suporte oficial, com validade; não um diagnóstico AliExpress.
7. Lucro líquido modelado = venda − fornecedor − reserva − taxas. Validar simultaneamente lucro mínimo em BRL e percentual mínimo sobre a venda. Essa é a composição de custos cadastrada, não garantia contábil de lucro se houver despesas da loja omitidas. Incluir tais custos na política antes de ativar a oferta.

Tarifas podem embutir uma reserva no frete cobrado, mas o sistema NÃO inventa esse acréscimo nem aumenta um total já aceito. Se não houver margem, custo conhecido, cobertura ou evidência válida, bloquear o fechamento imediato e oferecer a cotação assistida.

## Implementação e telas

- ADM /admin/fretes: custos por SKU, tarifas com seletor das 27 UFs e três portes, fontes/datas/validade, limites fiscais, reserva, taxas e margem. Cada salvamento cria um rascunho novo; publicação exige aprovação operacional da configuração e evidência válida. Versões publicadas são imutáveis, podem ser retiradas e não voltam silenciosamente a uma versão anterior. Copiar para revisão cria outro rascunho.
- Produto /produtos/{slug}, carrinho /carrinho e /checkout: cálculo por CEP, com variante e quantidade. O backend resolve UF pelo ViaCEP; não confia na UF enviada pelo navegador. CEP inexistente, resposta divergente e falha de consulta bloqueiam o frete imediato. Cache limitado de UF por CEP, por uma hora, evita consultas repetitivas; não guarda resposta completa de endereço. ViaCEP fornece UF, não comprova frete AliExpress.
- Produto/carrinho podem consultar sem login. Resposta pública contém preço cobrado e estimativa, não política completa, custos de produto, reserva ou margem. Checkout exige autenticação e endereço pertencente ao usuário.
- Cliente vê produtos + frete antes de confirmar. Havendo uma tarifa U_, a requisição inclui total esperado, endereço exibido e chave de idempotência. O backend recalcula tudo, verifica CEP/UF e endereço e rejeita divergências em vez de cobrar outro total. O pedido não fica aguardando confirmação de cotação assistida.
- Sem tarifa válida, a UI oferece ASSISTED_QUOTE, mantendo endereço e dados de destinatário. A cotação assistida não é automaticamente aprovada. Carrinhos mistos com estoque local/CJ mantêm as regras anteriores e devem ser separados quando o fallback assistido não os suporta; não omitimos frete desses itens para produzir uma oferta parcial.

## Contrato e persistência

GET/POST /api/admin/shipping-tables, POST /{id}/publish e POST /{id}/retire são ADMIN. POST /api/shipping/quote usa produtos/variantes atuais do banco, valida estoque e não aceita preço/porte/origem do cliente como autoridade.

U_ é uma referência SHA-256 de versão + CEP/UF + itens/quantidades + preços + regras + custos de validação. Não é credencial. Publicar outra versão, mudar SKU/preço/custo ou alterar a seleção invalida a referência anterior. Isso evita o intervalo entre mostrar um preço e registrar outro no pedido.

orders.commercial_shipping JSONB armazena versão, referência, UF, validade, custos, reserva, taxas, lucro modelado, regras e referências de evidência, mais digest do contexto completo. Endereço, itens, remessas e total continuam nos snapshots existentes de orders/order_items/shipping_legs. O novo snapshot interno não é exposto integralmente ao cliente. A validade do pedido é limitada à evidência mais curta, além do limite de expiração existente.

Antes de preparar e criar preferência de pagamento, o backend verifica o contexto e recalcula com a política atual. Alteração, expiração ou margem insuficiente exigem nova decisão/aceite; não altera o total do pedido anterior. O gate de pagamentos é verificado ANTES de ler/criar tentativas ou chamar o provedor. Na aplicação controlled a criação segue indisponível. Reconferência de fornecimento após pagamento e o tratamento de webhooks tardios/custos novos devem integrar a homologação financeira antes de liberar vendas pagas; esta entrega não habilita essa fase.

## Banco e publicação futura

O projeto continua usando Spring SQL init, NÃO Flyway. Script aditivo V20261030_02__commercial_freight.sql cria commercial_freight_tables e adiciona orders.commercial_shipping nullable. Não há preços iniciais, backfill, DROP TABLE, DELETE, TRUNCATE ou alteração de totais históricos. O wrapper ops/commercial-freight-migration.sql aplica apenas essa evolução, em transação com timeouts, após backup e ensaio isolado.

Não foi executado SQL Railway. O perfil controlled mantém validate + SQL init never; uma publicação futura precisará aplicar manualmente a migração após autorização e backup, antes de iniciar o backend. Não ativar Hibernate update para contornar falta de estrutura. Manter PAYMENT_CREATION_ENABLED=false, ALIEXPRESS_ORDERS_ENABLED=false, ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false e NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=false.

Somente após revisar esta proposta, obter evidências suficientes e aprovar custos, usar COMMERCIAL_FREIGHT_ENABLED=true para disponibilizar as tarifas da loja; isso não habilita pagamento. Não alterar credenciais existentes. Não há commit, push ou deploy nesta execução.

## Como testar localmente sem cobrança

Os testes CommercialFreightIntegrationTests habilitam a tabela exclusivamente no contexto de teste, com números sintéticos, ViaCEP e AliExpress/MP simulados. Criam pedido imediato, conferem total e snapshot e recebem HTTP 409 ao tentar pagamento. Incluem 27 UFs, margem, evidência desconhecida, expiração, volumes, duplicações, consolidação e mudanças que exigem novo aceite.

CommercialFreightPostgresTests são opt-in: INGARAGE_HML_POSTGRES=true e INGARAGE_HML_PG_URL deve ser exatamente jdbc:postgresql://127.0.0.1:PORTA/ingarage_homologation, usuário ingarage_hml. Qualquer outro banco é recusado. Repetem o fluxo e testam o SQL duas vezes, preservação de totais antigos e roundtrip JSONB/NUMERIC.

Não copiar tarifas sintéticas dos testes para o cadastro administrativo. Para ensaio visual, usar usuários/produtos/endereço sintéticos em ambiente local isolado; preencher formulário somente com evidências fictícias explicitamente identificadas de teste, pagamentos false e gateways simulados. Em produção, permanecer no fallback enquanto não houver preço real aprovado.

Fonte para resolução postal: https://viacep.com.br/ . Evidências históricas de AliExpress: ALIEXPRESS-ASSISTED-PREFILL-EVIDENCE.json e ALIEXPRESS-ASSISTED-PREFILL.md. Não foi presumida autorização de uma API alternativa de cotação por CEP.
## Resultado final da validação local — 30/10/2026

- Backend: 146 testes, zero falhas/erros/ignorados, BUILD SUCCESS e JAR empacotado. Inclui 39 testes em PostgreSQL 18 isolado, roundtrip JSONB/NUMERIC e aplicação duplicada da migração em schema transacional de teste.
- Frontend: 55 testes aprovados; lint sem erros (quatro avisos existentes); build Next.js e TypeScript aprovados, incluindo /admin/fretes.
- Perfil controlled preservado. Pagamento HTTP 409 testado; nenhuma chamada aos gateways reais de pagamento, pedido do fornecedor ou OAuth nos testes comerciais.
- Cluster PostgreSQL local de homologação encerrado após as verificações; dados preservados. Nenhum banco de produção acessado.
- Na calibração comercial, usar o teto comprovado das taxas entre todos os meios de pagamento e parcelamentos oferecidos. Uma taxa genérica sem essa cobertura não comprova rentabilidade.

Logs locais (artefatos ignorados pelo Git): target/homologation-final/uf-release-tests.log e uf-release-frontend-{tests,lint,build}.log.