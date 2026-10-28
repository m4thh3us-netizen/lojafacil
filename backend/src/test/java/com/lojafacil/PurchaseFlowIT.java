package com.lojafacil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.lojafacil.catalog.CatalogDtos.ProductDetail;
import com.lojafacil.catalog.ProductVariant;
import com.lojafacil.orders.Order;
import com.lojafacil.orders.OrderDtos.CheckoutResponse;
import com.lojafacil.orders.OrderStatus;
import com.lojafacil.orders.PaymentStatus;
import com.lojafacil.payments.Payment;
import com.lojafacil.payments.PaymentService;
import com.lojafacil.payments.WebhookService;
import com.lojafacil.payments.simulator.SimulatedPaymentProvider;
import com.lojafacil.shared.error.ApiException;
import com.lojafacil.support.IntegrationTest;
import com.lojafacil.support.TestData;

/** Fluxo de compra, estoque, pagamentos, webhooks, cancelamentos e reembolsos. */
@Import(TestData.class)
class PurchaseFlowIT extends IntegrationTest {

    @Autowired
    PaymentService payments;
    @Autowired
    WebhookService webhooks;
    @Autowired
    WebhookService.WebhookProcessor processor;
    @Autowired
    SimulatedPaymentProvider simulator;
    @Autowired
    JdbcTemplate jdbc;

    @Test
    void fluxoPrincipalDeCompra() {
        TestData.StoreCtx s = data.newStore();
        ProductDetail p = data.product(s.storeId(), "100.00", "80.00", "30.00", 5);
        Long variantId = data.variantOf(p);

        CheckoutResponse r = data.buy(s.slug(), variantId, 2);
        // total calculado no servidor: 2 x 80,00 (preço promocional), retirada sem taxa
        assertThat(r.total()).isEqualByComparingTo("160.00");
        assertThat(r.simulated()).isTrue();
        assertThat(r.checkoutUrl()).contains("/pagamento-simulado/");
        ProductVariant reserved = data.variant(variantId);
        assertThat(reserved.getPhysicalQty()).isEqualTo(5);
        assertThat(reserved.getReservedQty()).isEqualTo(2);

        assertThat(data.pay(s.storeId(), r.orderNumber(), true)).isEqualTo("APPROVED");

        Order o = data.order(s.storeId(), r.orderNumber());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(o.isStockCommitted()).isTrue();
        ProductVariant after = data.variant(variantId);
        assertThat(after.getPhysicalQty()).isEqualTo(3);
        assertThat(after.getReservedQty()).isZero();
        // item preserva a "fotografia" da venda
        assertThat(o.getItems().get(0).getUnitCost()).isEqualByComparingTo("30.00");
        assertThat(o.getItems().get(0).getUnitDiscount()).isEqualByComparingTo("20.00");

        assertThat(countEntries(s.storeId(), "ORDER_PAYMENT")).isEqualTo(1);
        assertThat(countEntries(s.storeId(), "PAYMENT_FEE")).isEqualTo(1);
    }

