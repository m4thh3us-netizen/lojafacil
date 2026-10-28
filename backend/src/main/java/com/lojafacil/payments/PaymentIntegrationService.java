package com.lojafacil.payments;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.payments.PaymentProvider.ConnectionTest;
import com.lojafacil.payments.PaymentProvider.ProviderContext;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.crypto.SecretCipher;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreRepository;

/**
 * Configuração de pagamentos por loja e escolha do provedor.
 * Sem Mercado Pago configurado (ou no ambiente demonstrativo), a loja usa o simulador local.
 */
@Service
public class PaymentIntegrationService {

    public record ConfigView(ProviderType provider, boolean simulated, String publicKey, String accessTokenMasked,
                             String webhookSecretMasked, String webhookUrl, Instant lastTestAt, Boolean lastTestOk,
                             boolean demoMode, boolean liveTransactionsAllowed) {
    }

    public record SaveRequest(@NotNull ProviderType provider,
                              @Size(max = 300) String accessToken,
                              @Size(max = 200) String publicKey,
                              @Size(max = 300) String webhookSecret) {
    }

    /** Provedor + credenciais prontos para uso (credenciais só existem em memória durante a chamada). */
    public record Resolved(PaymentProvider provider, ProviderContext context) {
    }

    private final PaymentIntegration.Repo integrations;
    private final StoreRepository stores;
    private final SecretCipher cipher;
    private final AppProperties props;
    private final AuditService audit;
    private final CurrentUser currentUser;
    private final Map<ProviderType, PaymentProvider> providers;

    public PaymentIntegrationService(PaymentIntegration.Repo integrations, StoreRepository stores, SecretCipher cipher,
                                     AppProperties props, AuditService audit, CurrentUser currentUser,
                                     List<PaymentProvider> providerList) {
        this.integrations = integrations;
        this.stores = stores;
        this.cipher = cipher;
        this.props = props;
        this.audit = audit;
        this.currentUser = currentUser;
        this.providers = providerList.stream().collect(Collectors.toMap(PaymentProvider::type, Function.identity()));
    }

    /** Provedor que será usado em NOVAS cobranças da loja. */
    @Transactional(readOnly = true)
    public ProviderType activeProvider(Long storeId) {
        if (props.demoMode()) {
            return ProviderType.SIMULATOR;
        }
        return integrations.findByStoreId(storeId)
                .filter(i -> i.getProvider() == ProviderType.MERCADO_PAGO && i.getAccessTokenEnc() != null)
                .map(i -> ProviderType.MERCADO_PAGO)
                .orElse(ProviderType.SIMULATOR);
    }

    /** Resolve provedor e credenciais para operar um pagamento já existente (usa o provedor do pagamento). */
    @Transactional(readOnly = true)
    public Resolved resolve(Long storeId, ProviderType type) {
        if (type == ProviderType.MANUAL) {
            throw new IllegalArgumentException("Pagamento manual não possui provedor");
        }
        Store store = stores.findById(storeId).orElseThrow(() -> Errors.notFound("Loja"));
        if (type == ProviderType.SIMULATOR) {
            return new Resolved(providers.get(type), new ProviderContext(storeId, store.getSlug(), null, null));
        }
        PaymentIntegration integration = integrations.findByStoreId(storeId)
                .filter(i -> i.getAccessTokenEnc() != null)
                .orElseThrow(() -> new PaymentProvider.ProviderException("Credenciais do Mercado Pago não configuradas."));
        return new Resolved(providers.get(type), new ProviderContext(storeId, store.getSlug(),
                cipher.decrypt(integration.getAccessTokenEnc()), cipher.decrypt(integration.getWebhookSecretEnc())));
    }

    public PaymentProvider provider(ProviderType type) {
        return providers.get(type);
    }

