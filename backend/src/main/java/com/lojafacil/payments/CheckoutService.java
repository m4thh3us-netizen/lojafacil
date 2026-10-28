package com.lojafacil.payments;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lojafacil.auth.MailService;
import com.lojafacil.orders.DeliveryMethod;
import com.lojafacil.orders.Order;
import com.lojafacil.orders.OrderDtos.CheckoutRequest;
import com.lojafacil.orders.OrderDtos.CheckoutResponse;
import com.lojafacil.orders.OrderDtos.ManualOrderRequest;
import com.lojafacil.orders.OrderDtos.ManualPaymentMode;
import com.lojafacil.orders.OrderDtos.Quote;
import com.lojafacil.orders.OrderDtos.QuoteRequest;
import com.lojafacil.orders.OrderRepository;
import com.lojafacil.orders.OrderService;
import com.lojafacil.orders.OrderService.PlaceOrderCommand;
import com.lojafacil.orders.OrderStatus;
import com.lojafacil.orders.PricingService;
import com.lojafacil.payments.PaymentProvider.CheckoutSession;
import com.lojafacil.payments.PaymentProvider.ProviderException;
import com.lojafacil.shared.config.AppProperties;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.util.Tokens;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreService;

@Service
public class CheckoutService {
    private final StoreService stores;
    private final OrderService orderService;
    private final OrderRepository orders;
    private final PricingService pricing;
    private final PaymentService payments;
    private final Payment.Repo paymentRepo;
    private final PaymentIntegrationService integrations;
    private final MailService mail;
    private final AppProperties props;
    private final TransactionTemplate tx;

    public CheckoutService(StoreService stores, OrderService orderService, OrderRepository orders, PricingService pricing,
                           PaymentService payments, Payment.Repo paymentRepo, PaymentIntegrationService integrations,
                           MailService mail, AppProperties props, PlatformTransactionManager tm) {
        this.stores = stores;
        this.orderService = orderService;
        this.orders = orders;
        this.pricing = pricing;
        this.payments = payments;
        this.paymentRepo = paymentRepo;
        this.integrations = integrations;
        this.mail = mail;
        this.props = props;
        this.tx = new TransactionTemplate(tm);
    }

    public Quote quote(String slug, QuoteRequest req) {
        return pricing.quote(stores.getBySlug(slug), req.deliveryMethod(), req.items());
    }

    public CheckoutResponse publicCheckout(String slug, CheckoutRequest req) {
        Store store = stores.getBySlug(slug);
        ProviderType provider = integrations.activeProvider(store.getId());
        PlaceOrderCommand cmd = new PlaceOrderCommand(store.getId(), Order.Channel.ONLINE, null,
                req.customer().name(), req.customer().email(), req.customer().phone(), req.deliveryMethod(),
                req.deliveryMethod() == DeliveryMethod.LOCAL_DELIVERY ? req.address() : null,
                req.items(), req.notes(), true);
        return placeAndCharge(store, cmd, provider, true);
    }

    public CheckoutResponse manualOrder(Long storeId, ManualOrderRequest req) {
        Store store = stores.get(storeId);
        if (req.customerId() == null && req.customer() == null) {
            throw Errors.badRequest("CUSTOMER_REQUIRED", "Selecione um cliente ou informe os dados do comprador.");
        }
        ProviderType provider = req.paymentMode() == ManualPaymentMode.OFFLINE ? ProviderType.MANUAL : integrations.activeProvider(storeId);
        PlaceOrderCommand cmd = new PlaceOrderCommand(storeId, Order.Channel.MANUAL, req.customerId(),
                req.customer() != null ? req.customer().name() : null,
                req.customer() != null ? req.customer().email() : null,
                req.customer() != null ? req.customer().phone() : null,
                req.deliveryMethod(), req.deliveryMethod() == DeliveryMethod.LOCAL_DELIVERY ? req.address() : null,
                req.items(), req.notes(), false);
        return placeAndCharge(store, cmd, provider, false);
    }

    public String retryCheckout(String slug, String trackingToken) {
        Store store = stores.getBySlug(slug);
        Order order = orders.findByTrackingTokenHash(Tokens.sha256(trackingToken))
                .filter(o -> o.getStoreId().equals(store.getId()))
                .orElseThrow(() -> Errors.notFound("Pedido"));
        if (order.getStatus() != OrderStatus.OPEN || order.getPaymentStatus().isPaid()) {
            throw Errors.rule("NOT_PAYABLE", "Este pedido não está aguardando pagamento.");
        }
        Payment p = paymentRepo.findFirstByOrderIdOrderByCreatedAtDesc(order.getId()).orElseThrow(() -> Errors.notFound("Pagamento"));
        try {
            return payments.startCheckout(p.getId(), trackingUrl(store, trackingToken)).checkoutUrl();
        } catch (ProviderException e) {
            throw Errors.rule("PAYMENT_PROVIDER_ERROR", e.getMessage());
        }
    }

    public void syncByToken(String slug, String trackingToken) {
        Store store = stores.getBySlug(slug);
        Order order = orders.findByTrackingTokenHash(Tokens.sha256(trackingToken))
                .filter(o -> o.getStoreId().equals(store.getId()))
                .orElseThrow(() -> Errors.notFound("Pedido"));
        paymentRepo.findFirstByOrderIdOrderByCreatedAtDesc(order.getId())
                .filter(p -> p.getProvider() != ProviderType.MANUAL)
                .ifPresent(p -> {
                    try {
                        payments.sync(p.getId());
                    } catch (ProviderException ignored) {
                    }
                });
    }

    private CheckoutResponse placeAndCharge(Store store, PlaceOrderCommand cmd, ProviderType provider, boolean notifyCustomer) {
        record Placed(Long paymentId, int number, String token, java.math.BigDecimal total, String email, String name) {
        }
        Placed placed = tx.execute(s -> {
            OrderService.PlacedOrder po = orderService.place(cmd);
            Payment payment = payments.createPending(po.order(), provider);
            return new Placed(payment.getId(), po.order().getNumber(), po.trackingToken(), po.order().getTotal(),
                    po.order().getCustomerEmail(), po.order().getCustomerName());
        });
        String trackingUrl = trackingUrl(store, placed.token());
        String checkoutUrl = null;
        String paymentError = null;
        if (provider != ProviderType.MANUAL) {
            try {
                CheckoutSession session = payments.startCheckout(placed.paymentId(), trackingUrl);
                checkoutUrl = session.checkoutUrl();
            } catch (ProviderException e) {
                paymentError = e.getMessage() + " Seu pedido foi registrado; tente pagar novamente pela página do pedido.";
            }
        }
        if (notifyCustomer) {
            mail.send(placed.email(), "Pedido #" + placed.number() + " - " + store.getName(),
                    "Olá, " + placed.name() + "!\n\nRecebemos seu pedido #" + placed.number() + " no valor de R$ " + placed.total()
                            + ".\nAcompanhe por este link (não compartilhe):\n" + trackingUrl);
        }
        return new CheckoutResponse(placed.number(), placed.token(), trackingUrl, checkoutUrl,
                provider == ProviderType.SIMULATOR, placed.total(), paymentError);
    }

    private String trackingUrl(Store store, String token) {
        return props.frontendBaseUrl() + "/loja/" + store.getSlug() + "/pedido/" + token;
    }
}
