# Ativação do Mercado Pago Checkout Pro — 14/11/2026

Preparação concluída localmente. Nenhuma variável de produção foi alterada, nenhum deploy,
commit ou push foi executado nesta tarefa e nenhuma preferência/cobrança foi criada no provedor.
Não é necessário alterar código de produção nem migrar o banco para esta ativação.

## Causa confirmada

Configuração consultada em modo somente leitura no Railway e na Vercel:

| Local | Configuração | Valor atual | Valor para ativar |
| --- | --- | --- | --- |
| Vercel, Production | `NEXT_PUBLIC_PAYMENT_CREATION_ENABLED` | `false` | `true` |
| Railway, production, loja-automotiva | `APP_PAYMENT_CREATION_ENABLED` | `false` | `true` |
| Railway, production, loja-automotiva | `PAYMENT_CREATION_ENABLED` | `false` | `true` |

`frontend/components/MercadoPagoPayment.tsx:10` oculta a ação e mostra
“Pagamento indisponível” se a flag pública não for exatamente `true`.
O valor é incorporado ao JavaScript durante o build; mudar a variável sem reconstruir
o frontend não atualiza o botão.

`backend/src/main/resources/application.properties:29` resolve
`app.payment.creation-enabled` por `${PAYMENT_CREATION_ENABLED:false}`.
Entretanto, o perfil ativo `controlled` contém
`app.payment.creation-enabled=false` em `application-controlled.properties:5`.
Por isso, mudar somente `PAYMENT_CREATION_ENABLED` não libera pagamentos neste perfil.

A variável de ambiente `APP_PAYMENT_CREATION_ENABLED` sobrepõe diretamente
`app.payment.creation-enabled`, inclusive o valor do perfil. Ela é a configuração decisiva
no backend atual. Manter as duas variáveis em `true` evita valores contraditórios.
Não é preciso remover `controlled` nem alterar seu padrão seguro.

`PaymentAttemptService` injeta essa propriedade e verifica-a nos dois pontos de entrada:
`prepare` e `createCheckoutPreference`. Enquanto estiver falsa, retorna conflito HTTP 409
antes de criar a preferência.

## Integração existente preservada

- A API aceita somente `method=MERCADO_PAGO`; o contrato legado `PIX` continua rejeitado.
- O gateway existente usa `POST /checkout/preferences` e, com sandbox desativado, retorna
  `init_point`. O frontend redireciona para `payment.checkoutUrl` depois do clique.
- O payload não exclui Pix ou cartões. Os meios efetivamente apresentados dependem da
  configuração e disponibilidade da conta no Mercado Pago; não foi aberta uma sessão real.
- Permanecem autenticação, propriedade do pedido, expiração, total calculado no servidor,
  validações de entrega, locks e reutilização da preferência existente.
- Permanecem assinatura do webhook, consulta autenticada do pagamento e conferência de
  referência, moeda, valor e transições de status. A URL de retorno não confirma pagamento.
- Não modificar frete, checkout, AliExpress, seeds, simulações nem pagamentos ao fornecedor.

O Railway tem access token e webhook secret preenchidos. O token tem prefixo `APP_USR-`,
não `TEST-`; isso não comprova sozinho sua validade ou o modo da conta.
Não foi feita chamada ao Mercado Pago para validar credenciais ou criar preferência.
`MERCADO_PAGO_CHECKOUT_SANDBOX` não está definido, logo o padrão existente é `false`.

As três URLs de retorno atuais, sob `https://loja-automotiva-phi.vercel.app/pagamento/`,
responderam HTTP 200 por HEAD. Foram preservadas.
`NEXT_PUBLIC_API_BASE_URL` em Production já aponta para `https://api.ingarage.com.br`.

`MERCADO_PAGO_CHECKOUT_NOTIFICATION_URL` não está definido no Railway. Isso não desabilita
o botão: nesse caso, o gateway não inclui `notification_url` na preferência e a entrega
de notificações depende da configuração já existente na aplicação do Mercado Pago.
O endpoint implementado é `https://api.ingarage.com.br/api/webhooks/mercadopago`.
Se ele já está configurado no painel do provedor com o secret correspondente, mantê-lo.
Para explicitá-lo por preferência, a variável existente pode receber esse mesmo endereço;
isso não exige mudança no código nem retirada da validação de assinatura.

## Publicação manual preparada

Os comandos abaixo são um roteiro para execução posterior, não foram executados.
Eles alteram produção. Usar a conta Railway/Vercel que já administra a inGarage.

1. No Railway, projeto `positive-cooperation` (`f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79`),
   ambiente `production`, serviço `loja-automotiva`, preparar somente as duas flags.
   `--skip-deploys` evita publicação automática durante a edição:

   ```powershell
   railway.cmd variable set APP_PAYMENT_CREATION_ENABLED=true PAYMENT_CREATION_ENABLED=true --project f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79 --service loja-automotiva --environment production --skip-deploys
   ```

