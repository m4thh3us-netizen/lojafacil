package com.lojafacil.customers;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "customer_addresses")
@Getter
@Setter
public class CustomerAddress {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id")
    private Customer customer;

    private String label;
    private String street;
    private String number;
    private String complement;
    private String district;
    private String city;
    private String state;
    private String zip;

    /** Endereço em uma linha, usado como "fotografia" no pedido. */
    public String formatted() {
        return street + ", " + number + (complement != null ? " - " + complement : "")
                + " - " + district + ", " + city + "/" + state + " - CEP " + zip;
    }
}