    @Transactional(readOnly = true)
    public ConfigView config(Long storeId) {
        Store store = stores.findById(storeId).orElseThrow(() -> Errors.notFound("Loja"));
        PaymentIntegration i = integrations.findByStoreId(storeId).orElseGet(PaymentIntegration::new);
        ProviderType active = activeProvider(storeId);
        return new ConfigView(active, active == ProviderType.SIMULATOR, i.getPublicKey(),
                i.getAccessTokenEnc() == null ? null : SecretCipher.mask(safeDecrypt(i.getAccessTokenEnc())),
                i.getWebhookSecretEnc() == null ? null : SecretCipher.mask(safeDecrypt(i.getWebhookSecretEnc())),
                props.publicApiBaseUrl() + "/api/webhooks/mercadopago/" + store.getSlug(),
                i.getLastTestAt(), i.getLastTestOk(), props.demoMode(), props.payments().allowLiveTransactions());
    }

    /**
     * Salva a configuração. Campos de credencial em branco mantêm o valor atual
     * (a interface nunca recebe o valor salvo, só a versão mascarada).
     */
    @Transactional
    public ConfigView save(Long storeId, SaveRequest req) {
        if (req.provider() == ProviderType.MANUAL) {
            throw Errors.badRequest("INVALID_PROVIDER", "Provedor inválido.");
        }
        if (req.provider() == ProviderType.MERCADO_PAGO && props.demoMode()) {
            throw Errors.rule("DEMO_MODE", "O ambiente demonstrativo bloqueia credenciais reais. Os pagamentos usam o simulador.");
        }
        PaymentIntegration i = integrations.findByStoreId(storeId).orElseGet(() -> {
            PaymentIntegration n = new PaymentIntegration();
            n.setStoreId(storeId);
            return n;
        });
        i.setProvider(req.provider());
        if (req.accessToken() != null && !req.accessToken().isBlank()) {
            String token = req.accessToken().trim();
            if (!token.startsWith("TEST-") && !token.startsWith("APP_USR-")) {
                throw Errors.badRequest("INVALID_TOKEN", "O access token do Mercado Pago começa com TEST- ou APP_USR-.");
            }
            i.setAccessTokenEnc(cipher.encrypt(token));
            i.setLastTestOk(null);
        }
        if (req.webhookSecret() != null && !req.webhookSecret().isBlank()) {
            i.setWebhookSecretEnc(cipher.encrypt(req.webhookSecret().trim()));
        }
        if (req.publicKey() != null) {
            i.setPublicKey(req.publicKey().isBlank() ? null : req.publicKey().trim());
        }
        if (req.provider() == ProviderType.MERCADO_PAGO && i.getAccessTokenEnc() == null) {
            throw Errors.badRequest("TOKEN_REQUIRED", "Informe o access token de teste do Mercado Pago.");
        }
        i.setUpdatedBy(currentUser.idOrNull());
        i.setUpdatedAt(Instant.now());
        integrations.save(i);
        // nunca registrar a credencial: apenas o fato de que foi alterada
        audit.record(storeId, "PAYMENT_INTEGRATION", i.getId(), "INTEGRATION_UPDATED", "Provedor: " + req.provider());
        return config(storeId);
    }

    /** Testa as credenciais salvas. A chamada de rede acontece fora de transação. */
    public ConnectionTest test(Long storeId) {
        ProviderType type = activeProvider(storeId);
        Resolved r = resolve(storeId, type);
        ConnectionTest result = r.provider().testConnection(r.context());
        markTest(storeId, result.ok());
        return result;
    }

    private void markTest(Long storeId, boolean ok) {
        integrations.findByStoreId(storeId).ifPresent(i -> {
            i.setLastTestAt(Instant.now());
            i.setLastTestOk(ok);
            integrations.save(i); // chamada interna: sem @Transactional, o save do repositório grava
        });
    }

    private String safeDecrypt(String enc) {
        try {
            return cipher.decrypt(enc);
        } catch (IllegalStateException e) {
            return "????";
        }
    }
}
