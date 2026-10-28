package com.lojafacil.shared.audit;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.lojafacil.shared.security.CurrentUser;

@Service
public class AuditService {
    private final AuditLogRepository repository;
    private final CurrentUser currentUser;

    public AuditService(AuditLogRepository repository, CurrentUser currentUser) {
        this.repository = repository;
        this.currentUser = currentUser;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(Long storeId, String entityType, Long entityId, String action, String details) {
        repository.save(new AuditLog(storeId, currentUser.idOrNull(), entityType, entityId, action, details));
    }

    @Transactional(readOnly = true)
    public Page<AuditLog> list(Long storeId, Pageable pageable) {
        return repository.findByStoreIdOrderByCreatedAtDesc(storeId, pageable);
    }

    public record AuditView(Long id, Long userId, String entityType, Long entityId, String action, String details, Instant createdAt) {
        public static AuditView of(AuditLog a) {
            return new AuditView(a.getId(), a.getUserId(), a.getEntityType(), a.getEntityId(), a.getAction(), a.getDetails(), a.getCreatedAt());
        }
    }

    @Repository
    public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
        Page<AuditLog> findByStoreIdOrderByCreatedAtDesc(Long storeId, Pageable pageable);
    }
}