2. Revisar a alteração no Railway. Preservar `SPRING_PROFILES_ACTIVE=controlled`,
   `SPRING_JPA_HIBERNATE_DDL_AUTO=validate`, `SPRING_SQL_INIT_MODE=never`,
   `ALIEXPRESS_ORDERS_ENABLED=false`, `ALIEXPRESS_ORDERS_CONTRACT_CONFIRMED=false`
   e `DSERS_SIMULATION_ENABLED=false`. As flags globais de sincronização AliExpress
   continuam falsas por padrão/perfil. Não alterar a exceção administrativa já existente
   do pedido #19. Nenhuma migração é necessária para mudar flags.

3. Aplicar as variáveis reiniciando/publicando a mesma versão do backend, após a revisão:

   ```powershell
   railway.cmd redeploy --project f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79 --service loja-automotiva --environment production
   ```

   Não usar `--from-source`: esta tarefa não exige buscar outra branch ou versão.
   Aguardar SUCCESS/serviço saudável e conferir as duas flags no ambiente do processo.
   Não chamar `POST /api/orders/{id}/payments` como smoke test: essa chamada cria preferência.

4. Na Vercel, projeto `loja-automotiva` (`prj_TfjMGqBEd8TsQc9TEbTVKHIyOgKu`),
   scope `michael-t1`, atualizar a variável pública somente em **Production**.
   A entrada existente foi conferida e pertence somente a Production:

   ```powershell
   vercel.cmd env update NEXT_PUBLIC_PAYMENT_CREATION_ENABLED --environment production --project prj_TfjMGqBEd8TsQc9TEbTVKHIyOgKu --scope michael-t1 --value true
   ```

5. Fazer um novo build e deploy de Production da versão atualmente aprovada.
   Na data desta preparação, o deployment aprovado é `dpl_89jussnyWAi99aD3cohUtndEFKZF`,
   commit `f66d65e04fea76d29da936b2133bf6152c36b7d1`:

   ```powershell
   vercel.cmd redeploy dpl_89jussnyWAi99aD3cohUtndEFKZF --environment production --scope michael-t1
   ```

   Se outra versão tiver sido publicada depois deste roteiro, conferir o deployment aprovado
   antes de escolher qual reconstruir. Não promover um build antigo com a flag `false`.
   A branch de produção configurada no vínculo Git é `main`; esta ativação não exige
   publicar código de outra branch nem mudar esse vínculo.

6. Após READY e associação ao domínio `www.ingarage.com.br`, recarregar a tela do pedido
   do cliente com Ctrl+Shift+R. Conferir visualmente “Ir para pagamento”, sem clicar.
   O fluxo é `https://www.ingarage.com.br/checkout` → pedido válido → botão Mercado Pago.
   O botão continua sujeito às validações existentes do pedido, inclusive entrega e prazo.
   Não criar pedido/cobrança de teste nem executar `placeorder` nesta validação.

## Reversão

Voltar as duas flags do Railway para `false`, inicialmente com `--skip-deploys`,
e republicar/reiniciar o mesmo backend. Voltar a flag Production da Vercel para `false`
e reconstruir o frontend. O bloqueio no backend impede novas preferências mesmo que
um navegador mantenha o JavaScript anterior em cache. Manter o webhook ativo para
processar pagamentos já iniciados; não apagar registros nem preferências existentes.

## Verificação local concluída

- Backend: **28 testes, zero falhas**, incluindo autenticação/propriedade, concorrência,
  reutilização de preferência, webhook/assinatura, validação financeira e evidência de modo.
- Quatro testes novos de configuração exercitam a injeção `@Value` real com o arquivo
  `application-controlled.properties`: padrão falso, placeholder isolado insuficiente,
  override explícito verdadeiro e precedência da variável canônica. Também verificam que
  os demais bloqueios do perfil permanecem falsos e o schema continua em validate/never.
- Frontend: **73 testes, zero falhas**; verificação isolada com flag `true` renderizou botão
  habilitado e redirecionou uma vez após clique com API mockada. Renderizar não criou pagamento.
- `next build` com `NEXT_PUBLIC_PAYMENT_CREATION_ENABLED=true` e API de produção: sucesso,
  incluindo compilação e TypeScript. Não houve publicação desse build.
- Testes usam H2 e mocks; não fazem chamadas ao Mercado Pago, Railway ou Vercel para cobrar.

Logs locais ignorados pelo Git:
`backend/target/mercadopago-activation-tests-20261114.log`,
`backend/target/mercadopago-activation-frontend-tests-20261114.log` e
`backend/target/mercadopago-activation-frontend-build-20261114.log`.

## Referências

- [Spring Boot — precedência de configuração e variáveis de ambiente](https://docs.spring.io/spring-boot/reference/features/external-config.html)
- [Next.js — variáveis públicas incorporadas no build](https://nextjs.org/docs/app/guides/environment-variables)
- [Mercado Pago — Checkout Pro via Preferences API](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro-preferences/overview)
