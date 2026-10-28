package com.lojafacil.customers;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.customers.CustomerDtos.AddressData;
import com.lojafacil.customers.CustomerDtos.CustomerDetail;
import com.lojafacil.customers.CustomerDtos.CustomerListItem;
import com.lojafacil.customers.CustomerDtos.CustomerOrderSummary;
import com.lojafacil.customers.CustomerDtos.CustomerRequest;
import com.lojafacil.customers.CustomerDtos.SupplierRequest;
import com.lojafacil.customers.CustomerDtos.SupplierView;
import com.lojafacil.orders.OrderRepository;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.web.PageResponse;

@Service
public class CustomerService {

    private final Customer.Repo customers;
    private final Supplier.Repo suppliers;
    private final OrderRepository orders;

    public CustomerService(Customer.Repo customers, Supplier.Repo suppliers, OrderRepository orders) {
        this.customers = customers;
        this.suppliers = suppliers;
        this.orders = orders;
    }

    // ------------------------------------------------------------------ clientes

    @Transactional(readOnly = true)
    public PageResponse<CustomerListItem> search(Long storeId, String q, Pageable pageable) {
        return PageResponse.of(customers.search(storeId, normalize(q), pageable), CustomerListItem::of);
    }

    @Transactional(readOnly = true)
    public CustomerDetail get(Long storeId, Long id) {
        Customer c = find(storeId, id);
        List<CustomerOrderSummary> recent = orders.findByStoreIdAndCustomerIdOrderByCreatedAtDesc(storeId, id, PageRequest.of(0, 20))
                .map(o -> new CustomerOrderSummary(o.getId(), o.getNumber(), o.getCreatedAt(), o.getTotal(),
                        o.getStatus().name(), o.getPaymentStatus().name(), o.getFulfillmentStatus().name()))
                .getContent();
        long count = orders.countByStoreIdAndCustomerId(storeId, id);
        BigDecimal spent = orders.totalConfirmedForCustomer(storeId, id);
        return new CustomerDetail(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getNotes(), c.getCreatedAt(),
                c.getAddresses().stream().map(AddressData::of).toList(), recent, count, spent);
    }

    @Transactional
    public CustomerDetail create(Long storeId, CustomerRequest req) {
        if (customers.findByEmail(storeId, req.email().trim()).isPresent()) {
            throw Errors.conflict("CUSTOMER_EXISTS", "Já existe um cliente com este e-mail.");
        }
        Customer c = new Customer();
        c.setStoreId(storeId);
        apply(c, req);
        customers.save(c);
        return get(storeId, c.getId());
    }

    @Transactional
    public CustomerDetail update(Long storeId, Long id, CustomerRequest req) {
        Customer c = find(storeId, id);
        customers.findByEmail(storeId, req.email().trim())
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw Errors.conflict("CUSTOMER_EXISTS", "Já existe um cliente com este e-mail.");
                });
        apply(c, req);
        return get(storeId, id);
    }

    /**
     * Vincula um pedido da vitrine a um cliente. Se o e-mail já existe, os dados cadastrados NÃO são
     * alterados (qualquer pessoa pode digitar qualquer e-mail no checkout); o pedido guarda a própria
     * cópia de nome, telefone e endereço.
     */
    @Transactional
    public Customer findOrCreateForOrder(Long storeId, String name, String email, String phone, AddressData address) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        boolean created = customers.insertIfAbsent(storeId, name.trim(), normalized, blankToNull(phone)) == 1;
        Customer c = customers.findByEmail(storeId, normalized).orElseThrow();
        if (created && address != null) {
            c.getAddresses().add(toEntity(c, address));
        }
        return c;
    }

    /** Cliente existente escolhido no pedido manual (sempre da mesma loja). */
    @Transactional(readOnly = true)
    public Customer findForOrder(Long storeId, Long id) {
        return find(storeId, id);
    }

    Customer find(Long storeId, Long id) {
        return customers.findByIdAndStoreId(id, storeId).orElseThrow(() -> Errors.notFound("Cliente"));
    }

    private void apply(Customer c, CustomerRequest req) {
        c.setName(req.name().trim());
        c.setEmail(req.email().trim().toLowerCase(Locale.ROOT));
        c.setPhone(blankToNull(req.phone()));
        c.setNotes(blankToNull(req.notes()));
        c.getAddresses().clear();
        if (req.addresses() != null) {
            req.addresses().forEach(a -> c.getAddresses().add(toEntity(c, a)));
        }
    }

    private static CustomerAddress toEntity(Customer c, AddressData a) {
        CustomerAddress e = new CustomerAddress();
        e.setCustomer(c);
        e.setLabel(blankToNull(a.label()));
        e.setStreet(a.street().trim());
        e.setNumber(a.number().trim());
        e.setComplement(blankToNull(a.complement()));
        e.setDistrict(a.district().trim());
        e.setCity(a.city().trim());
        e.setState(a.state().toUpperCase(Locale.ROOT));
        e.setZip(a.zip());
        return e;
    }

    // ------------------------------------------------------------------ fornecedores

    @Transactional(readOnly = true)
    public PageResponse<SupplierView> searchSuppliers(Long storeId, String q, Pageable pageable) {
        return PageResponse.of(suppliers.search(storeId, normalize(q), pageable), SupplierView::of);
    }

    @Transactional(readOnly = true)
    public List<SupplierView> activeSuppliers(Long storeId) {
        return suppliers.findByStoreIdAndActiveTrueOrderByName(storeId).stream().map(SupplierView::of).toList();
    }

    @Transactional
    public SupplierView saveSupplier(Long storeId, Long id, SupplierRequest req) {
        Supplier s = id == null ? new Supplier()
                : suppliers.findByIdAndStoreId(id, storeId).orElseThrow(() -> Errors.notFound("Fornecedor"));
        s.setStoreId(storeId);
        s.setName(req.name().trim());
        s.setContactName(blankToNull(req.contactName()));
        s.setPhone(blankToNull(req.phone()));
        s.setEmail(blankToNull(req.email()));
        s.setAddress(blankToNull(req.address()));
        s.setNotes(blankToNull(req.notes()));
        s.setActive(req.active());
        return SupplierView.of(suppliers.save(s));
    }

    public Supplier requireSupplier(Long storeId, Long id) {
        return suppliers.findByIdAndStoreId(id, storeId).orElseThrow(() -> Errors.notFound("Fornecedor"));
    }

    private static String normalize(String q) {
        return q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
