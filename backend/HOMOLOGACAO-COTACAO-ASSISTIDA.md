# Homologação final — cotação assistida inGarage

Data: 30/10/2026. Resultado: **BLOQUEADO para publicação controlada com vendas liberadas**, exclusivamente pelas etapas externas de Mercado Pago descritas abaixo. O fluxo local, o PostgreSQL isolado e os scripts de migração foram validados. Não houve acesso ao PostgreSQL de produção, mudança de credenciais existentes, compra, cobrança, commit, push ou deploy.

## Pendências que impedem a aprovação final

1. Criar/preencher a configuração exclusiva `backend/homologation.local.ps1` com credencial do vendedor de teste e segredo do webhook correspondente. O arquivo não foi criado nem preenchido automaticamente.
2. Disponibilizar backend e frontend de homologação em HTTPS, por túneis temporários ou ambientes separados. As URLs de produção informadas não foram usadas para testes.
3. Concluir uma compra no Checkout Pro hospedado usando comprador e cartão de teste; confirmar `approved` e `live_mode=false` na consulta autenticada do pagamento.
4. Receber a notificação enviada pelo próprio Mercado Pago no endpoint HTTPS de homologação, validar a assinatura com o segredo dessa configuração, consultar o pagamento autenticado e confirmar a atualização única do pedido. A repetição local de webhook assinado foi validada, mas não substitui essa entrega externa.

Uma consulta **somente de leitura** a `/users/me` comprovou que a credencial herdada do processo pertence a usuário de teste brasileiro. Isso não comprova que o ambiente completo esteja configurado. Não foi criada preferência nem transação usando essa credencial, e ela não foi copiada para a nova configuração.

## Migrações: mecanismo real e evidências

Não há dependência Flyway no `pom.xml`, nem execução Flyway configurada. Os arquivos versionados são executados pelo mecanismo Spring SQL initialization, em `spring.sql.init.schema-locations`, depois do Hibernate. Não foi instalado Flyway nem alterado o mecanismo de produção durante esta tarefa.

Foi criado um cluster PostgreSQL **18.4**, exclusivo, em `backend/target/homologation-final/pgdata`, ouvindo somente `127.0.0.1`, porta exclusiva registrada em `target/homologation-final/port.txt`, banco `ingarage_homologation`, usuário `ingarage_hml`. Não foram usadas DB_URL, usuário ou senha de produção.

Os seguintes scripts foram executados duas vezes em um schema de auditoria isolado, com estrutura anterior às colunas novas e pedido legado sintético:

- `V20260820_01__order_item_supplier_snapshot.sql`
- `V20260820_02__order_shipping_provider_snapshot.sql`
- `V20260820_03__order_fulfillment.sql`
- `V20261015_01__aliexpress_manual_orders.sql`
- `V20261030_01__assisted_quote.sql`

Foram comprovados: ausência da coluna assisted_quote antes da aplicação, criação JSONB, leitura/escrita JSONB, preservação do total e status do pedido antigo, ausência de preenchimento automático de CPF/cotação, reaplicação dos scripts, restrição UNIQUE dos IDs de fornecedor e exclusão em cascata. Além disso, o JAR foi iniciado **duas vezes** com o perfil `homologation`, credenciais sintéticas e SQL initialization real habilitado; ambas as inicializações responderam HTTP 200 em `/api/products`. Os processos temporários foram encerrados.

## Validações locais

- 15 cenários HTTP em PostgreSQL real: os sete fluxos de AssistedQuoteIntegrationTests, migrações, webhook assinado repetido, cinco alterações do contexto da cotação e round trip JSONB/NUMERIC.
- Configuração isolada: um teste comprova que DB_URL e token comuns de produção não são herdados pelo perfil de homologação.
- Regressão H2: sete cenários de cotação assistida e sete de webhook/pagamento simulado.
- Backend recompilado e empacotado com o perfil de homologação.
- O script de inicialização teve sintaxe verificada e bloqueou a execução quando a configuração exclusiva estava ausente. Não foi realizado login ou pagamento externo.

Evidências locais: `target/homologation-final/final-validation.log`, `postgres-final.log`, `boot-1.log` e `boot-2.log`. Os logs são artefatos locais de testes, não configuração de produção.

## Vínculo da cotação e invalidação

