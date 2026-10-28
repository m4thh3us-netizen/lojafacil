package com.lojafacil.store;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class StoreDtos {
    private StoreDtos() {
    }

    public record StoreSettingsRequest(
            @NotBlank(message = "Informe o nome da loja.") @Size(max = 120) String name,
            @Size(max = 1000) String description,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida.") String primaryColor,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida.") String secondaryColor,
            @Email(message = "E-mail inválido.") @Size(max = 160) String contactEmail,
            @Size(max = 30) String contactPhone,
            @Size(max = 30) String whatsapp,
            @Size(max = 120) String instagram,
            @Size(max = 120) String facebook,
            @Size(max = 200) String addressLine,
            @Size(max = 100) String addressCity,
            @Pattern(regexp = "^$|^[A-Za-z]{2}$", message = "Use a sigla do estado (ex.: SP).") String addressState,
            @Pattern(regexp = "^$|^\\d{5}-?\\d{3}$", message = "CEP inválido.") String addressZip,
            @Size(max = 300) String serviceHours,
            boolean pickupEnabled,
            boolean localDeliveryEnabled,
            @NotNull @DecimalMin(value = "0.00", message = "A taxa não pode ser negativa.")
            @DecimalMax(value = "9999.99") BigDecimal localDeliveryFee,
            @Min(value = 5, message = "Mínimo de 5 minutos.") @Max(value = 1440, message = "Máximo de 24 horas.") int reservationMinutes) {
    }

    public record StoreSettingsView(
            Long id, String slug, String name, String description,
            String logoUrl, String bannerUrl,
            String primaryColor, String secondaryColor, String onPrimaryColor, String onSecondaryColor,
            String contactEmail, String contactPhone, String whatsapp, String instagram, String facebook,
            String addressLine, String addressCity, String addressState, String addressZip, String serviceHours,
            boolean pickupEnabled, boolean localDeliveryEnabled, BigDecimal localDeliveryFee,
            int reservationMinutes, boolean demo) {
        public static StoreSettingsView of(Store s) {
            return new StoreSettingsView(s.getId(), s.getSlug(), s.getName(), s.getDescription(),
                    fileUrl(s.getLogoPath()), fileUrl(s.getBannerPath()),
                    s.getPrimaryColor(), s.getSecondaryColor(),
                    ColorContrast.bestTextOn(s.getPrimaryColor()), ColorContrast.bestTextOn(s.getSecondaryColor()),
                    s.getContactEmail(), s.getContactPhone(), s.getWhatsapp(), s.getInstagram(), s.getFacebook(),
                    s.getAddressLine(), s.getAddressCity(), s.getAddressState(), s.getAddressZip(), s.getServiceHours(),
                    s.isPickupEnabled(), s.isLocalDeliveryEnabled(), s.getLocalDeliveryFee(),
                    s.getReservationMinutes(), s.isDemo());
        }
    }

    public record PublicStoreView(
            String slug, String name, String description, String logoUrl, String bannerUrl,
            String primaryColor, String secondaryColor, String onPrimaryColor, String onSecondaryColor,
            String contactEmail, String contactPhone, String whatsapp, String instagram, String facebook,
            String address, String serviceHours,
            boolean pickupEnabled, boolean localDeliveryEnabled, BigDecimal localDeliveryFee, boolean demo) {
        public static PublicStoreView of(Store s) {
            String address = s.getAddressLine() == null ? null
                    : s.getAddressLine() + (s.getAddressCity() != null ? " - " + s.getAddressCity() : "")
                      + (s.getAddressState() != null ? "/" + s.getAddressState() : "");
            return new PublicStoreView(s.getSlug(), s.getName(), s.getDescription(),
                    fileUrl(s.getLogoPath()), fileUrl(s.getBannerPath()),
                    s.getPrimaryColor(), s.getSecondaryColor(),
                    ColorContrast.bestTextOn(s.getPrimaryColor()), ColorContrast.bestTextOn(s.getSecondaryColor()),
                    s.getContactEmail(), s.getContactPhone(), s.getWhatsapp(), s.getInstagram(), s.getFacebook(),
                    address, s.getServiceHours(), s.isPickupEnabled(), s.isLocalDeliveryEnabled(),
                    s.getLocalDeliveryFee(), s.isDemo());
        }
    }

    public record AppearanceRequest(
            @NotNull Store.PanelBackground mode,

            @Size(max = 255) String imagePath,
            @NotBlank @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Cor inválida.") String color,
            @Min(0) @Max(100) int posX,
            @Min(0) @Max(100) int posY,
            @Min(value = 0, message = "Escurecimento mínimo: 0%.") @Max(value = 70, message = "Escurecimento máximo: 70%.") int dim,
            @Min(value = 55, message = "Opacidade mínima do vidro: 55%.") @Max(value = 95, message = "Opacidade máxima do vidro: 95%.") int glass) {
    }

    public record AppearanceView(Store.PanelBackground mode, String imagePath, String imageUrl, String color,
                                 int posX, int posY, int dim, int glass) {
        public static AppearanceView of(Store s) {
            return new AppearanceView(s.getPanelBgMode(), s.getPanelBgImagePath(), fileUrl(s.getPanelBgImagePath()),
                    s.getPanelBgColor(), s.getPanelBgPosX(), s.getPanelBgPosY(), s.getPanelBgDim(), s.getPanelGlassOpacity());
        }
    }

    public record UploadedPhoto(String path, String url) {
    }

    public static String fileUrl(String path) {
        return path == null ? null : "/files/" + path;
    }
}
