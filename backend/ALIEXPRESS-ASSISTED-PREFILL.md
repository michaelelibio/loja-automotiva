# AliExpress — pré-preenchimento da cotação assistida

Implementação local de 30/10/2026. Sem commit, push, deploy, compras, pagamentos ou acesso ao PostgreSQL de produção. O perfil controlled e o bloqueio de criação de pagamentos não foram alterados.

## Fluxo implementado

Na tela /admin/pedidos/{id}, o painel existente consulta automaticamente POST /api/admin/orders/{id}/quote/suggestions ao abrir uma cotação WAITING. Somente ADMIN tem acesso; a resposta usa Cache-Control: no-store. Há atualização manual dos dados e tratamento de falha sem impedir a conferência manual existente.

O servidor resolve produto, SKU, quantidade e UF a partir do snapshot do pedido, ignorando IDs/quantidades enviados no corpo da requisição. Só aceita pedido assistido pendente e não expirado. A transação de leitura termina ANTES das chamadas externas; nenhum bloqueio de pedido é mantido enquanto aguarda o fornecedor.

AliExpressFreightService delega ao auxiliar AliExpressAssistedFreightPreview, que reutiliza o mesmo bean AliExpressDsFreightClient utilizado pelo gateway e pelos diagnósticos. Usa o AliExpressProductClient e o normalizador existentes para observar preço/estoque atual. A consulta de produto é compartilhada entre SKUs do mesmo produto dentro da requisição. Não usa preço antigo importado como se fosse custo atual.

- Frete: aliexpress.ds.freight.query por SKU e quantidade, BR e nome da província correspondente à UF, usando somente parâmetros documentados.
- Produto: aliexpress.ds.product.get em BRL/BR. Moeda diferente, estoque insuficiente ou preço ausente tornam o subtotal observado desconhecido; não há conversão cambial presumida.
- Não envia CPF, telefone, nome, rua ou CEP ao método de frete. O contrato consultado não documenta endereço completo/CEP. Não inventa provinceCode/cityCode a partir de IBGE, CEP ou IDs territoriais.
- Limita a consulta a 20 linhas e verifica orçamento de 45 segundos entre linhas (não é um timeout global: uma chamada em andamento segue o timeout do cliente). Rate limit, token rejeitado e permissão negada interrompem as linhas seguintes. Não faz retries, refresh de OAuth, fallback para métodos legados ou chamadas de criação de pedido.

Uma modalidade única preenche automaticamente os campos vazios comprovados. Com várias modalidades, o operador escolhe qual usar. Nunca sobrescreve valores manuais, mistura um código logístico já informado com outro código, ou aplica frete individual a SKUs agrupados. Campos desconhecidos continuam vazios. A origem vem exclusivamente de ship_from_country, nunca do país da loja. Prazo usa min_delivery_days/max_delivery_days válidos, de até 365 dias, como estimativa.

As observações têm horário e um prazo LOCAL de revisão de 10 minutos; isso não reserva preço nem representa validade concedida pelo fornecedor. A cotação continua WAITING até confirmação administrativa explícita. O painel preserva a separação entre consulta, confirmação e aceite. Não foi adicionada consulta externa à requisição de checkout do cliente: o pré-preenchimento ocorre automaticamente ao administrador abrir o pedido que contém o endereço informado pelo cliente.

## Dinheiro e custos

A resposta separa receivedAmount, formattedAmount, currency, documentedScale, monetaryConsistency e shippingBrl. A descrição oficial ainda diz centavos, enquanto o exemplo oficial usa shipping_fee_cent="1.99" e shipping_fee_format="US $1.99". A observação anterior relatada no projeto, "24.00" BRL / "24.00" formatado, é MAJOR_MATCH. Essa consistência não resolve a contradição do contrato.

Por isso, frete não zero NÃO é convertido ou copiado para shippingBrl. O ADM vê os dois valores e a consistência para revisar no portal. A única normalização atualmente permitida é zero em BRL com free_shipping=true, sem limiar promocional, aliases conflitantes ou texto formatado contraditório. Zero é invariável à escala; isso também não confirma o destino completo. Nenhuma modalidade com valor desconhecido vira frete grátis.

