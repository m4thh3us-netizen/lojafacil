# Decisões técnicas — explicadas de forma simples

Perguntas comuns em entrevistas e como este projeto responde a elas.

### Por que um monólito modular e não microserviços?
Uma pequena loja tem pouco volume e uma equipe pequena. Microserviços trariam rede entre serviços,
transações distribuídas e infraestrutura extra sem benefício real. O monólito modular mantém
**um deploy e um banco**, mas com pacotes por assunto (`orders`, `payments`, `finance`...) — se um
dia for preciso separar, as fronteiras já existem.

### Por que sessão com cookie em vez de JWT no localStorage?
O cookie `HttpOnly` não pode ser lido por JavaScript, então um eventual XSS não consegue roubar a
sessão. Logout e expiração são imediatos (a sessão some do servidor). O custo é precisar de proteção
CSRF, feita com o padrão de token em cookie + cabeçalho, que o Angular já suporta.

### Como o sistema garante que lojas não vejam dados umas das outras?
O `storeId` nunca vem do navegador: vem do usuário guardado na sessão (`CurrentUser`). Toda consulta
filtra por ele (`findByIdAndStoreId`). Se alguém tentar o id de um pedido de outra loja, recebe 404 —
nem descobre que o registro existe. Há testes automatizados para isso.

### Como evitar vender a última unidade duas vezes?
Com **travamento pessimista** (`SELECT ... FOR UPDATE`): a segunda compra espera a primeira terminar e
então vê o saldo atualizado. As linhas são travadas sempre na mesma ordem (por id) para evitar deadlock.
Como rede de segurança, o banco tem `CHECK (physical_qty >= 0)`. O teste `comprasSimultaneasDaUltimaUnidade`
dispara 8 compras ao mesmo tempo e verifica que só 1 é aceita.

### Por que reservar estoque antes do pagamento?
Entre o checkout e a confirmação do pagamento há alguns minutos. Sem reserva, dois clientes poderiam
pagar pela mesma unidade. A reserva tem prazo (configurável por loja); vencido o prazo, um job libera o estoque.

### Por que não manter a transação aberta enquanto chama o Mercado Pago?
Uma chamada de rede pode levar segundos. Com a transação aberta, as linhas de estoque e pedido ficariam
travadas esse tempo todo, bloqueando outras compras. O fluxo é dividido: transação curta → chamada ao
provedor → transação curta (ver `PaymentService`, que usa `TransactionTemplate` para deixar isso explícito).

### O que é idempotência e onde ela aparece?
É poder repetir uma operação sem efeito duplicado. Aparece em quatro lugares:
1. **Chave de idempotência** enviada ao Mercado Pago na criação da cobrança e no reembolso.
2. **Webhooks** gravados com chave única: repetições são descartadas.
3. **Baixa de estoque** controlada pela flag `stock_committed` do pedido.
4. **Lançamentos financeiros** com índice único `(store_id, source_type, source_ref)`.

### Por que não confiar no redirecionamento do checkout?
Qualquer pessoa pode abrir a URL de retorno com `?status=approved`. Por isso o backend sempre
**pergunta ao provedor** qual é a situação real antes de confirmar.

### Por que BigDecimal?
`double` não representa 0,10 exatamente (0,1 + 0,2 = 0,30000000000000004). Em dinheiro isso vira
centavos sumindo. `BigDecimal` com escala 2 e regra de arredondamento definida resolve.

### Por que estorno em vez de editar lançamentos?
Num controle financeiro, apagar ou editar um valor já realizado destrói o histórico. O estorno cria um
lançamento oposto ligado ao original: o saldo fica correto e qualquer pessoa entende o que aconteceu.

### Por que três estados separados no pedido?
"Pago", "enviado" e "cancelado" respondem a perguntas diferentes. Misturar tudo num único status gera
combinações impossíveis de representar (ex.: "entregue mas reembolsado parcialmente"). A classe
`OrderStateMachine` lista as transições válidas e impede as incoerentes.

### Por que Testcontainers com PostgreSQL real nos testes?
Os pontos críticos (travas de linha, `CHECK`, índices únicos, `ON CONFLICT`) dependem do banco.
Um banco em memória (H2) se comporta diferente e esconderia exatamente os bugs que importam — inclusive
um que os testes encontraram: dois checkouts simultâneos do mesmo cliente novo colidiam no índice de
e-mail. A correção usa `INSERT ... ON CONFLICT DO NOTHING`.

### Por que o frontend usa signals e componentes standalone?
É o modelo atual do Angular: menos código, detecção de mudanças mais previsível (o app roda sem
zone.js) e cada tela é carregada sob demanda, deixando o carregamento inicial pequeno.

### Como manter a leitura com vidro fosco sobre qualquer fotografia?
A transparência é uma variável (`--panel-alpha`) com limites validados no servidor (55–95%).
Elementos de leitura densa — tabelas, formulários, menus, modais — usam `max(alpha, 0,93)`, quase
sólidos. Sobre a foto há uma camada escura **uniforme** (não um gradiente), ajustável. Textos nunca
ficam soltos sobre a fotografia: cabeçalhos, filtros, abas e paginação também são painéis.
Sem suporte a `backdrop-filter`, ou com `prefers-reduced-transparency`, tudo vira superfície sólida;
no celular o raio do desfoque cai de 18 px para 8 px para poupar GPU.

### Por que a pré-visualização não grava nada?
O upload devolve apenas o caminho da foto otimizada; a loja só muda quando o lojista salva. Até lá,
o painel aplica um "rascunho" local (um signal), então dá para testar fotos e ajustes sem afetar a
equipe. Ao salvar, o servidor confere que o caminho pertence à própria loja.

### Como o frontend lida com carregamento e envios duplicados?
Skeletons do tamanho do conteúdo final (sem "pulos" de layout), botões que mudam para "Salvando..." e
ficam desabilitados durante a ação, e notificações de sucesso ou erro ao final. Na busca, `debounceTime`
espera o usuário parar de digitar e `switchMap` cancela a requisição anterior.
