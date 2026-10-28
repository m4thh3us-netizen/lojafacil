package com.lojafacil.finance;

import java.time.LocalDate;
import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.finance.FinanceDtos.CashFlow;
import com.lojafacil.finance.FinanceDtos.CategoryRequest;
import com.lojafacil.finance.FinanceDtos.CategoryView;
import com.lojafacil.finance.FinanceDtos.EntryRequest;
import com.lojafacil.finance.FinanceDtos.EntryView;
import com.lojafacil.finance.FinanceDtos.ReasonRequest;
import com.lojafacil.finance.FinanceDtos.SettleRequest;
import com.lojafacil.finance.FinanceDtos.Summary;
import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.web.PageResponse;
import com.lojafacil.shared.web.Pages;

import io.swagger.v3.oas.annotations.tags.Tag;

/** Financeiro: disponível para Proprietário e Gerente (Operador não acessa). */
@RestController
@RequestMapping("/api/admin/finance")
@PreAuthorize(Access.MANAGEMENT)
@Tag(name = "Financeiro")
public class FinanceController {

    private final FinanceService finance;
    private final CurrentUser currentUser;

    public FinanceController(FinanceService finance, CurrentUser currentUser) {
        this.finance = finance;
        this.currentUser = currentUser;
    }

    @GetMapping("/categories")
    public List<CategoryView> categories() {
        return finance.listCategories(currentUser.storeId());
    }

    @PostMapping("/categories")
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryView createCategory(@Valid @RequestBody CategoryRequest req) {
        return finance.saveCategory(currentUser.storeId(), null, req);
    }

    @PutMapping("/categories/{id}")
    public CategoryView updateCategory(@PathVariable Long id, @Valid @RequestBody CategoryRequest req) {
        return finance.saveCategory(currentUser.storeId(), id, req);
    }

    @GetMapping("/entries")
    public PageResponse<EntryView> entries(@RequestParam(required = false) EntryType type,
                                           @RequestParam(required = false) FinancialEntry.Status status,
                                           @RequestParam(required = false) Long categoryId,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                           @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                           @RequestParam(defaultValue = "due") String dateField,
                                           @RequestParam(defaultValue = "false") boolean overdueOnly,
                                           @RequestParam(required = false) String q,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "30") int size) {
        Sort sort = "paid".equals(dateField)
                ? Sort.by(Sort.Direction.DESC, "paidDate").and(Sort.by(Sort.Direction.DESC, "id"))
                : Sort.by("dueDate").and(Sort.by("id"));
        return finance.search(currentUser.storeId(), type, status, categoryId, from, to, dateField, overdueOnly, q,
                Pages.of(page, size, sort));
    }

    @PostMapping("/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public List<EntryView> create(@Valid @RequestBody EntryRequest req) {
        return finance.create(currentUser.storeId(), req);
    }

    @PostMapping("/entries/{id}/settle")
    public EntryView settle(@PathVariable Long id, @Valid @RequestBody SettleRequest req) {
        return finance.settle(currentUser.storeId(), id, req.paidDate());
    }

    @PostMapping("/entries/{id}/cancel")
    public EntryView cancel(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return finance.cancel(currentUser.storeId(), id, req.reason());
    }

    @PostMapping("/entries/{id}/reverse")
    public EntryView reverse(@PathVariable Long id, @Valid @RequestBody ReasonRequest req) {
        return finance.reverse(currentUser.storeId(), id, req.reason());
    }

    @GetMapping("/cash-flow")
    public CashFlow cashFlow(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                             @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.cashFlow(currentUser.storeId(), from, to);
    }

    @GetMapping("/summary")
    public Summary summary(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                           @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return finance.summary(currentUser.storeId(), from, to);
    }
}