Indicadores oficiais preservados:

- Modalidade: ddpIncludeVATTax, como booleano observado.
- Produto/SKU: price_include_tax, tax_amount e tax_currency_code. O valor fiscal recebido é exibido sem assumir sua escala/cobertura; não é somado automaticamente ao preço.

Esses campos NÃO foram encontrados na fixture histórica do produto como prova de todos os tributos brasileiros. Os testes de presença são sintéticos. Uma flag VAT/DDP não comprova II, ICMS, taxas adicionais e custo final para o endereço. Imposto ausente nunca é convertido em zero. Preço pode conter promoção pessoal, descontos por quantidade ou tributos embutidos; o subtotal unitário observado não é checkout completo.

Costs.calculate prepara a composição de produtos + frete + impostos exclusivos + outras cobranças do fornecedor, margem bruta e margem líquida após taxa de pagamento. Se um componente necessário for desconhecido, o resultado correspondente permanece null/INCOMPLETE. Não soma impostos duas vezes quando já incluídos no produto: qualquer futuro adaptador deve provar essa decomposição antes de usar o cálculo.

Evidence.automaticEligible exige composição completa, margem líquida não negativa, endereço completo confirmado, escala confirmada, modalidade/agrupamento confirmados, SKU/preço/estoque atuais e validade comprovada pelo fornecedor. Esse predicado é arquitetura para adaptadores futuros, não uma liberação de checkout. O adaptador atual devolve commercialAuthorized=false e custos completos/margens desconhecidos; não existe promoção automática da cotação.

O custo completo informado manualmente continua dependendo da conferência explícita do administrador, como no Caminho B anterior. O painel calcula o total de venda somente quando todas as remessas têm frete preenchido e mostra margem bruta a partir do custo completo informado; não a apresenta como margem líquida comprovada pela API.

## APIs oficiais investigadas

Fontes públicas consultadas novamente, sem credenciais. IDs documentais, campos e SHA-256 estão em ALIEXPRESS-ASSISTED-PREFILL-EVIDENCE.json.

| Método | Achado / uso |
|---|---|
| aliexpress.ds.freight.query, docId 1579 | SKU/quantidade/país, province ou provinceCode e cityCode opcional. Sem campo documentado para CEP, rua ou número. Mantido como único transporte de frete desta implementação. |
| aliexpress.logistics.buyer.freight.get, docId 1442 | Documento público retornou; inclui SKU, país/província/cidade e origem. Não documenta CEP/endereço completo. Permissão da aplicação não comprovada; NÃO foi invocado nem adotado como fallback. |
| aliexpress.logistics.buyer.freight.calculate, docId 525 | País/província/cidade, SKU e origem; sem CEP/endereço completo no contrato consultado. Não foi usado para contornar as restrições do DS. |
| aliexpress.ds.product.get, docId 13307 | Preço/estoque e indicadores de imposto. Não prova checkout completo por endereço; os campos fiscais são observações. |
| aliexpress.ds.address.get, docId 1584 | Vocabulário territorial. Reconhecer estado/cidade não confirma cobertura ou preço por CEP e não comprova que IDs servem aos campos de frete. |
| aliexpress.ds.shippinginfo.get | A consulta pública não devolveu definição desse método. Não foi tratado como API disponível/autorizada. |

A existência de documentação pública não prova autorização da aplicação nem operação atual de cada método. Não foi necessário afirmar depreciação sem evidência: nenhuma alternativa foi chamada. Não se conclui que nenhuma API AliExpress possa atender ao CEP; a conclusão é que NÃO foi encontrado um contrato autorizado e comprovado que resolva isso entre os métodos consultados. Exige confirmação oficial do AliExpress, documentação da escala e uma resposta autenticada que vincule o preço ao endereço e à composição integral.

## Repetir observações reais sem extrair token

