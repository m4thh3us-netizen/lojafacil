package com.lojafacil.payments;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.lojafacil.payments.mercadopago.MercadoPagoSignature;
import com.lojafacil.payments.simulator.SimulatorSigner;
import com.lojafacil.shared.error.ApiException;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreService;

@Service
public class WebhookService {
    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    public enum Outcome { ACCEPTED, DUPLICATE, IGNORED }

    private final WebhookEvent.Repo events;
    private final StoreService stores;
    private final PaymentIntegrationService integrations;
    private final SimulatorSigner simulatorSigner;
    private final WebhookProcessor processor;

    public WebhookService(WebhookEvent.Repo events, StoreService stores, PaymentIntegrationService integrations,
                          SimulatorSigner simulatorSigner, WebhookProcessor processor) {
        this.events = events;
        this.stores = stores;
        this.integrations = integrations;
        this.simulatorSigner = simulatorSigner;
        this.processor = processor;
    }

    public Outcome receiveMercadoPago(String storeSlug, String xSignature, String xRequestId, String dataId,
                                      String type, String notificationId, String action) {
        Store store = stores.getBySlug(storeSlug);
        if (!"payment".equals(type) || dataId == null || dataId.isBlank()) {
            return Outcome.IGNORED;
        }
        String secret = integrations.resolve(store.getId(), ProviderType.MERCADO_PAGO).context().webhookSecret();
        if (!MercadoPagoSignature.isValid(xSignature, xRequestId, dataId, secret)) {
            log.warn("Webhook do Mercado Pago com assinatura inválida para a loja {}", store.getId());
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Assinatura inválida.");
        }
        String key = "payment:" + dataId + ":" + (notificationId != null ? notificationId : xRequestId) + ":" + action;
        return register(ProviderType.MERCADO_PAGO, key, dataId, store.getId());
    }

    public Outcome receiveSimulator(String payload, String signature) {
        if (signature == null || !simulatorSigner.verify(payload, signature)) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_SIGNATURE", "Assinatura inválida.");
        }
        Map<String, String> fields = parse(payload);
        Long storeId = Long.valueOf(fields.get("storeId"));
        return register(ProviderType.SIMULATOR, "charge:" + fields.get("chargeId") + ":" + fields.get("eventId"),
                fields.get("chargeId"), storeId);
    }

    public String signSimulatorPayload(String payload) {
        return simulatorSigner.sign(payload);
    }

    private Outcome register(ProviderType provider, String key, String resourceId, Long storeId) {
        if (!insertEvent(provider, key, resourceId, storeId)) {
            return Outcome.DUPLICATE;
        }
        processor.process(provider, key, resourceId, storeId);
        return Outcome.ACCEPTED;
    }

    private boolean insertEvent(ProviderType provider, String key, String resourceId, Long storeId) {
        return events.insertIfAbsent(provider.name(), key, resourceId, storeId) == 1;
    }

    private static Map<String, String> parse(String payload) {
        Map<String, String> map = new LinkedHashMap<>();
        for (String part : payload.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2) {
                map.put(kv[0], kv[1]);
            }
        }
        if (!map.containsKey("storeId") || !map.containsKey("chargeId") || !map.containsKey("eventId")) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_PAYLOAD", "Notificação inválida.");
        }
        return map;
    }

    @Service
    public static class WebhookProcessor {
        private final PaymentService payments;
        private final WebhookEvent.Repo events;

        public WebhookProcessor(PaymentService payments, WebhookEvent.Repo events) {
            this.payments = payments;
            this.events = events;
        }

        @Async
        public void process(ProviderType provider, String key, String resourceId, Long storeId) {
            processNow(provider, key, resourceId, storeId);
        }

        public String processNow(ProviderType provider, String key, String resourceId, Long storeId) {
            String result;
            try {
                result = payments.processNotification(storeId, provider, resourceId);
            } catch (RuntimeException e) {
                log.warn("Falha ao processar notificação {}: {}", key, e.getClass().getSimpleName());
                result = "ERROR: " + e.getClass().getSimpleName();
            }
            events.markProcessed(provider.name(), key, result, Instant.now());
            return result;
        }
    }
}