Foi acrescentado um digest canônico do snapshot comercial à cotação persistida: endereço/destinatário do pedido, produtos, SKUs, quantidades, remessas/modalidades e valores. Antes do aceite, pagamento e encaminhamento, o backend verifica esse vínculo. Os testes alteram endereço, total, modalidade, SKU e quantidade do pedido e comprovam bloqueio HTTP 409 antes de qualquer chamada ao gateway. A estabilidade do digest após leitura JSONB/NUMERIC no PostgreSQL foi testada.

Editar o cadastro de endereço ou o preço de catálogo **não reescreve** um pedido já cotado: ele mantém o snapshot que o cliente revisa. Uma compra para outro endereço/carrinho exige outro pedido e outra cotação. Uma alteração efetiva do contexto do pedido invalida o vínculo. Expiração impede aceite/pagamento; aprovação tardia e mudança de custo/entrega após pagamento geram tratamento de reembolso. Nenhuma cobrança adicional é criada.

Cotações de uma versão anterior sem `contextDigest` não são autorizadas para pagamento/encaminhamento por esta versão. Antes de promover a mudança, conferir essas pendências: criar nova solicitação para pedidos não pagos; reconciliar manualmente os pagos. Não há preenchimento automático dessa comprovação.

Envio manual: pagamento PAID comprovado no banco + reconferência de custo sem mudança, realizada nos últimos dez minutos + contexto íntegro. Reserva durável impede segundo encaminhamento; IDs externos têm unicidade no banco. Pagamento ao fornecedor e eventual reembolso ao cliente continuam manuais.

## Arquivos preparados nesta etapa

- `src/main/resources/application-homologation.properties`: perfil local, porta 18080, PostgreSQL de loopback, variáveis HML exclusivas, redirecionamento sandbox e fornecedores automáticos desabilitados.
- `homologation.env.example.ps1`: modelo sem credenciais.
- `start-homologation.ps1`: valida as variáveis e URLs; rejeita URLs de produção; consulta a identidade da credencial e exige `test_user` brasileiro; exige JAR contendo o perfil antes de iniciar.
- `src/test/java/com/garage/garageapi/order/quote/AssistedQuotePostgresHomologationTests.java`
- `src/test/java/com/garage/garageapi/order/quote/HomologationConfigurationTests.java`
- `Order`, `AssistedQuote`, `AssistedQuoteService` e `AliExpressFreightService`: verificação do digest da cotação.

Nenhum valor de `.env.local.ps1` ou `.env.example` foi modificado. A inspeção do arquivo local foi estática, sem executá-lo e sem imprimir valores. `MERCADO_PAGO_SANDBOX_PAYER_EMAIL` existe na configuração antiga, mas não determina o modo do gateway Checkout Pro: é necessário usar as contas e credenciais corretas, além da opção sandbox.

## O que configurar manualmente no Mercado Pago Developers

1. Em **Suas integrações**, abrir a aplicação usada para homologação do Checkout Pro via **Preferences** (a integração atual usa `/checkout/preferences` e tópico `payment`). Não alterar o webhook/credencial da operação de produção.
2. Em **Contas de teste**, identificar/criar vendedor e comprador brasileiros distintos. Usar a credencial da conta de vendedor de teste associada à aplicação. O prefixo APP_USR ou TEST isoladamente não comprova o ambiente: o inicializador verifica `test_user` por `/users/me`.
3. Copiar o token desse vendedor apenas para `HML_MERCADO_PAGO_ACCESS_TOKEN`, no arquivo local ignorado pelo Git. Não enviar token, senha do comprador ou segredo pelo chat.
4. Em **Webhooks > Configurar notificações**, selecionar a configuração de teste correspondente à aplicação/credencial, registrar `https://BACKEND-HML/api/webhooks/mercadopago` e habilitar **Pagamentos / payment**. Copiar o segredo gerado para `HML_MERCADO_PAGO_WEBHOOK_SECRET`. O segredo deve pertencer à mesma configuração usada para notificar o teste.
5. Preencher as URLs HTTPS de retorno: `/pagamento/sucesso`, `/pagamento/pendente` e `/pagamento/falhou` no frontend de homologação. O backend também envia `notification_url` na preferência; deve apontar para o mesmo backend de homologação.
6. Usar **Enviar teste** para comprovar a entrega HTTP. Esse teste de conectividade não comprova pagamento aprovado. Em seguida, abrir a loja de homologação em janela anônima, usar comprador de teste distinto do vendedor e realizar o fluxo completo com cartão de teste da documentação. Conferir o pagamento autenticado com `live_mode=false` antes de dar a homologação por encerrada.
7. Guardar evidências sanitizadas: pedido interno, referência externa, ID do pagamento de teste, estado aprovado, total BRL, resposta do webhook e repetição sem duplicar pagamento/pedido/encaminhamento. Não guardar token, assinatura ou senha nessas evidências.