O código publicado já dispõe dos diagnósticos ADMIN abaixo, sob /api/admin/integrations/aliexpress, que usam existingAccessToken dentro do backend, não fazem refresh e devolvem projeções sanitizadas:

- POST /freight-diagnostic/product: produto/variantes/estoque para preparar a consulta.
- POST /freight-diagnostic: produto, SKU e quantidade; valores monetários sanitizados.
- POST /freight-diagnostic/province-comparison: duas leituras planejadas, BR e BR + Santa Catarina, incluindo origem observada quando disponível.
- POST /territorial-diagnostic: consulta territorial fixa BR; sem pedido/compra.

Usar a sessão ADMIN já autenticada e as telas de diagnóstico existentes; nunca copiar token OAuth, JWT ou credenciais para chat. Registrar somente campos sanitizados e horário da resposta. A comparação por província não comprova CEP, mesmo se retornar preço idêntico.

Não foram executadas essas chamadas nesta tarefa: não há sessão administrativa disponibilizada às ferramentas nem access token isolado local, e o usuário proibiu buscar o OAuth no banco de produção. A implementação foi verificada com HTTP simulado e fixtures do projeto; isso não foi apresentado como novo teste autenticado.

Se os diagnósticos existentes não trouxerem evidência suficiente para os campos novos, criar ambiente AliExpress de teste separado: banco local/staging exclusivo com dados sintéticos, aplicação/conta de teste permitida pelo AliExpress e OAuth autorizado nela; ALIEXPRESS_APP_KEY, ALIEXPRESS_APP_SECRET, ALIEXPRESS_CALLBACK_URL e ALIEXPRESS_TOKEN_ENCRYPTION_KEY exclusivos. Guardar o OAuth criptografado nesse banco via fluxo existente. Manter PAYMENT_CREATION_ENABLED=false, pedidos AliExpress desativados e nenhuma integração de pagamento habilitada. O perfil homologation atual limpa as chaves AliExpress por segurança; para esse ensaio usar configuração própria isolada com override explícito apenas dessas chaves, sem alterar controlled nem carregar .env.local.ps1 de produção. Se não houver sandbox AliExpress oficial disponível para a conta, solicitar confirmação ao suporte; não presumir que uma chave de produção se torna sandbox por configuração.

## Publicação e regressão

Não há migração nova, mudança de tabelas, alteração de credenciais ou mudança de flags. O perfil controlled permanece com validate + SQL init never, criação de pagamento false e pedidos AliExpress false. O teste HTTP 409 permanece parte da regressão.

Arquivos principais: AliExpressDsFreightClient, AliExpressFreightService, novo auxiliar AliExpressAssistedFreightPreview, AssistedQuoteService/Controller, AssistedQuotePanel, auxiliar frontend lib/admin/assisted-freight.ts, cliente ADM orders.ts e key do painel em app/admin/pedidos/[id]/page.tsx. Testes novos cobrem parser/HTTP externo simulado, custos, snapshot do pedido, autorização HTTP e pré-preenchimento sem sobrescrever campos; wiring tests verificam o compartilhamento do mesmo client.

Nenhuma etapa desta implementação cria preferência Mercado Pago ou pedido no fornecedor. A confirmação manual existente segue sendo indispensável para endereço, preço integral, tributos, agrupamento e margem comercial. Encaminhamento e pagamento não foram automatizados.
## Validação executada

