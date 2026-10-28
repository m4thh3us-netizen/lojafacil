package com.lojafacil.customers;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class CustomerDtos {
    private CustomerDtos() {
    }

    public record AddressData(
            @Size(max = 40) String label,
            @NotBlank(message = "Informe a rua.") @Size(max = 160) String street,
            @NotBlank(message = "Informe o número.") @Size(max = 20) String number,
            @Size(max = 80) String complement,
            @NotBlank(message = "Informe o bairro.") @Size(max = 80) String district,
            @NotBlank(message = "Informe a cidade.") @Size(max = 80) String city,
            @NotBlank(message = "Informe o estado.") @Pattern(regexp = "^[A-Za-z]{2}$", message = "Use a sigla (ex.: SP).") String state,
            @NotBlank(message = "Informe o CEP.") @Pattern(regexp = "^\\d{5}-?\\d{3}$", message = "CEP inválido.") String zip) {
        public String formatted() {
            return street.trim() + ", " + number.trim() + (complement != null && !complement.isBlank() ? " - " + complement.trim() : "")
                    + " - " + district.trim() + ", " + city.trim() + "/" + state.toUpperCase() + " - CEP " + zip;
        }

        static AddressData of(CustomerAddress a) {
            return new AddressData(a.getLabel(), a.getStreet(), a.getNumber(), a.getComplement(), a.getDistrict(),
                    a.getCity(), a.getState(), a.getZip());
        }
    }

    public record CustomerRequest(
            @NotBlank(message = "Informe o nome.") @Size(max = 120) String name,
            @NotBlank(message = "Informe o e-mail.") @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @Size(max = 30) String phone,
            @Size(max = 2000) String notes,
            @Valid @Size(max = 10) List<AddressData> addresses) {
    }

    public record CustomerListItem(Long id, String name, String email, String phone, Instant createdAt) {
        static CustomerListItem of(Customer c) {
            return new CustomerListItem(c.getId(), c.getName(), c.getEmail(), c.getPhone(), c.getCreatedAt());
        }
    }

    public record CustomerOrderSummary(Long id, int number, Instant createdAt, BigDecimal total, String status,
                                       String paymentStatus, String fulfillmentStatus) {
    }

    public record CustomerDetail(Long id, String name, String email, String phone, String notes, Instant createdAt,
                                 List<AddressData> addresses, List<CustomerOrderSummary> recentOrders,
                                 long orderCount, BigDecimal totalSpent) {
    }

    public record SupplierRequest(
            @NotBlank(message = "Informe o nome ou razão social.") @Size(max = 160) String name,
            @Size(max = 120) String contactName,
            @Size(max = 30) String phone,
            @Email(message = "E-mail inválido.") @Size(max = 160) String email,
            @Size(max = 300) String address,
            @Size(max = 2000) String notes,
            boolean active) {
    }

    public record SupplierView(Long id, String name, String contactName, String phone, String email, String address,
                               String notes, boolean active) {
        static SupplierView of(Supplier s) {
            return new SupplierView(s.getId(), s.getName(), s.getContactName(), s.getPhone(), s.getEmail(),
                    s.getAddress(), s.getNotes(), s.isActive());
        }
    }
}
