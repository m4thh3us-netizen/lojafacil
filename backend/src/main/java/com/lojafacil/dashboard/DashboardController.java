package com.lojafacil.dashboard;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.lojafacil.shared.security.Access;
import com.lojafacil.shared.security.AuthUser;
import com.lojafacil.shared.security.CurrentUser;
import com.lojafacil.store.StoreService;

import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Dashboard")
public class DashboardController {
    private final DashboardService dashboard;
    private final CurrentUser currentUser;
    private final StoreService stores;

    public DashboardController(DashboardService dashboard, CurrentUser currentUser, StoreService stores) {
        this.dashboard = dashboard;
        this.currentUser = currentUser;
        this.stores = stores;
    }

    @GetMapping("/api/admin/dashboard")
    @PreAuthorize(Access.OPERATIONS)
    public DashboardService.Dashboard get(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        AuthUser user = currentUser.get();
        LocalDate end = to != null ? to : LocalDate.now(DashboardService.ZONE);
        LocalDate start = from != null ? from : end.minusDays(29);
        return dashboard.build(user.storeId(), user.role(), start, end, stores.get(user.storeId()).isDemo());
    }
}