- Regressão de integração/HTTP simulado: **114 testes, 0 falhas, 0 erros, BUILD SUCCESS**. Inclui novo endpoint ADMIN, snapshot do pedido, cotação/aceite existentes, frete e bloqueio HTTP 409.
- Casos adicionais de segurança e configuração: **82 testes, 0 falhas, 0 erros, BUILD SUCCESS**.
- Artefato final, após os últimos ajustes no parser: **105 testes, 0 falhas, 0 erros, BUILD SUCCESS**. São conjuntos parcialmente sobrepostos, não uma contagem de testes únicos. Incluem 12 casos do novo auxiliar: resposta decimal documentada, dois SKUs/quantidades, indicadores fiscais, zero invariável, formato contraditório, conflito de resposta, rate limit, ausência de OAuth, custos incompletos e elegibilidade futura.
- Frontend final: **53 testes aprovados**, build Next.js/TypeScript aprovado, lint com **0 erros e 4 avisos existentes**. O build terminou depois da última alteração do painel.
- git diff --check aprovado. Varredura de padrões de credenciais nos 17 arquivos de projeto alterados/criados não encontrou suspeitas; esse número inclui next-env.d.ts gerado, já modificado ao iniciar a tarefa. O arquivo avulso não relacionado existente na raiz foi preservado.
- Nenhuma chamada autenticada ao AliExpress, consulta ao banco de produção, compra, cobrança, commit, push ou deploy. Apenas a documentação pública oficial foi consultada pela rede.

Logs locais não versionados: backend/target/homologation-final/assisted-prefill-backend-final.log, assisted-prefill-final-safety.log, assisted-prefill-parser-final.log e assisted-prefill-frontend-*.log. JAR final em backend/target/assisted-verification/garage-api-0.0.1-SNAPSHOT.jar.
## Publicação desta evolução — release/cotacao-assistida-controlada

Revisão em 30/10/2026: branch local confirmada, HEAD anterior 089696a. O manifesto ALIEXPRESS-PREFILL-RELEASE-FILES.txt contém exclusivamente os 17 arquivos desta evolução, incluindo o próprio manifesto: 5 arquivos de implementação backend, 4 testes backend, 5 arquivos frontend e 3 arquivos de documentação/manifesto. Não inclui next-env.d.ts gerado nem o arquivo avulso preexistente na raiz.

### Railway e Vercel precisam ser atualizados

O código versionado em HEAD de AssistedQuoteController NÃO contém POST /api/admin/orders/{id}/quote/suggestions. A árvore local acrescenta esse endpoint. Os diagnósticos administrativos existentes consultam frete/produto/território, mas não substituem o novo contrato que resolve o snapshot do pedido e devolve modalidades, preços observados e pendências comerciais por SKU. Portanto publicar somente o frontend deixaria a consulta automática indisponível; a cotação manual permanece possível em caso de falha da consulta.

A versão efetivamente implantada não foi inspecionada por sessão ADMIN nesta execução. A conclusão sobre compatibilidade baseia-se no código versionado da release e na nova chamada exigida pelo frontend. Uma resposta 401 sem autenticação não comprovaria a existência dessa rota, por isso não foi usada como evidência. Não houve leitura do pedido de produção #16 nem chamada ao seu endpoint de pagamento.

Nenhuma migração acompanha esta evolução. Não alterar banco, chaves, OAuth ou flags para instalar a funcionalidade. Manter no Railway o perfil controlled, validate + SQL init never, PAYMENT_CREATION_ENABLED=false, ALIEXPRESS_ORDERS_ENABLED=false e ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false. Na Vercel manter NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=false e API apontando para https://api.ingarage.com.br. Não ativar sandbox ou pagamentos como parte desse teste.

O OAuth é lido por existingAccessToken, com transação readOnly, sem refresh nem escrita, e descriptografado somente em memória no backend para assinar consultas. O token nunca faz parte da resposta de sugestões ou dos dados do formulário. Esta preparação não extraiu tokens nem acessou o PostgreSQL Railway.

### Comandos manuais de commit/push

Antes do push, pausar os gatilhos automáticos Railway/Vercel ou selecionar um procedimento de publicação manual que permita backend primeiro. Os comandos abaixo NÃO foram executados:

```powershell
if ((git branch --show-current) -ne 'release/cotacao-assistida-controlada') { throw 'Branch incorreta' }
if (git diff --cached --name-only) { throw 'Ha arquivos staged anteriores: revisar antes de continuar' }
$releaseFiles = Get-Content .\backend\ALIEXPRESS-PREFILL-RELEASE-FILES.txt
git --literal-pathspecs add -- $releaseFiles
git diff --cached --check
if ($LASTEXITCODE -ne 0) { throw 'Corrigir diff antes do commit' }
git diff --cached --stat
# Revisar os 17 arquivos staged; nao adicionar next-env.d.ts, arquivos .local ou o arquivo avulso da raiz.
git commit -m "feat: pre-preenchimento seguro da cotacao assistida AliExpress"
if ($LASTEXITCODE -ne 0) { throw 'Commit falhou: nao executar push' }
git push origin release/cotacao-assistida-controlada
```

