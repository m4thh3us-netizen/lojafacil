package com.lojafacil.payments;

import java.math.BigDecimal;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.payments.PaymentIntegrationService.ConfigView;
import com.lojafacil.payments.PaymentIntegrationService.SaveRequest;
import com.lojafacil.payments.PaymentProvider.ConnectionTest;
import com.lojafacil.payments.simulator.SimulatedPaymentProvider;
import com.lojafacil.payments.simulator.SimulatorCharge;
import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.store.StoreRepository;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Pagamentos")
public class PaymentController {

    public record SimulatedChargeView(UUID id, String storeName, String description, BigDecimal amount,
                                      String status, String returnUrl, boolean simulated) {
    }

    private final PaymentIntegrationService integrations;
    private final WebhookService webhooks;
    private final SimulatedPaymentProvider simulator;
    private final StoreRepository stores;
    private final CurrentUser currentUser;

    public PaymentController(PaymentIntegrationService integrations, WebhookService webhooks,
                             SimulatedPaymentProvider simulator, StoreRepository stores, CurrentUser currentUser) {
        this.integrations = integrations;
        this.webhooks = webhooks;
        this.simulator = simulator;
        this.stores = stores;
        this.currentUser = currentUser;
    }

    // ------------------------------------------------------------------ configuração (apenas Proprietário)

    @GetMapping("/api/admin/payments/integration")
    @PreAuthorize(Access.OWNER)
    public ConfigView config() {
        return integrations.config(currentUser.storeId());
    }

    @PutMapping("/api/admin/payments/integration")
    @PreAuthorize(Access.OWNER)
    public ConfigView save(@Valid @RequestBody SaveRequest req) {
        return integrations.save(currentUser.storeId(), req);
    }

    @PostMapping("/api/admin/payments/integration/test")
    @PreAuthorize(Access.OWNER)
    public ConnectionTest test() {
        return integrations.test(currentUser.storeId());
    }

    // ------------------------------------------------------------------ webhooks

    @Operation(summary = "Webhook do Mercado Pago (assinatura x-signature validada)")
    @PostMapping("/api/webhooks/mercadopago/{storeSlug}")
    public ResponseEntity<Void> mercadoPago(@PathVariable String storeSlug,
                                            @RequestHeader(value = "x-signature", required = false) String signature,
                                            @RequestHeader(value = "x-request-id", required = false) String requestId,
                                            @RequestParam(value = "data.id", required = false) String dataId,
                                            @RequestParam(value = "type", required = false) String type,
                                            @RequestBody(required = false) java.util.Map<String, Object> body) {
        String bodyType = body != null && body.get("type") != null ? String.valueOf(body.get("type")) : type;
        String notificationId = body != null && body.get("id") != null ? String.valueOf(body.get("id")) : null;
        String action = body != null && body.get("action") != null ? String.valueOf(body.get("action")) : "";
        webhooks.receiveMercadoPago(storeSlug, signature, requestId, dataId, bodyType, notificationId, action);
        return ResponseEntity.ok().build();
    }

    @Operation(summary = "Webhook do simulador local (assinatura HMAC)")
    @PostMapping("/api/webhooks/simulator")
    public ResponseEntity<String> simulatorWebhook(@RequestHeader(value = "X-Simulator-Signature", required = false) String signature,
                                                   @RequestBody String payload) {
        return ResponseEntity.ok(webhooks.receiveSimulator(payload, signature).name());
    }

    // ------------------------------------------------------------------ página de pagamento simulado (pública)

    @GetMapping("/api/public/simulator/charges/{id}")
    public SimulatedChargeView simulatedCharge(@PathVariable UUID id) {
        SimulatorCharge c = simulator.get(id);
        return view(c);
    }

    /**
     * O "cliente" aprova ou recusa na página simulada. O simulador então envia uma notificação assinada,
     * que percorre exatamente o mesmo caminho de um webhook real (validação, deduplicação, consulta).
     */
    @PostMapping("/api/public/simulator/charges/{id}/{decision:approve|decline}")
    @ResponseStatus(HttpStatus.OK)
    public SimulatedChargeView decide(@PathVariable UUID id, @PathVariable String decision) {
        SimulatorCharge c = simulator.decide(id, "approve".equals(decision));
        String payload = "eventId=" + UUID.randomUUID() + "&chargeId=" + c.getId() + "&storeId=" + c.getStoreId();
        webhooks.receiveSimulator(payload, webhooks.signSimulatorPayload(payload));
        return view(c);
    }

    private SimulatedChargeView view(SimulatorCharge c) {
        String storeName = stores.findById(c.getStoreId()).map(s -> s.getName()).orElse("");
        return new SimulatedChargeView(c.getId(), storeName, c.getDescription(), c.getAmount(), c.getStatus().name(),
                c.getReturnUrl(), true);
    }
}
