# Remetente e marca dos e-mails da inGarage

## Configuração identificada

O remetente de todos os e-mails é lido pelo cliente compartilhado `ResendEmailClient`:

- `backend/src/main/resources/application.properties`: `app.account.email.from=${ACCOUNT_EMAIL_FROM:onboarding@resend.dev}`.
- `backend/src/main/java/com/garage/garageapi/shared/email/ResendEmailClient.java`:
  injeta `app.account.email.from` e envia o valor no campo `from` da requisição Resend.

Na consulta somente de leitura ao Railway, em 14/11/2026:

| Variável | Estado encontrado | Ação necessária |
| --- | --- | --- |
| `ACCOUNT_EMAIL_FROM` | Ausente; fallback `onboarding@resend.dev` | Definir `inGarage <nao-responda@ingarage.com.br>` |
| `ACCOUNT_EMAIL_MODE` | `resend` | Manter |
| `RESEND_API_KEY` | Preenchida; valor não exibido | Manter a credencial existente |
| `FRONTEND_URL` | `https://ingarage.com.br` | Manter |

`APP_ACCOUNT_EMAIL_FROM` e `APP_ACCOUNT_EMAIL_MODE` também estão ausentes.
Não é preciso criá-las: usar somente a variável já prevista, `ACCOUNT_EMAIL_FROM`.
Nome e endereço ficam juntos nesse único valor; não existe variável separada para o nome.
No painel Railway, preencher o valor sem aspas externas.
O exemplo local `backend/.env.example` já foi atualizado com o remetente solicitado.

## Alterações de código necessárias

O remetente não exige alteração de código. Os assuntos e templates têm a marca escrita
diretamente no Java, por isso foram ajustados somente estes dois arquivos de produção:

- `auth/email/ResendAccountEmailService.java`: assuntos, introduções e título HTML.
- `order/email/OrderEmailContent.java`: assuntos, título HTML e versão texto.

A marca passa de `GARAGE` para `inGarage`. Não foram modificados o cliente Resend,
rotas de links, geração/armazenamento/validação/expiração dos tokens ou gatilhos de envio.

Os seis e-mails já existentes usam o mesmo `ResendEmailClient` e receberão o mesmo remetente:

1. Confirmação de endereço de e-mail.
2. Recuperação/redefinição de senha.
3. Pagamento aprovado.
4. Pedido em preparação.
5. Pedido enviado.
6. Pedido entregue.

Nenhum fluxo novo foi criado. Notificações de pedido continuam nos eventos já existentes.
Checkout, pagamentos, AliExpress, frete e cadastro de produtos não foram alterados.

## Aplicação posterior no Railway

Nenhuma configuração de produção, commit ou deploy foi realizado nesta tarefa.
Projeto: `positive-cooperation` (`f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79`),
serviço `loja-automotiva`, ambiente `production`.

Após revisão, o comando abaixo pode preparar apenas o remetente, sem iniciar deploy:

```powershell
railway.cmd variable set 'ACCOUNT_EMAIL_FROM=inGarage <nao-responda@ingarage.com.br>' --project f3ad479c-d78a-41fb-b7bb-fb2d0ebddb79 --service loja-automotiva --environment production --skip-deploys
```

O processo em execução precisa receber a variável em uma publicação/reinicialização posterior.
Para que os assuntos e templates também usem `inGarage`, essa publicação deve incluir
os dois arquivos Java alterados. Não basta reiniciar a versão antiga para atualizar os textos.
Não há migração de banco nem mudança na Vercel para este ajuste.

## Testes

Resultado local: **31 testes passaram, zero falhas, erros ou testes ignorados**.

Foram executadas as suítes `AccountEmailModeTests`, `ResendAccountEmailServiceTests`,
`OrderEmailModeTests`, `ResendOrderEmailServiceTests`, `OrderEmailContentTests`,
`AccountSecurityIntegrationTests` e `AuthenticatedPasswordChangeIntegrationTests`.

As chamadas Resend são interceptadas por `MockRestServiceServer`; os testes de tokens
usam H2 e o serviço de e-mail de desenvolvimento. Não é enviado e-mail real.
Log local: `backend/target/email-branding-tests-20261114.log`.
