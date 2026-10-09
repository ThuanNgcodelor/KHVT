package com.example.quanlymuahang.identity;

import com.example.quanlymuahang.identity.application.ApplicationModuleCatalog;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationModuleCatalogTest {
    @Test
    void wildcardGrantsAllRegisteredModules() {
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("*")))
                .extracting(ApplicationModuleCatalog.ModuleView::code)
                .containsExactly("PURCHASING", "PERSONNEL", "ADMINISTRATION");
    }

    @Test
    void purchasingRequiresBothReadPermissionsAndNotADeclaredRoleName() {
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("PO_READ"))).isEmpty();
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("CATALOG_READ"))).isEmpty();
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("ROLE_ADMIN"))).isEmpty();
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("PO_READ", "CATALOG_READ")))
                .extracting(ApplicationModuleCatalog.ModuleView::code).containsExactly("PURCHASING");
    }

    @Test
    void permissionsCanGrantSeveralModulesWithoutWildcard() {
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("PO_READ", "CATALOG_READ", "PERSONNEL_READ", "USER_READ")))
                .extracting(ApplicationModuleCatalog.ModuleView::code)
                .containsExactly("PURCHASING", "PERSONNEL", "ADMINISTRATION");
    }

    @Test
    void unknownPermissionsDoNotExposeUnimplementedApplications() {
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("SALES_READ"))).isEmpty();
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of())).isEmpty();
    }
}
