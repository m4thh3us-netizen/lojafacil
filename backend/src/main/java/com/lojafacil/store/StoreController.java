package com.lojafacil.store;

import jakarta.validation.Valid;

import org.springframework.data.domain.Sort;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.lojafacil.shared.audit.AuditService;
import com.lojafacil.shared.audit.AuditService.AuditView;
import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.shared.web.PageResponse;
import com.lojafacil.shared.web.Pages;
import com.lojafacil.store.StoreDtos.PublicStoreView;
import com.lojafacil.store.StoreDtos.StoreSettingsRequest;
import com.lojafacil.store.StoreDtos.StoreSettingsView;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Loja e personalização")
public class StoreController {

    private final StoreService service;
    private final CurrentUser currentUser;
    private final AuditService audit;

    public StoreController(StoreService service, CurrentUser currentUser, AuditService audit) {
        this.service = service;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @GetMapping("/api/admin/store")
    @PreAuthorize(Access.OPERATIONS)
    public StoreSettingsView settings() {
        return service.settings(currentUser.storeId());
    }

    @PutMapping("/api/admin/store")
    @PreAuthorize(Access.MANAGEMENT)
    public StoreSettingsView update(@Valid @RequestBody StoreSettingsRequest req) {
        return service.update(currentUser.storeId(), req);
    }

    @PostMapping("/api/admin/store/theme/reset")
    @PreAuthorize(Access.MANAGEMENT)
    public StoreSettingsView resetTheme() {
        return service.resetTheme(currentUser.storeId());
    }

    @PostMapping(value = "/api/admin/store/images/{kind}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Access.MANAGEMENT)
    public StoreSettingsView upload(@PathVariable String kind, @RequestPart("file") MultipartFile file) {
        return service.uploadImage(currentUser.storeId(), parseKind(kind), file);
    }

    @DeleteMapping("/api/admin/store/images/{kind}")
    @PreAuthorize(Access.MANAGEMENT)
    public StoreSettingsView removeImage(@PathVariable String kind) {
        return service.removeImage(currentUser.storeId(), parseKind(kind));
    }

    @GetMapping("/api/admin/store/appearance")
    @PreAuthorize(Access.OPERATIONS)
    public StoreDtos.AppearanceView appearance() {
        return service.appearance(currentUser.storeId());
    }

    @PutMapping("/api/admin/store/appearance")
    @PreAuthorize(Access.MANAGEMENT)
    public StoreDtos.AppearanceView updateAppearance(@Valid @RequestBody StoreDtos.AppearanceRequest req) {
        return service.updateAppearance(currentUser.storeId(), req);
    }

    @PostMapping(value = "/api/admin/store/appearance/photo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize(Access.MANAGEMENT)
    public StoreDtos.UploadedPhoto uploadPanelPhoto(@RequestPart("file") MultipartFile file) {
        return service.uploadPanelPhoto(currentUser.storeId(), file);
    }

    @PostMapping("/api/admin/store/appearance/reset")
    @PreAuthorize(Access.MANAGEMENT)
    public StoreDtos.AppearanceView resetAppearance() {
        return service.resetAppearance(currentUser.storeId());
    }

    @GetMapping("/api/admin/audit")
    @PreAuthorize(Access.MANAGEMENT)
    public PageResponse<AuditView> auditLog(@RequestParam(defaultValue = "0") int page,
                                            @RequestParam(defaultValue = "30") int size) {
        return PageResponse.of(audit.list(currentUser.storeId(), Pages.of(page, size, Sort.unsorted())), AuditView::of);
    }

    @GetMapping("/api/public/stores/{slug}")
    public PublicStoreView publicStore(@PathVariable String slug) {
        return service.publicView(slug);
    }

    private static StoreService.ImageKind parseKind(String kind) {
        return "banner".equalsIgnoreCase(kind) ? StoreService.ImageKind.BANNER : StoreService.ImageKind.LOGO;
    }
}