    @Test
    void webhooksDuplicadosNaoDuplicamBaixaNemLancamentos() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "50.00", null, "10.00", 3));
        CheckoutResponse r = data.buy(s.slug(), variantId, 1);
        Payment payment = data.paymentOf(data.order(s.storeId(), r.orderNumber()).getId());
        simulator.decide(java.util.UUID.fromString(payment.getProviderCheckoutId()), true);

        String payload = "eventId=evt-1&chargeId=" + payment.getProviderCheckoutId() + "&storeId=" + s.storeId();
        String sig = webhooks.signSimulatorPayload(payload);
        assertThat(webhooks.receiveSimulator(payload, sig)).isEqualTo(WebhookService.Outcome.ACCEPTED);
        assertThat(webhooks.receiveSimulator(payload, sig)).isEqualTo(WebhookService.Outcome.DUPLICATE);

        // mesmo com eventos diferentes para a mesma aprovação, o efeito acontece uma única vez
        for (int i = 0; i < 3; i++) {
            processor.processNow(com.lojafacil.payments.ProviderType.SIMULATOR, "k" + i, payment.getProviderCheckoutId(), s.storeId());
        }
        assertThat(data.variant(variantId).getPhysicalQty()).isEqualTo(2);
        assertThat(countEntries(s.storeId(), "ORDER_PAYMENT")).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from stock_movements where type = 'SALE' and store_id = ?", Long.class, s.storeId()))
                .isEqualTo(1);
    }

    @Test
    void assinaturaInvalidaEhRecusada() {
        assertThatThrownBy(() -> webhooks.receiveSimulator("eventId=1&chargeId=x&storeId=1", "assinatura-falsa"))
                .isInstanceOf(ApiException.class).hasMessageContaining("Assinatura");
    }

    @Test
    void notificacaoForaDeOrdemNaoDesaprova() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "20.00", null, "5.00", 2));
        CheckoutResponse r = data.buy(s.slug(), variantId, 1);
        Order o = data.order(s.storeId(), r.orderNumber());
        data.pay(s.storeId(), r.orderNumber(), true);
        Payment p = data.paymentOf(o.getId());
        // notificação antiga "pendente" chegando depois da aprovação
        String result = payments.apply(p.getId(), new com.lojafacil.payments.PaymentProvider.ProviderPayment(
                p.getProviderCheckoutId(), p.getPublicId().toString(), PaymentStatus.PENDING, "PENDING",
                p.getAmount(), "BRL", BigDecimal.ZERO, BigDecimal.ZERO, null, false));
        assertThat(result).isEqualTo("IGNORED_STALE");
        assertThat(data.order(s.storeId(), r.orderNumber()).getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
    }

    @Test
    void valorDivergenteNaoConfirmaPedido() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "20.00", null, "5.00", 2));
        CheckoutResponse r = data.buy(s.slug(), variantId, 1);
        Payment p = data.paymentOf(data.order(s.storeId(), r.orderNumber()).getId());
        String result = payments.apply(p.getId(), new com.lojafacil.payments.PaymentProvider.ProviderPayment(
                "x", p.getPublicId().toString(), PaymentStatus.APPROVED, "approved",
                new BigDecimal("1.00"), "BRL", BigDecimal.ZERO, BigDecimal.ZERO, null, false));
        assertThat(result).isEqualTo("AMOUNT_MISMATCH");
        Order o = data.order(s.storeId(), r.orderNumber());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(o.isRequiresAttention()).isTrue();
    }

    @Test
    void comprasSimultaneasDaUltimaUnidade() throws Exception {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "30.00", null, "10.00", 1));
        int buyers = 8;
        ExecutorService pool = Executors.newFixedThreadPool(buyers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int i = 0; i < buyers; i++) {
            Callable<Boolean> task = () -> {
                start.await();
                try {
                    data.buy(s.slug(), variantId, 1);
                    return true;
                } catch (ApiException e) {
                    return false;
                }
            };
            results.add(pool.submit(task));
        }
        start.countDown();
        int ok = 0;
        for (Future<Boolean> f : results) {
            ok += f.get() ? 1 : 0;
        }
        pool.shutdown();
        assertThat(ok).isEqualTo(1);
        ProductVariant v = data.variant(variantId);
        assertThat(v.getReservedQty()).isEqualTo(1);
        assertThat(v.getPhysicalQty()).isEqualTo(1);
    }

    @Test
    void reservaExpiraELiberaEstoqueEPagamentoTardioReativaSeHouverEstoque() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "40.00", null, "10.00", 1));
        CheckoutResponse r = data.buy(s.slug(), variantId, 1);
        Order o = data.order(s.storeId(), r.orderNumber());
        expireNow(o.getId());
        payments.expireOrder(o.getId());

        Order expired = data.order(s.storeId(), r.orderNumber());
        assertThat(expired.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(expired.getPaymentStatus()).isEqualTo(PaymentStatus.EXPIRED);
        assertThat(data.variant(variantId).getReservedQty()).isZero();

        // cliente pagou depois do prazo e ainda há estoque: pedido reativado, com aviso
        data.pay(s.storeId(), r.orderNumber(), true);
        Order late = data.order(s.storeId(), r.orderNumber());
        assertThat(late.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(late.isRequiresAttention()).isTrue();
        assertThat(data.variant(variantId).getPhysicalQty()).isZero();
    }

    @Test
    void pagamentoTardioSemEstoqueNaoGeraSaldoNegativo() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "40.00", null, "10.00", 1));
        CheckoutResponse first = data.buy(s.slug(), variantId, 1);
        Order o = data.order(s.storeId(), first.orderNumber());
        expireNow(o.getId());
        payments.expireOrder(o.getId());
        // outra pessoa compra e paga a unidade liberada
        CheckoutResponse second = data.buy(s.slug(), variantId, 1);
        data.pay(s.storeId(), second.orderNumber(), true);

        data.pay(s.storeId(), first.orderNumber(), true);
        Order late = data.order(s.storeId(), first.orderNumber());
        assertThat(late.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(late.getPaymentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(late.isRequiresAttention()).isTrue();
        assertThat(data.variant(variantId).getPhysicalQty()).isZero();
    }

    @Test
    void cancelamentoDePedidoPagoReembolsaEDevolveAoEstoque() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "60.00", null, "20.00", 4));
        CheckoutResponse r = data.buy(s.slug(), variantId, 2);
        data.pay(s.storeId(), r.orderNumber(), true);
        Order o = data.order(s.storeId(), r.orderNumber());
        assertThat(data.variant(variantId).getPhysicalQty()).isEqualTo(2);

        payments.cancelOrder(s.storeId(), o.getId(), "Cliente desistiu");

        Order cancelled = data.order(s.storeId(), r.orderNumber());
        assertThat(cancelled.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(cancelled.getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        assertThat(data.variant(variantId).getPhysicalQty()).isEqualTo(4);
        assertThat(sumEntries(s.storeId(), "REFUND")).isEqualByComparingTo("120.00");
    }

    @Test
    void reembolsoParcialNaoUltrapassaOValorPago() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "100.00", null, "20.00", 2));
        CheckoutResponse r = data.buy(s.slug(), variantId, 1);
        data.pay(s.storeId(), r.orderNumber(), true);
        Long orderId = data.order(s.storeId(), r.orderNumber()).getId();

        payments.refund(s.storeId(), orderId, new BigDecimal("30.00"), "Avaria na embalagem");
        assertThat(data.order(s.storeId(), r.orderNumber()).getPaymentStatus()).isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
        assertThatThrownBy(() -> payments.refund(s.storeId(), orderId, new BigDecimal("70.01"), "excesso"))
                .isInstanceOf(ApiException.class);
        payments.refund(s.storeId(), orderId, new BigDecimal("70.00"), "restante");
        assertThat(data.order(s.storeId(), r.orderNumber()).getPaymentStatus()).isEqualTo(PaymentStatus.REFUNDED);
        // a sincronização posterior com o provedor não duplica os reembolsos
        payments.sync(data.paymentOf(orderId).getId());
        assertThat(sumEntries(s.storeId(), "REFUND")).isEqualByComparingTo("100.00");
    }

    @Test
    void pagamentoRecusadoMantemPedidoAberto() {
        TestData.StoreCtx s = data.newStore();
        Long variantId = data.variantOf(data.product(s.storeId(), "10.00", null, "2.00", 1));
        CheckoutResponse r = data.buy(s.slug(), variantId, 1);
        data.pay(s.storeId(), r.orderNumber(), false);
        Order o = data.order(s.storeId(), r.orderNumber());
        assertThat(o.getStatus()).isEqualTo(OrderStatus.OPEN);
        assertThat(o.getPaymentStatus()).isEqualTo(PaymentStatus.DECLINED);
        assertThat(data.variant(variantId).getReservedQty()).isEqualTo(1);
    }

    private void expireNow(Long orderId) {
        jdbc.update("update orders set reservation_expires_at = now() - interval '1 minute' where id = ?", orderId);
        jdbc.update("update stock_reservations set expires_at = now() - interval '1 minute' where order_id = ?", orderId);
    }

    private long countEntries(Long storeId, String source) {
        return jdbc.queryForObject("select count(*) from financial_entries where store_id = ? and source_type = ?", Long.class, storeId, source);
    }

    private BigDecimal sumEntries(Long storeId, String source) {
        return jdbc.queryForObject("select coalesce(sum(amount),0) from financial_entries where store_id = ? and source_type = ?",
                BigDecimal.class, storeId, source);
    }
}
