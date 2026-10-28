package com.lojafacil.store;

import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.error.Errors;
import com.lojafacil.shared.files.FileStorageService;
import com.lojafacil.store.StoreDtos.PublicStoreView;
import com.lojafacil.store.StoreDtos.StoreSettingsRequest;
import com.lojafacil.store.StoreDtos.StoreSettingsView;

@Service
public class StoreService {

    public enum ImageKind { LOGO, BANNER }

    private final StoreRepository stores;
    private final FileStorageService files;
    private final AuditService audit;

    public StoreService(StoreRepository stores, FileStorageService files, AuditService audit) {
        this.stores = stores;
        this.files = files;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public Store get(Long storeId) {
        return stores.findById(storeId).orElseThrow(() -> Errors.notFound("Loja"));
    }

    /** Busca para a vitrine pública: identificador inexistente responde 404. */
    @Transactional(readOnly = true)
    public Store getBySlug(String slug) {
        return stores.findBySlug(slug.toLowerCase(Locale.ROOT)).orElseThrow(() -> Errors.notFound("Loja"));
    }

    @Transactional(readOnly = true)
    public PublicStoreView publicView(String slug) {
        return PublicStoreView.of(getBySlug(slug));
    }

    @Transactional(readOnly = true)
    public StoreSettingsView settings(Long storeId) {
        return StoreSettingsView.of(get(storeId));
    }

    @Transactional
    public StoreSettingsView update(Long storeId, StoreSettingsRequest r) {
        requireReadable(r.primaryColor(), "principal");
        requireReadable(r.secondaryColor(), "secundária");
        if (!r.pickupEnabled() && !r.localDeliveryEnabled()) {
            throw Errors.rule("NO_DELIVERY_METHOD", "Habilite ao menos uma forma de entrega: retirada ou entrega local.");
        }
        Store s = get(storeId);
        s.setName(r.name().trim());
        s.setDescription(trim(r.description()));
        s.setPrimaryColor(r.primaryColor().toUpperCase(Locale.ROOT));
        s.setSecondaryColor(r.secondaryColor().toUpperCase(Locale.ROOT));
        s.setContactEmail(trim(r.contactEmail()));
        s.setContactPhone(trim(r.contactPhone()));
        s.setWhatsapp(trim(r.whatsapp()));
        s.setInstagram(trim(r.instagram()));
        s.setFacebook(trim(r.facebook()));
        s.setAddressLine(trim(r.addressLine()));
        s.setAddressCity(trim(r.addressCity()));
        s.setAddressState(r.addressState() == null || r.addressState().isBlank() ? null : r.addressState().toUpperCase(Locale.ROOT));
        s.setAddressZip(trim(r.addressZip()));
        s.setServiceHours(trim(r.serviceHours()));
        s.setPickupEnabled(r.pickupEnabled());
        s.setLocalDeliveryEnabled(r.localDeliveryEnabled());
        s.setLocalDeliveryFee(r.localDeliveryFee());
        s.setReservationMinutes(r.reservationMinutes());
        audit.record(storeId, "STORE", storeId, "SETTINGS_UPDATED", null);
        return StoreSettingsView.of(s);
    }

    @Transactional
    public StoreSettingsView resetTheme(Long storeId) {
        Store s = get(storeId);
        s.setPrimaryColor(Store.DEFAULT_PRIMARY);
        s.setSecondaryColor(Store.DEFAULT_SECONDARY);
        audit.record(storeId, "STORE", storeId, "THEME_RESET", null);
        return StoreSettingsView.of(s);
    }

    @Transactional
    public StoreSettingsView uploadImage(Long storeId, ImageKind kind, MultipartFile file) {
        Store s = get(storeId);
        String path = files.storeImage(storeId, file, kind == ImageKind.LOGO ? 512 : 1920);
        String old;
        if (kind == ImageKind.LOGO) {
            old = s.getLogoPath();
            s.setLogoPath(path);
        } else {
            old = s.getBannerPath();
            s.setBannerPath(path);
        }
        files.delete(old);
        audit.record(storeId, "STORE", storeId, kind + "_UPLOADED", null);
        return StoreSettingsView.of(s);
    }

    @Transactional
    public StoreSettingsView removeImage(Long storeId, ImageKind kind) {
        Store s = get(storeId);
        if (kind == ImageKind.LOGO) {
            files.delete(s.getLogoPath());
            s.setLogoPath(null);
        } else {
            files.delete(s.getBannerPath());
            s.setBannerPath(null);
        }
        return StoreSettingsView.of(s);
    }

    // ------------------------------------------------------------------ aparência do painel

    @Transactional(readOnly = true)
    public StoreDtos.AppearanceView appearance(Long storeId) {
        return StoreDtos.AppearanceView.of(get(storeId));
    }

    /**
     * Grava a fotografia otimizada, mas não altera a loja: assim o lojista pode visualizar
     * antes de salvar. Fotos enviadas e descartadas são arquivos órfãos pequenos (limitação aceita).
     */
    public StoreDtos.UploadedPhoto uploadPanelPhoto(Long storeId, MultipartFile file) {
        String path = files.storePhoto(storeId, file, 2400);
        return new StoreDtos.UploadedPhoto(path, StoreDtos.fileUrl(path));
    }

    @Transactional
    public StoreDtos.AppearanceView updateAppearance(Long storeId, StoreDtos.AppearanceRequest r) {
        Store s = get(storeId);
        String newPath = r.imagePath() == null || r.imagePath().isBlank() ? null : r.imagePath().trim();
        // o caminho precisa ser um arquivo desta loja: nunca de outra loja nem fora da pasta de uploads
        if (newPath != null && !files.belongsTo(storeId, newPath)) {
            throw Errors.badRequest("INVALID_IMAGE", "Fotografia inválida. Envie a imagem novamente.");
        }
        if (r.mode() == Store.PanelBackground.PHOTO && newPath == null) {
            throw Errors.badRequest("IMAGE_REQUIRED", "Envie uma fotografia ou escolha o fundo de cor sólida.");
        }
        String old = s.getPanelBgImagePath();
        if (old != null && !old.equals(newPath)) {
            files.delete(old);
        }
        s.setPanelBgMode(r.mode());
        s.setPanelBgImagePath(newPath);
        s.setPanelBgColor(r.color().toUpperCase(Locale.ROOT));
        s.setPanelBgPosX(r.posX());
        s.setPanelBgPosY(r.posY());
        s.setPanelBgDim(r.dim());
        s.setPanelGlassOpacity(r.glass());
        audit.record(storeId, "STORE", storeId, "APPEARANCE_UPDATED", r.mode() + ", escurecimento " + r.dim() + "%, vidro " + r.glass() + "%");
        return StoreDtos.AppearanceView.of(s);
    }

    @Transactional
    public StoreDtos.AppearanceView resetAppearance(Long storeId) {
        Store s = get(storeId);
        files.delete(s.getPanelBgImagePath());
        s.setPanelBgMode(Store.PanelBackground.COLOR);
        s.setPanelBgImagePath(null);
        s.setPanelBgColor(Store.DEFAULT_PANEL_COLOR);
        s.setPanelBgPosX(50);
        s.setPanelBgPosY(50);
        s.setPanelBgDim(Store.DEFAULT_PANEL_DIM);
        s.setPanelGlassOpacity(Store.DEFAULT_PANEL_GLASS);
        audit.record(storeId, "STORE", storeId, "APPEARANCE_RESET", null);
        return StoreDtos.AppearanceView.of(s);
    }

    private static void requireReadable(String color, String label) {
        if (!ColorContrast.isReadable(color)) {
            throw Errors.rule("LOW_CONTRAST",
                    "A cor " + label + " não oferece contraste suficiente com texto claro nem escuro. Escolha um tom mais claro ou mais escuro.");
        }
    }

    private static String trim(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
