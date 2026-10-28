package com.lojafacil.orders;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.catalog.ProductVariant;
import com.lojafacil.catalog.VariantRepository;
import com.lojafacil.orders.OrderDtos.CartLine;
import com.lojafacil.orders.OrderDtos.Quote;
import com.lojafacil.orders.OrderDtos.QuoteLine;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.money.Money;
import com.lojafacil.store.Store;
import com.lojafacil.store.StoreDtos;

@Service
public class PricingService {
    private final VariantRepository variants;

    public PricingService(VariantRepository variants) {
        this.variants = variants;
    }

    public static Map<Long, Integer> aggregate(List<CartLine> lines) {
        Map<Long, Integer> map = new LinkedHashMap<>();
        for (CartLine l : lines) {
            map.merge(l.variantId(), l.quantity(), Integer::sum);
        }
        map.values().forEach(q -> {
            if (q > 999) {
                throw Errors.rule("QUANTITY_LIMIT", "Quantidade máxima por item: 999.");
            }
        });
        return map;
    }

    public static BigDecimal deliveryFee(Store store, DeliveryMethod method) {
        if (method == DeliveryMethod.PICKUP) {
            if (!store.isPickupEnabled()) {
                throw Errors.rule("DELIVERY_UNAVAILABLE", "Esta loja não oferece retirada no local.");
            }
            return Money.ZERO;
        }
        if (!store.isLocalDeliveryEnabled()) {
            throw Errors.rule("DELIVERY_UNAVAILABLE", "Esta loja não oferece entrega local.");
        }
        return Money.normalize(store.getLocalDeliveryFee());
    }

    @Transactional(readOnly = true)
    public Quote quote(Store store, DeliveryMethod method, List<CartLine> lines) {
        Map<Long, Integer> quantities = aggregate(lines);
        Map<Long, ProductVariant> found = variants.findAllById(quantities.keySet()).stream()
                .filter(v -> v.getStoreId().equals(store.getId()))
                .collect(Collectors.toMap(ProductVariant::getId, Function.identity()));
        List<QuoteLine> out = new ArrayList<>();
        BigDecimal subtotal = Money.ZERO;
        BigDecimal discount = Money.ZERO;
        boolean valid = true;
        for (var e : quantities.entrySet()) {
            ProductVariant v = found.get(e.getKey());
            int qty = e.getValue();
            if (v == null || !v.isSellable() || !v.getProduct().isVisible()) {
                valid = false;
                out.add(new QuoteLine(e.getKey(), "Produto indisponível", null, null, null, qty, Money.ZERO, Money.ZERO,
                        Money.ZERO, false, 0, "Este produto não está mais disponível."));
                continue;
            }
            BigDecimal unit = v.listPrice();
            BigDecimal unitDiscount = v.unitDiscount();
            BigDecimal line = Money.times(unit.subtract(unitDiscount), qty);
            String problem = null;
            if (v.availableQty() < qty) {
                valid = false;
                problem = v.availableQty() <= 0 ? "Esgotado." : "Disponível: " + v.availableQty() + " unidade(s).";
            }
            subtotal = subtotal.add(Money.times(unit, qty));
            discount = discount.add(Money.times(unitDiscount, qty));
            out.add(new QuoteLine(v.getId(), v.getProduct().getName(), v.displayName(), v.getSku(),
                    StoreDtos.fileUrl(v.getProduct().mainImagePath()), qty, unit, unitDiscount, line,
                    v.availableQty() >= qty, Math.max(v.availableQty(), 0), problem));
        }
        BigDecimal fee;
        try {
            fee = deliveryFee(store, method);
        } catch (RuntimeException ex) {
            fee = Money.ZERO;
            valid = false;
        }
        BigDecimal total = subtotal.subtract(discount).add(fee);
        return new Quote(out, subtotal, discount, fee, total, valid && !out.isEmpty());
    }

    static void fillItems(Order order, Map<Long, Integer> quantities, Map<Long, ProductVariant> lockedVariants) {
        BigDecimal subtotal = Money.ZERO;
        BigDecimal discount = Money.ZERO;
        for (var e : quantities.entrySet()) {
            ProductVariant v = lockedVariants.get(e.getKey());
            int qty = e.getValue();
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(v.getProduct().getId());
            item.setVariantId(v.getId());
            item.setProductName(v.getProduct().getName());
            item.setVariantName(v.displayName());
            item.setSku(v.getSku());
            item.setQuantity(qty);
            item.setUnitPrice(v.listPrice());
            item.setUnitDiscount(v.unitDiscount());
            item.setLineTotal(Money.times(v.finalUnitPrice(), qty));
            item.setUnitCost(Money.normalize(v.getProduct().getCostPrice()));
            order.getItems().add(item);
            subtotal = subtotal.add(Money.times(item.getUnitPrice(), qty));
            discount = discount.add(Money.times(item.getUnitDiscount(), qty));
        }
        order.setSubtotal(subtotal);
        order.setDiscountTotal(discount);
        order.setTotal(subtotal.subtract(discount).add(order.getDeliveryFee()));
    }
}
