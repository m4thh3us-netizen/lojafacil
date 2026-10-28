package com.lojafacil.payments;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.lojafacil.inventory.InventoryRepositories.ReservationRepo;
import com.lojafacil.shared.config.AppProperties;

@Component
public class PaymentJobs {
    private static final Logger log = LoggerFactory.getLogger(PaymentJobs.class);

    private final ReservationRepo reservations;
    private final Payment.Repo payments;
    private final PaymentService paymentService;
    private final AppProperties props;

    public PaymentJobs(ReservationRepo reservations, Payment.Repo payments, PaymentService paymentService, AppProperties props) {
        this.reservations = reservations;
        this.payments = payments;
        this.paymentService = paymentService;
        this.props = props;
    }

    @Scheduled(fixedDelayString = "${app.jobs.expiration-delay-ms:60000}", initialDelay = 20_000)
    public void expireReservations() {
        List<Long> orderIds = reservations.findOrdersWithExpiredReservations(Instant.now(), PageRequest.of(0, 100));
        for (Long orderId : orderIds) {
            try {
                paymentService.expireOrder(orderId);
            } catch (RuntimeException e) {
                log.warn("Falha ao expirar pedido {}: {}", orderId, e.getMessage());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.jobs.reconcile-delay-ms:300000}", initialDelay = 60_000)
    public void reconcilePending() {
        Instant before = Instant.now().minus(props.payments().reconcileAfterMinutes(), ChronoUnit.MINUTES);
        for (Payment p : payments.findPendingOnlineBefore(before, PageRequest.of(0, 50))) {
            try {
                paymentService.sync(p.getId());
            } catch (RuntimeException e) {
                log.debug("Conciliação do pagamento {} não concluída: {}", p.getId(), e.getMessage());
            }
        }
    }
}
