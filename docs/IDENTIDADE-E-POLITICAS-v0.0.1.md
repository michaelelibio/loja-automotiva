# Identidade e políticas da inGarage v0.0.1

## Estado

Implementação preparada localmente; **publicação comercial bloqueada enquanto faltarem dados confirmados e revisão das condições**. Não houve alteração de produção. As páginas explicam ausências em linguagem de atendimento, sem apresentar placeholders como dados reais, inventar prazos ou simular canais ativos.

Confirmado no projeto: marca inGarage, domínios ingarage.com.br e www.ingarage.com.br; checkout Mercado Pago; entrega grátis dos produtos habilitados; criação de pedido AliExpress após aprovação; coleta de CPF, nascimento e telefone do destinatário para esse atendimento. O remetente transacional `nao-responda@ingarage.com.br` documentado no projeto **não comprova um canal de atendimento monitorado**.

Propostas do responsável, ainda sem comprovação operacional: `contato@ingarage.com.br`, `privacidade@ingarage.com.br` e atendimento de segunda a sexta, das 9h às 18h. Esses valores não foram configurados como fatos públicos. É necessário ativar e testar recebimento, resposta, responsáveis e horário/fuso antes de confirmar. Nenhum CPF pessoal recebido foi copiado para o código, configuração, exemplo ou documento.

## Dados e decisões pendentes

| Campo | Confirmação necessária |
| --- | --- |
| `LEGAL_NAME` | Nome ou razão social de quem efetivamente vende e responde pelo tratamento de dados. Marca não substitui identificação do responsável. |
| `TAX_ID_TYPE`, `TAX_ID` | Tipo de inscrição fiscal aplicável e número aprovado expressamente para identificação pública; não reutilizar dados de cliente nem publicar CPF pessoal recebido em conversa sem autorização específica. |
| `BUSINESS_ADDRESS` | Endereço comercial completo e válido para divulgação pública. Não usar endereço de cliente ou inferir endereço de residência. |
| `SUPPORT_EMAIL` | Ativar/testar o canal proposto, confirmar monitoramento e capacidade de tratar pedidos, cancelamento, arrependimento e problemas. |
| `PRIVACY_EMAIL` | Ativar/testar o canal proposto, confirmar responsável pelo atendimento de titulares. Pode coincidir com atendimento somente se isso for confirmado. |
| `SUPPORT_PHONE` | Opcional; publicar somente um número real confirmado. Não há número configurado. |
| `SUPPORT_HOURS` | Confirmar dias, horário, fuso e tratamento de feriados. A faixa proposta ainda não foi apresentada como compromisso público. |
| `DELIVERY_TERMS` | Prazo real sustentado pela operação, marco inicial e dias úteis/corridos, abrangência e restrições. Explicar o atendimento via AliExpress, eventuais etapas internacionais e responsabilidades/cobranças sem contrariar a oferta de frete grátis. |
| `RETURN_INSTRUCTIONS` | Fluxo real de solicitação, confirmação de recebimento, responsável, endereço de retorno confirmado ou regra segura de fornecimento ao cliente, logística reversa e custos conforme o direito aplicável. Nunca presumir retorno direto ao fornecedor. |
| `REFUND_TERMS` | Procedimento comprovado de cancelamento/reembolso Mercado Pago, prazo de atuação da loja, distinção do processamento pelo meio de pagamento e direitos legais. Não inventar SLA nem condicionar direito a pagamento do fornecedor. |
| `PRIVACY_RETENTION` | Critérios/prazos de conservação por categoria e finalidade, fundamento aplicável, descarte e atendimento de direitos. Não declarar exclusão automática que não existe. |
| `PRIVACY_TRANSFERS` | Prestadores/destinatários, dados enviados, países ou critérios aplicáveis, responsabilidades e mecanismo de transferência internacional efetivamente utilizado e documentado. Não presumir adequação ou cláusula contratual que não foi verificada. |
| `POLICY_UPDATED_AT` | Data real de aprovação da versão, em `AAAA-MM-DD`. |
| `BUSINESS_CONFIRMED`, `POLICIES_CONFIRMED` | Somente `true` após conferência dos dados e aprovação da versão final pelo responsável, com revisão jurídica/operacional adequada. Um booleano não constitui prova de conformidade. |

