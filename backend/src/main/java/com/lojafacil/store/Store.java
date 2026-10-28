package com.lojafacil.store;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Uma loja (tenant). Todos os dados de negócio pertencem a exatamente uma loja. */
@Entity
@Table(name = "stores")
@Getter
@Setter
public class Store {

    public static final String DEFAULT_PRIMARY = "#1F5EFF";
    public static final String DEFAULT_SECONDARY = "#0F172A";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String slug;

    @Column(nullable = false)
    private String name;

    private String description;
    private String logoPath;
    private String bannerPath;
    private String primaryColor = DEFAULT_PRIMARY;
    private String secondaryColor = DEFAULT_SECONDARY;
    private String contactEmail;
    private String contactPhone;
    private String whatsapp;
    private String instagram;
    private String facebook;
    private String addressLine;
    private String addressCity;
    private String addressState;
    private String addressZip;
    private String serviceHours;
    private boolean pickupEnabled = true;
    private boolean localDeliveryEnabled;
    private BigDecimal localDeliveryFee = BigDecimal.ZERO;
    /** Minutos que o estoque fica reservado aguardando pagamento. */
    private int reservationMinutes = 30;
    private boolean demo;

    // ------------------------------------------------ aparência do painel (independente da vitrine)
    public static final String DEFAULT_PANEL_COLOR = "#3B4656";
    public static final int DEFAULT_PANEL_DIM = 25;
    public static final int DEFAULT_PANEL_GLASS = 74;

    public enum PanelBackground { PHOTO, COLOR }

    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    @Column(name = "panel_bg_mode")
    private PanelBackground panelBgMode = PanelBackground.COLOR;
    @Column(name = "panel_bg_image_path")
    private String panelBgImagePath;
    @Column(name = "panel_bg_color")
    private String panelBgColor = DEFAULT_PANEL_COLOR;
    @Column(name = "panel_bg_pos_x")
    private int panelBgPosX = 50;
    @Column(name = "panel_bg_pos_y")
    private int panelBgPosY = 50;
    @Column(name = "panel_bg_dim")
    private int panelBgDim = DEFAULT_PANEL_DIM;
    @Column(name = "panel_glass_opacity")
    private int panelGlassOpacity = DEFAULT_PANEL_GLASS;

    @Column(nullable = false, updatable = false)
    private Instant createdAt = Instant.now();
    private Instant updatedAt = Instant.now();

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }
}
