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
                .containsExactly("PURCHASING", "ADMINISTRATION");
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
                .containsExactly("PURCHASING", "ADMINISTRATION");
    }

    @Test
    void directoryManagerEntersAdministrationWithoutAccountManagementPermissions() {
        var modules = ApplicationModuleCatalog.forPermissions(Set.of("PERSONNEL_READ", "PERSONNEL_MANAGE"));
        assertThat(modules).singleElement().satisfies(module -> {
            assertThat(module.code()).isEqualTo("ADMINISTRATION");
            assertThat(module.entryPath()).isEqualTo("/admin/employees");
        });
    }

    @Test
    void accountReaderEntersAccountsAndCombinedPermissionsDoNotDuplicateAdministration() {
        var modules = ApplicationModuleCatalog.forPermissions(Set.of("USER_READ", "PERSONNEL_READ"));
        assertThat(modules).singleElement().satisfies(module -> {
            assertThat(module.code()).isEqualTo("ADMINISTRATION");
            assertThat(module.entryPath()).isEqualTo("/admin/users");
        });
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("*")))
                .filteredOn(module -> module.code().equals("ADMINISTRATION"))
                .singleElement().extracting(ApplicationModuleCatalog.ModuleView::entryPath).isEqualTo("/admin/users");
    }

    @Test
    void unknownPermissionsDoNotExposeUnimplementedApplications() {
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of("SALES_READ"))).isEmpty();
        assertThat(ApplicationModuleCatalog.forPermissions(Set.of())).isEmpty();
    }
}
