package com.lojafacil.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.hibernate.Hibernate;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.lojafacil.auth.AuthDtos.OwnerData;
import com.lojafacil.auth.AuthDtos.RegisterRequest;
import com.lojafacil.auth.AuthDtos.StoreData;
import com.lojafacil.auth.AuthService;
import com.lojafacil.auth.Role;
import com.lojafacil.auth.User;
import com.lojafacil.auth.UserRepository;
import com.lojafacil.catalog.CatalogDtos.ProductDetail;
import com.lojafacil.catalog.CatalogDtos.ProductRequest;
import com.lojafacil.catalog.CatalogDtos.VariantRequest;
import com.lojafacil.catalog.ProductService;
import com.lojafacil.catalog.ProductVariant;
import com.lojafacil.catalog.VariantRepository;
import com.lojafacil.orders.DeliveryMethod;
import com.lojafacil.orders.Order;
import com.lojafacil.orders.OrderDtos.CartLine;
import com.lojafacil.orders.OrderDtos.CheckoutRequest;
import com.lojafacil.orders.OrderDtos.CheckoutResponse;
import com.lojafacil.orders.OrderDtos.CustomerData;
import com.lojafacil.orders.OrderRepository;
import com.lojafacil.payments.CheckoutService;
import com.lojafacil.payments.Payment;
import com.lojafacil.payments.PaymentService;
import com.lojafacil.payments.ProviderType;
import com.lojafacil.payments.simulator.SimulatedPaymentProvider;
import com.lojafacil.shared.security.AuthUser;

/** Atalhos para montar cenários de teste usando os serviços reais. */
@TestComponent
public class TestData {

    private static final AtomicInteger SEQ = new AtomicInteger();

    public record StoreCtx(Long storeId, String slug, User owner) {
    }

    private final AuthService auth;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ProductService products;
    private final VariantRepository variants;
    private final CheckoutService checkout;
    private final PaymentService payments;
    private final Payment.Repo paymentRepo;
    private final OrderRepository orders;
    private final SimulatedPaymentProvider simulator;
    private final TransactionTemplate tx;

    public TestData(AuthService auth, UserRepository users, PasswordEncoder encoder, ProductService products,
                    VariantRepository variants, CheckoutService checkout, PaymentService payments,
                    Payment.Repo paymentRepo, OrderRepository orders, SimulatedPaymentProvider simulator,
                    PlatformTransactionManager tm) {
        this.tx = new TransactionTemplate(tm);
        this.auth = auth;
        this.users = users;
        this.encoder = encoder;
        this.products = products;
        this.variants = variants;
        this.checkout = checkout;
        this.payments = payments;
        this.paymentRepo = paymentRepo;
        this.orders = orders;
        this.simulator = simulator;
    }

    public StoreCtx newStore() {
        int n = SEQ.incrementAndGet();
        String slug = "loja-" + n + "-" + UUID.randomUUID().toString().substring(0, 6);
        User owner = auth.register(new RegisterRequest(
                new OwnerData("Dono " + n, slug + "@teste.dev", "Senha123", "Senha123", null),
                new StoreData("Loja " + n, slug, null)));
        return new StoreCtx(owner.getStoreId(), slug, owner);
    }

    public User member(Long storeId, Role role) {
        User u = new User();
        u.setStoreId(storeId);
        u.setFullName(role + " " + SEQ.incrementAndGet());
        u.setEmail(UUID.randomUUID() + "@teste.dev");
        u.setRole(role);
        u.setPasswordHash(encoder.encode("Senha123"));
        return users.save(u);
    }

    public static AuthUser principal(User u) {
        return new AuthUser(u.getId(), u.getStoreId(), u.getEmail(), u.getFullName(), u.getRole(), true, null);
    }

    /** Produto simples com estoque inicial. */
    public ProductDetail product(Long storeId, String price, String promo, String cost, int stock) {
        String sku = "SKU-" + SEQ.incrementAndGet();
        return products.create(storeId, new ProductRequest("Produto " + sku, null, null, sku, null,
                new BigDecimal(cost), new BigDecimal(price), promo == null ? null : new BigDecimal(promo), "UN", 1,
                true, true, false, false, null, null,
                List.of(new VariantRequest(null, null, null, null, null, null, stock, true))));
    }

    public Long variantOf(ProductDetail p) {
        return p.variants().get(0).id();
    }

    public ProductVariant variant(Long id) {
        return variants.findById(id).orElseThrow();
    }

    public CheckoutResponse buy(String slug, Long variantId, int qty) {
        return checkout.publicCheckout(slug, new CheckoutRequest(
                new CustomerData("Cliente Teste", "cliente@teste.dev", "11999990000"),
                DeliveryMethod.PICKUP, null, List.of(new CartLine(variantId, qty)), null));
    }

    /** Lê o pedido (com itens) numa transação curta, já que os testes rodam fora de transação. */
    public Order order(Long storeId, int number) {
        return tx.execute(s -> {
            Order o = orders.findAll().stream().filter(x -> x.getStoreId().equals(storeId) && x.getNumber() == number)
                    .findFirst().orElseThrow();
            Hibernate.initialize(o.getItems());
            return o;
        });
    }

    public Payment paymentOf(Long orderId) {
        return paymentRepo.findFirstByOrderIdOrderByCreatedAtDesc(orderId).orElseThrow();
    }

    /** Cliente decide na página simulada e o provedor notifica (mesmo caminho do webhook). */
    public String pay(Long storeId, int orderNumber, boolean approve) {
        Payment p = paymentOf(order(storeId, orderNumber).getId());
        simulator.decide(UUID.fromString(p.getProviderCheckoutId()), approve);
        return payments.processNotification(storeId, ProviderType.SIMULATOR, p.getProviderCheckoutId());
    }
}