As chaves completas usam o prefixo `INGARAGE_PUBLIC_`. A identificação publicada é também apresentada como responsável pelo tratamento; se vendedor e controlador não coincidirem, ajustar o modelo antes de confirmar a configuração.

## Implementação e publicação

`frontend/lib/store/business-profile.ts` centraliza leitura e validação. Valores vazios, exemplos, formatos inválidos e blocos não confirmados não são apresentados como fatos comerciais. E-mail é validado antes de gerar `mailto:`; telefone é opcional e validado antes de gerar `tel:`. Textos são renderizados pelo React, sem HTML arbitrário. A inscrição fiscal tem checagem de formato e dígitos verificadores, inclusive CNPJ alfanumérico; confirmar sua existência, regularidade e titularidade na fonte apropriada continua obrigatório.

`frontend/config/business-profile.env.example` contém apenas chaves vazias e flags `false`; não contém identidade, documento ou endereço de pessoa real. As variáveis não usam `NEXT_PUBLIC_`: a leitura ocorre nas páginas servidor, mas todo conteúdo aceito será público no HTML. Não incluir credenciais, dados de cliente ou informações privadas.

Antes de publicar:

1. Confirmar os dados acima em canal apropriado; guardar evidência com acesso restrito, fora do repositório público.
2. Preencher o ambiente frontend com os dados aprovados e os textos finais revisados. Não mudar flags para esconder pendências.
3. Executar `node scripts/check-business-profile.cjs` em `frontend`, no mesmo ambiente usado para o build. O comando retorna código 1 e somente nomes de campos se houver pendências; não imprime valores. Essa etapa é obrigatória no checklist de lançamento, não foi adicionada automaticamente ao build técnico.
4. Executar testes, tipos, lint e build coordenados pelo responsável da publicação. Inspecionar as cinco páginas no artefato final: identidade e canais coerentes, dados reais, prazos aprovados, compartilhamento AliExpress e nenhuma pendência pública.
5. Publicar somente após confirmação de completude e revisão final. As páginas são pré-renderizadas; alteração de variáveis exige novo build/deploy. Não houve deploy nesta tarefa.

## Fontes oficiais consultadas em 15/11/2026

- [Decreto 7.962/2013, arts. 2, 4 e 5 — Câmara dos Deputados](https://www2.camara.leg.br/legin/fed/decret/2013/decreto-7962-15-marco-2013-775557-publicacaooriginal-139266-pe.html): identificação e condições claras, atendimento eletrônico e exercício de arrependimento. A prontidão depende de processos reais, não apenas de texto publicado.
- [Código de Defesa do Consumidor, art. 49 — Câmara dos Deputados](https://www2.camara.leg.br/legin/fed/lei/1990/lei-8078-11-setembro-1990-365086-normaatualizada-pl.html): direito de arrependimento em contratação fora do estabelecimento e devolução de valores nos termos legais.
- [LGPD, especialmente arts. 9, 18, 33 e 41 — Câmara dos Deputados](https://www2.camara.leg.br/legin/fed/lei/2018/lei-13709-14-agosto-2018-787077-normaatualizada-pl.html): transparência, direitos, transferências internacionais e contato. A configuração não presume a existência de encarregado ou de hipótese de dispensa.
- [Manual de cálculo do dígito verificador CNPJ — Receita Federal](https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/documentos-tecnicos/cnpj/manual-dv-cnpj.pdf): validação do formato alfanumérico por módulo 11 e conversão ASCII menos 48. O exemplo técnico é usado apenas em teste automatizado, não como identificação da loja.

As páginas mantêm direitos legais e registram o funcionamento comprovado. A revisão não inventa prazo comercial, parecer jurídico, base legal específica para finalidade não analisada ou garantia de conformidade.
