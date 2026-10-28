package com.lojafacil.payments;

import java.time.Instant;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
public class WebhookEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String provider;
    private String eventKey;
    private String resourceId;
    private Long storeId;
    private Instant receivedAt = Instant.now();
    private Instant processedAt;
    private String result;

    public interface Repo extends JpaRepository<WebhookEvent, Long> {
        @Transactional
        @Modifying
        @Query(value = """
                insert into webhook_events (provider, event_key, resource_id, store_id)
                values (:provider, :eventKey, :resourceId, :storeId)
                on conflict (provider, event_key) do nothing
                """, nativeQuery = true)
        int insertIfAbsent(String provider, String eventKey, String resourceId, Long storeId);

        @Transactional
        @Modifying
        @Query("update WebhookEvent e set e.processedAt = :now, e.result = :result where e.provider = :provider and e.eventKey = :eventKey")
        void markProcessed(String provider, String eventKey, String result, Instant now);
    }
}
