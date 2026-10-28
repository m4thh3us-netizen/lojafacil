package com.lojafacil.inventory;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.auth.User;
import com.lojafacil.auth.UserRepository;
import com.lojafacil.catalog.ProductVariant;
import com.lojafacil.catalog.VariantRepository;
import com.lojafacil.inventory.InventoryRepositories.MovementRepo;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.web.PageResponse;

/** Consultas de estoque (somente leitura). */
@Service
@Transactional(readOnly = true)
public class InventoryQueries {

    public static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");

    public record StockView(Long variantId, Long productId, String productName, String sku, String variantName,
                            int physicalQty, int reservedQty, int availableQty, int minStock, boolean lowStock) {
        public static StockView of(ProductVariant v) {
            return new StockView(v.getId(), v.getProduct().getId(), v.getProduct().getName(), v.getSku(), v.displayName(),
                    v.getPhysicalQty(), v.getReservedQty(), v.availableQty(), v.getProduct().getMinStock(),
                    v.availableQty() <= v.getProduct().getMinStock());
        }
    }

    public record MovementView(Long id, Long variantId, String productName, String sku, String variantName,
                               StockMovement.Type type, int quantity, int balanceAfter, String reason,
                               Long orderId, String userName, Instant createdAt) {
    }

    private final VariantRepository variants;
    private final MovementRepo movements;
    private final UserRepository users;

    public InventoryQueries(VariantRepository variants, MovementRepo movements, UserRepository users) {
        this.variants = variants;
        this.movements = movements;
        this.users = users;
    }

    public PageResponse<StockView> stock(Long storeId, String q, boolean lowOnly, Pageable pageable) {
        return PageResponse.of(variants.search(storeId, q.trim().toLowerCase(), lowOnly, pageable), StockView::of);
    }

    public PageResponse<StockView> alerts(Long storeId, Pageable pageable) {
        return PageResponse.of(variants.findLowStock(storeId, pageable), StockView::of);
    }

    public StockView stockOf(Long storeId, Long variantId) {
        return variants.findByIdAndStoreId(variantId, storeId).map(StockView::of)
                .orElseThrow(() -> Errors.notFound("Variante"));
    }

    public PageResponse<MovementView> movements(Long storeId, Long variantId, Long productId, StockMovement.Type type,
                                                LocalDate from, LocalDate to, Pageable pageable) {
        Instant start = (from != null ? from : LocalDate.of(2000, 1, 1)).atStartOfDay(ZONE).toInstant();
        Instant end = (to != null ? to.plusDays(1) : LocalDate.now(ZONE).plusDays(1)).atStartOfDay(ZONE).toInstant();
        Page<StockMovement> result = movements.search(storeId, variantId, productId, type, start, end, pageable);
        // Busca os nomes dos usuários de uma só vez (evita uma consulta por linha - problema N+1)
        Map<Long, String> userNames = users.findAllById(result.getContent().stream()
                        .map(StockMovement::getUserId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, User::getFullName));
        return PageResponse.of(result, m -> new MovementView(m.getId(), m.getVariant().getId(),
                m.getVariant().getProduct().getName(), m.getVariant().getSku(), m.getVariant().displayName(),
                m.getType(), m.getQuantity(), m.getBalanceAfter(), m.getReason(), m.getOrderId(),
                m.getUserId() == null ? "Sistema" : userNames.getOrDefault(m.getUserId(), "—"), m.getCreatedAt()));
    }
}
