package com.lojafacil.orders;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

@Entity
@Table(name = "order_returns")
@Getter
@Setter
public class OrderReturn {
    public enum Status { AWAITING_RECEIPT, RECEIVED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    private Status status;

    private String reason;
    private boolean restocked;
    private Boolean conditionOk;
    private String inspectionNote;
    private Long createdBy;
    private Instant createdAt = Instant.now();
    private Long receivedBy;
    private Instant receivedAt;

    @OneToMany(mappedBy = "orderReturn", cascade = CascadeType.ALL)
    private List<Item> items = new ArrayList<>();

    @Entity(name = "OrderReturnItem")
    @Table(name = "order_return_items")
    @Getter
    @Setter
    public static class Item {
        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "return_id")
        private OrderReturn orderReturn;

        @ManyToOne(fetch = FetchType.LAZY, optional = false)
        @JoinColumn(name = "order_item_id")
        private OrderItem orderItem;

        private int quantity;
    }

    public interface Repo extends JpaRepository<OrderReturn, Long> {
        List<OrderReturn> findByOrderIdOrderByCreatedAt(Long orderId);

        java.util.Optional<OrderReturn> findByIdAndOrderId(Long id, Long orderId);

        @Query("select coalesce(sum(i.quantity), 0) from OrderReturnItem i where i.orderItem.id = :orderItemId")
        int returnedQuantity(Long orderItemId);
    }
}
