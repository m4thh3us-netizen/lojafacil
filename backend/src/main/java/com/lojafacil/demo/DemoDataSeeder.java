package com.lojafacil.demo;

import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.lojafacil.auth.Role;
import com.lojafacil.auth.StoreCreatedEvent;
import com.lojafacil.auth.User;
import com.lojafacil.auth.UserRepository;
import com.lojafacil.catalog.CatalogDtos.CategoryRequest;
import com.lojafacil.catalog.CatalogDtos.CategoryView;
import com.lojafacil.catalog.CatalogDtos.ProductDetail;
import com.lojafacil.catalog.CatalogDtos.ProductRequest;
import com.lojafacil.catalog.CatalogDtos.VariantRequest;
import com.lojafacil.catalog.CategoryService;
import com.lojafacil.catalog.Product;
import com.lojafacil.catalog.ProductImage;
import com.lojafacil.catalog.ProductRepository;
import com.lojafacil.catalog.ProductService;
import com.lojafacil.customers.CustomerDtos.AddressData;
import com.lojafacil.customers.CustomerDtos.CustomerRequest;
import com.lojafacil.customers.CustomerDtos.SupplierRequest;
import com.lojafacil.customers.CustomerDtos.SupplierView;
import com.lojafacil.customers.CustomerService;
import com.lojafacil.finance.EntryType;
import com.lojafacil.finance.FinanceDtos.EntryRequest;
import com.lojafacil.finance.FinanceService;
import com.lojafacil.inventory.InventoryService;
import com.lojafacil.orders.DeliveryMethod;
import com.lojafacil.orders.FulfillmentStatus;
import com.lojafacil.orders.OrderDtos.CartLine;
import com.lojafacil.orders.OrderDtos.CheckoutResponse;
import com.lojafacil.orders.OrderDtos.CustomerData;
import com.lojafacil.orders.OrderDtos.ManualOrderRequest;
import com.lojafacil.orders.OrderDtos.ManualPaymentMode;
import com.lojafacil.orders.OrderDtos.ReturnLine;
import com.lojafacil.orders.OrderDtos.ReturnRequest;
import com.lojafacil.orders.OrderItem;
import com.lojafacil.orders.OrderRepository;
import com.lojafacil.orders.OrderService;
import com.lojafacil.payments.CheckoutService;
import com.lojafacil.payments.Payment;
import com.lojafacil.payments.PaymentService;
import com.lojafacil.payments.simulator.SimulatedPaymentProvider;
import com.lojafacil.shared.files.FileStorageService;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreRepository;

/**
 * Dados do AMBIENTE DEMONSTRATIVO (perfil "demo"). Nunca roda em outros perfis.
 *
 * Tudo é criado pelos mesmos serviços usados pela aplicação (pedidos, estoque, pagamentos simulados),
 * então os dados respeitam as regras de negócio. Ao final, as datas são distribuídas pelos últimos
 * 30 dias para que gráficos e relatórios tenham conteúdo. A loja fica marcada como "demo",
 * e a interface exibe o aviso de dados fictícios.
 */
