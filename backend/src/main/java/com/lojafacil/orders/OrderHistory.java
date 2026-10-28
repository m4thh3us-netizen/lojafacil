package com.lojafacil.orders;

import java.time.Instant;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "order_history")
@Getter
@NoArgsConstructor
public class OrderHistory {
    public enum Field { ORDER, PAYMENT, FULFILLMENT, NOTE, RETURN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    private Long userId;

    @Column(nullable = false)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Field field;

    private String fromValue;
    private String toValue;
    private String note;
    private Instant createdAt = Instant.now();

    public OrderHistory(Long orderId, Long userId, Field field, String fromValue, String toValue, String note) {
        this.orderId = orderId;
        this.userId = userId;
        this.field = field;
        this.fromValue = fromValue;
        this.toValue = toValue;
        this.note = note != null && note.length() > 500 ? note.substring(0, 500) : note;
    }

    public interface Repo extends JpaRepository<OrderHistory, Long> {
        List<OrderHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
    }
}
