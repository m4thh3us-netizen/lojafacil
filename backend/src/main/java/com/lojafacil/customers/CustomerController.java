package com.lojafacil.customers;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
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

import com.lojafacil.customers.CustomerDtos.CustomerDetail;
import com.lojafacil.customers.CustomerDtos.CustomerListItem;
import com.lojafacil.customers.CustomerDtos.CustomerRequest;
import com.lojafacil.customers.CustomerDtos.SupplierRequest;
import com.lojafacil.customers.CustomerDtos.SupplierView;
import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.web.PageResponse;
import com.lojafacil.shared.web.Pages;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize(Access.OPERATIONS)
@Tag(name = "Clientes e fornecedores")
public class CustomerController {

    private final CustomerService service;
    private final CurrentUser currentUser;

    public CustomerController(CustomerService service, CurrentUser currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping("/customers")
    public PageResponse<CustomerListItem> customers(@RequestParam(required = false) String q,
                                                    @RequestParam(defaultValue = "0") int page,
                                                    @RequestParam(defaultValue = "20") int size) {
        return service.search(currentUser.storeId(), q, Pages.of(page, size, Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/customers/{id}")
    public CustomerDetail customer(@PathVariable Long id) {
        return service.get(currentUser.storeId(), id);
    }

    @PostMapping("/customers")
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerDetail create(@Valid @RequestBody CustomerRequest req) {
        return service.create(currentUser.storeId(), req);
    }

    @PutMapping("/customers/{id}")
    public CustomerDetail update(@PathVariable Long id, @Valid @RequestBody CustomerRequest req) {
        return service.update(currentUser.storeId(), id, req);
    }

    @GetMapping("/suppliers")
    public PageResponse<SupplierView> suppliers(@RequestParam(required = false) String q,
                                                @RequestParam(defaultValue = "0") int page,
                                                @RequestParam(defaultValue = "20") int size) {
        return service.searchSuppliers(currentUser.storeId(), q, Pages.of(page, size, Sort.by("name").and(Sort.by("id"))));
    }

    @GetMapping("/suppliers/active")
    public List<SupplierView> activeSuppliers() {
        return service.activeSuppliers(currentUser.storeId());
    }

    @PostMapping("/suppliers")
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierView createSupplier(@Valid @RequestBody SupplierRequest req) {
        return service.saveSupplier(currentUser.storeId(), null, req);
    }

    @PutMapping("/suppliers/{id}")
    public SupplierView updateSupplier(@PathVariable Long id, @Valid @RequestBody SupplierRequest req) {
        return service.saveSupplier(currentUser.storeId(), id, req);
    }
}