@Component
@Profile("demo")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);
    public static final String SLUG = "demo";
    public static final String OWNER_EMAIL = "demo@lojafacil.dev";
    public static final String DEMO_PASSWORD = "Demo@1234";

    private final StoreRepository stores;
    private final UserRepository users;
    private final PasswordEncoder encoder;
    private final ApplicationEventPublisher events;
    private final CategoryService categories;
    private final ProductService products;
    private final ProductRepository productRepo;
    private final InventoryService inventory;
    private final CustomerService customers;
    private final CheckoutService checkout;
    private final PaymentService payments;
    private final Payment.Repo paymentRepo;
    private final SimulatedPaymentProvider simulator;
    private final OrderService orderService;
    private final OrderRepository orderRepo;
    private final FinanceService finance;
    private final FileStorageService files;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;

    public DemoDataSeeder(StoreRepository stores, UserRepository users, PasswordEncoder encoder,
                          ApplicationEventPublisher events, CategoryService categories, ProductService products,
                          ProductRepository productRepo, InventoryService inventory, CustomerService customers,
                          CheckoutService checkout, PaymentService payments, Payment.Repo paymentRepo,
                          SimulatedPaymentProvider simulator, OrderService orderService, OrderRepository orderRepo,
                          FinanceService finance, FileStorageService files, JdbcTemplate jdbc,
                          PlatformTransactionManager tm) {
        this.stores = stores;
        this.users = users;
        this.encoder = encoder;
        this.events = events;
        this.categories = categories;
        this.products = products;
        this.productRepo = productRepo;
        this.inventory = inventory;
        this.customers = customers;
        this.checkout = checkout;
        this.payments = payments;
        this.paymentRepo = paymentRepo;
        this.simulator = simulator;
        this.orderService = orderService;
        this.orderRepo = orderRepo;
        this.finance = finance;
        this.files = files;
        this.jdbc = jdbc;
        this.tx = new TransactionTemplate(tm);
    }

    @Override
    public void run(ApplicationArguments args) {
        if (stores.existsBySlug(SLUG)) {
            return;
        }
        log.info("Criando dados do ambiente demonstrativo (loja '{}')...", SLUG);
        Long storeId = tx.execute(s -> createStoreAndUsers());
        seedCatalogAndStock(storeId);
        seedCustomersAndOrders(storeId);
        seedFinance(storeId);
        spreadDates(storeId);
        log.info("Ambiente demonstrativo pronto. Login: {} / senha em README (apenas demonstração).", OWNER_EMAIL);
    }

    // ------------------------------------------------------------------ loja e equipe

    private Long createStoreAndUsers() {
        Store store = new Store();
        store.setSlug(SLUG);
        store.setName("Ateliê Aurora");
        store.setDescription("Loja demonstrativa de camisetas, canecas e papelaria autoral. Todos os dados são fictícios.");
        store.setPrimaryColor("#4F46E5");
        store.setSecondaryColor("#0F172A");
        store.setContactEmail("contato@atelieaurora.exemplo");
        store.setContactPhone("(11) 4000-0000");
        store.setWhatsapp("(11) 90000-0000");
        store.setInstagram("@atelieaurora.exemplo");
        store.setAddressLine("Rua Exemplo, 123 - Centro");
        store.setAddressCity("São Paulo");
        store.setAddressState("SP");
        store.setAddressZip("01000-000");
        store.setServiceHours("Seg a sex, 9h às 18h · Sáb, 9h às 13h");
        store.setPickupEnabled(true);
        store.setLocalDeliveryEnabled(true);
        store.setLocalDeliveryFee(new BigDecimal("12.00"));
        store.setReservationMinutes(30);
        store.setDemo(true);
        stores.save(store);
        events.publishEvent(new StoreCreatedEvent(store.getId()));
        user(store.getId(), "Ana Demonstração", OWNER_EMAIL, Role.OWNER);
        user(store.getId(), "Bruno Gerente (demo)", "gerente@lojafacil.dev", Role.MANAGER);
        user(store.getId(), "Carla Operadora (demo)", "operador@lojafacil.dev", Role.OPERATOR);
        return store.getId();
    }

    private void user(Long storeId, String name, String email, Role role) {
        User u = new User();
        u.setStoreId(storeId);
        u.setFullName(name);
        u.setEmail(email);
        u.setRole(role);
        u.setPasswordHash(encoder.encode(DEMO_PASSWORD));
        users.save(u);
    }

    // ------------------------------------------------------------------ catálogo e estoque

    private final List<Long> sellable = new ArrayList<>();

    private void seedCatalogAndStock(Long storeId) {
        SupplierView supplier = customers.saveSupplier(storeId, null, new SupplierRequest("Malharia Exemplo Ltda.",
                "Rogério (exemplo)", "(11) 3000-0000", "vendas@malharia.exemplo", "Av. Fictícia, 500 - SP", "Prazo de entrega: 7 dias", true));
        customers.saveSupplier(storeId, null, new SupplierRequest("Gráfica Modelo", "Patrícia (exemplo)", "(11) 3100-0000",
                "orcamento@grafica.exemplo", null, "Cadernos e adesivos", true));

        CategoryView shirts = categories.create(storeId, new CategoryRequest("Camisetas", true));
        CategoryView mugs = categories.create(storeId, new CategoryRequest("Canecas", true));
        CategoryView paper = categories.create(storeId, new CategoryRequest("Papelaria", true));
        CategoryView acc = categories.create(storeId, new CategoryRequest("Acessórios", true));

        variantProduct(storeId, shirts.id(), "Camiseta Constelação", "CAM-CONST", "39.90", "89.90", "74.90",
                "Camiseta 100% algodão com estampa de constelações.", new Color(79, 70, 229), true,
                new String[][]{{"P", "Preta", "6"}, {"M", "Preta", "10"}, {"G", "Preta", "4"}, {"M", "Branca", "8"}, {"G", "Branca", "1"}});
        variantProduct(storeId, shirts.id(), "Camiseta Café & Código", "CAM-CAFE", "36.00", "79.90", null,
                "Para quem programa movido a café.", new Color(120, 72, 40), true,
                new String[][]{{"P", "Areia", "5"}, {"M", "Areia", "7"}, {"G", "Areia", "3"}});
        variantProduct(storeId, shirts.id(), "Camiseta Minimal Onda", "CAM-ONDA", "32.00", "69.90", null,
                "Traço minimalista de onda, malha leve.", new Color(14, 116, 144), false,
                new String[][]{{"M", "Azul", "2"}, {"G", "Azul", "0"}});
        simpleProduct(storeId, mugs.id(), "Caneca Aurora 350 ml", "CAN-AUR", "18.00", "49.90", "42.90", 20, 5,
                "Caneca de porcelana com degradê aurora.", new Color(217, 70, 239), true);
        simpleProduct(storeId, mugs.id(), "Caneca Esmaltada Clássica", "CAN-ESM", "22.00", "59.90", null, 3, 4,
                "Caneca esmaltada estilo acampamento.", new Color(22, 101, 52), false);
        simpleProduct(storeId, paper.id(), "Caderno Pontilhado A5", "CAD-A5", "14.50", "39.90", null, 30, 8,
                "Caderno com 160 páginas pontilhadas, capa dura.", new Color(234, 88, 12), true);
        simpleProduct(storeId, paper.id(), "Kit Adesivos Dev", "ADS-DEV", "4.00", "19.90", "15.90", 60, 10,
                "Cartela com 12 adesivos de vinil resistentes à água.", new Color(37, 99, 235), false);
        simpleProduct(storeId, acc.id(), "Ecobag Aurora", "ECO-AUR", "15.00", "44.90", null, 2, 5,
                "Ecobag de algodão cru com alça reforçada.", new Color(190, 18, 60), false);
        simpleProduct(storeId, acc.id(), "Chaveiro Acrílico", "CHV-ACR", "3.50", "14.90", null, 0, 5,
                "Chaveiro em acrílico cortado a laser.", new Color(100, 116, 139), false);

        // reposição registrada com fornecedor (histórico de movimentações)
        tx.executeWithoutResult(s -> {
            Long variantId = sellable.get(0);
            inventory.entry(storeId, variantId, 5, "Reposição - NF do fornecedor (exemplo)", supplier.id());
        });
        tx.executeWithoutResult(s -> inventory.adjust(storeId, sellable.get(1),
                productRepo.findById(productOf(sellable.get(1))).orElseThrow().getVariants().stream()
                        .filter(v -> v.getId().equals(sellable.get(1))).findFirst().orElseThrow().getPhysicalQty() - 1,
                "Ajuste de inventário: 1 unidade com defeito de estampa"));
    }

    private Long productOf(Long variantId) {
        return jdbc.queryForObject("select product_id from product_variants where id = ?", Long.class, variantId);
    }

    private void simpleProduct(Long storeId, Long categoryId, String name, String sku, String cost, String price,
                               String promo, int stock, int min, String desc, Color color, boolean featured) {
        ProductDetail p = tx.execute(s -> products.create(storeId, new ProductRequest(name, desc, categoryId, sku, null,
                new BigDecimal(cost), new BigDecimal(price), promo == null ? null : new BigDecimal(promo), "UN", min,
                true, true, featured, false, null, null,
                List.of(new VariantRequest(null, null, null, null, null, null, stock, true)))));
        addImage(storeId, p.id(), name, color);
        sellable.add(p.variants().get(0).id());
    }

    private void variantProduct(Long storeId, Long categoryId, String name, String sku, String cost, String price,
                                String promo, String desc, Color color, boolean featured, String[][] variants) {
        List<VariantRequest> vrs = new ArrayList<>();
        for (String[] v : variants) {
            vrs.add(new VariantRequest(null, sku + "-" + v[0] + "-" + v[1].substring(0, 3).toUpperCase(), null,
                    v[0], v[1], null, Integer.valueOf(v[2]), true));
        }
        ProductDetail p = tx.execute(s -> products.create(storeId, new ProductRequest(name, desc, categoryId, sku, null,
                new BigDecimal(cost), new BigDecimal(price), promo == null ? null : new BigDecimal(promo), "UN", 2,
                true, true, featured, true, "Tamanho", "Cor", vrs)));
        addImage(storeId, p.id(), name, color);
        p.variants().forEach(v -> sellable.add(v.id()));
    }

    private void addImage(Long storeId, Long productId, String name, Color color) {
        String path = files.storeRendered(storeId, render(name, color));
        tx.executeWithoutResult(s -> {
            Product product = productRepo.findById(productId).orElseThrow();
            ProductImage img = new ProductImage();
            img.setProduct(product);
            img.setPath(path);
            product.getImages().add(img);
        });
    }

    /** Gera uma "foto" ilustrativa (degradê + iniciais) — evita depender de imagens de terceiros. */
    private static BufferedImage render(String name, Color base) {
        int size = 800;
        BufferedImage img = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(base);
        g.fillRect(0, 0, size, size);
        g.setColor(new Color(255, 255, 255, 40));
        g.fillOval(size / 2, -size / 4, size, size);
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 220));
        String initials = initials(name);
        int w = g.getFontMetrics().stringWidth(initials);
        g.drawString(initials, (size - w) / 2, size / 2 + 80);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 34));
        g.drawString("imagem ilustrativa", 40, size - 40);
        g.dispose();
        return img;
    }

    private static String initials(String name) {
        StringBuilder sb = new StringBuilder();
        for (String w : name.split(" ")) {
            if (!w.isEmpty() && Character.isLetter(w.charAt(0)) && sb.length() < 2 && w.length() > 2) {
                sb.append(Character.toUpperCase(w.charAt(0)));
            }
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ clientes e pedidos

    private void seedCustomersAndOrders(Long storeId) {
        String[][] people = {
                {"Mariana Exemplo", "mariana@cliente.exemplo", "(11) 91111-1111"},
                {"João Fictício", "joao@cliente.exemplo", "(11) 92222-2222"},
                {"Luiza Modelo", "luiza@cliente.exemplo", "(11) 93333-3333"},
                {"Pedro Teste", "pedro@cliente.exemplo", "(11) 94444-4444"},
                {"Rafaela Demo", "rafaela@cliente.exemplo", "(11) 95555-5555"},
        };
        AddressData addr = new AddressData("Casa", "Rua das Flores", "100", "Apto 12", "Jardim Exemplo", "São Paulo", "SP", "01234-000");
        for (String[] p : people) {
            customers.create(storeId, new CustomerRequest(p[0], p[1], p[2],
                    "Cliente fictício do ambiente demonstrativo.", List.of(addr)));
        }

        // 10 pedidos pagos (confirmados), com atendimento em estágios diferentes
        for (int i = 0; i < 10; i++) {
            String[] p = people[i % people.length];
            DeliveryMethod dm = i % 3 == 0 ? DeliveryMethod.LOCAL_DELIVERY : DeliveryMethod.PICKUP;
            Long orderId = paidOrder(storeId, p, dm, dm == DeliveryMethod.LOCAL_DELIVERY ? addr : null,
                    List.of(new CartLine(sellable.get((i * 3) % sellable.size()), 1 + i % 2),
                            new CartLine(sellable.get((i * 5 + 7) % sellable.size()), 1)));
            if (orderId == null) {
                continue;
            }
            if (i < 6) {
                advance(storeId, orderId, dm, i < 4);
            } else if (i == 6) {
                tx.executeWithoutResult(s -> orderService.updateFulfillment(storeId, orderId, FulfillmentStatus.PICKING, "Separando itens"));
            }
            if (i == 2) {
                // devolução recebida e conferida (repõe estoque)
                tx.executeWithoutResult(s -> {
                    OrderItem item = orderRepo.findById(orderId).orElseThrow().getItems().get(0);
                    orderService.registerReturn(storeId, orderId, new ReturnRequest(
                            List.of(new ReturnLine(item.getId(), 1)), "Tamanho não serviu (exemplo)", true, true, "Produto lacrado"));
                });
                OrderItem returned = tx.execute(s -> orderRepo.findById(orderId).orElseThrow().getItems().get(0));
                BigDecimal unitPaid = returned.getLineTotal().divide(BigDecimal.valueOf(returned.getQuantity()), 2, java.math.RoundingMode.HALF_UP);
                payments.refund(storeId, orderId, unitPaid, "Devolução de 1 item (exemplo)");
            }
        }

        // pedido aguardando pagamento (reserva ativa)
        checkout.publicCheckout(SLUG, checkoutRequest(people[1], List.of(new CartLine(sellable.get(3), 1))));

        // pagamento recusado
        CheckoutResponse declined = checkout.publicCheckout(SLUG, checkoutRequest(people[2], List.of(new CartLine(sellable.get(13), 1))));
        decideLatest(storeId, declined, false);

        // pedido cancelado pela loja antes do pagamento
        CheckoutResponse toCancel = checkout.publicCheckout(SLUG, checkoutRequest(people[3], List.of(new CartLine(sellable.get(10), 2))));
        Long cancelId = orderIdByNumber(storeId, toCancel.orderNumber());
        payments.cancelOrder(storeId, cancelId, "Cliente desistiu da compra (exemplo)");

        // pedido manual pago na loja (dinheiro)
        CheckoutResponse manual = checkout.manualOrder(storeId, new ManualOrderRequest(null,
                new CustomerData(people[4][0], people[4][1], people[4][2]), DeliveryMethod.PICKUP, null,
                List.of(new CartLine(sellable.get(12), 2)), ManualPaymentMode.OFFLINE, "Venda no balcão"));
        Long manualId = orderIdByNumber(storeId, manual.orderNumber());
        payments.registerManualPayment(storeId, manualId, "Dinheiro");

        // pedido com reserva expirada (prazo vencido sem pagamento)
        CheckoutResponse expired = checkout.publicCheckout(SLUG, checkoutRequest(people[0], List.of(new CartLine(sellable.get(13), 1))));
        Long expiredId = orderIdByNumber(storeId, expired.orderNumber());
        jdbc.update("update orders set reservation_expires_at = now() - interval '1 hour' where id = ?", expiredId);
        jdbc.update("update stock_reservations set expires_at = now() - interval '1 hour' where order_id = ?", expiredId);
        payments.expireOrder(expiredId);
    }

    private Long paidOrder(Long storeId, String[] person, DeliveryMethod dm, AddressData addr, List<CartLine> lines) {
        try {
            CheckoutResponse r = checkout.publicCheckout(SLUG, new com.lojafacil.orders.OrderDtos.CheckoutRequest(
                    new CustomerData(person[0], person[1], person[2]), dm, addr, lines, null));
            decideLatest(storeId, r, true);
            return orderIdByNumber(storeId, r.orderNumber());
        } catch (RuntimeException e) {
            log.debug("Pedido demonstrativo ignorado: {}", e.getMessage());
            return null;
        }
    }

    /** Simula o cliente decidindo na página de pagamento e o webhook chegando. */
    private void decideLatest(Long storeId, CheckoutResponse r, boolean approve) {
        Long orderId = orderIdByNumber(storeId, r.orderNumber());
        Payment p = paymentRepo.findFirstByOrderIdOrderByCreatedAtDesc(orderId).orElseThrow();
        simulator.decide(UUID.fromString(p.getProviderCheckoutId()), approve);
        payments.processNotification(storeId, com.lojafacil.payments.ProviderType.SIMULATOR, p.getProviderCheckoutId());
    }

    private void advance(Long storeId, Long orderId, DeliveryMethod dm, boolean deliver) {
        tx.executeWithoutResult(s -> orderService.updateFulfillment(storeId, orderId, FulfillmentStatus.PICKING, null));
        tx.executeWithoutResult(s -> orderService.updateFulfillment(storeId, orderId,
                dm == DeliveryMethod.PICKUP ? FulfillmentStatus.READY_FOR_PICKUP : FulfillmentStatus.SHIPPED, null));
        if (deliver) {
            tx.executeWithoutResult(s -> orderService.updateFulfillment(storeId, orderId, FulfillmentStatus.DELIVERED, null));
        }
    }

    private com.lojafacil.orders.OrderDtos.CheckoutRequest checkoutRequest(String[] p, List<CartLine> lines) {
        return new com.lojafacil.orders.OrderDtos.CheckoutRequest(new CustomerData(p[0], p[1], p[2]),
                DeliveryMethod.PICKUP, null, lines, null);
    }

    private Long orderIdByNumber(Long storeId, int number) {
        return jdbc.queryForObject("select id from orders where store_id = ? and number = ?", Long.class, storeId, number);
    }

    // ------------------------------------------------------------------ financeiro

    private void seedFinance(Long storeId) {
        var cats = finance.listCategories(storeId);
        Long rent = cats.stream().filter(c -> c.name().equals("Aluguel")).findFirst().orElseThrow().id();
        Long purchases = cats.stream().filter(c -> c.name().equals("Compras de mercadorias")).findFirst().orElseThrow().id();
        Long utilities = cats.stream().filter(c -> c.name().startsWith("Energia")).findFirst().orElseThrow().id();
        Long marketing = cats.stream().filter(c -> c.name().equals("Marketing")).findFirst().orElseThrow().id();
        Long otherIncome = cats.stream().filter(c -> c.name().equals("Outras receitas")).findFirst().orElseThrow().id();
        LocalDate today = FinanceService.today();

        finance.create(storeId, new EntryRequest(EntryType.EXPENSE, rent, "Aluguel do mês anterior (exemplo)", new BigDecimal("1800.00"),
                1, today.minusDays(25), true, today.minusDays(25), "Imobiliária Exemplo", null, null, null));
        finance.create(storeId, new EntryRequest(EntryType.EXPENSE, rent, "Aluguel do mês (exemplo)", new BigDecimal("1800.00"),
                1, today.plusDays(5), false, null, "Imobiliária Exemplo", null, null, null));
        var installments = finance.create(storeId, new EntryRequest(EntryType.EXPENSE, purchases, "Compra de camisetas lisas (exemplo)",
                new BigDecimal("1000.00"), 3, today.minusDays(10), false, null, "Malharia Exemplo Ltda.", null, null, "3 parcelas: centavos distribuídos"));
        finance.settle(storeId, installments.get(0).id(), today.minusDays(10));
        finance.create(storeId, new EntryRequest(EntryType.EXPENSE, utilities, "Conta de energia (exemplo)", new BigDecimal("243.87"),
                1, today.minusDays(3), false, null, "Distribuidora Exemplo", null, null, null));
        var ads = finance.create(storeId, new EntryRequest(EntryType.EXPENSE, marketing, "Anúncios em rede social (exemplo)",
                new BigDecimal("150.00"), 1, today.minusDays(12), true, today.minusDays(12), null, null, null, null));
        finance.reverse(storeId, ads.get(0).id(), "Lançado em duplicidade (exemplo de estorno)");
        finance.create(storeId, new EntryRequest(EntryType.INCOME, otherIncome, "Oficina de estamparia (exemplo)",
                new BigDecimal("600.00"), 2, today.plusDays(7), false, null, "Coletivo Exemplo", null, null, null));
    }

    /** Distribui os registros pelos últimos 30 dias (apenas na loja demonstrativa). */
    private void spreadDates(Long storeId) {
        List<Long> ids = jdbc.queryForList("select id from orders where store_id = ? and status in ('CONFIRMED','COMPLETED') order by id", Long.class, storeId);
        for (int i = 0; i < ids.size(); i++) {
            int days = 27 - i * 27 / Math.max(ids.size(), 1);
            Long id = ids.get(i);
            jdbc.update("update orders set created_at = created_at - make_interval(days => ?), confirmed_at = confirmed_at - make_interval(days => ?), "
                    + "completed_at = completed_at - make_interval(days => ?) where id = ?", days, days, Math.max(days - 2, 0), id);
            jdbc.update("update order_history set created_at = created_at - make_interval(days => ?) where order_id = ?", days, id);
            jdbc.update("update stock_movements set created_at = created_at - make_interval(days => ?) where order_id = ?", days, id);
            jdbc.update("update financial_entries set due_date = due_date - ?, paid_date = paid_date - ? where order_id = ? and source_type in ('ORDER_PAYMENT','PAYMENT_FEE')",
                    days, days, id);
        }
        jdbc.update("update stock_movements set created_at = created_at - interval '29 days' where store_id = ? and order_id is null and reason = 'Estoque inicial'", storeId);
    }
}
