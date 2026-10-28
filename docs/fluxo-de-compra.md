# Fluxo de compra

```mermaid
sequenceDiagram
    autonumber
    actor C as Cliente (navegador)
    participant F as Frontend (Angular)
    participant A as API (Spring)
    participant DB as PostgreSQL
    participant P as Provedor (Mercado Pago ou simulador)

    C->>F: adiciona itens ao carrinho (localStorage: variante + quantidade)
    F->>A: POST /cart/quote
    A->>DB: lê preços e disponibilidade
    A-->>F: totais calculados no servidor
    C->>F: Finalizar compra
    F->>A: POST /checkout (dados do comprador + variantes/quantidades)
    rect rgb(235, 242, 255)
    note over A,DB: Transação 1 (curta)
    A->>DB: SELECT ... FOR UPDATE nas variantes (ordenadas por id)
    A->>DB: cria pedido, itens (fotografia), reserva estoque, cobrança PENDING
    end
    A->>P: cria checkout (X-Idempotency-Key) — sem transação aberta
    P-->>A: URL do checkout hospedado
    A-->>F: número, link de acompanhamento, URL de pagamento
    F->>P: redireciona o cliente
    C->>P: paga (dados do cartão só no provedor)
    P-)A: webhook (assinatura HMAC)
    A->>DB: registra evento (chave única: repetidos são descartados)
    A->>P: consulta a situação real do pagamento
    rect rgb(232, 248, 238)
    note over A,DB: Transação 2 (curta)
    A->>DB: trava pedido e pagamento
    A->>DB: confere loja, referência, valor e moeda
    A->>DB: reserva → baixa de estoque (uma única vez)
    A->>DB: lançamentos de receita e taxa (únicos por origem)
    end
    P-->>C: volta para /loja/{slug}/pedido/{token}
    F->>A: POST /orders/{token}/sync (confere de novo no provedor)
    A-->>F: pedido confirmado
```

## Caminhos alternativos

| Situação | Comportamento |
|---|---|
| Duas pessoas compram a última unidade ao mesmo tempo | A segunda transação espera a trava da primeira e então vê o saldo zerado: recebe "esgotado". |
| Cliente não paga no prazo | Job a cada minuto: consulta o provedor (caso o webhook tenha se perdido); sem pagamento, libera a reserva, pagamento = Expirado, pedido = Cancelado. |
| Pagamento aprovado após a expiração | Se ainda houver estoque: baixa e reativa o pedido com aviso. Se não houver: registra o pagamento, mantém o pedido cancelado e o marca "exige atenção" para reembolso. Nunca gera saldo negativo. |
| Webhook repetido | O evento já existe na tabela `webhook_events` → descartado. Mesmo que fosse processado, o estado final seria o mesmo. |
| Notificação antiga chega depois da aprovação | Ignorada: um pagamento aprovado nunca volta para pendente/recusado. |
| Valor ou moeda divergentes | Pedido não é confirmado; fica marcado "exige atenção". |
| Pagamento recusado | Pedido continua aberto, estoque reservado; o cliente pode tentar de novo pelo link. |
| Falha ao criar o checkout | O pedido é mantido; o cliente usa "Pagar agora" na página do pedido (mesma chave de idempotência). |
| Cancelamento de pedido pago (antes do envio) | Reembolso no provedor (fora de transação) → itens voltam ao estoque → pedido cancelado. |
| Devolução | Registrada; o estoque só é reposto quando o recebimento é confirmado e o item está em condições de revenda. |
