package com.lojafacil.payments.mercadopago;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.lojafacil.orders.PaymentStatus;
import com.lojafacil.payments.PaymentProvider;
import com.lojafacil.payments.ProviderType;
import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.money.Money;

@Component
public class MercadoPagoPaymentProvider implements PaymentProvider {
    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentProvider.class);
    private static final DateTimeFormatter MP_DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX");

    private final RestClient http;

    public MercadoPagoPaymentProvider(AppProperties props) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(5));
        factory.setReadTimeout(Duration.ofSeconds(15));
        this.http = RestClient.builder()
                .baseUrl(props.payments().mercadoPagoApiBase())
                .requestFactory(factory)
                .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
                .build();
    }

    @Override
    public ProviderType type() {
        return ProviderType.MERCADO_PAGO;
    }

    @Override
    public boolean simulated() {
        return false;
    }

    @Override
    @SuppressWarnings("unchecked")
    public CheckoutSession createCheckout(ProviderContext ctx, CheckoutRequest req) {
        Map<String, Object> body = new LinkedHashMap<>();

        body.put("items", List.of(Map.of(
                "id", req.externalReference().toString(),
                "title", req.description(),
                "quantity", 1,
                "currency_id", "BRL",
                "unit_price", req.amount())));
        body.put("payer", Map.of("name", req.payerName(), "email", req.payerEmail()));
        body.put("external_reference", req.externalReference().toString());
        body.put("back_urls", Map.of("success", req.returnUrl(), "pending", req.returnUrl(), "failure", req.returnUrl()));
        if (req.returnUrl().startsWith("https://")) {
            body.put("auto_return", "approved");
        }
        if (req.notificationUrl() != null && req.notificationUrl().startsWith("https://")) {
            body.put("notification_url", req.notificationUrl());
        }
        if (req.expiresAt() != null) {
            body.put("expires", true);
            body.put("expiration_date_to", MP_DATE.format(req.expiresAt().atOffset(ZoneOffset.ofHours(-3))));
        }
        Map<String, Object> res = call(() -> http.post().uri("/checkout/preferences")
                .header("Authorization", "Bearer " + ctx.accessToken())
                .header("X-Idempotency-Key", req.idempotencyKey().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve().body(Map.class), "criar a cobrança");
        String url = (String) (ctx.accessToken().startsWith("TEST-") && res.get("sandbox_init_point") != null
                ? res.get("sandbox_init_point") : res.get("init_point"));
        return new CheckoutSession(String.valueOf(res.get("id")), url);
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ProviderPayment> findPayment(ProviderContext ctx, String providerPaymentId) {
        if (!providerPaymentId.matches("^[0-9]{1,30}$")) {
            return Optional.empty();
        }
        try {
            Map<String, Object> res = http.get().uri("/v1/payments/{id}", providerPaymentId)
                    .header("Authorization", "Bearer " + ctx.accessToken())
                    .retrieve().body(Map.class);
            return Optional.ofNullable(res).map(MercadoPagoPaymentProvider::toProviderPayment);
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 404) {
                return Optional.empty();
            }
            throw failure("consultar o pagamento", e.getStatusCode());
        } catch (RestClientException e) {
            throw new ProviderException("Não foi possível consultar o Mercado Pago. Tente novamente.");
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public Optional<ProviderPayment> findByExternalReference(ProviderContext ctx, UUID externalReference) {
        Map<String, Object> res = call(() -> http.get()
                .uri(u -> u.path("/v1/payments/search")
                        .queryParam("external_reference", externalReference.toString())
                        .queryParam("sort", "date_created")
                        .queryParam("criteria", "desc")
                        .build())
                .header("Authorization", "Bearer " + ctx.accessToken())
                .retrieve().body(Map.class), "consultar pagamentos");
        List<Map<String, Object>> results = (List<Map<String, Object>>) res.getOrDefault("results", List.of());
        List<ProviderPayment> payments = results.stream().map(MercadoPagoPaymentProvider::toProviderPayment).toList();
        return payments.stream().filter(p -> p.status() == PaymentStatus.APPROVED
                        || p.status() == PaymentStatus.PARTIALLY_REFUNDED || p.status() == PaymentStatus.REFUNDED)
                .findFirst()
                .or(() -> payments.stream().findFirst());
    }

    @Override
    @SuppressWarnings("unchecked")
    public ProviderRefund refund(ProviderContext ctx, String providerPaymentId, BigDecimal amount, UUID idempotencyKey) {
        Map<String, Object> res = call(() -> http.post().uri("/v1/payments/{id}/refunds", providerPaymentId)
                .header("Authorization", "Bearer " + ctx.accessToken())
                .header("X-Idempotency-Key", idempotencyKey.toString())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of("amount", amount))
                .retrieve().body(Map.class), "reembolsar");
        return new ProviderRefund(String.valueOf(res.get("id")), amount);
    }

    @Override
    @SuppressWarnings("unchecked")
    public ConnectionTest testConnection(ProviderContext ctx) {
        try {
            Map<String, Object> me = http.get().uri("/users/me")
                    .header("Authorization", "Bearer " + ctx.accessToken())
                    .retrieve().body(Map.class);
            Object site = me == null ? null : me.get("site_id");
            if (site != null && !"MLB".equals(site)) {
                return new ConnectionTest(false, "A conta não é do Mercado Pago Brasil (site " + site + ").");
            }
            return new ConnectionTest(true, "Conexão bem-sucedida com a conta " + (me != null ? me.get("nickname") : "") + ".");
        } catch (RestClientResponseException e) {
            return new ConnectionTest(false, e.getStatusCode().value() == 401
                    ? "Credencial inválida ou expirada." : "O Mercado Pago respondeu com erro " + e.getStatusCode().value() + ".");
        } catch (RestClientException e) {
            return new ConnectionTest(false, "Não foi possível conectar ao Mercado Pago.");
        }
    }

    @SuppressWarnings("unchecked")
    static ProviderPayment toProviderPayment(Map<String, Object> p) {
        String raw = String.valueOf(p.get("status"));
        BigDecimal amount = decimal(p.get("transaction_amount"));
        BigDecimal refunded = decimal(p.get("transaction_amount_refunded"));
        BigDecimal fee = Money.ZERO;
        Object feeDetails = p.get("fee_details");
        if (feeDetails instanceof List<?> list) {
            for (Object f : list) {
                if (f instanceof Map<?, ?> m) {
                    fee = fee.add(decimal(m.get("amount")));
                }
            }
        }
        PaymentStatus status = switch (raw) {
            case "approved" -> refunded.signum() > 0 ? PaymentStatus.PARTIALLY_REFUNDED : PaymentStatus.APPROVED;
            case "refunded", "charged_back" -> PaymentStatus.REFUNDED;
            case "rejected" -> PaymentStatus.DECLINED;
            case "cancelled" -> PaymentStatus.EXPIRED;
            default -> PaymentStatus.PENDING;
        };
        Object live = p.get("live_mode");
        return new ProviderPayment(String.valueOf(p.get("id")), (String) p.get("external_reference"), status, raw,
                amount, String.valueOf(p.getOrDefault("currency_id", "BRL")),
                "refunded".equals(raw) ? amount : refunded, Money.normalize(fee),
                (String) p.get("payment_type_id"), Boolean.TRUE.equals(live));
    }

    private static BigDecimal decimal(Object v) {
        if (v == null) {
            return Money.ZERO;
        }
        return Money.normalize(new BigDecimal(v.toString()));
    }

    private <T> T call(java.util.function.Supplier<T> request, String action) {
        try {
            T result = request.get();
            if (result == null) {
                throw new ProviderException("Resposta vazia do Mercado Pago ao " + action + ".");
            }
            return result;
        } catch (RestClientResponseException e) {
            throw failure(action, e.getStatusCode());
        } catch (RestClientException e) {
            log.warn("Falha de comunicação com o Mercado Pago ao {}", action);
            throw new ProviderException("Não foi possível comunicar com o Mercado Pago para " + action + ". Tente novamente.");
        }
    }

    private static ProviderException failure(String action, HttpStatusCode status) {
        log.warn("Mercado Pago respondeu HTTP {} ao {}", status.value(), action);
        return new ProviderException(status.value() == 401
                ? "Credenciais do Mercado Pago inválidas."
                : "O Mercado Pago recusou a operação (" + action + ", HTTP " + status.value() + ").");
    }
}