Referências oficiais: [contas de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/test-accounts), [compra de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/integration-test/test-purchases), [cartões de teste](https://www.mercadopago.com.br/developers/pt/docs/your-integrations/test/cards) e [notificações](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/payment-notifications).

## Variáveis exclusivas necessárias

Copiar `homologation.env.example.ps1` para `homologation.local.ps1` e preencher:

| Variável | Valor/uso |
|---|---|
| HML_POSTGRES_PORT | Porta do cluster local isolado, consultável em target/homologation-final/port.txt nesta sessão |
| HML_POSTGRES_PASSWORD | Senha somente desse cluster; vazio apenas no cluster local trust criado para os testes |
| HML_JWT_SECRET | Novo segredo aleatório exclusivo, pelo menos 32 bytes |
| HML_FRONTEND_URL | Origem do frontend de homologação, usada no CORS e retornos da aplicação |
| HML_MERCADO_PAGO_ACCESS_TOKEN | Credencial exclusiva do vendedor de teste |
| HML_MERCADO_PAGO_WEBHOOK_SECRET | Segredo do webhook exclusivo do teste |
| HML_MERCADO_PAGO_SUCCESS_URL | https://FRONTEND-HML/pagamento/sucesso |
| HML_MERCADO_PAGO_PENDING_URL | https://FRONTEND-HML/pagamento/pendente |
| HML_MERCADO_PAGO_FAILURE_URL | https://FRONTEND-HML/pagamento/falhou |
| HML_MERCADO_PAGO_NOTIFICATION_URL | https://BACKEND-HML/api/webhooks/mercadopago |

O perfil fixa `mercadopago.checkout-pro.sandbox=true` e usa a API oficial normal `https://api.mercadopago.com`; não existe necessidade de inventar outra base de API. A flag escolhe o link sandbox retornado, e não transforma uma credencial de produção em credencial de teste.

Para validar sem iniciar o servidor:

    powershell -NoProfile -ExecutionPolicy RemoteSigned -File .\start-homologation.ps1 -ValidateOnly

Para iniciar, retirar ValidateOnly e informar o JAR atualizado se necessário. O inicializador não cria o cluster, não inicia túnel e não cria pagamentos. Não executar `start-local.ps1` para essa homologação, pois ele carrega o ambiente comum existente.

## HTTPS temporário, sem deploy

Opção local: dois Quick Tunnels, um para backend 18080 e outro para frontend 3000:

    cloudflared tunnel --url http://127.0.0.1:18080
    cloudflared tunnel --url http://127.0.0.1:3000

Usar os dois endereços HTTPS gerados nas variáveis correspondentes. Configurar o frontend local com `NEXT_PUBLIC_API_BASE_URL` apontando para o túnel do backend; acessar a loja pelo túnel do frontend durante todo o teste. O webhook deve receber POST sem uma tela de login do provedor do túnel. Encerrar os túneis ao terminar; suas URLs são temporárias e devem ser atualizadas no Developers a cada mudança.

Alternativa: backend/DB separados em Railway e frontend Preview na Vercel. Não foi criado túnel nem feito deploy. [Quick Tunnels oficiais](https://developers.cloudflare.com/tunnel/get-started/quick-tunnels/).

## Checklist de publicação Railway / Vercel

### Homologação antes da promoção

- [x] Scripts e estrutura de pedidos/cotações revisados; mecanismo Spring SQL init identificado.
- [x] PostgreSQL isolado, reaplicação, dados legados e constraints testados.
- [x] Cotação, aceite, total/validade, mutações e encaminhamento manual testados localmente.
- [x] Webhook assinado repetido sem duplicação testado localmente.
- [x] JAR iniciado duas vezes com SQL init real e credenciais sintéticas.
- [ ] Configuração exclusiva de contas/credencial/segredo Mercado Pago preenchida manualmente.
- [ ] URLs HTTPS de homologação configuradas e testadas.
- [ ] Compra no Checkout Pro hospedado aprovada em sandbox, com `live_mode=false`.
- [ ] Webhook externo entregue, autenticado e reconciliado ao pedido correto.

### Railway — ação manual autorizada após homologar

- [ ] Serviço e PostgreSQL de staging distintos da produção; dados sintéticos e variáveis separadas. O perfil `homologation` entregue é **local**, com host fixo 127.0.0.1; não usá-lo no Railway.
- [ ] No staging remoto, configurar DB_URL/DB_USERNAME/DB_PASSWORD do banco exclusivo, JWT_SECRET próprio, FRONTEND_URL e CORS_ALLOWED_ORIGINS do Preview, credenciais/URLs Mercado Pago de teste e `MERCADO_PAGO_CHECKOUT_SANDBOX=true`.
- [ ] Para a promoção posterior à produção, revisar backup, migração aditiva e pendências de cotações antigas sem digest; o projeto executa Spring SQL init, não Flyway. Não trocar automaticamente ddl-auto para validate antes de aplicar as migrações.
- [ ] Conferir porta do serviço/PORT e uma verificação HTTP que retorne 2xx; o projeto não ganhou um endpoint Actuator nesta entrega.
- [ ] `ALIEXPRESS_ORDERS_ENABLED=false`, `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false`, pagamento ao fornecedor manual, diagnóstico Mercado Pago desativado. Não há envio automático AliExpress.
- [ ] Confirmar credenciais e webhook de produção apenas na promoção explicitamente autorizada; não reutilizar configuração de teste para receber vendas reais.

### Vercel — ação manual autorizada após homologar

- [ ] Configurar `NEXT_PUBLIC_API_BASE_URL` no escopo **Preview**, apontando para backend staging; não para api.ingarage.com.br durante o teste.
- [ ] Conferir CORS da origem Preview e URLs de retorno/webhook da mesma homologação.
- [ ] Recompilar o frontend após alterar variável pública; testar cliente e administrador, inclusive total a confirmar, aceite, expiração e bloqueio de envio/reembolso.
- [ ] Promover para Production somente depois da evidência sandbox/webhook e da revisão conjunta das variáveis Railway/Vercel. Nenhum segredo deve usar prefixo NEXT_PUBLIC_.

Referências oficiais: [Railway variables](https://docs.railway.com/variables), [Railway healthchecks](https://docs.railway.com/deployments/healthchecks) e [Vercel environment variables](https://vercel.com/docs/environment-variables).
## Resultado final desta execução

A última execução Maven `verify` terminou com **30 testes, 0 falhas, 0 erros, 0 ignorados e BUILD SUCCESS**: 15 em PostgreSQL isolado, 7 do fluxo assistido, 7 de webhook e 1 de isolamento da configuração. O JAR foi gerado. Duas inicializações completas da aplicação retornaram HTTP 200 usando exclusivamente o PostgreSQL local.

Os processos temporários da aplicação e o cluster PostgreSQL foram encerrados. Os dados do cluster foram preservados em `backend/target/homologation-final/pgdata`. Para reutilizá-lo nesta máquina, executar a partir da raiz do projeto, conferindo a porta registrada em `backend/target/homologation-final/port.txt`:

```powershell
& 'C:/Program Files/PostgreSQL/18/bin/pg_ctl.exe' -D "$PWD/backend/target/homologation-final/pgdata" -l "$PWD/backend/target/homologation-final/postgres.log" -o '-h 127.0.0.1 -p 52639' -w start
```

Esse cluster usa autenticação trust exclusivamente local e dados sintéticos; não deve ser exposto à rede. Nenhuma cobrança, preferência Mercado Pago ou ordem AliExpress foi criada nesta homologação. O banco de produção e as credenciais existentes não foram alterados.

## Atualização para publicação controlada

A publicação do site sem pagamento usa o procedimento DEPLOY-CONTROLADO.md e o perfil controlled. Todos os novos pagamentos ficam bloqueados por padrão. Para retomar exclusivamente a homologação externa em ambiente LOCAL isolado, depois de validar a conta de teste pelo inicializador e configurar HTTPS/webhook, definir HML_PAYMENT_CREATION_ENABLED=true em homologation.local.ps1. Manter false no site de produção; o perfil controlled não habilita pagamentos. Isso não constitui autorização para executar transações automaticamente.