Publicar manualmente o BACKEND Railway primeiro, preservando todas as configurações e credenciais existentes. Verificar startup controlled sem DDL e, com sessão ADMIN, o novo endpoint respondendo JSON no contrato esperado. Só depois publicar FRONTEND Vercel com novo build e pagamentos false. Não há instrução de aplicar SQL nem alterar tabelas.

### Teste de aceitação no pedido #16

1. Entrar como administrador e abrir https://ingarage.com.br/admin/pedidos/16 após as duas publicações.
2. O pré-preenchimento só ocorre se a cotação ainda estiver WAITING, o pedido PENDING_PAYMENT e o prazo do pedido não tiver expirado. Não reabrir nem editar o pedido/banco para contornar esse requisito. Se não atender, usar novo pedido sintético autorizado.
3. Conferir no Network o POST /api/admin/orders/16/quote/suggestions. Esperado: 200, Cache-Control: no-store, orderId=16, commercialAuthorized=false, custos completos desconhecidos e nenhum checkoutUrl. Não compartilhar headers de autenticação.
4. Uma opção única preenche somente campos vazios comprovados. Com múltiplas opções, selecionar a modalidade; se houver dados prévios de outra, usar o botão de limpeza explícita da remessa. Quantidade e SKU devem corresponder ao pedido. Origem/nome/prazo ausentes não serão inventados.
5. Frete 24.00/MAJOR_MATCH continua como observação, sem preencher automaticamente o valor comercial não comprovado. Impostos desconhecidos e componentes ausentes continuam pendentes. Não confundir subtotal de produtos com custo final.
6. Apenas abrir a tela não confirma nem aceita a cotação, não inicia cobrança e não cria ordem no fornecedor. Esperado: quote.state continua WAITING, pedido continua não pago e nenhum supplier_order_id é criado. Não clicar em confirmação sem a conferência manual completa e não usar o botão de pagamento.
7. Se OAuth expirado, fornecedor indisponível ou rota não publicada, a tela mostra a falha e preserva os dados manuais. Corrigir configuração/deploy com o operador; não renovar credenciais nem mudar pagamentos automaticamente.

O HTTP 409 de pagamento é verificado localmente nos testes de ControlledPublicationPaymentTests. Não foi enviado um pagamento de produção para demonstrá-lo. A verificação remota de flags e versão deve ser feita pelo operador, sem gerar cobrança. Se houver falha apenas na interface, reverter frontend mantendo o backend controlled; nunca voltar para uma imagem que ignore o bloqueio de pagamentos.

### Resultado desta preparação de publicação

Regressão final executada novamente: **119 testes backend, 0 falhas, 0 erros, 0 ignorados, BUILD SUCCESS**; **53 testes frontend aprovados**, lint com **0 erros e 4 avisos existentes**, build Next.js/TypeScript aprovado. Testes backend exclusivamente em H2/mocks HTTP; nenhum PostgreSQL foi iniciado ou acessado nesta preparação. Logs não versionados em backend/target/homologation-final/prefill-release-*.log. Perfil controlled comparado ao HEAD e idêntico. Manifesto com 17 arquivos; varredura não encontrou padrões de credenciais suspeitos; git diff --check aprovado. Nenhum arquivo staged, commit, push ou deploy executado.

**PRONTO PARA PUBLICAÇÃO CONTROLADA DE BACKEND E FRONTEND**, mantendo confirmação manual e pagamentos bloqueados. O teste autenticado no pedido #16 e a conferência da configuração efetiva remota são etapas manuais após publicar; não foram apresentados como testes já realizados.
