# Pagamentos de teste

## Simulador local (padrão)

Sem nenhuma configuração, todas as lojas usam o **simulador**. Ele se comporta como um gateway
externo: tem estado próprio (`simulator_charges`), uma página de pagamento
(`/pagamento-simulado/{id}`) e envia notificações assinadas com HMAC, que passam pelo mesmo
caminho dos webhooks reais (validação → deduplicação → consulta → aplicação).

Pagamentos simulados aparecem com o selo **SIMULADO** no painel, na vitrine e no financeiro
(lançamentos com "[simulado]"). A taxa simulada é de 3,99%.

No ambiente demonstrativo (`APP_DEMO_MODE=true`) o simulador é obrigatório e credenciais reais
são recusadas.

## Mercado Pago (Checkout Pro) em ambiente de teste

1. Defina `APP_DEMO_MODE=false` e uma chave de criptografia em `APP_ENCRYPTION_KEY`
   (`openssl rand -base64 32`). Mantenha `APP_ALLOW_LIVE_PAYMENTS=false`.
2. No [painel de desenvolvedores do Mercado Pago](https://www.mercadopago.com.br/developers/panel/app),
   crie uma aplicação do tipo *Checkout Pro* e **contas de teste** (vendedor e comprador).
3. Copie o **access token de teste** da conta vendedora de teste (`APP_USR-...` ou `TEST-...`).
4. Para receber webhooks, a API precisa de uma URL **HTTPS pública**. Em desenvolvimento, use um túnel:
   ```bash
   ngrok http 8080
   ```
   e defina `APP_PUBLIC_API_URL` com a URL gerada.
5. Em *Suas integrações > Webhooks*, cadastre a URL exibida no painel da LojaFácil
   (`{APP_PUBLIC_API_URL}/api/webhooks/mercadopago/{identificador-da-loja}`), evento **Pagamentos**,
   e copie a **assinatura secreta**.
6. Na LojaFácil, entre como **Proprietário** → Configurações → Pagamentos → Mercado Pago:
   informe access token, public key e assinatura secreta → **Salvar** → **Testar conexão**.
7. Faça uma compra na vitrine e pague com o usuário comprador de teste e os
   [cartões de teste](https://www.mercadopago.com.br/developers/pt/docs/checkout-pro/additional-content/your-integrations/test/cards)
   da documentação oficial.

### Como a integração funciona

| Operação | Endpoint do Mercado Pago |
|---|---|
| Criar cobrança | `POST /checkout/preferences` com `X-Idempotency-Key`, `external_reference` = id público do pagamento, `notification_url`, `back_urls` e expiração igual ao prazo da reserva |
| Consultar pagamento (webhook) | `GET /v1/payments/{id}` |
| Conciliar pela referência | `GET /v1/payments/search?external_reference=...` |
| Reembolsar | `POST /v1/payments/{id}/refunds` com `X-Idempotency-Key` |
| Testar credenciais | `GET /users/me` |

Validação do webhook: cabeçalho `x-signature` (`ts=...,v1=...`) comparado com
HMAC-SHA256 de `id:{data.id};request-id:{x-request-id};ts:{ts};` usando a assinatura secreta
(`MercadoPagoSignature.java`).

Mesmo sem webhook (ex.: localhost sem túnel), o pedido é confirmado quando o cliente volta à página do
pedido (o backend consulta o provedor) ou pela conciliação automática a cada 5 minutos.

> Confira sempre a documentação oficial vigente: endpoints, formato de assinatura e o uso de
> `init_point`/`sandbox_init_point` podem mudar. O código isola esses detalhes em
> `payments/mercadopago/`.
