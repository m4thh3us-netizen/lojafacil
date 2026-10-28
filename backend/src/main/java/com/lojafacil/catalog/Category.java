package com.lojafacil.catalog;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import org.springframework.data.jpa.repository.JpaRepository;

@Entity
@Table(name = "categories")
@Getter
@Setter
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long storeId;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    private boolean active = true;

    public interface Repo extends JpaRepository<Category, Long> {
        List<Category> findByStoreIdOrderByName(Long storeId);

        List<Category> findByStoreIdAndActiveTrueOrderByName(Long storeId);

        Optional<Category> findByIdAndStoreId(Long id, Long storeId);

        Optional<Category> findByStoreIdAndSlug(Long storeId, String slug);

        boolean existsByStoreIdAndSlug(Long storeId, String slug);
    }
}
