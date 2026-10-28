package com.lojafacil.inventory;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.catalog.ProductVariant;
import com.lojafacil.catalog.VariantRepository;
import com.lojafacil.inventory.InventoryRepositories.MovementRepo;
import com.lojafacil.inventory.InventoryRepositories.ReservationRepo;
import com.lojafacil.inventory.StockMovement.Type;
import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.security.CurrentUser;

@Service
public class InventoryService {
    private final VariantRepository variants;
    private final MovementRepo movements;
    private final ReservationRepo reservations;
    private final AuditService audit;
    private final CurrentUser currentUser;

    public InventoryService(VariantRepository variants, MovementRepo movements, ReservationRepo reservations,
                            AuditService audit, CurrentUser currentUser) {
        this.variants = variants;
        this.movements = movements;
        this.reservations = reservations;
        this.audit = audit;
        this.currentUser = currentUser;
    }

    @Transactional
    public StockMovement entry(Long storeId, Long variantId, int quantity, String reason, Long supplierId) {
        requirePositive(quantity);
        ProductVariant v = lockOne(storeId, variantId);
        v.setPhysicalQty(v.getPhysicalQty() + quantity);
        return record(v, Type.ENTRY, quantity, reason, null, supplierId);
    }

    @Transactional
    public StockMovement exit(Long storeId, Long variantId, int quantity, String reason) {
        requirePositive(quantity);
        ProductVariant v = lockOne(storeId, variantId);
        if (v.availableQty() < quantity) {
            throw Errors.rule("INSUFFICIENT_STOCK",
                    "Saldo disponível insuficiente (" + v.availableQty() + "). Unidades reservadas para pedidos não podem sair.");
        }
        v.setPhysicalQty(v.getPhysicalQty() - quantity);
        return record(v, Type.EXIT, -quantity, reason, null, null);
    }

