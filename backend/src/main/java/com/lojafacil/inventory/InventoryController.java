package com.lojafacil.inventory;

import java.time.LocalDate;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.inventory.InventoryQueries.MovementView;
import com.lojafacil.inventory.InventoryQueries.StockView;
import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.web.PageResponse;
import com.lojafacil.shared.web.Pages;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin/inventory")
@PreAuthorize(Access.OPERATIONS)
@Tag(name = "Estoque")
public class InventoryController {

    public enum ManualType { ENTRY, EXIT, ADJUSTMENT }

    public record MovementRequest(
            @NotNull Long variantId,
            @NotNull ManualType type,
            /** Quantidade movimentada (entrada/saída) ou novo saldo físico (ajuste). */
            @NotNull @Min(0) @Max(1_000_000) Integer quantity,
            @NotBlank(message = "Informe o motivo.") @Size(min = 3, max = 300, message = "Descreva o motivo (3 a 300 caracteres).") String reason,
            Long supplierId) {
    }

    private final InventoryService inventory;
    private final InventoryQueries queries;
    private final CurrentUser currentUser;

    public InventoryController(InventoryService inventory, InventoryQueries queries, CurrentUser currentUser) {
        this.inventory = inventory;
        this.queries = queries;
        this.currentUser = currentUser;
    }

    @GetMapping("/stock")
    public PageResponse<StockView> stock(@RequestParam(defaultValue = "") String q,
                                         @RequestParam(defaultValue = "false") boolean lowOnly,
                                         @RequestParam(defaultValue = "0") int page,
                                         @RequestParam(defaultValue = "20") int size) {
        return queries.stock(currentUser.storeId(), q, lowOnly, Pages.of(page, size, Sort.unsorted()));
    }

    @GetMapping("/alerts")
    public PageResponse<StockView> alerts(@RequestParam(defaultValue = "0") int page,
                                          @RequestParam(defaultValue = "20") int size) {
        return queries.alerts(currentUser.storeId(), Pages.of(page, size, Sort.unsorted()));
    }

    @PostMapping("/movements")
    @ResponseStatus(HttpStatus.CREATED)
    public StockView move(@Valid @RequestBody MovementRequest req) {
        Long storeId = currentUser.storeId();
        switch (req.type()) {
            case ENTRY -> inventory.entry(storeId, req.variantId(), req.quantity(), req.reason(), req.supplierId());
            case EXIT -> inventory.exit(storeId, req.variantId(), req.quantity(), req.reason());
            case ADJUSTMENT -> inventory.adjust(storeId, req.variantId(), req.quantity(), req.reason());
        }
        return queries.stockOf(storeId, req.variantId());
    }

    @GetMapping("/movements")
    public PageResponse<MovementView> movements(
            @RequestParam(required = false) Long variantId,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) StockMovement.Type type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "30") int size) {
        return queries.movements(currentUser.storeId(), variantId, productId, type, from, to, Pages.of(page, size, Sort.unsorted()));
    }
}
