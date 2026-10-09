package com.example.quanlymuahang.identity;

import com.example.quanlymuahang.identity.domain.model.RoleCode;
import com.example.quanlymuahang.identity.domain.model.UserAccount;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserAccountTest {

    @Test
    void adminHasWildcardAccess() {
        UserAccount admin = new UserAccount(1L, "admin", true, true);
        admin.assignRole(RoleCode.ADMIN);

        assertThat(admin.can("PERSONNEL_MANAGE")).isTrue();
        assertThat(admin.can("A_PERMISSION_ADDED_LATER")).isTrue();
    }

    @Test
    void plannerCanWorkWithPurchaseOrdersButNotPersonnel() {
        UserAccount planner = new UserAccount(2L, "planner", true, false);
        planner.assignRole(RoleCode.PLANNER);

        assertThat(planner.can("PO_CREATE")).isTrue();
        assertThat(planner.can("PERSONNEL_MANAGE")).isFalse();
    }

    @Test
    void viewerCanReadCatalogAndPricesButCannotEdit() {
        UserAccount viewer = new UserAccount(3L, "viewer", true, false);
        viewer.assignRole(RoleCode.VIEWER);

        assertThat(viewer.can("CATALOG_READ")).isTrue();
        assertThat(viewer.can("PRICE_READ")).isTrue();
        assertThat(viewer.can("CATALOG_MANAGE")).isFalse();
    }
}