    @Transactional
    public StockMovement adjust(Long storeId, Long variantId, int newPhysicalQty, String reason) {
        if (newPhysicalQty < 0) {
            throw Errors.rule("NEGATIVE_STOCK", "O saldo não pode ser negativo.");
        }
        ProductVariant v = lockOne(storeId, variantId);
        if (newPhysicalQty < v.getReservedQty()) {
            throw Errors.rule("BELOW_RESERVED",
                    "O saldo não pode ficar abaixo das " + v.getReservedQty() + " unidades reservadas para pedidos.");
        }
        int delta = newPhysicalQty - v.getPhysicalQty();
        if (delta == 0) {
            throw Errors.rule("NO_CHANGE", "O saldo informado é igual ao atual.");
        }
        v.setPhysicalQty(newPhysicalQty);
        return record(v, Type.ADJUSTMENT, delta, reason, null, null);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void reserve(Long storeId, Long orderId, Map<Long, Integer> quantities, Instant expiresAt) {
        for (ProductVariant v : lockMany(storeId, quantities)) {
            int qty = quantities.get(v.getId());
            if (!v.isSellable()) {
                throw Errors.rule("PRODUCT_UNAVAILABLE", "O produto \"" + v.getProduct().getName() + "\" não está disponível para venda.");
            }
            if (v.availableQty() < qty) {
                throw Errors.rule("INSUFFICIENT_STOCK", insufficientMessage(v));
            }
            v.setReservedQty(v.getReservedQty() + qty);
            reservations.save(new StockReservation(storeId, orderId, v.getId(), qty, expiresAt));
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservations(Long storeId, Long orderId) {
        List<StockReservation> active = reservations.findByOrderIdAndStatus(orderId, StockReservation.Status.ACTIVE);
        if (active.isEmpty()) {
            return;
        }
        Map<Long, ProductVariant> locked = lockedById(storeId, active);
        Instant now = Instant.now();
        for (StockReservation r : active) {
            ProductVariant v = locked.get(r.getVariantId());
            v.setReservedQty(v.getReservedQty() - r.getQuantity());
            r.setStatus(StockReservation.Status.RELEASED);
            r.setResolvedAt(now);
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean commitReservations(Long storeId, Long orderId) {
        List<StockReservation> active = reservations.findByOrderIdAndStatus(orderId, StockReservation.Status.ACTIVE);
        if (active.isEmpty()) {
            return false;
        }
        Map<Long, ProductVariant> locked = lockedById(storeId, active);
        Instant now = Instant.now();
        for (StockReservation r : active) {
            ProductVariant v = locked.get(r.getVariantId());
            v.setReservedQty(v.getReservedQty() - r.getQuantity());
            v.setPhysicalQty(v.getPhysicalQty() - r.getQuantity());
            r.setStatus(StockReservation.Status.CONSUMED);
            r.setResolvedAt(now);
            record(v, Type.SALE, -r.getQuantity(), "Venda - pedido confirmado", orderId, null);
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean commitWithoutReservation(Long storeId, Long orderId, Map<Long, Integer> quantities) {
        List<ProductVariant> locked = lockMany(storeId, quantities);
        boolean allAvailable = locked.stream().allMatch(v -> v.availableQty() >= quantities.get(v.getId()));
        if (!allAvailable) {
            return false;
        }
        for (ProductVariant v : locked) {
            int qty = quantities.get(v.getId());
            v.setPhysicalQty(v.getPhysicalQty() - qty);
            record(v, Type.SALE, -qty, "Venda - pagamento confirmado após expiração da reserva", orderId, null);
        }
        return true;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void restockReturn(Long storeId, Long orderId, Long variantId, int quantity, String reason) {
        requirePositive(quantity);
        ProductVariant v = lockOne(storeId, variantId);
        v.setPhysicalQty(v.getPhysicalQty() + quantity);
        record(v, Type.RETURN, quantity, reason, orderId, null);
    }

    private StockMovement record(ProductVariant v, Type type, int qty, String reason, Long orderId, Long supplierId) {
        String safeReason = reason == null || reason.isBlank() ? type.name() : reason.trim();
        StockMovement m = movements.save(new StockMovement(v, type, qty, safeReason, orderId, supplierId, currentUser.idOrNull()));
        if (orderId == null) {
            audit.record(v.getStoreId(), "STOCK", v.getId(), "STOCK_" + type,
                    v.getSku() + ": " + (qty > 0 ? "+" : "") + qty + " (saldo " + v.getPhysicalQty() + ") - " + safeReason);
        }
        return m;
    }

    private ProductVariant lockOne(Long storeId, Long variantId) {
        List<ProductVariant> list = variants.lockForUpdate(storeId, List.of(variantId));
        if (list.isEmpty()) {
            throw Errors.notFound("Variante");
        }
        return list.get(0);
    }

    private List<ProductVariant> lockMany(Long storeId, Map<Long, Integer> quantities) {
        List<ProductVariant> locked = variants.lockForUpdate(storeId, new TreeMap<>(quantities).keySet());
        if (locked.size() != quantities.size()) {
            throw Errors.rule("PRODUCT_UNAVAILABLE", "Um ou mais produtos não estão mais disponíveis.");
        }
        return locked;
    }

    private Map<Long, ProductVariant> lockedById(Long storeId, List<StockReservation> rs) {
        Map<Long, ProductVariant> map = new TreeMap<>();
        variants.lockForUpdate(storeId, rs.stream().map(StockReservation::getVariantId).distinct().sorted().toList())
                .forEach(v -> map.put(v.getId(), v));
        return map;
    }

    private static void requirePositive(int quantity) {
        if (quantity <= 0) {
            throw Errors.rule("INVALID_QUANTITY", "A quantidade deve ser maior que zero.");
        }
    }

    private static String insufficientMessage(ProductVariant v) {
        String name = v.getProduct().getName() + (v.displayName() != null ? " (" + v.displayName() + ")" : "");
        return v.availableQty() <= 0
                ? "\"" + name + "\" está esgotado."
                : "Restam apenas " + v.availableQty() + " unidade(s) de \"" + name + "\".";
    }
}
